package nl.openschatkamer.core.auth

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerItem

/**
 * Je lijsten en series op De Schatkamer zelf, als je bent ingelogd. Wat je
 * hier aanpast, zie je ook op de website (en andersom, na verversen).
 *
 * Favoriete uitzendingen komen in een eigen lijst "Favorieten"; een
 * gevolgde serie is op de website "Mijn series".
 */
class OnlineLibrary(context: Context, val account: SchatkamerAccount) {

    private val api = UserApi(SchatkamerHttpClient.getInstance(context).client, account)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lists = MutableStateFlow<List<OnlineList>>(emptyList())
    val lists: StateFlow<List<OnlineList>> = _lists.asStateFlow()

    private val _savedSeries = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    val savedSeries: StateFlow<List<SchatkamerItem>> = _savedSeries.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            account.account.distinctUntilChangedBy { it?.userId }.collect { acc ->
                if (acc == null) {
                    _lists.value = emptyList(); _savedSeries.value = emptyList()
                } else refresh()
            }
        }
    }

    val isSignedIn: Boolean get() = account.isSignedIn

    fun refresh() = scope.launch {
        if (!account.isSignedIn) return@launch
        _syncing.value = true
        runCatching {
            _lists.value = api.lists()
            _savedSeries.value = api.savedSeries()
            _error.value = null
        }.onFailure { _error.value = it.message }
        _syncing.value = false
    }

    /** De volledige inhoud van een lijst (met uitzendingen). */
    suspend fun listDetail(uuid: String): Result<OnlineList> = runCatching { api.list(uuid) }

    // --------------------------------------------------------------- lijsten

    fun createList(name: String, first: SchatkamerItem? = null) = mutate {
        val uuid = api.createList(name.trim().ifBlank { "Nieuwe lijst" })
        if (first != null && first.kind == ItemKind.PROGRAM) api.addToList(uuid, first.id)
    }

    fun renameList(uuid: String, name: String) = mutate { api.renameList(uuid, name.trim()) }

    fun deleteList(uuid: String) = mutate { api.deleteList(uuid) }

    fun addToList(uuid: String, item: SchatkamerItem) = mutate { api.addToList(uuid, item.id) }

    fun removeFromList(uuid: String, item: SchatkamerItem) = mutate { api.removeFromList(uuid, item.id) }

    fun isInList(uuid: String, programId: String): Boolean =
        _lists.value.firstOrNull { it.uuid == uuid }?.programIds?.contains(programId) == true

    // ------------------------------------------------------------- favorieten

    val favoritesList: OnlineList? get() = _lists.value.firstOrNull { it.name.equals(FAVORITES, ignoreCase = true) }

    fun isFavorite(programId: String): Boolean = favoritesList?.programIds?.contains(programId) == true

    /** Zet een uitzending in of uit de online lijst "Favorieten" (die zo nodig wordt aangemaakt). */
    fun toggleFavorite(item: SchatkamerItem) = mutate {
        val existing = favoritesList
        if (existing != null && item.id in existing.programIds) {
            api.removeFromList(existing.uuid, item.id)
        } else {
            val uuid = existing?.uuid ?: api.createList(FAVORITES)
            api.addToList(uuid, item.id)
        }
    }

    // ---------------------------------------------------------------- series

    fun isSeriesSaved(seriesId: String): Boolean = _savedSeries.value.any { it.id == seriesId }

    fun toggleSeries(series: SchatkamerItem) = mutate {
        if (isSeriesSaved(series.id)) api.unsaveSeries(series.id) else api.saveSeries(series.id)
    }

    private fun mutate(block: suspend () -> Unit) = scope.launch {
        _syncing.value = true
        runCatching { block() }
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message }
        runCatching {
            _lists.value = api.lists()
            _savedSeries.value = api.savedSeries()
        }
        _syncing.value = false
    }

    companion object {
        const val FAVORITES = "Favorieten"

        @Volatile
        private var instance: OnlineLibrary? = null

        fun getInstance(context: Context): OnlineLibrary =
            instance ?: synchronized(this) {
                instance ?: OnlineLibrary(context, SchatkamerAccount.getInstance(context)).also { instance = it }
            }
    }
}
