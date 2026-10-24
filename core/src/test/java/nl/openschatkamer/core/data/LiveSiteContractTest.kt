package nl.openschatkamer.core.data

import nl.openschatkamer.core.playback.CastPayload
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Contracttests tegen de echte website: controleren dat alles wat de app
 * van De Schatkamer verwacht er nog is. Alleen met -Plive (of LIVE=1), zodat
 * gewone builds niet van het netwerk afhangen. Een wekelijkse GitHub Action
 * draait ze en meldt het als de site is veranderd.
 */
class LiveSiteContractTest {

    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val base = SchatkamerHttpClient.BASE_URL

    @Before
    fun onlyWhenLive() {
        assumeTrue("Live-tests overgeslagen (gebruik -Plive)", System.getProperty("live") == "true" || System.getenv("LIVE") == "1")
    }

    private fun get(url: String, cookie: String? = null): Pair<Int, String> {
        val request = Request.Builder().url(url).header("User-Agent", SchatkamerHttpClient.USER_AGENT)
            .apply { if (cookie != null) header("Cookie", cookie) }.build()
        http.newCall(request).execute().use { return it.code to it.body?.string().orEmpty() }
    }

    @Test
    fun `startpagina heeft getypeerde rijen`() {
        val (code, html) = get(base)
        assertEquals(200, code)
        val home = SchatkamerParser.parseHome(html)
        assertTrue("primaire route: ${home.rows.size} rijen", home.rows.size >= 5)
        assertTrue("HTML-terugval: werkt ook", SiteFallbacks.homeFromHtml(html).rows.size >= 5)
    }

    @Test
    fun `zoeken, serie, programma, stream en cast`() {
        val (code, json) = get("$base/api/media/bff/search?query=jiskefet&playable=true&limit=5&offset=0")
        assertEquals(200, code)
        val result = SchatkamerParser.parseBffSearch(json, "jiskefet", 0)
        assertTrue(result.total > 100)
        val episode = result.items.first()
        assertTrue(result.series.isNotEmpty())

        val (sCode, sJson) = get("$base/api/media/bff/series/${result.series.first().id}")
        assertEquals(200, sCode)
        assertTrue(SchatkamerParser.parseBffSeries(sJson).title.isNotBlank())

        val (pCode, pJson) = get("$base/api/media/bff/programs/${episode.id}")
        assertEquals(200, pCode)
        assertEquals(episode.id, SchatkamerParser.parseBffProgramDetail(pJson).id)

        val (hCode, html) = get("$base/programma/${episode.id}")
        assertEquals(200, hCode)
        val stream = SchatkamerParser.parseStreamInfo(html) ?: error("Geen programStream meer op de uitzendingspagina")
        val config = SchatkamerParser.parseSiteConfig(html) ?: error("Geen window.__CONFIG__ meer")
        assertEquals("377B8439", config.chromecastAppId)

        val (mCode, manifest) = get(stream.manifestUrl, stream.cookieHeader)
        assertEquals("manifest met CloudFront-cookies", 200, mCode)
        assertTrue(manifest.startsWith("#EXTM3U"))

        val payload = CastPayload.customData(PlayableMedia(episode.id, episode.title, stream = stream), config)
        assertEquals(stream.playUrl, payload.getJSONObject("sourceDescription").getJSONArray("sources").getJSONObject(0).getString("src"))
    }

    @Test
    fun `kinderseries van de kids-sectie bestaan nog`() {
        val ids = nl.openschatkamer.core.ui.KidsViewModel.CLASSICS.map { it.first } +
            nl.openschatkamer.core.ui.KidsViewModel.JEUGDJOURNAAL + nl.openschatkamer.core.ui.KidsViewModel.KLOKHUIS
        val missing = ids.filter { get("$base/api/media/bff/series/$it").first != 200 }
        assertTrue("Kinderseries niet meer gevonden: $missing", missing.isEmpty())
    }

    @Test
    fun `gebruikersdienst vraagt een token`() {
        val (code, body) = get("$base/api/user/bff/lists")
        assertEquals(401, code)
        assertTrue(body.contains("UNAUTHORIZED"))
    }
}
