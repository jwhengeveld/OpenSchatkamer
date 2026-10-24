package nl.openschatkamer.core.data

/** Wat een kaart in de catalogus voorstelt; bepaalt waar een tik naartoe gaat. */
enum class ItemKind { PROGRAM, SERIES, STORY, PERSON, BROADCASTER }

/** Hoe de website een rij toont; de apps kiezen daarop hun kaartvorm. */
enum class RowLayout { LANDSCAPE, LANDSCAPE_LARGE, PORTRAIT, RANKED, CIRCLE, LOGO }

data class SchatkamerItem(
    val id: String,
    val title: String,
    val kind: ItemKind = ItemKind.PROGRAM,
    val subTitle: String = "",
    val description: String = "",
    /** ISO-datum (jjjj-mm-dd) als die bekend is, anders de tekst van de site. */
    val date: String = "",
    val mediaType: String = "video",
    val broadcaster: String = "",
    val imageUrl: String = "",
    val url: String = "",
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val slug: String? = null,
    val isPlayable: Boolean = true
) {
    val isAudio: Boolean get() = mediaType.equals("audio", ignoreCase = true)
    val year: String get() = SchatkamerText.year(date)
}

data class SchatkamerRow(
    val id: String,
    val title: String,
    val items: List<SchatkamerItem> = emptyList(),
    val layout: RowLayout = RowLayout.LANDSCAPE
) {
    val kind: ItemKind get() = items.firstOrNull()?.kind ?: ItemKind.PROGRAM
}

data class SchatkamerHome(
    val rows: List<SchatkamerRow> = emptyList(),
    /** Sleutels (soort + id) van kaarten die er sinds de vorige keer bij zijn gekomen. */
    val newIds: Set<String> = emptySet()
) {
    fun isNew(item: SchatkamerItem): Boolean = (item.kind.name + item.id) in newIds

    /** Uitgelichte titels bovenaan: series en uitzendingen met een beeld, geen verhalen of personen. */
    val heroItems: List<SchatkamerItem>
        get() = rows.asSequence()
            .filter { it.kind == ItemKind.SERIES || it.kind == ItemKind.PROGRAM }
            .flatMap { it.items.take(3).asSequence() }
            .filter { it.imageUrl.isNotBlank() }
            .distinctBy { it.id }
            .take(6)
            .toList()

    val broadcasters: List<SchatkamerItem>
        get() = rows.firstOrNull { it.kind == ItemKind.BROADCASTER }?.items.orEmpty()

    /** De rijen om te bladeren; omroepen hebben een eigen plek in de apps. */
    val browseRows: List<SchatkamerRow>
        get() = rows.filter { it.kind != ItemKind.BROADCASTER }
}

/**
 * De afspeelgegevens van één uitzending, zoals de website ze aan de speler geeft.
 *
 * [playUrl] is het ondertekende adres precies zoals de site het levert. Bij het
 * openen zet CloudFront daarmee de toegangscookies en stuurt door naar
 * [manifestUrl]; zo werkt het ook op een Chromecast, die zelf geen cookies kan
 * krijgen. Op het toestel zelf sturen we [cookieHeader] direct mee.
 */
data class SchatkamerStreamInfo(
    val playUrl: String,
    val manifestUrl: String,
    val cookieHeader: String = "",
    val posterUrl: String = "",
    val subtitlesUrl: String? = null,
    val storyboardUrl: String? = null,
    /** Begin en eind van het programma binnen de archiefband, in seconden. */
    val clipStartSec: Double = 0.0,
    val clipEndSec: Double = 0.0,
    val drmProtected: Boolean = true,
    /** Unix-tijd (seconden) waarop de handtekening verloopt; 0 = onbekend. */
    val expiresAtSec: Long = 0L
) {
    val clipDurationMs: Long
        get() = if (clipEndSec > clipStartSec) ((clipEndSec - clipStartSec) * 1000).toLong() else 0L

    fun isExpired(nowMs: Long = System.currentTimeMillis(), marginMs: Long = 10 * 60_000L): Boolean =
        expiresAtSec > 0 && nowMs + marginMs >= expiresAtSec * 1000
}

data class Credit(val name: String, val role: String)

