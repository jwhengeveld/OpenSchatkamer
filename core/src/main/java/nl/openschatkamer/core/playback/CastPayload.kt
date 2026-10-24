package nl.openschatkamer.core.playback

import nl.openschatkamer.core.BuildConfig
import nl.openschatkamer.core.data.PlayableMedia
import nl.openschatkamer.core.data.SiteConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Het LOAD-bericht voor de Chromecast-receiver van de Schatkamer (377B8439).
 *
 * Die receiver draait THEOplayer en leest uitsluitend `customData` van het
 * LOAD-verzoek zelf: `configuration` (waar THEOplayer staat en de licentie) en
 * `sourceDescription` (de bron). We bouwen die precies na zoals de website hem
 * maakt:
 *
 * - `src` is het ondertekende adres ongewijzigd. CloudFront antwoordt daarop
 *   met een doorverwijzing én toegangscookies (SameSite=None) en staat de
 *   receiver expliciet toe via CORS. Daarom `useCredentials` en
 *   `network.cors.credentials = include`: zo krijgt de Chromecast toegang tot
 *   alle playlists en fragmenten.
 * - `clip` beperkt het afspelen tot het programma binnen de archiefband. De
 *   receiver rekent posities en duur vanaf het begin van de clip, net als onze
 *   lokale speler, dus overschakelen houdt dezelfde positie aan.
 */
object CastPayload {

    const val CONTENT_TYPE = "application/x-mpegurl"

    fun customData(media: PlayableMedia, config: SiteConfig): JSONObject {
        val stream = media.stream

        val source = JSONObject()
            .put("src", stream.playUrl)
            .put("type", CONTENT_TYPE)
            .put("useCredentials", stream.playUrl.contains('?'))
        if (stream.drmProtected) source.put("contentProtection", ezdrm())

        val textTracks = JSONArray()
        stream.subtitlesUrl?.let {
            textTracks.put(
                JSONObject().put("src", it).put("kind", "subtitles").put("srclang", "nl")
                    .put("label", "Nederlands").put("default", false).put("crossOrigin", "anonymous")
            )
        }

        val sourceDescription = JSONObject()
            .put("sources", JSONArray().put(source))
            .put("network", JSONObject().put("cors", JSONObject().put("credentials", "include")))
        // Zonder bekend einde laten we de clip weg; de receiver neemt dan de hele band.
        if (stream.clipEndSec > stream.clipStartSec) {
            sourceDescription.put("clip", JSONObject().put("startTime", stream.clipStartSec).put("endTime", stream.clipEndSec))
        }
        if (media.posterUrl.isNotBlank()) sourceDescription.put("poster", media.posterUrl)
        if (textTracks.length() > 0) sourceDescription.put("textTracks", textTracks)

        return JSONObject()
            .put(
                "configuration",
                JSONObject()
                    .put("libraryLocation", config.theoplayerLibraryLocation)
                    .put("license", config.theoplayerLicense)
            )
            .put("sourceDescription", sourceDescription)
            .put("isLive", false)
    }

    private fun ezdrm(): JSONObject = JSONObject()
        .put("integration", "ezdrm")
        .put("widevine", JSONObject().put("licenseAcquisitionURL", BuildConfig.WIDEVINE_LICENSE_URL))
        .put("playready", JSONObject().put("licenseAcquisitionURL", BuildConfig.PLAYREADY_LICENSE_URL))
        .put(
            "fairplay",
            JSONObject()
                .put("licenseAcquisitionURL", BuildConfig.FAIRPLAY_LICENSE_URL)
                .put("certificateURL", BuildConfig.FAIRPLAY_CERTIFICATE_URL)
        )
}
