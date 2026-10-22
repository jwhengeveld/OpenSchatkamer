package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HTTP client voor Schatkamer van Beeld & Geluid.
 *
 * Beheert CloudFront signed cookies, vereiste browser headers (User-Agent, Origin, Referer)
 * en caching.
 */
class SchatkamerHttpClient(context: Context) {

    val cookieJar = InMemoryCookieJar()

    private val cacheDir = File(context.applicationContext.cacheDir, "schatkamer_http_cache")
    private val httpCache = Cache(cacheDir, 50L * 1024L * 1024L) // 50 MB

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .cache(httpCache)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(HeaderInterceptor())
        .apply {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            addInterceptor(logging)
        }
        .build()

    suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP request failed with code ${response.code} for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP JSON request failed with code ${response.code} for $url")
            }
            response.body?.string().orEmpty()
        }
    }

    suspend fun getDocument(url: String): Document {
        val html = getHtml(url)
        return Jsoup.parse(html, url)
    }

    companion object {
        const val BASE_URL = "https://schatkamer.beeldengeluid.nl"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: SchatkamerHttpClient? = null

        fun getInstance(context: Context): SchatkamerHttpClient =
            instance ?: synchronized(this) {
                instance ?: SchatkamerHttpClient(context).also { instance = it }
            }
    }
}

/**
 * Onthoudt cookies (zoals CloudFront-Policy, CloudFront-Signature, CloudFront-Key-Pair-Id).
 */
class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val hostMap = cookieStore.computeIfAbsent(host) { ConcurrentHashMap() }
        for (cookie in cookies) {
            hostMap[cookie.name] = cookie
        }
        // CloudFront cookies mogen ook gebruikt worden voor *.cdn.beeldengeluid.nl
        if (host.endsWith("beeldengeluid.nl")) {
            val wildcard = cookieStore.computeIfAbsent("sk-video.cdn.beeldengeluid.nl") { ConcurrentHashMap() }
            for (cookie in cookies) {
                if (cookie.name.startsWith("CloudFront-")) {
                    wildcard[cookie.name] = cookie
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val hostCookies = cookieStore[url.host]?.values ?: emptyList()
        val generalCookies = if (url.host.endsWith("beeldengeluid.nl")) {
            cookieStore["schatkamer.beeldengeluid.nl"]?.values ?: emptyList()
        } else emptyList()

        val all = (hostCookies + generalCookies).distinctBy { it.name }
        return all.filter { it.expiresAt > now }
    }

    fun clear() {
        cookieStore.clear()
    }
}

private class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", SchatkamerHttpClient.USER_AGENT)
        }
        if (original.header("Referer") == null) {
            builder.header("Referer", "https://schatkamer.beeldengeluid.nl/")
        }
        if (original.header("Origin") == null) {
            builder.header("Origin", "https://schatkamer.beeldengeluid.nl")
        }
        if (original.header("Accept") == null) {
            builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        if (original.header("Accept-Language") == null) {
            builder.header("Accept-Language", "nl-NL,nl;q=0.9,en-US;q=0.8,en;q=0.7")
        }

        return chain.proceed(builder.build())
    }
}