data class SchatkamerProgramDetail(
    val id: String,
    val title: String,
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val seriesSlug: String? = null,
    val description: String = "",
    val date: String = "",
    val mediaType: String = "video",
    val broadcaster: String = "",
    val broadcasterLogo: String = "",
    val imageUrl: String = "",
    val durationMinutes: Int = 0,
    val genres: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val credits: List<Credit> = emptyList(),
    val episodeNumber: Int? = null,
    val seasonTitle: String? = null,
    val disclaimer: String? = null,
    val isPlayable: Boolean = true
) {
    val isAudio: Boolean get() = mediaType.equals("audio", ignoreCase = true)

    fun toItem(): SchatkamerItem = SchatkamerItem(
        id = id,
        title = title,
        kind = ItemKind.PROGRAM,
        subTitle = seriesTitle.orEmpty(),
        description = description,
        date = date,
        mediaType = mediaType,
        broadcaster = broadcaster,
        imageUrl = imageUrl,
        url = "${SchatkamerHttpClient.BASE_URL}/programma/$id",
        seriesId = seriesId,
        seriesTitle = seriesTitle,
        isPlayable = isPlayable
    )
}

data class SchatkamerSeries(
    val id: String,
    val title: String,
    val slug: String = "",
    val summary: String = "",
    val imageUrl: String = "",
    val portraitUrl: String = "",
    val broadcaster: String = "",
    val genres: List<String> = emptyList(),
    val firstYear: String = "",
    val lastYear: String = ""
) {
    val period: String
        get() = when {
            firstYear.isBlank() -> lastYear
            lastYear.isBlank() || lastYear == firstYear -> firstYear
            else -> "$firstYear – $lastYear"
        }

    fun toItem(): SchatkamerItem = SchatkamerItem(
        id = id,
        title = title,
        kind = ItemKind.SERIES,
        subTitle = broadcaster,
        description = summary,
        broadcaster = broadcaster,
        imageUrl = portraitUrl.ifBlank { imageUrl },
        seriesId = id,
        seriesTitle = title,
        slug = slug
    )
}

data class SchatkamerStory(
    val slug: String,
    val title: String,
    val description: String = "",
    val imageUrl: String = "",
    val programs: List<SchatkamerItem> = emptyList()
)

/** De sleutels die de website zelf aan haar speler en Chromecast geeft (window.__CONFIG__). */
data class SiteConfig(
    val theoplayerLicense: String,
    val theoplayerCdnKey: String,
    val chromecastAppId: String
) {
    val theoplayerLibraryLocation: String
        get() = "https://cdn.myth.theoplayer.com/$theoplayerCdnKey/"
}

data class PlayableMedia(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val seriesId: String? = null,
    val posterUrl: String = "",
    val mediaType: String = "video",
    val broadcaster: String = "",
    val stream: SchatkamerStreamInfo,
    val resumePositionMs: Long = 0L
) {
    val isAudio: Boolean get() = mediaType.equals("audio", ignoreCase = true)

    fun toItem(): SchatkamerItem = SchatkamerItem(
        id = id,
        title = title,
        subTitle = subtitle,
        mediaType = mediaType,
        broadcaster = broadcaster,
        imageUrl = posterUrl,
        seriesId = seriesId,
        seriesTitle = subtitle.takeIf { it.isNotBlank() }
    )
}

data class FacetItem(
    val label: String,
    val count: Int = 0
)

data class SearchFacets(
    val genres: List<FacetItem> = emptyList(),
    val subjects: List<FacetItem> = emptyList(),
    val people: List<FacetItem> = emptyList(),
    val broadcasters: List<FacetItem> = emptyList(),
    val materials: List<FacetItem> = emptyList(),
    val programs: List<FacetItem> = emptyList()
)

data class SearchResult(
    val query: String,
    val total: Int,
    val items: List<SchatkamerItem> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val facets: SearchFacets = SearchFacets()
) {
    /**
     * De series die in deze resultaten voorkomen, op volgorde van het aantal
     * treffers. Zo toont "jiskefet" eerst de serie zelf in plaats van alleen
     * losse afleveringen.
     */
    val series: List<SchatkamerItem>
        get() = items.filter { !it.seriesId.isNullOrBlank() && !it.seriesTitle.isNullOrBlank() }
            .groupBy { it.seriesId!! }
            .entries
            .sortedByDescending { it.value.size }
            .map { (seriesId, episodes) ->
                val first = episodes.first()
                SchatkamerItem(
                    id = seriesId,
                    title = first.seriesTitle!!,
                    kind = ItemKind.SERIES,
                    subTitle = first.broadcaster,
                    broadcaster = first.broadcaster,
                    imageUrl = first.imageUrl,
                    seriesId = seriesId,
                    seriesTitle = first.seriesTitle,
                    mediaType = first.mediaType
                )
            }
}

