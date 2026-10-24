package nl.openschatkamer.core.ui

import android.net.Uri
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SearchFilter
import nl.openschatkamer.core.data.SortOrder

/**
 * Wat een bladerscherm toont: een titel en een filter op de zoekdienst
 * (omroep, genre, onderwerp, persoon of decennium).
 */
data class BrowseSpec(
    val title: String,
    val broadcaster: String? = null,
    val genre: String? = null,
    val subject: String? = null,
    val person: String? = null,
    val decade: Decade? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    /** Omroepen precies zoals ze in het archief heten (bijv. NOT en TELEAC voor Schooltv). */
    val archiveBroadcasters: List<String> = emptyList(),
    val seriesId: String? = null,
    /** "video", "audio" of null. */
    val material: String? = null,
    /** Extra genres en onderwerpen (binnen een soort: of-of). */
    val genres: List<String> = emptyList(),
    val subjects: List<String> = emptyList()
) {
    fun toFilter(catalog: SchatkamerCatalogRepository): SearchFilter = SearchFilter(
        broadcasters = broadcaster?.let { catalog.broadcasterByName(it).archiveNames.toSet() }.orEmpty() + archiveBroadcasters,
        seriesId = seriesId,
        material = material,
        genres = setOfNotNull(genre) + genres,
        subjects = setOfNotNull(subject) + subjects,
        people = setOfNotNull(person),
        decade = decade,
        dateFrom = dateFrom,
        dateTo = dateTo,
        sort = when {
            dateFrom != null -> SortOrder.OLDEST
            seriesId != null -> SortOrder.NEWEST
            else -> SortOrder.RELEVANCE
        }
    )

    /** Kort label voor boven de titel. */
    val kindLabel: String
        get() = when {
            person != null -> "PERSOON"
            seriesId != null -> "SERIE"
            broadcaster != null || archiveBroadcasters.isNotEmpty() -> "OMROEP"
            genre != null || genres.isNotEmpty() -> "GENRE"
            subject != null || subjects.isNotEmpty() -> "ONDERWERP"
            dateFrom != null -> "TERUG IN DE TIJD"
            decade != null -> "PERIODE"
            else -> "BLADEREN"
        }

    fun encode(): String = Uri.Builder().apply {
        appendQueryParameter("t", title)
        broadcaster?.let { appendQueryParameter("b", it) }
        genre?.let { appendQueryParameter("g", it) }
        subject?.let { appendQueryParameter("s", it) }
        person?.let { appendQueryParameter("p", it) }
        decade?.let { appendQueryParameter("d", it.name) }
        dateFrom?.let { appendQueryParameter("df", it) }
        dateTo?.let { appendQueryParameter("dt", it) }
        if (archiveBroadcasters.isNotEmpty()) appendQueryParameter("ab", archiveBroadcasters.joinToString("|"))
        seriesId?.let { appendQueryParameter("sid", it) }
        material?.let { appendQueryParameter("m", it) }
        if (genres.isNotEmpty()) appendQueryParameter("gs", genres.joinToString("|"))
        if (subjects.isNotEmpty()) appendQueryParameter("ss", subjects.joinToString("|"))
    }.build().encodedQuery.orEmpty()

    companion object {
        fun decode(raw: String): BrowseSpec {
            val uri = Uri.parse("x://x?$raw")
            return BrowseSpec(
                title = uri.getQueryParameter("t").orEmpty(),
                broadcaster = uri.getQueryParameter("b"),
                genre = uri.getQueryParameter("g"),
                subject = uri.getQueryParameter("s"),
                person = uri.getQueryParameter("p"),
                decade = uri.getQueryParameter("d")?.let { runCatching { Decade.valueOf(it) }.getOrNull() },
                dateFrom = uri.getQueryParameter("df"),
                dateTo = uri.getQueryParameter("dt"),
                archiveBroadcasters = uri.getQueryParameter("ab")?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                seriesId = uri.getQueryParameter("sid"),
                material = uri.getQueryParameter("m"),
                genres = uri.getQueryParameter("gs")?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                subjects = uri.getQueryParameter("ss")?.split('|')?.filter { it.isNotBlank() }.orEmpty()
            )
        }
    }
}

