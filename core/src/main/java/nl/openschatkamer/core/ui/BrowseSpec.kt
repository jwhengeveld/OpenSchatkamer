package nl.openschatkamer.core.ui

import android.net.Uri
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SearchFilter

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
    val decade: Decade? = null
) {
    fun toFilter(catalog: SchatkamerCatalogRepository): SearchFilter = SearchFilter(
        broadcasters = broadcaster?.let { catalog.broadcasterByName(it).archiveNames }.orEmpty(),
        genre = genre,
        subject = subject,
        person = person,
        decade = decade
    )

    fun encode(): String = Uri.Builder().apply {
        appendQueryParameter("t", title)
        broadcaster?.let { appendQueryParameter("b", it) }
        genre?.let { appendQueryParameter("g", it) }
        subject?.let { appendQueryParameter("s", it) }
        person?.let { appendQueryParameter("p", it) }
        decade?.let { appendQueryParameter("d", it.name) }
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
                decade = uri.getQueryParameter("d")?.let { runCatching { Decade.valueOf(it) }.getOrNull() }
            )
        }
    }
}

