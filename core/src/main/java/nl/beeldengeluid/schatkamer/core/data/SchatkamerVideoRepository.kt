package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository voor het ophalen van programmadetails en afspeelbare video-/audiostreams.
 */
class SchatkamerVideoRepository(context: Context) {

    private val httpClient = SchatkamerHttpClient.getInstance(context)
    private val detailCache = ConcurrentHashMap<String, SchatkamerProgramDetail>()

    fun cachedDetail(programId: String): SchatkamerProgramDetail? =
        detailCache[programId]

    /**
     * Haalt de programmadetails op, inclusief afspeelstream, metadata en afleveringen.
     * [urlOrSlug]: Het volledige webadres of pad naar het programma/aflevering.
     */
    suspend fun getProgramDetail(urlOrSlug: String, programId: String): Result<SchatkamerProgramDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val url = when {
                urlOrSlug.startsWith("http") -> urlOrSlug
                urlOrSlug.startsWith("/") -> "${SchatkamerHttpClient.BASE_URL}$urlOrSlug"
                else -> "${SchatkamerHttpClient.BASE_URL}/program/$programId/$urlOrSlug"
            }

            val html = httpClient.getHtml(url)
            var detail = SchatkamerParser.parseProgramDetail(html, programId)

            // Als de pagina (bijv. een serieoverzicht) zelf geen directe stream bevat maar wel afleveringen:
            if (detail.streamInfo == null && detail.episodes.isNotEmpty()) {
                val firstEp = detail.episodes.first()
                val epUrl = if (firstEp.url.isNotBlank()) {
                    if (firstEp.url.startsWith("http")) firstEp.url else "${SchatkamerHttpClient.BASE_URL}${firstEp.url}"
                } else null

                if (epUrl != null) {
                    runCatching {
                        val epHtml = httpClient.getHtml(epUrl)
                        val epDetail = SchatkamerParser.parseProgramDetail(epHtml, firstEp.id)
                        if (epDetail.streamInfo != null) {
                            detail = detail.copy(
                                streamInfo = epDetail.streamInfo,
                                isPlayable = true
                            )
                        }
                    }
                }
            }

            detailCache[programId] = detail
            detail
        }
    }

    /**
     * Vormt een detail om tot een afspeelbaar Media-item voor ExoPlayer of Chromecast.
     */
    fun toPlayableMedia(detail: SchatkamerProgramDetail, resumePositionMs: Long = 0L): PlayableMedia? {
        val stream = detail.streamInfo ?: return null
        return PlayableMedia(
            title = detail.title,
            subtitle = detail.seriesTitle ?: detail.broadcaster,
            streamUrl = stream.streamUrl,
            posterUrl = stream.posterUrl.ifBlank { detail.imageUrl },
            isLive = false,
            id = detail.id,
            seriesId = detail.seriesId.orEmpty(),
            mediaType = detail.mediaType,
            widevineLicenseUrl = stream.widevineLicenseUrl,
            subtitlesUrl = stream.subtitlesUrl,
            resumePositionMs = resumePositionMs,
            cookieHeader = stream.cookieHeader
        )
    }

    companion object {
        @Volatile
        private var instance: SchatkamerVideoRepository? = null

        fun getInstance(context: Context): SchatkamerVideoRepository =
            instance ?: synchronized(this) {
                instance ?: SchatkamerVideoRepository(context).also { instance = it }
            }
    }
}
