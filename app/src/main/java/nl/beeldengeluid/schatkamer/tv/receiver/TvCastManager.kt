package nl.beeldengeluid.schatkamer.tv.receiver

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nl.beeldengeluid.schatkamer.core.data.PlayableMedia

sealed interface TvControlCommand {
    data object Play : TvControlCommand
    data object Pause : TvControlCommand
    data class Seek(val positionMs: Long) : TvControlCommand
    data object Stop : TvControlCommand
    data class Volume(val ratio: Float) : TvControlCommand
}

object TvCastManager {
    private val _incomingMedia = MutableSharedFlow<PlayableMedia>(extraBufferCapacity = 1)
    val incomingMedia: SharedFlow<PlayableMedia> = _incomingMedia.asSharedFlow()

    private val _controlCommands = MutableSharedFlow<TvControlCommand>(extraBufferCapacity = 5)
    val controlCommands: SharedFlow<TvControlCommand> = _controlCommands.asSharedFlow()

    @Volatile
    var currentPlayer: Player? = null

    fun play(media: PlayableMedia) {
        _incomingMedia.tryEmit(media)
    }

    fun sendCommand(command: TvControlCommand) {
        _controlCommands.tryEmit(command)
    }
}
