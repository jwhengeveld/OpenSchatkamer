package nl.beeldengeluid.schatkamer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SchatkamerParserTest {

    @Test
    fun testParseHome() {
        val file = File("/tmp/schatkamer_index.html")
        if (!file.exists()) return
        val html = file.readText()

        val home = SchatkamerParser.parseHome(html)
        println("Home rows count: ${home.rows.size}")
        for (r in home.rows) {
            println("Row: '${r.title}' has ${r.items.size} items")
        }
        assertTrue("Rows should not be empty", home.rows.isNotEmpty())
        assertTrue("First row should have items", home.rows[0].items.isNotEmpty())
    }

    @Test
    fun testParseSearch() {
        val file = File("/tmp/journaal_search.html")
        if (!file.exists()) return
        val html = file.readText()

        val result = SchatkamerParser.parseSearch(html, "journaal")
        println("Search results count: ${result.items.size}")
        assertTrue("Should find search results", result.items.isNotEmpty())
        val first = result.items[0]
        println("First result: ${first.title} (${first.date}) - URL: ${first.url}")
        assertNotNull(first.id)
    }

    @Test
    fun testParseProgramDetail() {
        val file = File("/tmp/schatkamer_program.html")
        if (!file.exists()) return
        val html = file.readText()

        val detail = SchatkamerParser.parseProgramDetail(html, "2101608060045622631")
        println("Detail title: ${detail.title}")
        println("Detail broadcaster: ${detail.broadcaster}")
        println("Detail streamUrl: ${detail.streamInfo?.streamUrl}")
        println("Detail widevineUrl: ${detail.streamInfo?.widevineLicenseUrl}")
        println("Detail episodes: ${detail.episodes.size}")

        assertEquals("Op bezoek in kunstenaarsdorp Bergen.", detail.title)
        assertNotNull(detail.streamInfo)
        assertTrue(detail.streamInfo!!.streamUrl.contains("master.m3u8"))
        assertTrue(detail.streamInfo!!.streamUrl.contains("Policy="))
        assertTrue(detail.streamInfo!!.cookieHeader.contains("CloudFront-Policy="))
    }

    @Test
    fun testSmartTitleResolution() {
        // Case 1: Geen afleveringstitel met serietitel en datum
        val title1 = SchatkamerParser.buildSmartTitle(
            rawTitle = "Geen afleveringstitel",
            seriesTitle = "KERKDIENST",
            date = "9 december 1956"
        )
        assertEquals("KERKDIENST (9 december 1956)", title1)

        // Case 2: Geen afleveringstitel met serietitel maar geen datum
        val title2 = SchatkamerParser.buildSmartTitle(
            rawTitle = "Geen afleveringstitel",
            seriesTitle = "PANORAMIEK"
        )
        assertEquals("PANORAMIEK", title2)

        // Case 3: Zowel serie als afleveringstitel aanwezig
        val title3 = SchatkamerParser.buildSmartTitle(
            rawTitle = "Vanuit Witmarsum.",
            seriesTitle = "LUISTERCLUBS OPGELET"
        )
        assertEquals("LUISTERCLUBS OPGELET: Vanuit Witmarsum.", title3)

        // Case 4: Geen serie, geen afleveringstitel, maar beschrijving
        val title4 = SchatkamerParser.buildSmartTitle(
            rawTitle = "Geen afleveringstitel",
            description = "Inleiding op kerkdienst te Utrecht door dominee Jansen."
        )
        assertEquals("Inleiding op kerkdienst te Utrecht door dominee Jansen", title4)
    }

    @Test
    fun testParseSearchSmartTitles() {
        val jsonPayload = """
            self.__next_f.push([1,"4:{\"results\":[{\"id\":\"2101608110097861431\",\"title\":\"Geen afleveringstitel\",\"seriesTitle\":\"KERKDIENST\",\"date\":\"1956-12-09\",\"broadcaster\":\"EO\",\"mediaType\":\"video\"}],\"total\":1}\n"])
        """.trimIndent()
        val result = SchatkamerParser.parseSearch(jsonPayload, "eo")
        assertEquals(1, result.items.size)
        val item = result.items[0]
        assertEquals("KERKDIENST (1956-12-09)", item.title)
        assertFalse(SatkamerParserIsGeneric(item.title))
    }

    private fun SatkamerParserIsGeneric(t: String) = SchatkamerParser.isGenericOrEmptyTitle(t)
}
