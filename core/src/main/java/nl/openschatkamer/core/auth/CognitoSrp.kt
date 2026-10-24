package nl.openschatkamer.core.auth

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Secure Remote Password zoals Amazon Cognito het gebruikt (USER_SRP_AUTH),
 * dezelfde inlogmethode als de website (Amplify `signIn`). Het wachtwoord
 * verlaat het toestel nooit; alleen een bewijs dat je het kent.
 */
@OptIn(ExperimentalEncodingApi::class)
class CognitoSrp(
    private val poolName: String,
    private val random: SecureRandom = SecureRandom(),
    /** Alleen voor tests: een vaste geheime waarde a. */
    fixedA: BigInteger? = null
) {
    private val a: BigInteger = fixedA ?: generateA()
    val largeA: BigInteger = G.modPow(a, N)

    /** SRP_A voor InitiateAuth, als hexadecimale tekst. */
    val srpA: String get() = largeA.toString(16)

    private fun generateA(): BigInteger {
        while (true) {
            val candidate = BigInteger(1, ByteArray(128).also { random.nextBytes(it) }).mod(N)
            if (G.modPow(candidate, N).mod(N) != BigInteger.ZERO) return candidate
        }
    }

    /**
     * Het antwoord op de PASSWORD_VERIFIER-uitdaging: de handtekening plus de
     * tijdstempel waarmee hij gemaakt is.
     */
    fun passwordVerifier(
        userIdForSrp: String,
        password: String,
        srpB: String,
        salt: String,
        secretBlock: String,
        now: Date = Date()
    ): Pair<String, String> {
        val b = BigInteger(srpB, 16)
        require(b.mod(N) != BigInteger.ZERO) { "Ongeldige SRP_B van de server." }
        val u = BigInteger(1, sha256(pad(largeA), pad(b)))
        require(u != BigInteger.ZERO) { "Ongeldige SRP-parameter u." }

        val userHash = sha256("$poolName$userIdForSrp:$password".toByteArray(Charsets.UTF_8))
        val x = BigInteger(1, sha256(pad(BigInteger(salt, 16)), userHash))
        val s = b.subtract(K.multiply(G.modPow(x, N))).mod(N).modPow(a.add(u.multiply(x)), N)
        val key = hkdf(pad(s), pad(u))

        val timestamp = timestamp(now)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        mac.update(poolName.toByteArray(Charsets.UTF_8))
        mac.update(userIdForSrp.toByteArray(Charsets.UTF_8))
        mac.update(Base64.decode(secretBlock))
        mac.update(timestamp.toByteArray(Charsets.UTF_8))
        return Base64.encode(mac.doFinal()) to timestamp
    }

    companion object {
        /** Het 3072-bits priemgetal uit RFC 5054, dat Cognito gebruikt. */
        val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
            16
        )
        val G: BigInteger = BigInteger.valueOf(2)
        val K = BigInteger(1, sha256(pad(N), pad(G)))

        /** Hex met even lengte en een voorloop-00 als het hoogste bit gezet is (zoals Amplify). */
        fun padHex(value: BigInteger): String {
            var hex = value.toString(16)
            if (hex.length % 2 == 1) hex = "0$hex" else if (hex[0] in "89abcdef") hex = "00$hex"
            return hex
        }

        fun pad(value: BigInteger): ByteArray = hexToBytes(padHex(value))

        fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        fun sha256(vararg parts: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").apply { parts.forEach { update(it) } }.digest()

        /** HKDF-SHA256 met de vaste info-tekst van Cognito; levert 16 bytes. */
        fun hkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
            val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(ikm)
            val info = "Caldera Derived Key".toByteArray(Charsets.UTF_8) + byteArrayOf(1)
            return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }.doFinal(info).copyOf(16)
        }

        /** "Fri Oct 24 6:05:09 UTC 2026": Engelse namen, dag zonder voorloopnul, UTC. */
        fun timestamp(date: Date): String =
            SimpleDateFormat("EEE MMM d HH:mm:ss 'UTC' yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    }
}
