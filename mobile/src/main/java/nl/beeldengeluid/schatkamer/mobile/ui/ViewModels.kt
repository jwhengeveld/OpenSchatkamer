package nl.beeldengeluid.schatkamer.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.beeldengeluid.schatkamer.core.data.BroadcasterItem
import nl.beeldengeluid.schatkamer.core.data.FacetItem
import nl.beeldengeluid.schatkamer.core.data.FavoritesRepository
import nl.beeldengeluid.schatkamer.core.data.GenreItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerHome
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerProgramDetail
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.core.data.SearchFilter
import nl.beeldengeluid.schatkamer.core.data.SearchResult

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val home: SchatkamerHome) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val catalogRepo: SchatkamerCatalogRepository
) : ViewModel() {

    // Start direct met de gecachte snapshot zodat de startpagina in 0ms getoond wordt
    private val _uiState = MutableStateFlow<HomeUiState>(
        catalogRepo.cachedHome()?.let { HomeUiState.Success(it) } ?: HomeUiState.Loading
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadHome(forceRefresh = false)
    }

    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Loading
            } else {
                _isRefreshing.value = true
            }
            catalogRepo.getHome(forceRefresh = forceRefresh)
                .onSuccess { home ->
                    _uiState.value = HomeUiState.Success(home)
                    _isRefreshing.value = false
                }
                .onFailure { error ->
                    val cached = catalogRepo.cachedHome()
                    if (cached != null) {
                        _uiState.value = HomeUiState.Success(cached)
                    } else {
                        _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Fout bij laden van de Schatkamer")
                    }
                    _isRefreshing.value = false
                }
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val result: SearchResult) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val catalogRepo: SchatkamerCatalogRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _currentFilter = MutableStateFlow(SearchFilter())
    val currentFilter: StateFlow<SearchFilter> = _currentFilter.asStateFlow()

    private val _items = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    val items: StateFlow<List<SchatkamerItem>> = _items.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _availableGenres = MutableStateFlow<List<FacetItem>>(emptyList())
    val availableGenres: StateFlow<List<FacetItem>> = _availableGenres.asStateFlow()

    private val _availableMaterials = MutableStateFlow<List<FacetItem>>(emptyList())
    val availableMaterials: StateFlow<List<FacetItem>> = _availableMaterials.asStateFlow()

    private val _scrollResetTrigger = MutableStateFlow(0L)
    val scrollResetTrigger: StateFlow<Long> = _scrollResetTrigger.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var isPagingRunning = false

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _items.value = emptyList()
            _totalCount.value = 0
            _hasMore.value = false
            _availableGenres.value = emptyList()
            _availableMaterials.value = emptyList()
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _scrollResetTrigger.value = System.currentTimeMillis()
            performSearch(newQuery, _currentFilter.value, offset = 0, append = false)
        }
    }

    fun updateFilter(transform: (SearchFilter) -> SearchFilter) {
        val newFilter = transform(_currentFilter.value)
        _currentFilter.value = newFilter
        _scrollResetTrigger.value = System.currentTimeMillis()
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            performSearch(q, newFilter, offset = 0, append = false)
        }
    }

    fun clearFilters() {
        updateFilter { it.withoutFilters() }
    }

    fun refresh() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        _isRefreshing.value = true
        catalogRepo.invalidateCache()
        performSearch(q, _currentFilter.value, offset = 0, append = false, isRefresh = true)
    }

    fun retry() {
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            performSearch(q, _currentFilter.value, offset = 0, append = false)
        }
    }

    fun loadMore() {
        val q = _query.value.trim()
        if (q.isBlank() || _isLoadingMore.value || !_hasMore.value || isPagingRunning) return
        val offset = _items.value.size
        if (offset == 0 || (_totalCount.value in 1..offset)) {
            _hasMore.value = false
            return
        }
        isPagingRunning = true
        _isLoadingMore.value = true
        performSearch(q, _currentFilter.value, offset = offset, append = true)
    }

    private fun performSearch(
        queryToSearch: String,
        filter: SearchFilter,
        offset: Int,
        append: Boolean,
        isRefresh: Boolean = false
    ) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isBlank()) return
        if (!append) searchJob?.cancel()
        val job = viewModelScope.launch {
            if (!append && !isRefresh) _uiState.value = SearchUiState.Loading

            val updated = filter.copy(query = trimmed)
            catalogRepo.queryCatalog(updated, offset = offset, forceRefresh = isRefresh)
                .onSuccess { result ->
                    if (append) {
                        _items.value = (_items.value + result.items).distinctBy { it.id }
                    } else {
                        _items.value = result.items
                    }
                    _totalCount.value = result.total
                    _hasMore.value = result.hasMore
                    if (result.availableGenres.isNotEmpty()) {
                        _availableGenres.value = result.availableGenres
                    }
                    if (result.availableMaterials.isNotEmpty()) {
                        _availableMaterials.value = result.availableMaterials
                    }
                    _uiState.value = SearchUiState.Success(
                        result.copy(items = _items.value, total = result.total, hasMore = result.hasMore)
                    )
                }
                .onFailure { error ->
                    if (!append) {
                        _uiState.value = SearchUiState.Error(error.localizedMessage ?: "Fout bij zoeken")
                    }
                }
            _isLoadingMore.value = false
            _isRefreshing.value = false
            isPagingRunning = false
        }
        if (!append) searchJob = job
    }
}

