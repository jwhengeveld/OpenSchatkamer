package nl.openschatkamer.core.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschatkamer.core.data.Broadcaster
import nl.openschatkamer.core.data.Decade
import nl.openschatkamer.core.data.FacetItem
import nl.openschatkamer.core.data.SchatkamerCatalogRepository
import nl.openschatkamer.core.data.SchatkamerHome
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerProgramDetail
import nl.openschatkamer.core.data.SchatkamerSeries
import nl.openschatkamer.core.data.SchatkamerStory
import nl.openschatkamer.core.data.SchatkamerVideoRepository
import nl.openschatkamer.core.data.SearchFacets
import nl.openschatkamer.core.data.SearchFilter
import nl.openschatkamer.core.data.SortOrder
import org.json.JSONArray
import java.io.IOException

/** Laadtoestand van een scherm. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun friendlyError(error: Throwable): String = when (error) {
    is IOException -> "Geen verbinding met De Schatkamer. Controleer je internet en probeer het opnieuw."
    else -> error.message ?: "Er ging iets mis."
}

/** Korte geheugensteun voor kaarten, zodat een detailscherm meteen titel en beeld toont. */
object ItemMemo {
    private val items = java.util.concurrent.ConcurrentHashMap<String, SchatkamerItem>()
    fun remember(item: SchatkamerItem) { items[item.kind.name + item.id] = item }
    fun get(kind: nl.openschatkamer.core.data.ItemKind, id: String): SchatkamerItem? = items[kind.name + id]
}

// ------------------------------------------------------------------ start

class HomeViewModel(private val catalog: SchatkamerCatalogRepository) : ViewModel() {

    private val _home = MutableStateFlow<Load<SchatkamerHome>>(
        catalog.cachedHome()?.let { Load.Ready(it) } ?: Load.Loading
    )
    val home: StateFlow<Load<SchatkamerHome>> = _home.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _refreshing.value = force
            catalog.getHome(forceRefresh = force)
                .onSuccess { _home.value = Load.Ready(it) }
                .onFailure { e -> if (_home.value !is Load.Ready) _home.value = Load.Failed(friendlyError(e)) }
            _refreshing.value = false
        }
    }
}

// ------------------------------------------------------- gepagineerde lijsten

data class QueryState(
    val filter: SearchFilter = SearchFilter(),
    val items: List<SchatkamerItem> = emptyList(),
    val series: List<SchatkamerItem> = emptyList(),
    val total: Int = 0,
    val facets: SearchFacets = SearchFacets(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    /** Pas waar na het eerste antwoord: zo tonen we geen "niets gevonden" tijdens het laden. */
    val loaded: Boolean = false
)

/**
 * Eén zoekopdracht met oneindig scrollen. Gebruikt voor zoeken, omroepen,
 * genres, personen, decennia en de afleveringen van een serie: het zijn
 * allemaal filters op dezelfde zoekdienst.
 */
open class PagedQueryViewModel(
    protected val catalog: SchatkamerCatalogRepository,
    initial: SearchFilter? = null
) : ViewModel() {

    protected val _state = MutableStateFlow(QueryState(filter = initial ?: SearchFilter()))
    val state: StateFlow<QueryState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        if (initial != null) setFilter(initial, force = true)
    }

    fun setFilter(filter: SearchFilter, force: Boolean = false) {
        if (!force && filter == _state.value.filter && _state.value.loaded) return
        job?.cancel()
        _state.value = QueryState(filter = filter, isLoading = true)
        if (!isRunnable(filter)) {
            _state.value = QueryState(filter = filter)
            return
        }
        job = viewModelScope.launch {
            catalog.search(filter, offset = 0)
                .onSuccess { r ->
                    _state.value = QueryState(
                        filter = filter, items = r.items, series = r.series, total = r.total, facets = r.facets,
                        endReached = !r.hasMore, loaded = true
                    )
                }
                .onFailure { e -> _state.value = QueryState(filter = filter, error = friendlyError(e), loaded = true) }
        }
    }

    protected open fun isRunnable(filter: SearchFilter): Boolean = true

    fun update(transform: (SearchFilter) -> SearchFilter) = setFilter(transform(_state.value.filter))

    fun retry() = setFilter(_state.value.filter, force = true)

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || s.endReached || s.error != null || !s.loaded) return
        _state.update { it.copy(isLoadingMore = true) }
        job = viewModelScope.launch {
            catalog.search(s.filter, offset = s.items.size)
                .onSuccess { r ->
                    _state.update {
                        val merged = (it.items + r.items).distinctBy { item -> item.id }
                        it.copy(items = merged, isLoadingMore = false, endReached = !r.hasMore || r.items.isEmpty())
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingMore = false) } }
        }
    }
}

class BrowseViewModel(catalog: SchatkamerCatalogRepository, filter: SearchFilter) : PagedQueryViewModel(catalog, filter)

/**
 * Zoeken terwijl je typt: na een korte pauze, met suggesties en geschiedenis.
 * Typefouten vangt de zoekdienst zelf op ("jiskfet" vindt Jiskefet).
 */
