package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

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

    fun getBroadcasterApiNames(slug: String): List<String> = when (slug.lowercase().trim()) {
        "eo" -> listOf("EO")
        "nos" -> listOf("NOS")
        "avrotros" -> listOf("AVROTROS", "AVRO", "TROS")
        "bnnvara" -> listOf("BNNVARA", "BNN", "VARA")
        "vpro" -> listOf("VPRO")
        "kro-ncrv" -> listOf("KRO-NCRV", "KRO", "NCRV")
        "ntr" -> listOf("NTR")
        "max" -> listOf("OMROEP MAX", "MAX")
        "human" -> listOf("HUMAN")
        "wnl" -> listOf("WNL")
        "powned" -> listOf("POWNED")
        "npo" -> listOf("NPO")
        else -> listOf(slug.uppercase().trim())
    }

    private val catalogMemoryCache = ConcurrentHashMap<String, CachedSearchResult>()

    private data class CachedSearchResult(
        val result: SearchResult,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() - timestamp > 10 * 60 * 1000L // 10 min
    }

    fun invalidateCache(slug: String? = null) {
        if (slug == null) {
            catalogMemoryCache.clear()
        } else {
            val keyPart = "b=$slug"
            val toRemove = catalogMemoryCache.keys.filter { it.contains(keyPart) }
            for (k in toRemove) {
                catalogMemoryCache.remove(k)
            }
        }
    }

    /**
     * Zoeken en filteren in het archief via de officiële BFF API met paginering en filters.
     */
    suspend fun queryCatalog(
        filter: SearchFilter,
        offset: Int = 0,
        limit: Int = 24,
        forceRefresh: Boolean = false
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        val cacheKey = "q=${filter.query}&b=${filter.broadcasterSlug}&names=${filter.broadcasterNames.joinToString(",")}&g=${filter.genre}&m=${filter.material}&s=${filter.sort}&d=${filter.decade}&ymin=${filter.effectiveYearMin}&ymax=${filter.effectiveYearMax}&p=${filter.playableOnly}&off=$offset&lim=$limit"

        if (!forceRefresh) {
            val cachedEntry = catalogMemoryCache[cacheKey]
            if (cachedEntry != null && !cachedEntry.isExpired && cachedEntry.result.items.isNotEmpty()) {
                return@withContext Result.success(cachedEntry.result)
            }
        }

        runCatching {
            val queryParams = mutableListOf<String>()

            if (filter.query.isNotBlank()) {
                queryParams.add("q=" + URLEncoder.encode(filter.query.trim(), "UTF-8"))
            }

            val bNames = if (filter.broadcasterNames.isNotEmpty()) {
                filter.broadcasterNames
            } else if (!filter.broadcasterSlug.isNullOrBlank()) {
                getBroadcasterApiNames(filter.broadcasterSlug)
            } else emptyList()

            for (b in bNames) {
                queryParams.add("broadcaster[]=" + URLEncoder.encode(b, "UTF-8"))
            }

            if (!filter.genre.isNullOrBlank()) {
                queryParams.add("genre[]=" + URLEncoder.encode(filter.genre.trim(), "UTF-8"))
            }

            if (!filter.material.isNullOrBlank()) {
                queryParams.add("material[]=" + URLEncoder.encode(filter.material.trim(), "UTF-8"))
            }

            if (filter.sort.isNotBlank()) {
                queryParams.add("sort=" + URLEncoder.encode(filter.sort.trim(), "UTF-8"))
            }

            filter.effectiveYearMin?.let { queryParams.add("yearmin=$it") }
            filter.effectiveYearMax?.let { queryParams.add("yearmax=$it") }

            if (filter.playableOnly) {
                queryParams.add("playable=true")
            }

            queryParams.add("offset=$offset")
            queryParams.add("limit=$limit")

            val queryString = queryParams.joinToString("&")
            val bffUrl = "${SchatkamerHttpClient.BASE_URL}/api/media/bff/search?$queryString"
            val json = httpClient.getJson(bffUrl)
            val parsed = SchatkamerParser.parseBffSearch(
                jsonString = json,
                fallbackQuery = filter.query.ifBlank { filter.broadcasterSlug.orEmpty() },
                currentOffset = offset
            )

            // Cache in memory voor bliksemsnelle filterwissels
            catalogMemoryCache[cacheKey] = CachedSearchResult(parsed)

            // Cache broadcaster eerste pagina
            if (offset == 0 && !filter.broadcasterSlug.isNullOrBlank() && !filter.hasActiveFilters && filter.query.isBlank() && parsed.items.isNotEmpty()) {
                snapshotStore.writeBroadcaster(filter.broadcasterSlug, parsed)
            }

            parsed
        }.recoverCatching { error ->
            // Fallback naar cache als offset == 0
            if (!filter.broadcasterSlug.isNullOrBlank() && offset == 0) {
                val cached = snapshotStore.readBroadcaster(filter.broadcasterSlug)
                if (cached != null) return@recoverCatching cached
            }
            // Fallback naar HTML/SSR search
            if (offset == 0 && filter.query.isNotBlank()) {
                return@recoverCatching searchLegacy(filter.query).getOrThrow()
            }
            throw error
        }
    }

    private suspend fun searchLegacy(query: String): Result<SearchResult> = runCatching {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "${SchatkamerHttpClient.BASE_URL}/zoeken?q=$encoded"
        val html = httpClient.getHtml(url)
        SchatkamerParser.parseSearch(html, query)
    }

    /**
     * Zoeken in het complete archief via de BFF API (met optionele filters en paginering).
     */
    suspend fun search(
        query: String,
        offset: Int = 0,
        filter: SearchFilter = SearchFilter(query = query)
    ): Result<SearchResult> {
        val updated = filter.copy(query = query)
        return queryCatalog(updated, offset = offset)
    }

    /**
     * Haalt een gecachte omroep-afspeellijst op uit het snapshot geheugen/schijf (0ms).
     */
    fun cachedBroadcaster(slug: String): SearchResult? = snapshotStore.readBroadcaster(slug)

    /**
     * Programma's van een specifieke publieke omroep met paginering, filters en Stale-While-Revalidate caching.
     */
    suspend fun getProgramsForBroadcaster(
        id: String,
        slug: String,
        forceRefresh: Boolean = false,
        offset: Int = 0,
        filter: SearchFilter = SearchFilter(broadcasterSlug = slug)
    ): Result<SearchResult> {
        if (!forceRefresh && offset == 0 && !filter.hasActiveFilters && filter.query.isBlank()) {
            val cached = snapshotStore.readBroadcaster(slug)
            if (cached != null && cached.items.isNotEmpty()) {
                return Result.success(cached)
            }
        }
        val updated = filter.copy(broadcasterSlug = slug)
        return queryCatalog(updated, offset = offset, forceRefresh = forceRefresh)
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
     * Bekende publieke omroepen in de Schatkamer met officiële ID's en slugs.
     */
    fun getBroadcasters(): List<BroadcasterItem> = listOf(
        BroadcasterItem(
            id = "209251",
            name = "EO",
            slug = "eo",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/EO_logo_liggend_a637cf870b.jpg",
            description = "Evangelische Omroep: hoop, zingeving en menselijke verhalen."
        ),
        BroadcasterItem(
            id = "209284",
            name = "NOS",
            slug = "nos",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/NOS_2017f2f8e7.jpg",
            description = "Nederlandse Omroep Stichting: nieuws, sport en nationale evenementen."
        ),
        BroadcasterItem(
            id = "236909",
            name = "AVROTROS",
            slug = "avrotros",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/AVROTROS_169_2017f2f8e7.jpg",
            description = "Voor cultuur, klassieke muziek, amusement en informatieve programma's."
        ),
        BroadcasterItem(
            id = "1672962",
            name = "BNNVARA",
            slug = "bnnvara",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/BNNVARA_LOGO_ROOD_RGB_6a1abdf767.png",
            description = "Progressief, maatschappelijk betrokken en spraakmakend entertainment."
        ),
        BroadcasterItem(
            id = "209345",
            name = "VPRO",
            slug = "vpro",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/VPRO_2017f2f8e7.jpg",
            description = "Eigenzinnige documentaires, cultuur en grensverleggende televisie."
        ),
        BroadcasterItem(
            id = "238098",
            name = "KRO-NCRV",
            slug = "kro-ncrv",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/KRONCRV_2017f2f8e7.jpg",
            description = "Verbindend, maatschappelijk en verhalen met een ziel."
        ),
        BroadcasterItem(
            id = "223534",
            name = "NTR",
            slug = "ntr",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/NTR_2017f2f8e7.jpg",
            description = "Informatie, educatie, cultuur en iconische jeugdtelevisie."
        ),
        BroadcasterItem(
            id = "209293",
            name = "Omroep MAX",
            slug = "omroep-max",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/MAX_2017f2f8e7.jpg",
            description = "Publieke omroep voor vijftigplussers: geschiedenis, cultuur en gezelligheid."
        ),
        BroadcasterItem(
            id = "209257",
            name = "HUMAN",
            slug = "human",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/Human169_079b8c5ce5.jpg",
            description = "Humanistische omroep met filosofische en ethische programma's."
        ),
        BroadcasterItem(
            id = "223533",
            name = "WNL",
            slug = "wnl",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/WNL_169_36efa28b22.jpg",
            description = "Wij Nederland: opinie, politiek, economie en ochtendtelevisie."
        ),
        BroadcasterItem(
            id = "223536",
            name = "PowNed",
            slug = "powned",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/POWNED_logo_liggend_c8821feec0.jpg",
            description = "Publieke omroep met prikkelende journalistiek en spraakmakende items."
        ),
        BroadcasterItem(
            id = "209287",
            name = "NPO",
            slug = "npo",
            logoUrl = "https://schatkamer.beeldengeluid.nl/assets/cms/NPO_logo_liggend_22253ac321.jpg",
            description = "Nederlandse Publieke Omroep: het overkoepelende publieke bestel."
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
