package nl.openschatkamer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFlyoutTest {

    private val flyout = SearchFlyout(
        titles = listOf("WIE KOMT ER IN M'N HUISJE", "EEN HUISJE OP DE CAMPING", "HEILIGE HUISJES", "Huisje, Boompje, Error", "Jan Huisjes")
    )

    @Test
    fun `half woord wordt aangevuld met het vaakste woord`() {
        assertEquals("huisje", flyout.complete("huisj"))
        assertEquals("huisje", flyout.complete("HUISJ"))
    }

    @Test
    fun `geen aanvulling bij te kort of onbekend`() {
        assertNull(flyout.complete("h"))
        assertNull(flyout.complete("xyz"))
        assertNull(flyout.complete("huisjes"))
    }

    @Test
    fun `flyout van de site wordt gelezen`() {
        val json = """{"data":{"media":[
            {"type":"series","title":"HEILIGE HUISJES","externalId":"1","image":{"url":"https://x/a.jpg"},"broadcaster":"NOT","mediaType":"video"},
            {"type":"program","title":"EEN HUISJE OP DE CAMPING","externalId":"2","date":"1980-01-01","mediaType":"video"}],
            "persons":[{"type":"person","title":"Jan Huisjes"}]}}"""
        val f = SchatkamerParser.parseFlyout(json)
        assertEquals(listOf("1"), f.series.map { it.id })
        assertEquals(ItemKind.SERIES, f.series.first().kind)
        assertEquals(listOf("2"), f.programs.map { it.id })
        assertEquals(listOf("Jan Huisjes"), f.people)
    }
}
