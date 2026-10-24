package nl.openschatkamer.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.RowLayout
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerRow
import nl.openschatkamer.core.data.SchatkamerText
import nl.openschatkamer.core.data.WeeklyPick

/**
 * De kids-sectie. De site heeft geen apart kindergenre, dus bestaat die uit
 * bekende kinderseries (vaste archief-id's, die veranderen niet) plus rijen uit
 * de zoekdienst: poppenspel, jeugddrama, het Jeugdjournaal en Het Klokhuis.
 * Een serie die niet (meer) laadt, valt gewoon weg.
 */
class KidsViewModel(private val catalog: SchatkamerCatalogRepository) : ViewModel() {

    /** Een rij met, als die er is, een "Meer…"-bladerpagina. */
    data class Row(val row: SchatkamerRow, val more: BrowseSpec? = null)

    private val _rows = MutableStateFlow<Load<List<Row>>>(Load.Loading)
    val rows: StateFlow<Load<List<Row>>> = _rows.asStateFlow()

    init { load() }

    fun load() {
        _rows.value = Load.Loading
        viewModelScope.launch {
            val rows = coroutineScope {
                val classics = async { classics() }
                val schoolTv = async { schoolTv() }
                val searches = SEARCH_ROWS.map { (title, spec) -> async { weeklyRow(title, spec) } }
                listOfNotNull(classics.await(), schoolTv.await()) + searches.awaitAll().filterNotNull()
            }
            _rows.value = if (rows.isEmpty()) Load.Failed("De kinderprogramma's konden niet geladen worden.") else Load.Ready(rows)
        }
    }

    private suspend fun classics(): Row? = coroutineScope {
        CLASSICS.map { (id, name) -> async { catalog.series(id).getOrNull()?.toItem()?.copy(title = name) } }
            .awaitAll()
            .filterNotNull()
            .let { WeeklyPick.shuffled(it, "klassiekers") }
            .takeIf { it.isNotEmpty() }
            ?.let { Row(SchatkamerRow("kids-klassiekers", "Klassiekers", it, RowLayout.PORTRAIT), BrowseSpec("Voor de jeugd", subject = "jeugd", material = "video")) }
    }

    /**
     * Een rij uit de zoekdienst met elke week een andere greep: een ander stuk
     * van de resultaten, in een andere volgorde. Lukt dat stuk niet, dan het begin.
     */
    private suspend fun weeklyRow(title: String, spec: BrowseSpec): Row? {
        val filter = spec.toFilter(catalog)
        val offset = WeeklyPick.index(WEEKLY_WINDOWS, title) * WINDOW
        val items = (catalog.search(filter, offset = offset, limit = WINDOW).getOrNull()?.items.orEmpty().takeIf { it.size >= 6 }
            ?: catalog.search(filter, limit = WINDOW).getOrNull()?.items.orEmpty())
            .filter { it.kind == ItemKind.PROGRAM }
        return WeeklyPick.shuffled(items, title).take(20)
            .takeIf { it.size >= 3 }
            ?.let { Row(SchatkamerRow("kids-$title", title, it), spec) }
    }

    /**
     * Schooltv-series: alles van de omroepen NOT en TELEAC, gegroepeerd per serie.
     * Elke week uit een ander deel van het archief, zodat er steeds andere series
     * uitgelicht worden; nieuwe series komen vanzelf mee.
     */
    private suspend fun schoolTv(): Row? = coroutineScope {
        val filter = SCHOOLTV.toFilter(catalog)
        suspend fun seriesFrom(start: Int) = listOf(0, 50, 100)
            .map { step -> async { catalog.search(filter, offset = start + step, limit = 50).getOrNull()?.items.orEmpty() } }
            .awaitAll().flatten()
            .filter { !it.seriesId.isNullOrBlank() && !it.seriesTitle.isNullOrBlank() }
            .groupBy { it.seriesId!! }
            .entries.toList()
        val start = WeeklyPick.index(8, "schooltv") * 150
        val series = seriesFrom(start).takeIf { it.size >= 6 } ?: seriesFrom(0)
        WeeklyPick.shuffled(series, "schooltv")
            .take(24)
            .map { (id, episodes) ->
                val first = episodes.firstOrNull { it.imageUrl.isNotBlank() } ?: episodes.first()
                SchatkamerItem(
                    id = id, title = SchatkamerText.prettyTitle(first.seriesTitle), kind = ItemKind.SERIES,
                    seriesId = id, imageUrl = first.imageUrl, broadcaster = first.broadcaster
                )
            }
            .takeIf { it.size >= 3 }
            ?.let { Row(SchatkamerRow("kids-schooltv", "Schooltv", it, RowLayout.PORTRAIT), SCHOOLTV) }
    }

    internal companion object {
        /**
         * Bekende kinderseries uit het archief (id met nette naam; de archieftitels
         * zijn vaak hoofdletters met toevoegingen), op volgorde van de rij.
         */
        val CLASSICS = listOf(
            "2101608030021453631" to "Sesamstraat",
            "2101608030021467131" to "Het Klokhuis",
            "2101608030021475931" to "Swiebertje",
            "2101608030021469231" to "Dappere Dodo",
            "2101608030025841831" to "Paulus de boskabouter",
            "2101608030021486931" to "Kunt u mij de weg naar Hamelen vertellen, mijnheer?",
            "2101608030021704631" to "Het Zandkasteel",
            "2101608030021478631" to "De film van Ome Willem",
            "2101608030021475531" to "Theo en Thea",
            "2101608030021472231" to "Stuif es in",
            "2101608030021480031" to "Alfred J. Kwak",
            "2101608030022628531" to "Pipo en de Waterlanders",
            "2101608030024902831" to "Tita Tovenaar",
            "2101608030022015831" to "Kinderen voor Kinderen",
            "2101608030021474731" to "Meneer Rommel"
        )

        const val JEUGDJOURNAAL = "2101608030021674731"
        const val KLOKHUIS = "2101608030021467131"

        /** Weekkeuze: een van de eerste [WEEKLY_WINDOWS] blokken van [WINDOW] resultaten. */
        const val WINDOW = 50
        const val WEEKLY_WINDOWS = 5

        /** Schooltv en Teleac staan in het archief onder deze omroepnamen. */
        val SCHOOLTV = BrowseSpec("Schooltv en Teleac", archiveBroadcasters = listOf("NOT", "TELEAC", "TELEAC/NOT"), material = "video")

        /** Rijen uit de zoekdienst; "Kijk meer" opent precies dezelfde zoekopdracht. */
        val SEARCH_ROWS = listOf(
            "Poppenspel" to BrowseSpec("Poppenspel", genre = "poppenspel", material = "video"),
            "Series en drama voor de jeugd" to BrowseSpec("Drama voor de jeugd", genre = "televisiedrama", subject = "jeugd", material = "video"),
            "Jeugdjournaal" to BrowseSpec("Jeugdjournaal", seriesId = JEUGDJOURNAAL),
            "Het Klokhuis" to BrowseSpec("Het Klokhuis", seriesId = KLOKHUIS)
        )

    }
}
