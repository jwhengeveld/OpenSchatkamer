package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.db.FollowedSeriesEntity
import nl.openschatkamer.core.data.db.LibraryDatabase
import nl.openschatkamer.core.data.db.SavedItemEntity
import nl.openschatkamer.core.data.db.UserListEntity
import nl.openschatkamer.core.data.db.UserListItemEntity
import nl.openschatkamer.core.data.db.WatchEntity
import org.json.JSONArray
import org.json.JSONObject

data class WatchProgress(
    val item: SchatkamerItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val watched: Boolean = false
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Bijna uitgekeken of als gezien gemarkeerd: dan begint de uitzending weer vooraan. */
    val isFinished: Boolean get() = watched || (durationMs > 0 && durationMs - positionMs < FINISHED_MARGIN_MS)

    companion object {
        const val FINISHED_MARGIN_MS = 60_000L
    }
}

data class UserList(val id: Long, val name: String, val items: List<SchatkamerItem>)

data class FollowedSeries(val item: SchatkamerItem, val newEpisodes: Int, val knownEpisodes: Int)

/**
 * Alles wat je bewaart, lokaal op het toestel: Mijn lijst, eigen lijsten,
 * kijkvoortgang met geschiedenis en gevolgde series. Lezen gaat via
 * StateFlows (direct beschikbaar in de UI), schrijven op de achtergrond.
 */
class LibraryRepository(context: Context) {

    private val dao = LibraryDatabase.get(context).dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.applicationContext.getSharedPreferences("library_meta", Context.MODE_PRIVATE)

    init {
        scope.launch { migrateFromPreferences(context.applicationContext) }
    }

    // --------------------------------------------------------------- Mijn lijst

