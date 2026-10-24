package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/** Er valt niets af te spelen: niet vrijgegeven, of de site gaf geen stream. */
class NotPlayableException : IllegalStateException("Dit programma is (nog) niet af te spelen in de Schatkamer.")

/**
 * Programmagegevens en afspeelbare streams.
 *
 * De gegevens van een uitzending komen uit de JSON-dienst. De stream zelf
 * staat alleen in de uitzendingspagina en is ondertekend met een
 * houdbaarheidsdatum; we halen hem daarom pas bij het afspelen op en
 * hergebruiken hem tot kort voor hij verloopt.
 */
class SchatkamerVideoRepository(context: Context) {

    private val http = SchatkamerHttpClient.getInstance(context)
    private val prefs = context.applicationContext.getSharedPreferences("site_config", Context.MODE_PRIVATE)
    private val details = ConcurrentHashMap<String, SchatkamerProgramDetail>()
    private val streams = ConcurrentHashMap<String, SchatkamerStreamInfo>()

    fun cachedDetail(programId: String): SchatkamerProgramDetail? = details[programId]

    suspend fun getProgramDetail(programId: String): Result<SchatkamerProgramDetail> = withContext(Dispatchers.IO) {
        runCatching {
            SchatkamerParser.parseBffProgramDetail(http.getJson(http.url("/api/media/bff/programs/$programId")))
                .also { details[programId] = it }
        }
    }

    /** Een geldige stream voor [programId]; ververst automatisch als de handtekening bijna verloopt. */
    suspend fun resolveStream(programId: String, forceRefresh: Boolean = false): Result<SchatkamerStreamInfo> =
        withContext(Dispatchers.IO) {
            streams[programId]?.takeIf { !forceRefresh && !it.isExpired() }?.let { return@withContext Result.success(it) }
            runCatching {
                // /programma/{id} stuurt door naar de juiste serie-/afleveringspagina.
                val html = http.getHtml(http.url("/programma/$programId"), fresh = true)
                SchatkamerParser.parseSiteConfig(html)?.let(::rememberSiteConfig)
                val stream = SchatkamerParser.parseStreamInfo(html) ?: throw NotPlayableException()
                streams[programId] = stream
                stream
            }
        }

    /** Alles wat de spelers nodig hebben, in één keer. */
    suspend fun playable(programId: String, resumePositionMs: Long = 0L): Result<PlayableMedia> = runCatching {
        val detail = details[programId] ?: getProgramDetail(programId).getOrThrow()
        val stream = resolveStream(programId).getOrThrow()
        PlayableMedia(
            id = detail.id.ifBlank { programId },
            title = detail.title,
            subtitle = detail.seriesTitle ?: detail.broadcaster,
            seriesId = detail.seriesId,
            posterUrl = detail.imageUrl.ifBlank { stream.posterUrl },
            mediaType = detail.mediaType,
            broadcaster = detail.broadcaster,
            stream = stream,
            resumePositionMs = resumePositionMs
        )
    }

    /**
     * De spelerinstellingen van de website. We lezen ze bij elke stream mee uit
     * de pagina, zodat een nieuwe licentie of receiver vanzelf wordt opgepikt;
     * de waarden uit de build zijn alleen de terugval.
     */
    fun siteConfig(): SiteConfig = SiteConfig(
        theoplayerLicense = prefs.getString(KEY_LICENSE, null) ?: BuildConfig.THEOPLAYER_LICENSE,
        theoplayerCdnKey = prefs.getString(KEY_CDN, null) ?: BuildConfig.THEOPLAYER_CDN_KEY,
        chromecastAppId = prefs.getString(KEY_APP, null) ?: BuildConfig.CAST_RECEIVER_ID
    )

    private fun rememberSiteConfig(config: SiteConfig) {
        prefs.edit()
            .putString(KEY_LICENSE, config.theoplayerLicense)
            .putString(KEY_CDN, config.theoplayerCdnKey)
            .apply { if (config.chromecastAppId.isNotBlank()) putString(KEY_APP, config.chromecastAppId) }
            .apply()
    }

    companion object {
        private const val KEY_LICENSE = "theoplayer_license"
        private const val KEY_CDN = "theoplayer_cdn_key"
        private const val KEY_APP = "chromecast_app_id"

        @Volatile
        private var instance: SchatkamerVideoRepository? = null

        fun getInstance(context: Context): SchatkamerVideoRepository =
            instance ?: synchronized(this) {
                instance ?: SchatkamerVideoRepository(context).also { instance = it }
            }
    }
}
