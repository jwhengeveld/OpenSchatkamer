package nl.beeldengeluid.schatkamer.core.data

import org.junit.Assert.assertEquals
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
}