    val favorites: StateFlow<List<SchatkamerItem>> = dao.saved()
        .map { rows -> rows.mapNotNull { decode(it.itemJson) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun isFavorite(id: String): Boolean = favorites.value.any { it.id == id }

    /** Zet [item] op Mijn lijst of haalt het eraf; geeft true als het er nu op staat. */
    fun toggleFavorite(item: SchatkamerItem): Boolean {
        val add = !isFavorite(item.id)
        scope.launch {
            if (add) dao.save(SavedItemEntity(key(item), encode(item), System.currentTimeMillis()))
            else dao.unsave(key(item)).also { dao.unsave(item.id) }
        }
        return add
    }

    fun removeFavorite(id: String) {
        val item = favorites.value.firstOrNull { it.id == id } ?: return
        scope.launch { dao.unsave(key(item)) }
    }

    // ------------------------------------------------------------ eigen lijsten

    val lists: StateFlow<List<UserList>> = combine(dao.lists(), dao.listItems()) { lists, items ->
        val byList = items.groupBy { it.listId }
        lists.map { l -> UserList(l.id, l.name, byList[l.id].orEmpty().mapNotNull { decode(it.itemJson) }) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun createList(name: String, firstItem: SchatkamerItem? = null) = scope.launch {
        val id = dao.createList(UserListEntity(name = name.trim().ifBlank { "Nieuwe lijst" }, createdAt = System.currentTimeMillis()))
        firstItem?.let { dao.addToList(UserListItemEntity(id, key(it), encode(it), System.currentTimeMillis())) }
    }

    fun renameList(id: Long, name: String) = scope.launch { dao.renameList(id, name.trim()) }

    fun deleteList(id: Long) = scope.launch { dao.clearList(id); dao.deleteList(id) }

    fun addToList(listId: Long, item: SchatkamerItem) =
        scope.launch { dao.addToList(UserListItemEntity(listId, key(item), encode(item), System.currentTimeMillis())) }

    fun removeFromList(listId: Long, item: SchatkamerItem) = scope.launch { dao.removeFromList(listId, key(item)) }

    // ------------------------------------------------------ kijken en geschiedenis

    /** Alles wat je bekeken hebt, meest recent eerst. */
    val history: StateFlow<List<WatchProgress>> = dao.watch()
        .map { rows -> rows.mapNotNull { r -> decode(r.itemJson)?.let { WatchProgress(it, r.positionMs, r.durationMs, r.updatedAt, r.watched) } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Wat je nog niet hebt uitgekeken. */
    val continueWatching: StateFlow<List<WatchProgress>> = history
        .map { list -> list.filter { !it.isFinished && it.positionMs > MIN_POSITION_MS }.take(30) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun progressOf(programId: String): WatchProgress? = history.value.firstOrNull { it.item.id == programId }

    fun resumePosition(programId: String): Long = progressOf(programId)?.takeIf { !it.isFinished }?.positionMs ?: 0L

    fun isWatched(programId: String): Boolean = progressOf(programId)?.isFinished == true

    fun saveProgress(item: SchatkamerItem, positionMs: Long, durationMs: Long) {
        if (item.id.isBlank() || durationMs <= 0) return
        scope.launch {
            val watched = durationMs - positionMs < WatchProgress.FINISHED_MARGIN_MS
            dao.upsertWatch(WatchEntity(item.id, encode(item), positionMs.coerceAtLeast(0), durationMs, System.currentTimeMillis(), watched))
        }
    }

    /** Handmatig als (on)gezien markeren. */
    fun setWatched(item: SchatkamerItem, watched: Boolean) = scope.launch {
        val existing = dao.watchEntry(item.id)
        dao.upsertWatch(
            WatchEntity(
                programId = item.id,
                itemJson = existing?.itemJson ?: encode(item),
                positionMs = if (watched) existing?.durationMs ?: 0 else 0,
                durationMs = existing?.durationMs ?: 0,
                updatedAt = System.currentTimeMillis(),
                watched = watched
            )
        )
    }

    fun removeFromHistory(programId: String) = scope.launch { dao.deleteWatch(programId) }

    fun clearHistory() = scope.launch { dao.clearWatch() }

    // ---------------------------------------------------------- gevolgde series

    val followed: StateFlow<List<FollowedSeries>> = dao.followed()
        .map { rows -> rows.mapNotNull { r -> decode(r.itemJson)?.let { FollowedSeries(it, r.newEpisodes, r.knownEpisodes) } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun isFollowing(seriesId: String): Boolean = followed.value.any { it.item.id == seriesId }

    fun toggleFollow(series: SchatkamerItem, knownEpisodes: Int) = scope.launch {
        if (dao.followedNow().any { it.seriesId == series.id }) dao.unfollow(series.id)
        else dao.follow(FollowedSeriesEntity(series.id, encode(series), System.currentTimeMillis(), knownEpisodes, 0, System.currentTimeMillis()))
    }

    /** Afleveringen gezien: de teller "nieuw" van een serie terug naar nul. */
    fun markSeriesSeen(seriesId: String, knownEpisodes: Int) = scope.launch {
        dao.followedNow().firstOrNull { it.seriesId == seriesId }?.let {
            dao.follow(it.copy(knownEpisodes = maxOf(knownEpisodes, it.knownEpisodes), newEpisodes = 0))
        }
    }

    /**
     * Kijkt of gevolgde series nieuwe afleveringen hebben. Alleen als de app
     * open is en hooguit eens per half uur: één klein verzoek per serie.
     */
    suspend fun refreshFollowed(catalog: SchatkamerCatalogRepository) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (series in dao.followedNow().take(MAX_FOLLOWED_CHECKS)) {
            if (now - series.checkedAt < FOLLOW_CHECK_INTERVAL_MS && series.knownEpisodes > 0) continue
            val total = catalog.search(SearchFilter(seriesId = series.seriesId, sort = SortOrder.NEWEST), limit = 1).getOrNull()?.total ?: continue
            val fresh = (total - series.knownEpisodes).coerceAtLeast(0)
            dao.follow(
                series.copy(
                    knownEpisodes = if (series.knownEpisodes == 0) total else series.knownEpisodes,
                    newEpisodes = if (series.knownEpisodes == 0) 0 else fresh,
                    checkedAt = now
                )
            )
        }
    }

    // ------------------------------------------------------- export / import

    /** Alles als één JSON-document, om zelf een back-up te bewaren. */
    fun export(): String = JSONObject()
        .put("format", "openschatkamer-library-1")
        .put("favorites", JSONArray(favorites.value.map { CatalogSnapshotStore.encodeItem(it) }))
        .put("lists", JSONArray(lists.value.map { l ->
            JSONObject().put("name", l.name).put("items", JSONArray(l.items.map { CatalogSnapshotStore.encodeItem(it) }))
        }))
        .put("watch", JSONArray(history.value.map { w ->
            JSONObject().put("item", CatalogSnapshotStore.encodeItem(w.item)).put("position", w.positionMs)
                .put("duration", w.durationMs).put("updatedAt", w.updatedAt).put("watched", w.watched)
        }))
        .put("followed", JSONArray(followed.value.map { f ->
            JSONObject().put("item", CatalogSnapshotStore.encodeItem(f.item)).put("known", f.knownEpisodes)
        }))
        .toString(2)

    /** Voegt een back-up samen met wat er al is; geeft het aantal ingelezen onderdelen. */
    suspend fun import(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optString("format").startsWith("openschatkamer-library")) { "Dit is geen OpenSchatkamer-back-up." }
        var count = 0
        val now = System.currentTimeMillis()
        root.optJSONArray("favorites")?.let { a ->
            for (i in 0 until a.length()) CatalogSnapshotStore.decodeItem(a.getJSONObject(i))?.let {
                dao.save(SavedItemEntity(key(it), encode(it), now - i)); count++
            }
        }
        root.optJSONArray("lists")?.let { a ->
            for (i in 0 until a.length()) {
                val l = a.getJSONObject(i)
                val id = dao.createList(UserListEntity(name = l.optString("name", "Lijst"), createdAt = now + i))
                val items = l.optJSONArray("items") ?: JSONArray()
                for (j in 0 until items.length()) CatalogSnapshotStore.decodeItem(items.getJSONObject(j))?.let {
                    dao.addToList(UserListItemEntity(id, key(it), encode(it), now - j))
                }
                count++
            }
        }
        root.optJSONArray("watch")?.let { a ->
            for (i in 0 until a.length()) {
                val w = a.getJSONObject(i)
                CatalogSnapshotStore.decodeItem(w.getJSONObject("item"))?.let {
                    dao.upsertWatch(WatchEntity(it.id, encode(it), w.optLong("position"), w.optLong("duration"), w.optLong("updatedAt", now), w.optBoolean("watched")))
                    count++
                }
            }
        }
        root.optJSONArray("followed")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.getJSONObject(i)
                CatalogSnapshotStore.decodeItem(f.getJSONObject("item"))?.let {
                    dao.follow(FollowedSeriesEntity(it.id, encode(it), now, f.optInt("known"), 0, 0)); count++
                }
            }
        }
        count
    }

    // --------------------------------------------------------------- intern

    /** Gegevens uit versie 2.0 (SharedPreferences) eenmalig overnemen. */
    private suspend fun migrateFromPreferences(context: Context) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val now = System.currentTimeMillis()
        runCatching {
            val raw = context.getSharedPreferences("schatkamer_favorites", Context.MODE_PRIVATE).getString("favorite_items_v2", null)
            if (raw != null) {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) CatalogSnapshotStore.decodeItem(a.getJSONObject(i))?.let {
                    dao.save(SavedItemEntity(key(it), encode(it), now - i))
                }
            }
        }
        runCatching {
            val raw = context.getSharedPreferences("schatkamer_progress", Context.MODE_PRIVATE).getString("entries", null)
            if (raw != null) {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    CatalogSnapshotStore.decodeItem(o.getJSONObject("item"))?.let {
                        val dur = o.optLong("duration"); val pos = o.optLong("position")
                        dao.upsertWatch(WatchEntity(it.id, encode(it), pos, dur, o.optLong("updatedAt", now), dur - pos < WatchProgress.FINISHED_MARGIN_MS))
                    }
                }
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun key(item: SchatkamerItem) = item.kind.name + ":" + item.id
    private fun encode(item: SchatkamerItem) = CatalogSnapshotStore.encodeItem(item).toString()
    private fun decode(json: String) = runCatching { CatalogSnapshotStore.decodeItem(JSONObject(json)) }.getOrNull()

    companion object {
        private const val KEY_MIGRATED = "migrated_from_prefs_v2"
        private const val MIN_POSITION_MS = 15_000L
        private const val MAX_FOLLOWED_CHECKS = 30
        private const val FOLLOW_CHECK_INTERVAL_MS = 30 * 60_000L

        @Volatile
        private var instance: LibraryRepository? = null

        fun getInstance(context: Context): LibraryRepository =
            instance ?: synchronized(this) {
                instance ?: LibraryRepository(context).also { instance = it }
            }
    }
}
