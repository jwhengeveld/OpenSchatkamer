package nl.openschatkamer.core.data

import kotlin.random.Random

/**
 * "Verras me": een willekeurige uitzending. De zoekdienst geeft bij elke zoekopdracht
 * het totaal; daaruit kiezen we een willekeurige plek en halen precies die ene
 * uitzending op. De zoekdienst gaat niet dieper dan 10.000 resultaten.
 */
object RandomPick {

    private const val MAX_RESULTS = 10_000

    /** Een willekeurige uitzending die bij [filter] past, of null als er niets is. */
    suspend fun from(catalog: SchatkamerCatalogRepository, filter: SearchFilter): SchatkamerItem? {
        // Een vaste volgorde, zodat een plek in de resultaten ook echt één uitzending is.
        val sorted = if (filter.query.isBlank() && filter.sort == SortOrder.RELEVANCE) filter.copy(sort = SortOrder.NEWEST) else filter
        val total = catalog.search(sorted, limit = 1).getOrNull()?.total ?: return null
        if (total <= 0) return null
        repeat(3) {
            val offset = Random.nextInt(minOf(total, MAX_RESULTS))
            catalog.search(sorted, offset = offset, limit = 1).getOrNull()?.items
                ?.firstOrNull { it.kind == ItemKind.PROGRAM }
                ?.let { return it }
        }
        return null
    }

    /**
     * Uit het hele archief: video en audio, alles wat je kunt afspelen. Het archief is
     * veel groter dan de 10.000 resultaten die de zoekdienst laat zien (dat zouden
     * alleen de nieuwste zijn); daarom eerst een willekeurig jaar en dan een
     * willekeurige uitzending uit dat jaar, zodat elke periode aan bod komt.
     */
    suspend fun archive(catalog: SchatkamerCatalogRepository): SchatkamerItem? {
        val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        repeat(5) {
            val year = Random.nextInt(FIRST_YEAR, thisYear + 1)
            from(catalog, SearchFilter(yearFrom = year, yearTo = year))?.let { return it }
        }
        return from(catalog, SearchFilter())
    }

    /** Het eerste jaar waaruit het archief (Polygoon) uitzendingen heeft. */
    private const val FIRST_YEAR = 1920

    /** Een willekeurige aflevering van één serie. */
    suspend fun episode(catalog: SchatkamerCatalogRepository, seriesId: String): SchatkamerItem? =
        from(catalog, SearchFilter(seriesId = seriesId, sort = SortOrder.OLDEST))
}
