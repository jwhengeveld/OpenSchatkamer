package nl.openschatkamer.core.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Updatecontrole voor beide apps. Kijkt bij het openen hoogstens twee keer per
 * dag; "Later" verbergt een versie tot er een nieuwere is.
 */
class UpdateViewModel(
    app: Application,
    private val currentVersion: String,
    private val assetPrefix: String
) : AndroidViewModel(app) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: AppUpdater.Release) : State
        data class Downloading(val release: AppUpdater.Release, val progress: Float) : State
        data class Installing(val release: AppUpdater.Release) : State
        data class Failed(val message: String, val release: AppUpdater.Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    // Eigen client: zonder de cache en cookies van de Schatkamer-client, die horen niet bij GitHub.
    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    init {
        viewModelScope.launch { UpdateEvents.error.collect { e -> if (e != null) { _state.value = State.Failed(e); UpdateEvents.error.value = null } } }
        val last = prefs.getLong(KEY_LAST, 0)
        if (System.currentTimeMillis() - last > 12 * 60 * 60_000L) check(manual = false)
    }

    /** Zoeken naar een update; [manual] toont ook "je hebt de nieuwste versie". */
    fun check(manual: Boolean = true) {
        if (_state.value is State.Downloading || _state.value is State.Checking) return
        if (manual) _state.value = State.Checking
        viewModelScope.launch {
            AppUpdater.latest(client, assetPrefix)
                .onSuccess { r ->
                    prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                    val newer = AppUpdater.isNewer(r.version, currentVersion)
                    val skipped = !manual && prefs.getString(KEY_SKIPPED, null) == r.version
                    _state.value = when {
                        newer && !skipped -> State.Available(r)
                        manual -> State.UpToDate
                        else -> State.Idle
                    }
                }
                .onFailure { e -> _state.value = if (manual) State.Failed(e.message ?: "Controleren lukte niet.") else State.Idle }
        }
    }

    fun install() {
        val release = when (val s = _state.value) {
            is State.Available -> s.release
            is State.Failed -> s.release ?: return
            else -> return
        }
        viewModelScope.launch {
            _state.value = State.Downloading(release, 0f)
            runCatching {
                AppUpdater.download(getApplication(), client, release) { p -> _state.value = State.Downloading(release, p) }
            }.onSuccess { file ->
                _state.value = State.Installing(release)
                runCatching { AppUpdater.install(getApplication(), file) }
                    .onFailure { e -> _state.value = State.Failed(e.message ?: "Installeren lukte niet.", release) }
            }.onFailure { e -> _state.value = State.Failed(e.message ?: "Downloaden lukte niet.", release) }
        }
    }

    /** "Later": deze versie niet meer vanzelf aanbieden. */
    fun later() {
        (_state.value as? State.Available)?.let { prefs.edit().putString(KEY_SKIPPED, it.release.version).apply() }
        _state.value = State.Idle
    }

    fun dismiss() { _state.value = State.Idle }

    private companion object {
        const val KEY_LAST = "last_check"
        const val KEY_SKIPPED = "skipped_version"
    }
}
