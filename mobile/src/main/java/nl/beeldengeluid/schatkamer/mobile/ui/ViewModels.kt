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
            delay(300) // Snelle responsieve debounce
            performSearch(newQuery)
        }
    }

    fun performSearch(queryToSearch: String) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading

            // Controleer of de zoekterm direct matcht met een omroep (zoals "EO", "NOS", "VPRO")
            val broadcasterMatch = catalogRepo.getBroadcasters().firstOrNull {
                it.slug.equals(trimmed, ignoreCase = true) ||
                it.name.equals(trimmed, ignoreCase = true)
            }
            if (broadcasterMatch != null) {
                val broadcasterResult = catalogRepo.getProgramsForBroadcaster(
                    broadcasterMatch.id,
                    broadcasterMatch.slug
                ).getOrNull()

                if (broadcasterResult != null && broadcasterResult.items.isNotEmpty()) {
                    _uiState.value = SearchUiState.Success(broadcasterResult)
                    return@launch
                }
            }

            // Algemene archief zoekopdracht
            catalogRepo.search(trimmed)
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
            // Directe weergave uit cache indien aanwezig
            val cached = catalogRepo.cachedBroadcaster(b.slug)
            if (cached != null && cached.items.isNotEmpty()) {
                _selectedItems.value = cached.items
                _isLoading.value = false
            } else {
                _isLoading.value = true
            }

            catalogRepo.getProgramsForBroadcaster(b.id, b.slug)
                .onSuccess { result ->
                    _selectedItems.value = result.items
                }
                .onFailure {
                    if (_selectedItems.value.isEmpty()) {
                        catalogRepo.search(b.slug).onSuccess { res ->
                            _selectedItems.value = res.items
                        }
                    }
                }
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

