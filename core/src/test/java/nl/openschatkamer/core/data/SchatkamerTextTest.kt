package nl.openschatkamer.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SchatkamerTextTest {

    @Test
    fun `hoofdlettertitels worden leesbaar`() {
        assertEquals("Jiskefet", SchatkamerText.prettyTitle("JISKEFET"))
        assertEquals("Theo en Thea", SchatkamerText.prettyTitle("THEO EN THEA"))
        assertEquals("TV Show", SchatkamerText.prettyTitle("TV SHOW"))
        assertEquals("Jiskefet: De Afgelopen Jaren", SchatkamerText.prettyTitle("JISKEFET: DE AFGELOPEN JAREN"))
        assertEquals("NOS", SchatkamerText.prettyTitle("NOS"))
        assertEquals("Lola da Musica", SchatkamerText.prettyTitle("Lola da Musica"))
    }

    @Test
    fun `ontbrekende afleveringstitel wordt een datum`() {
        assertEquals("Uitzending van 16 september 1990", SchatkamerText.episodeTitle("", "JISKEFET", "1990-09-16", ""))
        assertEquals("Uitzending van 16 september 1990", SchatkamerText.episodeTitle("Geen afleveringstitel", null, "1990-09-16", null))
        assertEquals("Jiskefet-compilatie", SchatkamerText.episodeTitle("Jiskefet-compilatie", "JISKEFET", "2012-05-04", ""))
    }

    @Test
    fun `datums`() {
        assertEquals("4 mei 2012", SchatkamerText.formatDate("2012-05-04"))
        assertEquals("1995-11-13", SchatkamerText.toIsoDate("13 november 1995"))
        assertEquals("1925", SchatkamerText.year("1 januari 1925"))
    }

    @Test
    fun `beschrijving zonder intern voorvoegsel en met spatie na een zin`() {
        assertEquals(
            "Compilatie van Jiskefet met Michiel Romeyn. Regie Ellen Jens",
            SchatkamerText.cleanDescription("EXTERNE INFORMATIE: Compilatie van <b>Jiskefet</b> met Michiel Romeyn.Regie Ellen Jens")
        )
    }

    @Test
    fun `regeleinde midden in een zin verdwijnt, opsomming blijft`() {
        assertEquals(
            "Over de invloed van de media op jongeren. In deze aflevering:\n1. Faro;\n2. geweld",
            SchatkamerText.cleanDescription("Over de invloed van de media op\njongeren. In deze aflevering:\n1. Faro;\n2. geweld")
        )
    }

    @Test
    fun `zoekfilter stuurt dezelfde parameters als de website`() {
        val params = SearchFilter(query = "polygoon", decade = Decade.D1950, material = "video")
            .toQueryParams(offset = 24, limit = 24).toMap()
        assertEquals("polygoon", params["query"])
        assertEquals("1950", params["yearmin"])
        assertEquals("1959", params["yearmax"])
        assertEquals("video", params["material[]"])
        assertEquals("true", params["playable"])
        assertEquals("relevance", params["sort"])
        assertEquals("24", params["offset"])

        // Zonder zoekterm sorteert de website op nieuwste.
        assertEquals("date-newest", SearchFilter().toQueryParams(0, 24).toMap()["sort"])
    }
}
