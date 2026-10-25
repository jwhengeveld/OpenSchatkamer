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
import nl.openschatkamer.core.data.RandomPick
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
                val sections = SECTIONS.map { (title, spec, episodes) ->
                    async { if (episodes) episodeRow(title, spec) else seriesRow(title, spec) }
                }
                listOfNotNull(classics.await()) + sections.awaitAll().filterNotNull()
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
     * Een rij uitzendingen (voor het Jeugdjournaal en Het Klokhuis, waar je juist
     * losse afleveringen kiest), elke week een ander stuk en in een andere volgorde.
     */
    private suspend fun episodeRow(title: String, spec: BrowseSpec): Row? {
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
     * Een rij series: de uitzendingen die bij [spec] horen, gegroepeerd per serie.
     * Elke week uit een ander deel van de resultaten en in een andere volgorde,
     * zodat er steeds andere series uitgelicht worden; nieuwe komen vanzelf mee.
     */
    private suspend fun seriesRow(title: String, spec: BrowseSpec): Row? = coroutineScope {
        val filter = spec.toFilter(catalog)
        suspend fun seriesFrom(start: Int) = listOf(0, 50, 100)
            .map { step -> async { catalog.search(filter, offset = start + step, limit = 50).getOrNull()?.items.orEmpty() } }
            .awaitAll().flatten()
            .filter { !it.seriesId.isNullOrBlank() && !it.seriesTitle.isNullOrBlank() && it.seriesId !in EXCLUDED_SERIES }
            .filter { s -> EXCLUDED_TITLES.none { s.seriesTitle!!.uppercase().startsWith(it) } }
            .groupBy { it.seriesId!! }
            .entries.toList()
        val start = WeeklyPick.index(6, title) * 150
        val series = (if (start > 0) seriesFrom(start).takeIf { it.size >= 6 } else null) ?: seriesFrom(0)
        WeeklyPick.shuffled(series, title)
            .take(24)
            .map { (id, episodes) ->
                val first = episodes.firstOrNull { it.imageUrl.isNotBlank() } ?: episodes.first()
                SchatkamerItem(
                    id = id, title = seriesName(first.seriesTitle.orEmpty()), kind = ItemKind.SERIES,
                    seriesId = id, imageUrl = first.imageUrl, broadcaster = first.broadcaster
                )
            }
            .takeIf { it.size >= 3 }
            ?.let { Row(SchatkamerRow("kids-$title", title, it, RowLayout.PORTRAIT), spec) }
    }

    /**
     * "Verras me": een willekeurige aflevering uit een van de bronnen van Kids (de
     * secties en de klassiekers). Series die wel bij een zoekopdracht passen maar
     * niet voor kinderen zijn, slaan we over.
     */
    suspend fun surprise(): SchatkamerItem? {
        val sources = SECTIONS.map { it.second } + CLASSICS.map { (id, name) -> BrowseSpec(name, seriesId = id) }
        repeat(4) {
            val item = RandomPick.from(catalog, sources.random().toFilter(catalog)) ?: return@repeat
            val series = item.seriesTitle.orEmpty().uppercase()
            if (EXCLUDED_TITLES.none { series.startsWith(it) }) return item
        }
        return null
    }

    /** "PAULUS DE BOSKABOUTER (Jean Dulieu)" → "Paulus de Boskabouter". */
    private fun seriesName(raw: String): String =
        SchatkamerText.prettyTitle(raw.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim())

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

        private val KIDS = listOf("kinderen", "jeugd")

        private fun kids(title: String, vararg genres: String) =
            BrowseSpec(title, subjects = KIDS, genres = genres.toList(), material = "video")

        /**
         * De secties van Kids, op volgorde. Series, behalve het Jeugdjournaal en
         * Het Klokhuis: daar kies je juist een losse aflevering.
         * "Kijk meer" opent precies dezelfde zoekopdracht.
         */
        val SECTIONS: List<Triple<String, BrowseSpec, Boolean>> = listOf(
            Triple("Schooltv", SCHOOLTV, false),
            Triple("Poppenspel", BrowseSpec("Poppenspel", genre = "poppenspel", material = "video"), false),
            Triple("Jeugdseries", BrowseSpec("Jeugdseries", genre = "televisiedrama", subject = "jeugd", material = "video"), false),
            Triple("Lachen en gekkigheid", kids("Lachen en gekkigheid", "amusement"), false),
            Triple("Quiz en spel", kids("Quiz en spel", "quiz", "spelprogramma"), false),
            Triple("Muziek en zingen", kids("Muziek en zingen", "muziekprogramma", "muziekuitzending"), false),
            Triple("Dieren en natuur", kids("Dieren en natuur", "natuur- en milieuprogramma"), false),
            Triple("Leren en ontdekken", kids("Leren en ontdekken", "educatie", "populairwetenschappelijke informatie"), false),
            Triple("Jeugdjournaal", BrowseSpec("Jeugdjournaal", seriesId = JEUGDJOURNAAL), true),
            Triple("Het Klokhuis", BrowseSpec("Het Klokhuis", seriesId = KLOKHUIS), true)
        )

        /** Series die wel bij de zoekopdracht passen, maar niet voor kinderen zijn. */
        val EXCLUDED_SERIES = setOf(JEUGDJOURNAAL, KLOKHUIS)
        val EXCLUDED_TITLES = listOf(
            "THEATER VAN HET SENTIMENT", "DE AVONDEN", "MEER OP ZONDAG", "NOORDERLICHT", "VROEGE VOGELS",
            "IP RECLAME", "POLYGOON", "JOURNAAL", "NOS JOURNAAL", "OPIUM", "NOVA", "NETWERK"
        )

    }
}
