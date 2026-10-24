package nl.openschatkamer.core.data

import nl.openschatkamer.core.playback.CastPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/** Tests op echte pagina's en JSON van de Schatkamer (opgeslagen in test/resources/fixtures). */
class SchatkamerParserTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
        val input = if (name.endsWith(".gz")) GZIPInputStream(stream) else stream
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `startpagina levert getypeerde rijen`() {
        val home = SchatkamerParser.parseHome(fixture("home.html.gz"))
        assertTrue("verwacht meerdere rijen, kreeg ${home.rows.size}", home.rows.size >= 10)

        val kinds = home.rows.map { it.kind }.toSet()
        assertTrue(ItemKind.SERIES in kinds)
        assertTrue(ItemKind.PROGRAM in kinds)
        assertTrue(ItemKind.STORY in kinds)
        assertTrue(ItemKind.PERSON in kinds)
        assertTrue(ItemKind.BROADCASTER in kinds)

        val series = home.rows.first { it.kind == ItemKind.SERIES }
        assertEquals(RowLayout.PORTRAIT, series.layout)
        assertTrue(series.items.all { it.seriesId == it.id })

        val ranked = home.rows.first { it.layout == RowLayout.RANKED }
        assertTrue(ranked.items.all { it.kind == ItemKind.PROGRAM })

        // Een uitzending binnen een serie krijgt het programma-id, niet het serie-id.
        val episode = home.rows.flatMap { it.items }.first { it.kind == ItemKind.PROGRAM && it.seriesId != null }
        assertFalse(episode.id == episode.seriesId)

        assertTrue(home.heroItems.isNotEmpty())
        assertTrue(home.broadcasters.isNotEmpty())
        assertTrue(home.rows.flatMap { it.items }.none { it.imageUrl.contains("\$undefined") })
    }

    @Test
    fun `stream behoudt het ondertekende adres en leidt cookies en clip af`() {
        val stream = SchatkamerParser.parseStreamInfo(fixture("episode.html.gz"))
        assertNotNull(stream)
        stream!!
        assertTrue(stream.playUrl.startsWith("https://sk-video.cdn.beeldengeluid.nl/"))
        assertTrue(stream.playUrl.contains("CloudFront-Policy="))
        assertTrue("canned signature moet behouden blijven", stream.playUrl.contains("&Key-Pair-Id="))
        assertFalse(stream.playUrl.contains("\\u0026"))
        assertTrue(stream.manifestUrl.endsWith("/master.m3u8"))

        val cookies = stream.cookieHeader.split("; ").map { it.substringBefore('=') }
        assertEquals(listOf("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id"), cookies)

        assertEquals(5.0, stream.clipStartSec, 0.001)
        assertEquals(1529.52, stream.clipEndSec, 0.001)
        assertEquals(1_524_520L, stream.clipDurationMs)
        assertTrue(stream.drmProtected)
        assertTrue(stream.expiresAtSec > 1_700_000_000L)
        assertNotNull(stream.storyboardUrl)
    }

    @Test
    fun `tekstblok direct achter een ander tekstblok wordt gevonden`() {
        // Hier staat "...naar huis gaan.27:T458,https://..." zonder regeleinde ertussen.
        val stream = SchatkamerParser.parseStreamInfo(fixture("episode_adjacent_chunk.html.gz"))
        assertNotNull(stream)
        assertTrue(stream!!.playUrl.startsWith("https://sk-video.cdn.beeldengeluid.nl/LOLADAMUSICA"))
        assertTrue(stream.cookieHeader.contains("CloudFront-Key-Pair-Id="))
    }

    @Test
    fun `site-config komt uit window CONFIG`() {
        val config = SchatkamerParser.parseSiteConfig(fixture("episode.html.gz"))
        assertNotNull(config)
        assertEquals("377B8439", config!!.chromecastAppId)
        assertEquals("fb21074d-a7cd-4fef-9206-e490101d6570", config.theoplayerCdnKey)
        assertTrue(config.theoplayerLicense.startsWith("sZP7IYe6T6"))
        assertEquals("https://cdn.myth.theoplayer.com/fb21074d-a7cd-4fef-9206-e490101d6570/", config.theoplayerLibraryLocation)
    }

    @Test
    fun `cast-payload volgt het formaat van de website`() {
        val stream = SchatkamerParser.parseStreamInfo(fixture("episode.html.gz"))!!
        val config = SchatkamerParser.parseSiteConfig(fixture("episode.html.gz"))!!
        val media = PlayableMedia(id = "1", title = "Test", stream = stream, posterUrl = "https://x/poster.jpg")
        val data = CastPayload.customData(media, config)

        assertEquals(config.theoplayerLibraryLocation, data.getJSONObject("configuration").getString("libraryLocation"))
        val description = data.getJSONObject("sourceDescription")
        val source = description.getJSONArray("sources").getJSONObject(0)
        assertEquals(stream.playUrl, source.getString("src"))
        assertTrue(source.getBoolean("useCredentials"))
        assertEquals("ezdrm", source.getJSONObject("contentProtection").getString("integration"))
        assertEquals("include", description.getJSONObject("network").getJSONObject("cors").getString("credentials"))
        assertEquals(5.0, description.getJSONObject("clip").getDouble("startTime"), 0.001)
    }

    @Test
    fun `zoekresultaten krijgen bruikbare titels en series`() {
        val result = SchatkamerParser.parseBffSearch(fixture("search_jiskefet.json"), "jiskefet", 0)
        assertEquals(418, result.total)
        assertTrue(result.hasMore)
        assertTrue(result.items.none { it.title.isBlank() })
        assertTrue(result.items.none { it.description.startsWith("EXTERNE INFORMATIE") })
        assertEquals("Jiskefet", result.series.first().title)
        assertEquals(ItemKind.SERIES, result.series.first().kind)
        assertTrue(result.facets.genres.any { it.label == "satire" })
        assertTrue(result.facets.programs.isNotEmpty())
    }

    @Test
    fun `serie en programma uit de JSON-dienst`() {
        val series = SchatkamerParser.parseBffSeries(fixture("series.json"))
        assertEquals("Jiskefet", series.title)
        assertTrue(series.portraitUrl.isNotBlank())
        assertTrue(series.summary.contains("Herman Koch"))

        val program = SchatkamerParser.parseBffProgramDetail(fixture("program.json"))
        assertEquals("2101608130117904531", program.id)
        assertEquals("Jiskefet-compilatie", program.title)
        assertEquals("Jiskefet", program.seriesTitle)
        assertEquals("2012-05-04", program.date)
        assertEquals("VPRO", program.broadcaster)
        assertTrue(program.credits.any { it.name == "Herman Koch" })
        assertFalse(program.description.startsWith("EXTERNE"))
    }

    @Test
    fun `verhaal bevat de ingebedde uitzendingen`() {
        val story = SchatkamerParser.parseStory(fixture("story.html.gz"), "storm")
        assertTrue(story.title.contains("storm", ignoreCase = true))
        assertTrue(story.programs.size >= 3)
        assertTrue(story.programs.all { it.id.isNotBlank() })
    }

    @Test
    fun `tekstverwijzing gebruikt byte-lengte`() {
        val payload = "1:[\"x\"]\n26:T6,één€x\n27:[]"
        // "één€" is 2+1+2+3 = 8 bytes; de kop zegt 6 bytes.
        assertEquals("één", SchatkamerParser.resolveTextRef(payload, "$26")!!.trimEnd('�'))
    }
}
