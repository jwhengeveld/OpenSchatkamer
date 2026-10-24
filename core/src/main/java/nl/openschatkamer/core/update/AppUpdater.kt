package nl.openschatkamer.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Updates vanuit de GitHub-releases (de app staat niet in de Play Store).
 * Kijkt naar de nieuwste release, kiest de APK voor dit platform, downloadt die
 * en laat Android hem installeren; de gebruiker bevestigt in het systeemvenster.
 */
object AppUpdater {

    const val RELEASES = "https://github.com/jwhengeveld/OpenSchatkamer/releases"
    private const val LATEST = "https://api.github.com/repos/jwhengeveld/OpenSchatkamer/releases/latest"

    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val pageUrl: String)

    /** De nieuwste release met een APK die begint met [assetPrefix] ("openschatkamer-tv-"). */
    suspend fun latest(client: OkHttpClient, assetPrefix: String): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            client.newCall(request).execute().use { r ->
                check(r.isSuccessful) { "GitHub antwoordde met ${r.code}" }
                parse(r.body?.string().orEmpty(), assetPrefix) ?: error("Geen APK in de nieuwste release")
            }
        }
    }

    internal fun parse(json: String, assetPrefix: String): Release? {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").startsWith(assetPrefix) && it.optString("name").endsWith(".apk") } ?: return null
        return Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = o.optString("body"),
            apkUrl = apk.optString("browser_download_url"),
            size = apk.optLong("size"),
            pageUrl = o.optString("html_url").ifBlank { RELEASES }
        )
    }

    /** Of [remote] nieuwer is dan [current] ("2.3.10" > "2.3.9"). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloadt de APK naar de cache; [onProgress] krijgt 0..1. */
    suspend fun download(context: Context, client: OkHttpClient, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = File(dir, "openschatkamer-${release.version}.apk")
            client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { r ->
                check(r.isSuccessful) { "Download mislukt (${r.code})" }
                val body = r.body ?: error("Lege download")
                val total = body.contentLength().takeIf { it > 0 } ?: release.size
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            file
        }

    /** Geeft de APK aan de systeeminstaller; Android vraagt de gebruiker om bevestiging. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("openschatkamer.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
        }
    }
}

/** Antwoord van de installer: vraagt hij om bevestiging, dan tonen we zijn venster. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> UpdateEvents.failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}

/** Foutmeldingen van de installer naar het scherm. */
object UpdateEvents {
    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun failed(message: String?) { error.value = message ?: "Installeren lukte niet." }
}
