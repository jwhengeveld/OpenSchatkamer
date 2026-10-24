package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class WatchProgress(
    val item: SchatkamerItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Bijna uitgekeken telt als gezien: dan begint de uitzending weer vooraan. */
    val isFinished: Boolean get() = durationMs > 0 && durationMs - positionMs < FINISHED_MARGIN_MS

    companion object {
        const val FINISHED_MARGIN_MS = 60_000L
    }
}

/**
 * Onthoudt tot waar je hebt gekeken, voor "Verder kijken" en om een uitzending
 * op dezelfde plek te hervatten (ook na casten). Alleen lokaal op het toestel.
 */
class ProgressRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("schatkamer_progress", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<WatchProgress>> = _entries.asStateFlow()

    /** Wat je nog niet hebt uitgekeken, meest recent eerst. */
    val continueWatching: List<WatchProgress>
        get() = _entries.value.filter { !it.isFinished && it.positionMs > MIN_POSITION_MS }

    fun resumePosition(programId: String): Long =
        _entries.value.firstOrNull { it.item.id == programId }?.takeIf { !it.isFinished }?.positionMs ?: 0L

    fun progressOf(programId: String): WatchProgress? = _entries.value.firstOrNull { it.item.id == programId }

    fun save(item: SchatkamerItem, positionMs: Long, durationMs: Long) {
        if (item.id.isBlank() || durationMs <= 0) return
        val entry = WatchProgress(item, positionMs.coerceAtLeast(0), durationMs, System.currentTimeMillis())
        val updated = (listOf(entry) + _entries.value.filterNot { it.item.id == item.id }).take(MAX_ENTRIES)
        _entries.value = updated
        persist(updated)
    }

    fun remove(programId: String) {
        val updated = _entries.value.filterNot { it.item.id == programId }
        _entries.value = updated
        persist(updated)
    }

    private fun load(): List<WatchProgress> = runCatching {
        val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val item = CatalogSnapshotStore.decodeItem(o.getJSONObject("item")) ?: return@mapNotNull null
            WatchProgress(item, o.optLong("position"), o.optLong("duration"), o.optLong("updatedAt"))
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<WatchProgress>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("item", CatalogSnapshotStore.encodeItem(it.item))
                    .put("position", it.positionMs)
                    .put("duration", it.durationMs)
                    .put("updatedAt", it.updatedAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 60
        private const val MIN_POSITION_MS = 15_000L

        @Volatile
        private var instance: ProgressRepository? = null

        fun getInstance(context: Context): ProgressRepository =
            instance ?: synchronized(this) {
                instance ?: ProgressRepository(context).also { instance = it }
            }
    }
}
