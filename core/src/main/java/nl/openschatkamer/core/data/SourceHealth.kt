package nl.openschatkamer.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Houdt per bron bij of de website nog levert wat de app verwacht. Als de
 * site verandert, vangen de terugvalroutes dat op; dit register maakt
 * zichtbaar (diagnosescherm) dat er een terugval nodig was, zodat het niet
 * ongemerkt blijft.
 */
object SourceHealth {

    enum class Source(val label: String) {
        HOME("Startpagina"),
        SEARCH("Zoeken"),
        SERIES("Series"),
        PROGRAM("Uitzendingen"),
        STREAM("Streams"),
        STORY("Verhalen"),
        CONFIG("Spelerinstellingen")
    }

    enum class Outcome { OK, FALLBACK, FAILED }

    data class Status(
        val outcome: Outcome,
        val detail: String,
        val at: Long = System.currentTimeMillis()
    )

    private val _status = MutableStateFlow<Map<Source, Status>>(emptyMap())
    val status: StateFlow<Map<Source, Status>> = _status.asStateFlow()

    fun ok(source: Source, detail: String = "primaire bron") = record(source, Status(Outcome.OK, detail))
    fun fallback(source: Source, detail: String) = record(source, Status(Outcome.FALLBACK, detail))
    fun failed(source: Source, error: Throwable) =
        record(source, Status(Outcome.FAILED, error.message ?: error::class.java.simpleName))

    private fun record(source: Source, status: Status) = _status.update { it + (source to status) }
}
