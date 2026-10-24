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
    val dateTo: String? = null
) {
    fun toFilter(catalog: SchatkamerCatalogRepository): SearchFilter = SearchFilter(
        broadcasters = broadcaster?.let { catalog.broadcasterByName(it).archiveNames.toSet() }.orEmpty(),
        genres = setOfNotNull(genre),
        subjects = setOfNotNull(subject),
        people = setOfNotNull(person),
        decade = decade,
        dateFrom = dateFrom,
        dateTo = dateTo,
        sort = if (dateFrom != null) SortOrder.OLDEST else SortOrder.RELEVANCE
    )

    /** Kort label voor boven de titel. */
    val kindLabel: String
        get() = when {
            person != null -> "PERSOON"
            broadcaster != null -> "OMROEP"
            genre != null -> "GENRE"
            subject != null -> "ONDERWERP"
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
                dateTo = uri.getQueryParameter("dt")
            )
        }
    }
}

