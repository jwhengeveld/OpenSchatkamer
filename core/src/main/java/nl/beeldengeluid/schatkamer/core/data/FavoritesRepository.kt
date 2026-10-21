package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokale opslag voor "Mijn Lijst" / favoriete programma's en afleveringen.
 */
class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    val favorites: StateFlow<List<SchatkamerItem>> = _favorites.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return
        val list = mutableListOf<SchatkamerItem>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SchatkamerItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        subTitle = obj.optString("subTitle", ""),
                        description = obj.optString("description", ""),
                        date = obj.optString("date", ""),
                        mediaType = obj.optString("mediaType", "video"),
                        broadcaster = obj.optString("broadcaster", ""),
                        imageUrl = obj.optString("imageUrl", ""),
                        url = obj.optString("url", ""),
                        seriesId = obj.optString("seriesId").takeIf { it.isNotBlank() },
                        seriesSlug = obj.optString("seriesSlug").takeIf { it.isNotBlank() },
                        isPlayable = obj.optBoolean("isPlayable", true)
                    )
                )
            }
        }
        _favorites.value = list
    }

    private fun persist(list: List<SchatkamerItem>) {
        _favorites.value = list
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subTitle", item.subTitle)
                put("description", item.description)
                put("date", item.date)
                put("mediaType", item.mediaType)
                put("broadcaster", item.broadcaster)
                put("imageUrl", item.imageUrl)
                put("url", item.url)
                put("seriesId", item.seriesId.orEmpty())
                put("seriesSlug", item.seriesSlug.orEmpty())
                put("isPlayable", item.isPlayable)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun isFavorite(id: String): Boolean =
        _favorites.value.any { it.id == id }

    fun toggleFavorite(item: SchatkamerItem): Boolean {
        val current = _favorites.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == item.id }
        val added = if (existingIdx != -1) {
            current.removeAt(existingIdx)
            false
        } else {
            current.add(0, item)
            true
        }
        persist(current)
        return added
    }

    fun removeFavorite(id: String) {
        val current = _favorites.value.filterNot { it.id == id }
        persist(current)
    }

    companion object {
        private const val PREFS_NAME = "schatkamer_favorites"
        private const val KEY_ITEMS = "favorite_items"

        @Volatile
        private var instance: FavoritesRepository? = null

        fun getInstance(context: Context): FavoritesRepository =
            instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context).also { instance = it }
            }
    }
}