class DiscoverViewModel(
    private val catalogRepo: SchatkamerCatalogRepository
) : ViewModel() {

    val broadcasters: List<BroadcasterItem> = catalogRepo.getBroadcasters()
    val genres: List<GenreItem> = catalogRepo.getGenres()

    private val _selectedBroadcaster = MutableStateFlow<BroadcasterItem?>(null)
    val selectedBroadcaster: StateFlow<BroadcasterItem?> = _selectedBroadcaster.asStateFlow()

    private val _selectedGenre = MutableStateFlow<GenreItem?>(null)
    val selectedGenre: StateFlow<GenreItem?> = _selectedGenre.asStateFlow()

    private val _selectedItems = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    val selectedItems: StateFlow<List<SchatkamerItem>> = _selectedItems.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentFilter = MutableStateFlow(SearchFilter())
    val currentFilter: StateFlow<SearchFilter> = _currentFilter.asStateFlow()

    private val _availableGenres = MutableStateFlow<List<FacetItem>>(emptyList())
    val availableGenres: StateFlow<List<FacetItem>> = _availableGenres.asStateFlow()

    private val _availableMaterials = MutableStateFlow<List<FacetItem>>(emptyList())
    val availableMaterials: StateFlow<List<FacetItem>> = _availableMaterials.asStateFlow()

    private val _scrollResetTrigger = MutableStateFlow(0L)
    val scrollResetTrigger: StateFlow<Long> = _scrollResetTrigger.asStateFlow()

    private var loadJob: Job? = null
    private var isPagingRunning = false

    fun selectBroadcaster(b: BroadcasterItem) {
        _selectedBroadcaster.value = b
        _selectedGenre.value = null
        _errorMessage.value = null
        _currentFilter.value = SearchFilter(broadcasterSlug = b.slug)
        _scrollResetTrigger.value = System.currentTimeMillis()

        // Snelle weergave uit snapshot cache
        val cached = catalogRepo.cachedBroadcaster(b.slug)
        if (cached != null && cached.items.isNotEmpty()) {
            _selectedItems.value = cached.items
            _totalCount.value = cached.total
            _hasMore.value = cached.hasMore
            _availableGenres.value = cached.availableGenres
            _availableMaterials.value = cached.availableMaterials
            _isLoading.value = false
        } else {
            _selectedItems.value = emptyList()
            _isLoading.value = true
        }

        fetchBroadcasterPrograms(b, _currentFilter.value, offset = 0, append = false)
    }

    fun selectGenre(g: GenreItem) {
        _selectedGenre.value = g
        _selectedBroadcaster.value = null
        _errorMessage.value = null
        _currentFilter.value = SearchFilter(genre = g.slug)
        _scrollResetTrigger.value = System.currentTimeMillis()
        _isLoading.value = true

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            catalogRepo.queryCatalog(_currentFilter.value, offset = 0)
                .onSuccess { result ->
                    _selectedItems.value = result.items
                    _totalCount.value = result.total
                    _hasMore.value = result.hasMore
                    _availableGenres.value = result.availableGenres
                    _availableMaterials.value = result.availableMaterials
                    _errorMessage.value = null
                }
                .onFailure {
                    // Fallback naar klassieke genre weergave
                    catalogRepo.getProgramsForGenre(g.slug)
                        .onSuccess { res ->
                            _selectedItems.value = res.items
                            _totalCount.value = res.total
                            _hasMore.value = false
                            _errorMessage.value = null
                        }
                        .onFailure { err ->
                            _selectedItems.value = emptyList()
                            _errorMessage.value = err.localizedMessage ?: "Kon genre niet laden"
                        }
                }
            _isLoading.value = false
        }
    }

    fun clearSelection() {
        _selectedBroadcaster.value = null
        _selectedGenre.value = null
        _selectedItems.value = emptyList()
        _totalCount.value = 0
        _hasMore.value = false
        _errorMessage.value = null
        _currentFilter.value = SearchFilter()
    }

    fun updateFilter(transform: (SearchFilter) -> SearchFilter) {
        val newFilter = transform(_currentFilter.value)
        _currentFilter.value = newFilter
        _scrollResetTrigger.value = System.currentTimeMillis()
        val b = _selectedBroadcaster.value
        if (b != null) {
            _isLoading.value = true
            fetchBroadcasterPrograms(b, newFilter, offset = 0, append = false)
        } else if (_selectedGenre.value != null) {
            _isLoading.value = true
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                catalogRepo.queryCatalog(newFilter, offset = 0)
                    .onSuccess { result ->
                        _selectedItems.value = result.items
                        _totalCount.value = result.total
                        _hasMore.value = result.hasMore
                        _errorMessage.value = null
                    }
                    .onFailure { err ->
                        _errorMessage.value = err.localizedMessage ?: "Fout bij filteren"
                    }
                _isLoading.value = false
            }
        }
    }

    fun clearFilters() {
        updateFilter { it.withoutFilters() }
    }

    fun refresh() {
        val b = _selectedBroadcaster.value
        val g = _selectedGenre.value
        if (b != null) {
            _isRefreshing.value = true
            catalogRepo.invalidateCache(b.slug)
            fetchBroadcasterPrograms(b, _currentFilter.value, offset = 0, append = false, isRefresh = true)
        } else if (g != null) {
            _isRefreshing.value = true
            catalogRepo.invalidateCache()
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                catalogRepo.queryCatalog(_currentFilter.value, offset = 0, forceRefresh = true)
                    .onSuccess { result ->
                        _selectedItems.value = result.items
                        _totalCount.value = result.total
                        _hasMore.value = result.hasMore
                        _errorMessage.value = null
                    }
                    .onFailure { err ->
                        _errorMessage.value = err.localizedMessage ?: "Fout bij verversen"
                    }
                _isRefreshing.value = false
            }
        }
    }

    fun retry() {
        val b = _selectedBroadcaster.value
        val g = _selectedGenre.value
        if (b != null) {
            _isLoading.value = true
            _errorMessage.value = null
            fetchBroadcasterPrograms(b, _currentFilter.value, offset = 0, append = false)
        } else if (g != null) {
            selectGenre(g)
        }
    }

    fun loadMore() {
        if (_isLoading.value || _isLoadingMore.value || !_hasMore.value || isPagingRunning) return
        val offset = _selectedItems.value.size
        if (offset == 0 || (_totalCount.value in 1..offset)) {
            _hasMore.value = false
            return
        }
        val b = _selectedBroadcaster.value
        val g = _selectedGenre.value
        isPagingRunning = true
        _isLoadingMore.value = true
        if (b != null) {
            fetchBroadcasterPrograms(b, _currentFilter.value, offset = offset, append = true)
        } else if (g != null) {
            viewModelScope.launch {
                catalogRepo.queryCatalog(_currentFilter.value, offset = offset)
                    .onSuccess { result ->
                        _selectedItems.value = (_selectedItems.value + result.items).distinctBy { it.id }
                        _totalCount.value = result.total
                        _hasMore.value = result.hasMore
                    }
                _isLoadingMore.value = false
                isPagingRunning = false
            }
        } else {
            _isLoadingMore.value = false
            isPagingRunning = false
        }
    }

    private fun fetchBroadcasterPrograms(
        broadcaster: BroadcasterItem,
        filter: SearchFilter,
        offset: Int,
        append: Boolean,
        isRefresh: Boolean = false
    ) {
        if (!append) loadJob?.cancel()
        val job = viewModelScope.launch {
            if (offset == 0 && filter.query.isNotBlank()) {
                delay(300)
            }
            val updated = filter.copy(broadcasterSlug = broadcaster.slug)
            catalogRepo.queryCatalog(updated, offset = offset, forceRefresh = isRefresh)
                .onSuccess { result ->
                    if (append) {
                        _selectedItems.value = (_selectedItems.value + result.items).distinctBy { it.id }
                    } else {
                        _selectedItems.value = result.items
                    }
                    _totalCount.value = result.total
                    _hasMore.value = result.hasMore
                    if (result.availableGenres.isNotEmpty()) {
                        _availableGenres.value = result.availableGenres
                    }
                    if (result.availableMaterials.isNotEmpty()) {
                        _availableMaterials.value = result.availableMaterials
                    }
                    _errorMessage.value = null
                }
                .onFailure { error ->
                    if (!append && _selectedItems.value.isEmpty()) {
                        catalogRepo.search(broadcaster.slug).onSuccess { res ->
                            _selectedItems.value = res.items
                            _totalCount.value = res.total
                            _hasMore.value = false
                            _errorMessage.value = null
                        }.onFailure { err ->
                            _errorMessage.value = err.localizedMessage ?: "Kon programma's niet laden"
                        }
                    }
                }
            _isLoading.value = false
            _isLoadingMore.value = false
            _isRefreshing.value = false
            isPagingRunning = false
        }
        if (!append) loadJob = job
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val detail: SchatkamerProgramDetail) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class ProgramDetailViewModel(
    private val videoRepo: SchatkamerVideoRepository,
    private val favoritesRepo: FavoritesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(urlOrSlug: String, programId: String) {
        val cached = videoRepo.cachedDetail(programId)
        if (cached != null) {
            _uiState.value = DetailUiState.Success(
                cached.copy(isFavorite = favoritesRepo.isFavorite(programId))
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            videoRepo.getProgramDetail(urlOrSlug, programId)
                .onSuccess { detail ->
                    _uiState.value = DetailUiState.Success(
                        detail.copy(isFavorite = favoritesRepo.isFavorite(programId))
                    )
                }
                .onFailure { err ->
                    _uiState.value = DetailUiState.Error(err.localizedMessage ?: "Kon programma niet laden.")
                }
        }
    }

    fun toggleFavorite(item: SchatkamerItem) {
        val isFav = favoritesRepo.toggleFavorite(item)
        _uiState.update { current ->
            if (current is DetailUiState.Success) {
                current.copy(detail = current.detail.copy(isFavorite = isFav))
            } else current
        }
    }
}

class MainNavigationViewModel : ViewModel() {
    var splashFinished = false
    val selectedTab = MutableStateFlow(0)
    private val programStack = mutableListOf<SchatkamerItem>()
    val selectedProgram = MutableStateFlow<SchatkamerItem?>(null)
    val showPlayerScreen = MutableStateFlow(false)

    fun openPlayer() {
        showPlayerScreen.value = true
    }

    fun closePlayer() {
        showPlayerScreen.value = false
    }

    fun selectTab(tab: Int) {
        selectedTab.value = tab
        programStack.clear()
        selectedProgram.value = null
    }

    fun selectProgram(item: SchatkamerItem?) {
        if (item == null) {
            programStack.clear()
            selectedProgram.value = null
        } else {
            if (programStack.lastOrNull()?.id != item.id) {
                programStack.add(item)
            }
            selectedProgram.value = item
        }
    }

    fun goBackProgram(): Boolean {
        if (programStack.size > 1) {
            programStack.removeAt(programStack.lastIndex)
            selectedProgram.value = programStack.last()
            return true
        } else if (programStack.size == 1) {
            programStack.clear()
            selectedProgram.value = null
            return true
        } else if (selectedProgram.value != null) {
            selectedProgram.value = null
            return true
        }
        return false
    }

    /**
     * Handelt een terug-actie af op globaal niveau.
     * Volgorde:
     * 1. Volledig speler-scherm sluiten (video blijft spelen in MiniPlayer)
     * 2. Programmadetails terug navigeren (vorige programma of sluiten)
     * 3. Naar start-tab terugkeren als we op een ander tabblad zitten
     * Geeft true terug als de actie is afgehandeld, false als de app mag sluiten.
     */
    fun handleBack(): Boolean {
        if (showPlayerScreen.value) {
            closePlayer()
            return true
        }
        if (goBackProgram()) {
            return true
        }
        if (selectedTab.value != 0) {
            selectTab(0)
            return true
        }
        return false
    }
}

