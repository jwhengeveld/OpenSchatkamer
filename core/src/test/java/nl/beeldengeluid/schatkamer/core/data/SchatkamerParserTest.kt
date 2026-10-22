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

    @Test
    fun testParseBffSearch() {
        val bffJson = """
            {
              "data": {
                "total": 83087,
                "results": [
                  {
                    "type": "program",
                    "title": "",
                    "slug": "checkpoint-proef",
                    "externalId": "2101608150136110131",
                    "mediaType": "video",
                    "series": {
                      "id": "2101608030024919731",
                      "title": "CHECKPOINT",
                      "slug": "checkpoint"
                    },
                    "date": "2014-04-03",
                    "summary": "Stoere proeven met het testteam van Checkpoint.",
                    "broadcaster": "EO",
                    "isPlayable": true
                  }
                ],
                "facets": {
                  "genre": [
                    { "total": 16000, "label": "jeugdprogramma" },
                    { "total": 5000, "label": "amusement" }
                  ]
                }
              }
            }
        """.trimIndent()
        val result = SchatkamerParser.parseBffSearch(bffJson, "eo", currentOffset = 0)
        assertEquals(83087, result.total)
        assertEquals(1, result.items.size)
        assertTrue(result.hasMore)
        assertEquals("CHECKPOINT (2014-04-03)", result.items[0].title)
        assertEquals(listOf("jeugdprogramma", "amusement"), result.genreLabels)
        assertEquals(16000, result.availableGenres[0].count)
        assertEquals("jeugdprogramma", result.availableGenres[0].label)
    }

    @Test
    fun testExtractStreamTokensWithNextJsEscapingAndChunkMarkers() {
        val nextJsPayload = """
            26:T458,https://sk-video.cdn.beeldengeluid.nl/MIJNFRANSETAN-HRE0000381C/cmaf/master.m3u8?CloudFront-Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly9zay12aWRlby5jZG4uYmVlbGRlbmdlbHVpZC5ubC9NSUpORlJBTlNFVEFOLUhSRTAwMDAzODFDLyoiLCJDb25kaXRpb24iOnsiRGF0ZUxlc3NUaGFuIjp7IkFXUzpFcG9jaFRpbWUiOjE3OTI3NTU0MDl9fX1dfQ__\u0026CloudFront-Signature=kY~-5RG0zHDw7ycuRRJTUe12XakUPmv3RUZSKBWHLGJqvqq6kHkviZicGXF4zu4S69zBFMx1XBFV6iibuOkY5MZJxze06JdMVbWeGgmf0VrEEoHpQBwznG~8CqBZaX5QzPGyFJCTIz5oq2VNgHxgt3YNhbeBa~DtMI1Zgp-qwZv7vXkz-4uY71ASbQyOZGPYedvlbLGWWXrOwfP~14lENXM2BH7yaxVf73GsAB93cUetTC9SD9ZvIpp85XnQAc92fUhZF1VCV-10spOqgIGLqGtMYtoaKBry24N4EsBWNhSwLLEYrrnvphA7gBaZdXg-LQlS1gYC-HovBYlT5obUNw__\u0026CloudFront-Key-Pair-Id=K1C5L1HDJC2G68\u0026Expires=1792672609\u0026Signature=cvKB1LbWtfj2bHkbXFIVoTuSA0lyyX0o78WwdSHDMO6SCRM95aOgIzu1kpgIE1YHpc11zQCjuf-cecqaux6abJuBhabL3DCi~5djbYW49ko47qktbzWRn-E4CU1CDc1YSUPW8xOHJ24nUULY3RxtszjT2HFCbHEivFPUYuupULOvrzq~P-fE8~ydwkPf66fueKHvzPxcjZ-VVnTqqQJUsX8csAMCMY~c3viov4MxGW-4Lx38ws0~dpl5lX1V4RsvfFR0hftcZfoZiygJ0bugAe9lfwaJNTPM5315Bi~9XUx-M2E~Rhp4teYd8V~8RCBgXh7d-8guhwzt5fV4Z35SEA__\u0026Key-Pair-Id=K1C5L1HDJC2G6824:["${'$'}","${'$'}L25",null,{"program":{"id":"2101608040030802031"}}]
        """.trimIndent()

        val tokens = SchatkamerParser.extractStreamTokens(nextJsPayload)
        assertNotNull("Tokens should not be null", tokens)
        assertFalse("Stream URL must not contain escaped ampersand", tokens!!.streamUrl.contains("\\u0026"))
        assertFalse("Stream URL must not contain Next.js chunk marker", tokens.streamUrl.contains("24:["))
        assertTrue("Stream URL must have clean Policy", tokens.streamUrl.contains("Policy=eyJTdGF0"))
        assertTrue("Stream URL must have clean Signature", tokens.streamUrl.contains("Signature=kY~-5RG0"))
        assertTrue("Stream URL must have clean Key-Pair-Id", tokens.streamUrl.endsWith("Key-Pair-Id=K1C5L1HDJC2G68"))
        assertEquals("CloudFront-Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly9zay12aWRlby5jZG4uYmVlbGRlbmdlbHVpZC5ubC9NSUpORlJBTlNFVEFOLUhSRTAwMDAzODFDLyoiLCJDb25kaXRpb24iOnsiRGF0ZUxlc3NUaGFuIjp7IkFXUzpFcG9jaFRpbWUiOjE3OTI3NTU0MDl9fX1dfQ__; CloudFront-Signature=kY~-5RG0zHDw7ycuRRJTUe12XakUPmv3RUZSKBWHLGJqvqq6kHkviZicGXF4zu4S69zBFMx1XBFV6iibuOkY5MZJxze06JdMVbWeGgmf0VrEEoHpQBwznG~8CqBZaX5QzPGyFJCTIz5oq2VNgHxgt3YNhbeBa~DtMI1Zgp-qwZv7vXkz-4uY71ASbQyOZGPYedvlbLGWWXrOwfP~14lENXM2BH7yaxVf73GsAB93cUetTC9SD9ZvIpp85XnQAc92fUhZF1VCV-10spOqgIGLqGtMYtoaKBry24N4EsBWNhSwLLEYrrnvphA7gBaZdXg-LQlS1gYC-HovBYlT5obUNw__; CloudFront-Key-Pair-Id=K1C5L1HDJC2G68", tokens.cookieHeader)
    }

    private fun SatkamerParserIsGeneric(t: String) = SchatkamerParser.isGenericOrEmptyTitle(t)
}
