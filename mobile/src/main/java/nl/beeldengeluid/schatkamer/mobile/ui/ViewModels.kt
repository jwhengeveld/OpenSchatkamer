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
import nl.beeldengeluid.schatkamer.core.data.FavoritesRepository
import nl.beeldengeluid.schatkamer.core.data.GenreItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerCatalogRepository
import nl.beeldengeluid.schatkamer.core.data.SchatkamerHome
import nl.beeldengeluid.schatkamer.core.data.SchatkamerItem
import nl.beeldengeluid.schatkamer.core.data.SchatkamerProgramDetail
import nl.beeldengeluid.schatkamer.core.data.SchatkamerVideoRepository
import nl.beeldengeluid.schatkamer.core.data.SearchResult

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val home: SchatkamerHome) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val catalogRepo: SchatkamerCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome(forceRefresh = false)
    }

    fun loadHome(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Loading
            }
            catalogRepo.getHome(forceRefresh = forceRefresh)
                .onSuccess { home ->
                    _uiState.value = HomeUiState.Success(home)
                }
                .onFailure { error ->
                    val cached = catalogRepo.cachedHome()
                    if (cached != null) {
                        _uiState.value = HomeUiState.Success(cached)
                    } else {
                        _uiState.value = HomeUiState.Error(error.localizedMessage ?: "Fout bij laden van de Schatkamer")
                    }
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

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            performSearch(newQuery)
        }
    }

    fun performSearch(queryToSearch: String) {
        if (queryToSearch.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            catalogRepo.search(queryToSearch)
                .onSuccess { result ->
                    _uiState.value = SearchUiState.Success(result)
                }
                .onFailure { error ->
                    _uiState.value = SearchUiState.Error(error.localizedMessage ?: "Fout bij zoeken")
                }
        }
    }
}

class DiscoverViewModel(
    private val catalogRepo: SchatkamerCatalogRepository
) : ViewModel() {

    val broadcasters: List<BroadcasterItem> = catalogRepo.getBroadcasters()
    val genres: List<GenreItem> = catalogRepo.getGenres()

    private val _selectedItems = MutableStateFlow<List<SchatkamerItem>>(emptyList())
    val selectedItems: StateFlow<List<SchatkamerItem>> = _selectedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun selectBroadcaster(b: BroadcasterItem) {
        viewModelScope.launch {
            _isLoading.value = true
            catalogRepo.getProgramsForBroadcaster(b.id, b.slug)
                .onSuccess { _selectedItems.value = it.items }
                .onFailure { _selectedItems.value = emptyList() }
            _isLoading.value = false
        }
    }

    fun selectGenre(g: GenreItem) {
        viewModelScope.launch {
            _isLoading.value = true
            catalogRepo.getProgramsForGenre(g.slug)
                .onSuccess { _selectedItems.value = it.items }
                .onFailure { _selectedItems.value = emptyList() }
            _isLoading.value = false
        }
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
