package nl.beeldengeluid.schatkamer.core.data

data class SchatkamerItem(
    val id: String,
    val title: String,
    val subTitle: String = "",
    val description: String = "",
    val date: String = "",
    val mediaType: String = "video", // "video" of "audio"
    val broadcaster: String = "",
    val imageUrl: String = "",
    val url: String = "",
    val seriesId: String? = null,
    val seriesSlug: String? = null,
    val isPlayable: Boolean = true
)

data class SchatkamerRow(
    val id: String,
    val title: String,
    val items: List<SchatkamerItem> = emptyList()
)

data class SchatkamerHome(
    val heroItems: List<SchatkamerItem> = emptyList(),
    val rows: List<SchatkamerRow> = emptyList()
)

data class SchatkamerStreamInfo(
    val streamUrl: String,
    val posterUrl: String = "",
    val subtitlesUrl: String? = null,
    val storyboardUrl: String? = null,
    val durationNumber: Long = 0L,
    val startSeconds: Double = 0.0,
    val endSeconds: Double = 0.0,
    val drmState: String = "LIVE",
    val widevineLicenseUrl: String = "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145",
    val cookieHeader: String = ""
)

data class SchatkamerProgramDetail(
    val id: String,
    val title: String,
    val seriesTitle: String? = null,
    val seriesId: String? = null,
    val seriesSlug: String? = null,
    val description: String = "",
    val date: String = "",
    val mediaType: String = "video",
    val broadcaster: String = "",
    val imageUrl: String = "",
    val presenters: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val collection: String = "",
    val legalBasisText: String = "",
    val isPlayable: Boolean = true,
    val streamInfo: SchatkamerStreamInfo? = null,
    val episodes: List<SchatkamerItem> = emptyList(),
    val isFavorite: Boolean = false
)

data class PlayableMedia(
    val title: String,
    val subtitle: String = "",
    val streamUrl: String,
    val posterUrl: String = "",
    val isLive: Boolean = false,
    val id: String = "",
    val seriesId: String = "",
    val mediaType: String = "video",
    val widevineLicenseUrl: String = "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145",
    val subtitlesUrl: String? = null,
    val resumePositionMs: Long = 0L,
    val cookieHeader: String = ""
)

data class BroadcasterItem(
    val id: String,
    val name: String,
    val slug: String,
    val logoUrl: String = "",
    val description: String = ""
)

data class GenreItem(
    val slug: String,
    val title: String,
    val imageUrl: String = ""
)

data class FacetItem(
    val label: String,
    val count: Int = 0
)

data class SearchResult(
    val query: String,
    val total: Int,
    val items: List<SchatkamerItem> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val availableGenres: List<FacetItem> = emptyList(),
    val availableMaterials: List<FacetItem> = emptyList(),
    val availableBroadcasters: List<FacetItem> = emptyList()
) {
    val genreLabels: List<String>
        get() = availableGenres.map { it.label }
}

data class SearchFilter(
    val query: String = "",
    val broadcasterSlug: String? = null,
    val broadcasterNames: List<String> = emptyList(),
    val genre: String? = null,
    val material: String? = null, // "video", "audio", of null (alles)
    val sort: String = "relevance", // "relevance", "date-newest", "date-oldest"
    val decade: String? = null, // "1950s", "1960s", "1970s", "1980s", "1990s", "2000s"
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val playableOnly: Boolean = false
) {
    val effectiveYearMin: Int?
        get() = yearMin ?: when (decade) {
            "1950s" -> 1950
            "1960s" -> 1960
            "1970s" -> 1970
            "1980s" -> 1980
            "1990s" -> 1990
            "2000s" -> 2000
            else -> null
        }

    val effectiveYearMax: Int?
        get() = yearMax ?: when (decade) {
            "1950s" -> 1959
            "1960s" -> 1969
            "1970s" -> 1979
            "1980s" -> 1989
            "1990s" -> 1999
            "2000s" -> 2026
            else -> null
        }

    val hasActiveFilters: Boolean
        get() = !genre.isNullOrBlank() || !material.isNullOrBlank() || sort != "relevance" || !decade.isNullOrBlank() || yearMin != null || yearMax != null || playableOnly

    val activeFilterCount: Int
        get() {
            var count = 0
            if (!genre.isNullOrBlank()) count++
            if (!material.isNullOrBlank()) count++
            if (sort != "relevance") count++
            if (!decade.isNullOrBlank() || yearMin != null || yearMax != null) count++
            if (playableOnly) count++
            return count
        }

    fun withToggledMaterial(type: String): SearchFilter {
        return if (material == type) copy(material = null) else copy(material = type)
    }

    fun withNextSort(): SearchFilter {
        val next = when (sort) {
            "relevance" -> "date-newest"
            "date-newest" -> "date-oldest"
            else -> "relevance"
        }
        return copy(sort = next)
    }

    fun withoutFilters(): SearchFilter {
        return SearchFilter(
            query = query,
            broadcasterSlug = broadcasterSlug,
            broadcasterNames = broadcasterNames
        )
    }
}
