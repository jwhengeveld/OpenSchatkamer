package nl.openschatkamer.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** "Mijn lijst": bewaarde series en uitzendingen, lokaal op het toestel. */
class FavoritesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<SchatkamerItem>> = _favorites.asStateFlow()

    private fun load(): List<SchatkamerItem> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ITEMS, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { CatalogSnapshotStore.decodeItem(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun persist(list: List<SchatkamerItem>) {
        _favorites.value = list
        val array = JSONArray().apply { list.forEach { put(CatalogSnapshotStore.encodeItem(it)) } }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    /** Zet [item] op de lijst of haalt het eraf; geeft true als het nu op de lijst staat. */
    fun toggleFavorite(item: SchatkamerItem): Boolean {
        val current = _favorites.value
        return if (current.any { it.id == item.id }) {
            persist(current.filterNot { it.id == item.id })
            false
        } else {
            persist(listOf(item) + current)
            true
        }
    }

    fun removeFavorite(id: String) = persist(_favorites.value.filterNot { it.id == id })

    companion object {
        private const val PREFS_NAME = "schatkamer_favorites"
        private const val KEY_ITEMS = "favorite_items_v2"

        @Volatile
        private var instance: FavoritesRepository? = null

        fun getInstance(context: Context): FavoritesRepository =
            instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context).also { instance = it }
            }
    }
}
