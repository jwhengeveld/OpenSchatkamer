package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** De website meldt dat iets niet (meer) bestaat. */
class NotFoundException(url: String) : IOException("Niet gevonden: $url")

/** De website gaf een foutcode; meestal tijdelijk. */
class HttpException(val code: Int, url: String) : IOException("HTTP $code voor $url")

/**
 * Haalt pagina's en JSON van de Schatkamer-website op, slim en zuinig
 * (hetzelfde principe als de PageFetcher van Family7):
 *
 * - Gelijktijdige verzoeken voor hetzelfde adres delen één download; de
 *   startpagina, de omroepenlijst en de uitgelichte titels vragen vaak
 *   tegelijk hetzelfde op.
 * - Een antwoord blijft kort in het geheugen, zodat heen en weer navigeren
 *   geen nieuwe verzoeken kost.
 * - Daaronder ligt een HTTP-schijfcache voor afbeeldingen en JSON.
 */
class SchatkamerHttpClient(context: Context) {

    private val httpCache = Cache(File(context.applicationContext.cacheDir, "schatkamer_http_cache"), 50L * 1024 * 1024)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(httpCache)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor(BrowserHeaders)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val inFlight = HashMap<String, Deferred<String>>()
    private val recent = LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true)

    suspend fun getHtml(url: String, memoMs: Long = PAGE_MEMO_MS, fresh: Boolean = false): String =
        fetch(url, ACCEPT_HTML, memoMs, fresh)

    suspend fun getJson(url: String, memoMs: Long = JSON_MEMO_MS, fresh: Boolean = false): String =
        fetch(url, ACCEPT_JSON, memoMs, fresh)

    /** Bouwt een adres op de website met correct gecodeerde parameters (ook "broadcaster[]"). */
    fun url(path: String, params: List<Pair<String, String>> = emptyList()): String {
        val builder = "$BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    fun forget(prefix: String? = null) = synchronized(lock) {
        if (prefix == null) recent.clear() else recent.keys.removeAll { it.startsWith(prefix) }
    }

    private suspend fun fetch(url: String, accept: String, memoMs: Long, fresh: Boolean): String {
        val pending = synchronized(lock) {
            if (!fresh) {
                recent[url]?.let { (at, body) -> if (System.currentTimeMillis() - at < memoMs) return body }
            }
            inFlight[url] ?: scope.async { download(url, accept) }.also { inFlight[url] = it }
        }
        return try {
            pending.await().also { body ->
                synchronized(lock) {
                    recent[url] = System.currentTimeMillis() to body
                    while (recent.size > MEMO_ENTRIES) recent.remove(recent.keys.first())
                }
            }
        } finally {
            synchronized(lock) { if (inFlight[url] === pending) inFlight.remove(url) }
        }
    }

    private fun download(url: String, accept: String): String {
        val request = Request.Builder().url(url).header("Accept", accept).build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> throw NotFoundException(url)
                !response.isSuccessful -> throw HttpException(response.code, url)
            }
            return response.body?.string().orEmpty()
        }
    }

    private object BrowserHeaders : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT)
            if (original.header("Accept-Language") == null) builder.header("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.6")
            if (original.url.host == HOST && original.header("Referer") == null) builder.header("Referer", "$BASE_URL/")
            return chain.proceed(builder.build())
        }
    }

    companion object {
        const val HOST = "schatkamer.beeldengeluid.nl"
        const val BASE_URL = "https://$HOST"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        private const val ACCEPT_HTML = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json"
        private const val PAGE_MEMO_MS = 30_000L
        private const val JSON_MEMO_MS = 10 * 60_000L
        private const val MEMO_ENTRIES = 120

        @Volatile
        private var instance: SchatkamerHttpClient? = null

        fun getInstance(context: Context): SchatkamerHttpClient =
            instance ?: synchronized(this) {
                instance ?: SchatkamerHttpClient(context).also { instance = it }
            }
    }
}
