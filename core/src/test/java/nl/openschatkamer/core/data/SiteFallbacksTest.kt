package nl.openschatkamer.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * Simuleert dat de website verandert: de gestructureerde gegevens
 * verdwijnen of krijgen andere namen. De terugvalroutes moeten dan nog
 * steeds bruikbare resultaten geven.
 */
class SiteFallbacksTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
        val input = if (name.endsWith(".gz")) GZIPInputStream(stream) else stream
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `startpagina zonder Next-gegevens wordt uit de HTML-kaarten gelezen`() {
        val html = fixture("home.html.gz").replace(Regex("""<script>self\.__next_f\.push.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue("RSC moet echt weg zijn", SchatkamerParser.parseHome(html).rows.isEmpty())

        val home = SiteFallbacks.homeFromHtml(html)
        assertTrue("verwacht rijen, kreeg ${home.rows.size}", home.rows.size >= 8)
        val kinds = home.rows.map { it.kind }.toSet()
        assertTrue(ItemKind.SERIES in kinds && ItemKind.PROGRAM in kinds && ItemKind.STORY in kinds)
        val series = home.rows.first { it.kind == ItemKind.SERIES }
        assertTrue(series.title.isNotBlank() && series.title != "Uitgelicht")
        assertTrue("afbeeldingen uit de beeldoptimalisatie gedecodeerd", series.items.first().imageUrl.startsWith("https://schatkamer.beeldengeluid.nl/assets/"))
        val episode = home.rows.flatMap { it.items }.first { it.kind == ItemKind.PROGRAM && it.seriesId != null }
        assertTrue(episode.id != episode.seriesId)
    }

    @Test
    fun `stream zonder programStream-blok wordt los uit de pagina gehaald`() {
        val html = fixture("episode.html.gz").replace("programStream", "somethingElse")
        assertNull(SchatkamerParser.parseStreamInfo(html))

        val stream = SiteFallbacks.signedManifest(html)
        assertNotNull(stream)
        stream!!
        assertTrue(stream.manifestUrl.endsWith("/master.m3u8"))
        assertTrue(stream.cookieHeader.contains("CloudFront-Key-Pair-Id="))
        // Geen aangeplakte rij-id achter de Key-Pair-Id.
        val keyPair = Regex("""[?&]Key-Pair-Id=([A-Za-z0-9]+)""").find(stream.playUrl)!!.groupValues[1]
        val cookieKey = Regex("""CloudFront-Key-Pair-Id=([A-Za-z0-9]+)""").find(stream.cookieHeader)!!.groupValues[1]
        assertEquals(cookieKey, keyPair)
    }

    @Test
    fun `zoekresultaat met hernoemde velden blijft leesbaar`() {
        val renamed = JSONObject()
            .put("id", "123")
            .put("name", "")
            .put("publishedAt", "1990-09-16T00:00:00.000Z")
            .put("description", "EXTERNE INFORMATIE: Iets.Meer")
            .put("imageUrl", "https://example.org/a.jpg")
            .put("series", JSONObject().put("externalId", "9").put("title", "JISKEFET"))
        val item = SchatkamerParser.parseBffProgram(renamed)!!
        assertEquals("123", item.id)
        assertEquals("Uitzending van 16 september 1990", item.title)
        assertEquals("https://example.org/a.jpg", item.imageUrl)
        assertEquals("9", item.seriesId)
        assertEquals("Jiskefet", item.seriesTitle)
        assertEquals("Iets. Meer", item.description)
    }
}
