package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nl.openschatkamer.core.auth.OnlineLibrary
import nl.openschatkamer.core.auth.OnlineList

/** Een lijst in de app: lokaal op dit toestel of online bij je Schatkamer-account. */
data class AnyList(
    val key: String,
    val name: String,
    val online: Boolean,
    val count: Int,
    val thumbnailUrl: String,
    /** Alleen gevuld voor lokale lijsten; online lijsten laden hun inhoud bij openen. */
    val items: List<SchatkamerItem> = emptyList(),
    val programIds: Set<String> = emptySet()
) {
    val localId: Long? get() = if (online) null else key.removePrefix("local:").toLongOrNull()
    val uuid: String? get() = if (online) key.removePrefix("online:") else null
}

/**
 * Eén ingang voor alles wat je bewaart. Ingelogd gaan favorieten en series
 * naar je Schatkamer-account (dus ook zichtbaar op de website); zonder
 * account blijft alles op het toestel. Kijkvoortgang en geschiedenis staan
 * altijd lokaal: daar heeft de website geen opslag voor.
 */
class UserLibrary(val local: LibraryRepository, val online: OnlineLibrary) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val account = online.account.account

    /** Bewaarde uitzendingen en series (lokaal), voor Mijn lijst. */
    val favorites: StateFlow<List<SchatkamerItem>> = local.favorites

    /** Alle lijsten: eerst online (bij inloggen), dan lokaal. */
    val lists: StateFlow<List<AnyList>> = combine(online.lists, local.lists) { remote, mine ->
        remote.map { it.toAnyList() } + mine.map { l ->
            AnyList(
                key = "local:${l.id}", name = l.name, online = false, count = l.items.size,
                thumbnailUrl = l.items.firstOrNull()?.imageUrl.orEmpty(), items = l.items,
                programIds = l.items.map { it.id }.toSet()
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Series die je volgt: online "Mijn series" en lokaal gevolgd, samengevoegd. */
    val series: StateFlow<List<FollowedSeries>> = combine(online.savedSeries, local.followed) { remote, mine ->
        val byId = mine.associateBy { it.item.id }
        (remote.map { s -> byId[s.id] ?: FollowedSeries(s, 0, 0) } + mine.filter { m -> remote.none { it.id == m.item.id } })
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun OnlineList.toAnyList() = AnyList(
        key = "online:$uuid", name = name, online = true, count = programCount, thumbnailUrl = thumbnailUrl,
        programIds = programIds.toSet()
    )

    // -------------------------------------------------------------- favoriet

    fun isFavorite(item: SchatkamerItem): Boolean = when {
        item.kind == ItemKind.SERIES -> isFollowing(item.id)
        online.isSignedIn -> online.isFavorite(item.id)
        else -> local.isFavorite(item.id)
    }

    /** Bewaar of verwijder; geeft true als het nu bewaard is. */
    fun toggleFavorite(item: SchatkamerItem): Boolean {
        if (item.kind == ItemKind.SERIES) return toggleFollow(item, 0)
        val now = !isFavorite(item)
        if (online.isSignedIn) online.toggleFavorite(item) else local.toggleFavorite(item)
        return now
    }

    // ---------------------------------------------------------------- volgen

    fun isFollowing(seriesId: String): Boolean = local.isFollowing(seriesId) || online.isSeriesSaved(seriesId)

    fun toggleFollow(series: SchatkamerItem, knownEpisodes: Int): Boolean {
        val following = isFollowing(series.id)
        if (online.isSignedIn && online.isSeriesSaved(series.id) == following) online.toggleSeries(series)
        if (local.isFollowing(series.id) == following) local.toggleFollow(series, knownEpisodes)
        return !following
    }

    // ---------------------------------------------------------------- lijsten

    fun createList(name: String, onlineList: Boolean, first: SchatkamerItem? = null) {
        if (onlineList && online.isSignedIn) online.createList(name, first) else local.createList(name, first)
    }

    fun isInList(list: AnyList, item: SchatkamerItem): Boolean = item.id in list.programIds

    fun toggleInList(list: AnyList, item: SchatkamerItem) {
        val inList = isInList(list, item)
        list.uuid?.let { uuid -> if (inList) online.removeFromList(uuid, item) else online.addToList(uuid, item) }
        list.localId?.let { id -> if (inList) local.removeFromList(id, item) else local.addToList(id, item) }
    }

    fun renameList(list: AnyList, name: String) {
        list.uuid?.let { online.renameList(it, name) }
        list.localId?.let { local.renameList(it, name) }
    }

    fun deleteList(list: AnyList) {
        list.uuid?.let { online.deleteList(it) }
        list.localId?.let { local.deleteList(it) }
    }

    /** De uitzendingen in een lijst; online wordt de actuele inhoud opgehaald. */
    suspend fun listItems(list: AnyList): Result<List<SchatkamerItem>> =
        list.uuid?.let { uuid -> online.listDetail(uuid).map { it.programs } } ?: Result.success(list.items)

    companion object {
        @Volatile
        private var instance: UserLibrary? = null

        fun getInstance(context: Context): UserLibrary =
            instance ?: synchronized(this) {
                instance ?: UserLibrary(LibraryRepository.getInstance(context), OnlineLibrary.getInstance(context)).also { instance = it }
            }
    }
}
