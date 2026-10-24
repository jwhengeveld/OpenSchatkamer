package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Een publieke omroep om op te bladeren, met de namen waaronder het archief hem kent. */
data class Broadcaster(
    val name: String,
    val logoUrl: String = "",
    /** Fusieomroepen zoeken ook in het archief van hun voorgangers. */
    val archiveNames: List<String> = listOf(name.uppercase()),
    val count: Int = 0
)

/**
 * Bladeren en zoeken in de Schatkamer.
 *
 * Zoeken, series, personen en omroepen lopen via de JSON-dienst van de website
 * (dezelfde die de site zelf gebruikt), met paginering, facetten en tolerantie
 * voor typefouten. De startpagina komt uit de HTML, omdat de redactie daar de
 * rijen samenstelt; die wordt op schijf bewaard voor een directe koude start.
 */
class SchatkamerCatalogRepository(context: Context) {

    private val http = SchatkamerHttpClient.getInstance(context)
    private val snapshots = CatalogSnapshotStore(context)
    private val homeCache = TimedCache<SchatkamerHome>(CATALOG_TTL_MS)

    init {
        snapshots.readHome()?.let { homeCache.seed(it) }
    }

    // ---------------------------------------------------------------- start

    /** De laatst bekende startpagina (geheugen of schijf), voor een directe weergave. */
    fun cachedHome(): SchatkamerHome? = homeCache.snapshot()

    suspend fun getHome(forceRefresh: Boolean = false): Result<SchatkamerHome> = withContext(Dispatchers.IO) {
        if (!forceRefresh) homeCache.fresh()?.let { return@withContext Result.success(it) }
        runCatching {
            val home = SchatkamerParser.parseHome(http.getHtml(SchatkamerHttpClient.BASE_URL, fresh = forceRefresh))
            check(home.rows.isNotEmpty()) { "De startpagina van de Schatkamer bevatte geen rijen." }
            homeCache.put(home)
            snapshots.writeHome(home)
            home
        }.recoverCatching { error -> homeCache.snapshot() ?: throw error }
    }

    // ---------------------------------------------------------------- zoeken