@OptIn(FlowPreview::class)
class SearchViewModel(catalog: SchatkamerCatalogRepository, context: Context) : PagedQueryViewModel(catalog) {

    private val prefs = context.applicationContext.getSharedPreferences("search", Context.MODE_PRIVATE)

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            _text.debounce(380).distinctUntilChanged().collect { q ->
                update { it.copy(query = q.trim()) }
            }
        }
        viewModelScope.launch {
            _text.debounce(180).distinctUntilChanged().collect { q ->
                _suggestions.value = if (q.trim().length >= 2) catalog.suggestions(q) else emptyList()
            }
        }
    }

    override fun isRunnable(filter: SearchFilter): Boolean = filter.query.isNotBlank()

    fun onTextChange(value: String) {
        _text.value = value
    }

    /** Direct zoeken (toets "zoeken", een suggestie of een geschiedenisregel). */
    fun submit(value: String = _text.value) {
        val q = value.trim()
        _text.value = q
        _suggestions.value = emptyList()
        if (q.isBlank()) return
        update { it.copy(query = q) }
        remember(q)
    }

    fun clear() {
        _text.value = ""
        _suggestions.value = emptyList()
        setFilter(SearchFilter())
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun remember(q: String) {
        val list = (listOf(q) + _history.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        _history.value = list
        prefs.edit().putString(KEY, JSONArray(list).toString()).apply()
    }

    private fun loadHistory(): List<String> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "history"
    }
}

// ------------------------------------------------------------------ serie

class SeriesViewModel(catalog: SchatkamerCatalogRepository, private val seriesId: String) :
    PagedQueryViewModel(catalog, SearchFilter(seriesId = seriesId, sort = SortOrder.OLDEST)) {

    private val _series = MutableStateFlow<Load<SchatkamerSeries>>(Load.Loading)
    val series: StateFlow<Load<SchatkamerSeries>> = _series.asStateFlow()

    init {
        loadSeries()
    }

    fun loadSeries() {
        viewModelScope.launch {
            catalog.series(seriesId)
                .onSuccess { _series.value = Load.Ready(it) }
                .onFailure { _series.value = Load.Failed(friendlyError(it)) }
        }
    }

    fun toggleSort() = update { it.copy(sort = if (it.sort == SortOrder.OLDEST) SortOrder.NEWEST else SortOrder.OLDEST) }
}

// --------------------------------------------------------------- uitzending

class ProgramViewModel(
    private val video: SchatkamerVideoRepository,
    private val catalog: SchatkamerCatalogRepository,
    private val programId: String
) : ViewModel() {

    private val _detail = MutableStateFlow<Load<SchatkamerProgramDetail>>(
        video.cachedDetail(programId)?.let { Load.Ready(it) } ?: Load.Loading
    )
    val detail: StateFlow<Load<SchatkamerProgramDetail>> = _detail.asStateFlow()

    private val _more = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    /** Andere uitzendingen uit dezelfde serie, rond deze aflevering. */
    val more: StateFlow<List<SchatkamerItem>> = _more.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            video.getProgramDetail(programId)
                .onSuccess { d ->
                    _detail.value = Load.Ready(d)
                    d.seriesId?.let { sid ->
                        catalog.episodes(sid, sort = SortOrder.OLDEST).onSuccess { r ->
                            val idx = r.items.indexOfFirst { it.id == programId }
                            val window = if (idx >= 0) r.items.drop((idx - 3).coerceAtLeast(0)) else r.items
                            _more.value = window.filterNot { it.id == programId }.take(12)
                        }
                    }
                }
                .onFailure { e -> if (_detail.value !is Load.Ready) _detail.value = Load.Failed(friendlyError(e)) }
        }
    }
}

// ----------------------------------------------------------------- verhaal

class StoryViewModel(private val catalog: SchatkamerCatalogRepository, private val slug: String) : ViewModel() {
    private val _story = MutableStateFlow<Load<SchatkamerStory>>(Load.Loading)
    val story: StateFlow<Load<SchatkamerStory>> = _story.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _story.value = Load.Loading
            catalog.story(slug)
                .onSuccess { _story.value = Load.Ready(it) }
                .onFailure { _story.value = Load.Failed(friendlyError(it)) }
        }
    }
}

// --------------------------------------------------------------- ontdekken

data class DiscoverData(
    val broadcasters: List<Broadcaster> = SchatkamerCatalogRepository.CURRENT_BROADCASTERS,
    val genres: List<FacetItem> = emptyList(),
    val decades: List<Decade> = Decade.entries.toList()
)

class DiscoverViewModel(private val catalog: SchatkamerCatalogRepository) : ViewModel() {
    private val _data = MutableStateFlow(DiscoverData())
    val data: StateFlow<DiscoverData> = _data.asStateFlow()

    init {
        viewModelScope.launch {
            var attempt = 0
            while (attempt++ < 3) {
                val broadcasters = catalog.broadcasters()
                val genres = catalog.genres()
                _data.value = DiscoverData(broadcasters = broadcasters, genres = genres)
                if (genres.isNotEmpty()) break
                delay(3_000L * attempt)
            }
        }
    }
}