/** Sorteringen die de zoekdienst van de Schatkamer kent. */
enum class SortOrder(val apiValue: String, val label: String) {
    RELEVANCE("relevance", "Relevantie"),
    NEWEST("date-newest", "Nieuwste eerst"),
    OLDEST("date-oldest", "Oudste eerst")
}

enum class Decade(val label: String, val from: Int, val to: Int) {
    D1920("Jaren 1920", 1920, 1929),
    D1930("Jaren '30", 1930, 1939),
    D1940("Jaren '40", 1940, 1949),
    D1950("Jaren '50", 1950, 1959),
    D1960("Jaren '60", 1960, 1969),
    D1970("Jaren '70", 1970, 1979),
    D1980("Jaren '80", 1980, 1989),
    D1990("Jaren '90", 1990, 1999),
    D2000("Jaren '00", 2000, 2009),
    D2010("Jaren '10", 2010, 2019),
    D2020("Sinds 2020", 2020, 2029)
}

/**
 * Alle zoek- en bladerfilters, één-op-één met de parameters van de zoekdienst
 * van de website (/api/media/bff/search). Meerdere waarden binnen één filter
 * (bijv. twee genres) betekent "het een of het ander", zoals op de site.
 */
data class SearchFilter(
    val query: String = "",
    /** Archiefnamen van omroepen (fusieomroepen tellen hun voorgangers mee). */
    val broadcasters: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val subjects: Set<String> = emptySet(),
    val people: Set<String> = emptySet(),
    val seriesId: String? = null,
    /** "video", "audio" of null voor allebei. */
    val material: String? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
    /** Snelkeuze voor een decennium; [yearFrom]/[yearTo] gaan voor als ze gezet zijn. */
    val decade: Decade? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** Exacte datums (jjjj-mm-dd), voor "deze week, 50 jaar geleden". */
    val dateFrom: String? = null,
    val dateTo: String? = null,
    /** Alleen wat je echt kunt afspelen; zo staat het op de website ook standaard. */
    val playableOnly: Boolean = true
) {
    val effectiveYearFrom: Int? get() = yearFrom ?: decade?.from
    val effectiveYearTo: Int? get() = yearTo ?: decade?.to
    val hasPeriod: Boolean get() = effectiveYearFrom != null || effectiveYearTo != null || dateFrom != null

    /** Aantal verfijningen bovenop de zoekterm of de context van het scherm. */
    val activeFilterCount: Int
        get() = genres.size + subjects.size + (if (material != null) 1 else 0) + (if (hasPeriod) 1 else 0) +
            (if (!playableOnly) 1 else 0) + (if (sort != SortOrder.RELEVANCE && query.isNotBlank()) 1 else 0)

    fun withoutRefinements(): SearchFilter = copy(
        genres = emptySet(), subjects = emptySet(), material = null, decade = null, yearFrom = null, yearTo = null,
        dateFrom = null, dateTo = null, sort = if (query.isBlank()) sort else SortOrder.RELEVANCE, playableOnly = true
    )

    /** De parameters voor de zoekdienst, in dezelfde vorm als de website ze stuurt. */
    fun toQueryParams(offset: Int, limit: Int): List<Pair<String, String>> = buildList {
        if (query.isNotBlank()) add("query" to query.trim())
        broadcasters.sorted().forEach { add("broadcaster[]" to it) }
        genres.sorted().forEach { add("genre[]" to it) }
        subjects.sorted().forEach { add("subject[]" to it) }
        people.sorted().forEach { add("person[]" to it) }
        seriesId?.let { add("seriesId" to it) }
        material?.let { add("material[]" to it) }
        // Zonder zoekterm is "relevantie" betekenisloos; de website toont dan de nieuwste.
        val effectiveSort = if (query.isBlank() && sort == SortOrder.RELEVANCE) SortOrder.NEWEST else sort
        add("sort" to effectiveSort.apiValue)
        effectiveYearFrom?.let { add("yearmin" to it.toString()) }
        effectiveYearTo?.let { add("yearmax" to it.toString()) }
        dateFrom?.let { add("datemin" to it) }
        dateTo?.let { add("datemax" to it) }
        if (playableOnly) add("playable" to "true")
        add("offset" to offset.toString())
        add("limit" to limit.toString())
    }

    val cacheKey: String get() = toQueryParams(0, 0).joinToString("&") { "${it.first}=${it.second}" }
}