    suspend fun search(
        filter: SearchFilter,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
        forceRefresh: Boolean = false
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = http.url("/api/media/bff/search", filter.toQueryParams(offset, limit))
            SchatkamerParser.parseBffSearch(http.getJson(url, fresh = forceRefresh), filter.query, offset)
        }
    }

    /**
     * Suggesties tijdens het typen: series en personen die bij de zoekterm passen,
     * uit de facetten van één klein zoekverzoek.
     */
    suspend fun suggestions(prefix: String): List<String> {
        if (prefix.trim().length < 2) return emptyList()
        val result = search(SearchFilter(query = prefix), limit = 1).getOrNull() ?: return emptyList()
        val series = result.facets.programs.map { SchatkamerText.prettyTitle(it.label) }
        val people = result.facets.people.map { it.label }
        return (series.take(5) + people.take(3))
            .distinctBy { it.lowercase() }
            .filter { it.isNotBlank() }
    }

    // ---------------------------------------------------------------- series

    suspend fun series(id: String): Result<SchatkamerSeries> = withContext(Dispatchers.IO) {
        runCatching { SchatkamerParser.parseBffSeries(http.getJson(http.url("/api/media/bff/series/$id"))) }
    }

    /** Afleveringen van een serie, standaard van oud naar nieuw zoals op de website. */
    suspend fun episodes(
        seriesId: String,
        offset: Int = 0,
        sort: SortOrder = SortOrder.OLDEST,
        forceRefresh: Boolean = false
    ): Result<SearchResult> = search(SearchFilter(seriesId = seriesId, sort = sort), offset, forceRefresh = forceRefresh)

    // --------------------------------------------------------------- verhalen

    suspend fun story(slug: String): Result<SchatkamerStory> = withContext(Dispatchers.IO) {
        runCatching { SchatkamerParser.parseStory(http.getHtml(http.url("/verhaal/$slug")), slug) }
    }

    // --------------------------------------------------------------- omroepen

    /**
     * Omroepen om op te bladeren: de huidige publieke omroepen bovenaan (met
     * hun voorgangers samengevoegd), daarna historische omroepen met de meeste
     * uitzendingen. Aantallen komen live uit de zoekdienst.
     */
    suspend fun broadcasters(): List<Broadcaster> {
        val logos = cachedHome()?.broadcasters.orEmpty().associate { it.title.uppercase() to it.imageUrl }
        val counts = search(SearchFilter(), limit = 1).getOrNull()?.facets?.broadcasters.orEmpty()
            .associate { it.label.uppercase() to it.count }
        val merged = CURRENT_BROADCASTERS.map { b ->
            b.copy(
                logoUrl = logos[b.name.uppercase()].orEmpty().ifBlank { b.logoUrl },
                count = b.archiveNames.sumOf { counts[it] ?: 0 }
            )
        }
        val covered = merged.flatMap { it.archiveNames }.toSet()
        val historic = counts.entries
            .filter { it.key !in covered && it.key !in NOT_BROADCASTERS && it.value >= 300 }
            .sortedByDescending { it.value }
            .map { Broadcaster(name = SchatkamerText.prettyTitle(it.key).ifBlank { it.key }, archiveNames = listOf(it.key), count = it.value) }
        return merged + historic
    }

    fun broadcasterByName(name: String): Broadcaster =
        CURRENT_BROADCASTERS.firstOrNull { it.name.equals(name, ignoreCase = true) || name.uppercase() in it.archiveNames }
            ?: Broadcaster(name = name, archiveNames = listOf(name.uppercase()))

    /** Genres met het aantal afspeelbare uitzendingen, direct uit de zoekdienst. */
    suspend fun genres(): List<FacetItem> =
        search(SearchFilter(), limit = 1).getOrNull()?.facets?.genres.orEmpty()

    fun invalidate() {
        homeCache.clear()
        http.forget()
    }

    companion object {
        const val PAGE_SIZE = 24

        private const val CMS = "https://schatkamer.beeldengeluid.nl/assets/cms"

        val CURRENT_BROADCASTERS = listOf(
            Broadcaster("NOS", "$CMS/NOS_2017f2f8e7.jpg", listOf("NOS", "NTS")),
            Broadcaster("AVROTROS", "$CMS/AVROTROS_169_2017f2f8e7.jpg", listOf("AVROTROS", "AVRO", "TROS")),
            Broadcaster("BNNVARA", "$CMS/BNNVARA_LOGO_ROOD_RGB_6a1abdf767.png", listOf("BNNVARA", "BNN", "VARA")),
            Broadcaster("KRO-NCRV", "$CMS/KRONCRV_2017f2f8e7.jpg", listOf("KRO-NCRV", "KRO", "NCRV", "RKK")),
            Broadcaster("VPRO", "$CMS/VPRO_2017f2f8e7.jpg", listOf("VPRO")),
            Broadcaster("EO", "$CMS/EO_logo_liggend_a637cf870b.jpg", listOf("EO")),
            Broadcaster("NTR", "$CMS/NTR_2017f2f8e7.jpg", listOf("NTR", "NPS", "TELEAC", "RVU", "NOT", "TELEAC/NOT")),
            Broadcaster("Omroep MAX", "$CMS/MAX_2017f2f8e7.jpg", listOf("OMROEP MAX")),
            Broadcaster("HUMAN", "$CMS/Human169_079b8c5ce5.jpg", listOf("HUMAN", "HV")),
            Broadcaster("IKON", "", listOf("IKON", "IKOR")),
            Broadcaster("WNL", "$CMS/WNL_169_36efa28b22.jpg", listOf("WNL")),
            Broadcaster("PowNed", "$CMS/POWNED_logo_liggend_c8821feec0.jpg", listOf("POWNED")),
            Broadcaster("FunX", "", listOf("FUNX")),
            Broadcaster("NPO", "$CMS/NPO_logo_liggend_22253ac321.jpg", listOf("NPO"))
        )

        private val NOT_BROADCASTERS = setOf("IP", "POLITIEKE PARTIJEN", "STER", "NEDERLANDS INSTITUUT VOOR BEELD EN GELUID")

        @Volatile
        private var instance: SchatkamerCatalogRepository? = null

        fun getInstance(context: Context): SchatkamerCatalogRepository =
            instance ?: synchronized(this) {
                instance ?: SchatkamerCatalogRepository(context).also { instance = it }
            }
    }
}
