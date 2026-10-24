package nl.openschatkamer.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.SchatkamerHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Wie er is ingelogd met een Schatkamer-account. */
data class Account(val email: String, val name: String, val userId: String)

/** Uitkomst van een inlogpoging. */
sealed interface SignInResult {
    data class SignedIn(val account: Account) : SignInResult
    /** Er is een extra code nodig (sms, e-mail of authenticator-app). */
    data class NeedsCode(val challenge: String, val session: String, val username: String, val hint: String) : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Inloggen met je account van De Schatkamer, op dezelfde manier als de
 * website (Amazon Cognito, SRP). Het wachtwoord wordt nergens opgeslagen;
 * alleen het verversingstoken, versleuteld met een sleutel uit de Android
 * Keystore.
 */
@OptIn(ExperimentalEncodingApi::class)
class SchatkamerAccount(context: Context) {

    private val http = SchatkamerHttpClient.getInstance(context).client
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _account = MutableStateFlow(storedAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    @Volatile private var accessToken: String? = null
    @Volatile private var accessExpiresAt: Long = 0

    val isSignedIn: Boolean get() = _account.value != null

    // -------------------------------------------------------------- inloggen

    suspend fun signIn(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        val username = email.trim()
        if (username.isBlank() || password.isEmpty()) return@withContext SignInResult.Failed("Vul je e-mailadres en wachtwoord in.")
        runCatching {
            val srp = CognitoSrp(POOL_ID.substringAfter('_'))
            val start = cognito(
                "InitiateAuth",
                JSONObject()
                    .put("AuthFlow", "USER_SRP_AUTH")
                    .put("ClientId", CLIENT_ID)
                    .put("AuthParameters", JSONObject().put("USERNAME", username).put("SRP_A", srp.srpA))
            )
            if (start.optString("ChallengeName") != "PASSWORD_VERIFIER") return@runCatching handleResult(start, username)
            val params = start.getJSONObject("ChallengeParameters")
            val userId = params.getString("USER_ID_FOR_SRP")
            val (signature, timestamp) = srp.passwordVerifier(
                userIdForSrp = userId,
                password = password,
                srpB = params.getString("SRP_B"),
                salt = params.getString("SALT"),
                secretBlock = params.getString("SECRET_BLOCK")
            )
            val answer = cognito(
                "RespondToAuthChallenge",
                JSONObject()
                    .put("ChallengeName", "PASSWORD_VERIFIER")
                    .put("ClientId", CLIENT_ID)
                    .put(
                        "ChallengeResponses",
                        JSONObject()
                            .put("USERNAME", userId)
                            .put("PASSWORD_CLAIM_SECRET_BLOCK", params.getString("SECRET_BLOCK"))
                            .put("PASSWORD_CLAIM_SIGNATURE", signature)
                            .put("TIMESTAMP", timestamp)
                    )
                    .apply { start.optString("Session").takeIf { it.isNotBlank() }?.let { put("Session", it) } }
            )
            handleResult(answer, userId)
        }.getOrElse { SignInResult.Failed(describe(it)) }
    }

    /** Tweede stap: de code uit de sms, e-mail of authenticator-app. */
    suspend fun confirmCode(pending: SignInResult.NeedsCode, code: String): SignInResult = withContext(Dispatchers.IO) {
        runCatching {
            val key = when (pending.challenge) {
                "SMS_MFA" -> "SMS_MFA_CODE"
                "SOFTWARE_TOKEN_MFA" -> "SOFTWARE_TOKEN_MFA_CODE"
                "EMAIL_OTP" -> "EMAIL_OTP_CODE"
                else -> "ANSWER"
            }
            val answer = cognito(
                "RespondToAuthChallenge",
                JSONObject()
                    .put("ChallengeName", pending.challenge)
                    .put("ClientId", CLIENT_ID)
                    .put("Session", pending.session)
                    .put("ChallengeResponses", JSONObject().put("USERNAME", pending.username).put(key, code.trim()))
            )
            handleResult(answer, pending.username)
        }.getOrElse { SignInResult.Failed(describe(it)) }
    }

    private fun handleResult(response: JSONObject, username: String): SignInResult {
        response.optJSONObject("AuthenticationResult")?.let { result ->
            val account = storeTokens(result, keepRefresh = null)
            return SignInResult.SignedIn(account)
        }
        val challenge = response.optString("ChallengeName")
        val session = response.optString("Session")
        return when (challenge) {
            "SMS_MFA", "SOFTWARE_TOKEN_MFA", "EMAIL_OTP" -> SignInResult.NeedsCode(
                challenge, session, username,
                when (challenge) {
                    "SMS_MFA" -> "Vul de code in die je per sms hebt ontvangen."
                    "EMAIL_OTP" -> "Vul de code in die je per e-mail hebt ontvangen."
                    else -> "Vul de code uit je authenticator-app in."
                }
            )
            "NEW_PASSWORD_REQUIRED" -> SignInResult.Failed("Je moet eerst een nieuw wachtwoord kiezen op schatkamer.beeldengeluid.nl.")
            else -> SignInResult.Failed("Inloggen vraagt een stap die de app (nog) niet kent ($challenge). Log in op de website.")
        }
    }

    fun signOut() {
        val token = accessToken
        accessToken = null
        accessExpiresAt = 0
        prefs.edit().clear().apply()
        _account.value = null
        // Het token ook bij Cognito intrekken; mislukt dat, dan verloopt het vanzelf.
        if (token != null) Thread { runCatching { cognito("GlobalSignOut", JSONObject().put("AccessToken", token)) } }.start()
    }

    // ---------------------------------------------------------------- tokens

    /** Een geldig toegangstoken, zo nodig ververst; null als je niet (meer) bent ingelogd. */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!forceRefresh && accessToken != null && System.currentTimeMillis() < accessExpiresAt - 60_000) return@withContext accessToken
            val refresh = decrypt(prefs.getString(KEY_REFRESH, null)) ?: return@withContext null
            runCatching {
                val response = cognito(
                    "InitiateAuth",
                    JSONObject().put("AuthFlow", "REFRESH_TOKEN_AUTH").put("ClientId", CLIENT_ID)
                        .put("AuthParameters", JSONObject().put("REFRESH_TOKEN", refresh))
                )
                storeTokens(response.getJSONObject("AuthenticationResult"), keepRefresh = refresh)
                accessToken
            }.getOrElse { error ->
                // Verversingstoken ingetrokken of verlopen: netjes uitloggen.
                if (error is CognitoException && error.type == "NotAuthorizedException") signOut()
                null
            }
        }
    }

    private fun storeTokens(result: JSONObject, keepRefresh: String?): Account {
        accessToken = result.getString("AccessToken")
        accessExpiresAt = System.currentTimeMillis() + result.optLong("ExpiresIn", 3600) * 1000
        val claims = jwtClaims(result.optString("IdToken"))
        val account = Account(
            email = claims.optString("email"),
            name = claims.optString("given_name").ifBlank { claims.optString("name") }.ifBlank { claims.optString("email").substringBefore('@') },
            userId = claims.optString("sub")
        )
        val refresh = result.optString("RefreshToken").ifBlank { keepRefresh }
        prefs.edit()
            .putString(KEY_REFRESH, encrypt(refresh))
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.name)
            .putString(KEY_SUB, account.userId)
            .apply()
        _account.value = account
        return account
    }

    private fun storedAccount(): Account? {
        val sub = prefs.getString(KEY_SUB, null) ?: return null
        if (prefs.getString(KEY_REFRESH, null) == null) return null
        return Account(prefs.getString(KEY_EMAIL, "").orEmpty(), prefs.getString(KEY_NAME, "").orEmpty(), sub)
    }

    private fun jwtClaims(jwt: String): JSONObject = runCatching {
        val part = jwt.split('.')[1]
        JSONObject(String(Base64.UrlSafe.decode(part + "=".repeat((4 - part.length % 4) % 4))))
    }.getOrDefault(JSONObject())

    // --------------------------------------------------------------- Cognito

    class CognitoException(val type: String, message: String) : IOException(message)

    private fun cognito(action: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("https://cognito-idp.${POOL_ID.substringBefore('_')}.amazonaws.com/")
            .header("X-Amz-Target", "AWSCognitoIdentityProviderService.$action")
            .header("Content-Type", "application/x-amz-json-1.1")
            .post(body.toString().toRequestBody("application/x-amz-json-1.1".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) {
                val type = json.optString("__type").substringAfterLast('#')
                throw CognitoException(type, json.optString("message", "HTTP ${response.code}"))
            }
            return json
        }
    }

    private fun describe(error: Throwable): String = when ((error as? CognitoException)?.type) {
        "NotAuthorizedException" -> "E-mailadres of wachtwoord klopt niet."
        "UserNotFoundException" -> "E-mailadres of wachtwoord klopt niet."
        "UserNotConfirmedException" -> "Bevestig eerst je e-mailadres via de link of code van De Schatkamer."
        "PasswordResetRequiredException" -> "Stel eerst een nieuw wachtwoord in op schatkamer.beeldengeluid.nl."
        "CodeMismatchException" -> "Die code klopt niet. Probeer het opnieuw."
        "ExpiredCodeException" -> "De code is verlopen. Log opnieuw in."
        "TooManyRequestsException", "LimitExceededException" -> "Te veel pogingen. Wacht even en probeer het opnieuw."
        else -> if (error is IOException && error !is CognitoException) "Geen verbinding. Controleer je internet."
        else error.message ?: "Inloggen lukte niet."
    }

    // ------------------------------------------------------------ versleuteling

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String?): String? = plain?.let {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            Base64.encode(cipher.iv) + ":" + Base64.encode(cipher.doFinal(it.toByteArray()))
        }.getOrNull()
    }

    private fun decrypt(stored: String?): String? = stored?.let {
        runCatching {
            val (iv, data) = it.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv)))
            }
            String(cipher.doFinal(Base64.decode(data)))
        }.getOrNull()
    }

    companion object {
        /** De Cognito-gebruikerspool van De Schatkamer (openbaar, uit de configuratie van de website). */
        const val POOL_ID = "eu-central-1_YfwXTqT21"
        const val CLIENT_ID = "78g5ccohfekr34114l6t03k96g"
        const val USER_API = "https://schatkamer.beeldengeluid.nl/api/user/bff"

        private const val KEY_ALIAS = "openschatkamer-account"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_SUB = "sub"

        @Volatile
        private var instance: SchatkamerAccount? = null

        fun getInstance(context: Context): SchatkamerAccount =
            instance ?: synchronized(this) {
                instance ?: SchatkamerAccount(context).also { instance = it }
            }
    }
}
