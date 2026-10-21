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
    val widevineLicenseUrl: String = "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145"
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
    val resumePositionMs: Long = 0L
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

data class SearchResult(
    val query: String,
    val total: Int,
    val items: List<SchatkamerItem> = emptyList()
)
