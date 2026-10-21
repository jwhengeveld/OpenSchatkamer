package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Repository voor het bladeren en doorzoeken van de Schatkamer-catalogus.
 */
class SchatkamerCatalogRepository(context: Context) {

    private val httpClient = SchatkamerHttpClient.getInstance(context)
    private val snapshotStore = CatalogSnapshotStore(context)
    private val homeCache = TimedCache<SchatkamerHome>(CATALOG_TTL_MS)

    init {
        // Seed cache direct met de snapshot van schijf voor snelle koude start
        snapshotStore.readHome()?.let { homeCache.seed(it) }
    }

    /**
     * Startpagina: direct uit het geheugen / snapshot en verversen op de achtergrond.
     */
    fun cachedHome(): SchatkamerHome? = homeCache.snapshot()

    suspend fun getHome(forceRefresh: Boolean = false): Result<SchatkamerHome> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            homeCache.fresh()?.let { return@withContext Result.success(it) }
        }

        runCatching {
            val html = httpClient.getHtml(SchatkamerHttpClient.BASE_URL)
            val parsed = SchatkamerParser.parseHome(html)
            if (parsed.rows.isNotEmpty()) {
                homeCache.put(parsed)
                snapshotStore.writeHome(parsed)
            }
            parsed
        }.recoverCatching { error ->
            homeCache.snapshot() ?: throw error
        }
    }

    /**
     * Zoeken in het complete archief via /zoeken?q=...
     */
    suspend fun search(query: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                return@runCatching SearchResult(query = "", total = 0, items = emptyList())
            }
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "${SchatkamerHttpClient.BASE_URL}/zoeken?q=$encoded"
            val html = httpClient.getHtml(url)
            SchatkamerParser.parseSearch(html, trimmed)
        }
    }

    /**
     * Programma's van een specifieke publieke omroep.
     */
    suspend fun getProgramsForBroadcaster(id: String, slug: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${SchatkamerHttpClient.BASE_URL}/omroep/$id/$slug"
            val html = httpClient.getHtml(url)
            SchatkamerParser.parseSearch(html, slug)
        }
    }

    /**
     * Programma's binnen een specifiek genre (verhaal).
     */
    suspend fun getProgramsForGenre(genreSlug: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${SchatkamerHttpClient.BASE_URL}/verhaal/$genreSlug"
            val html = httpClient.getHtml(url)
            val homeLike = SchatkamerParser.parseHome(html)
            val items = homeLike.rows.flatMap { it.items }.distinctBy { it.id }
            if (items.isNotEmpty()) {
                SearchResult(query = genreSlug, total = items.size, items = items)
            } else {
                SchatkamerParser.parseSearch(html, genreSlug)
            }
        }
    }

    /**
     * Bekende publieke omroepen in de Schatkamer.
     */
    fun getBroadcasters(): List<BroadcasterItem> = listOf(
        BroadcasterItem(
            id = "209287",
            name = "NOS",
            slug = "nos",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/NOS_2017f2f8e7.jpg",
            description = "Nederlandse Omroep Stichting: nieuws, sport en evenementen."
        ),
        BroadcasterItem(
            id = "236909",
            name = "AVROTROS",
            slug = "avrotros",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/AVROTROS_169_2017f2f8e7.jpg",
            description = "Voor cultuur, klassieke muziek, amusement en informatieve programma's."
        ),
        BroadcasterItem(
            id = "236908",
            name = "BNNVARA",
            slug = "bnnvara",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/BNNVARA_LOGO_ROOD_RGB_6a1abdf767.png",
            description = "Progressief, maatschappelijk betrokken en spraakmakend entertainment."
        ),
        BroadcasterItem(
            id = "209288",
            name = "VPRO",
            slug = "vpro",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/VPRO_2017f2f8e7.jpg",
            description = "Eigenzinnige documentaires, cultuur en grensverleggende televisie."
        ),
        BroadcasterItem(
            id = "236907",
            name = "KRO-NCRV",
            slug = "kro-ncrv",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/KRONCRV_2017f2f8e7.jpg",
            description = "Verbindend, maatschappelijk en verhalen met een ziel."
        ),
        BroadcasterItem(
            id = "211933",
            name = "NTR",
            slug = "ntr",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/NTR_2017f2f8e7.jpg",
            description = "Informatie, educatie, cultuur en jeugdtelevisie."
        ),
        BroadcasterItem(
            id = "209289",
            name = "EO",
            slug = "eo",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/EO_logo_liggend_a637cf870b.jpg",
            description = "Evangelische Omroep: hoop, zingeving en menselijke verhalen."
        ),
        BroadcasterItem(
            id = "209291",
            name = "Omroep MAX",
            slug = "omroep-max",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/MAX_2017f2f8e7.jpg",
            description = "Publieke omroep voor vijftigplussers: geschiedenis, cultuur en gezelligheid."
        ),
        BroadcasterItem(
            id = "211935",
            name = "HUMAN",
            slug = "human",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/Human169_079b8c5ce5.jpg",
            description = "Humanistische omroep met filosofische en ethische programma's."
        )
    )

    /**
     * Hoofdgenres in de Nederlandse televisiegeschiedenis.
     */
    fun getGenres(): List<GenreItem> = listOf(
        GenreItem("genre-amusement", "Amusement", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Amusement_ce12d192f7.jpg"),
        GenreItem("genre-humor", "Humor & Cabaret", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Humor_60e9037675.jpg"),
        GenreItem("genre-fictie", "Fictie & Drama", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Fictie_7fa3f1df05.jpg"),
        GenreItem("genre-jeugd", "Jeugd & Kinderen", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Jeugd_727d89f0a6.jpg"),
        GenreItem("genre-muziek", "Muziek & Concerten", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Muziek_2e22026792.jpg"),
        GenreItem("genre-actualiteiten", "Actualiteiten", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Actualiteiten_178b0a5884.jpg"),
        GenreItem("genre-reportages", "Reportages & Docu", "https://schatkamer.beeldengeluid.nl/assets/cms/GENRE_Reportages_adffa04319.jpg")
    )

    companion object {
        @Volatile
        private var instance: SchatkamerCatalogRepository? = null

        fun getInstance(context: Context): SchatkamerCatalogRepository =
            instance ?: synchronized(this) {
                instance ?: SchatkamerCatalogRepository(context).also { instance = it }
            }
    }
}
