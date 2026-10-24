package nl.openschatkamer.core.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.SchatkamerHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Miniaturen bij het spoelen, uit het storyboard dat de website ook gebruikt:
 * een WebVTT-bestand dat per tijdvak een uitsnede (#xywh) van een sprite aanwijst.
 */
class Storyboard private constructor(val tiles: List<Tile>) {

    data class Tile(val startMs: Long, val endMs: Long, val url: String, val x: Int, val y: Int, val w: Int, val h: Int)

    /** De tegel bij een positie op de archiefband (dus inclusief de clipstart). */
    fun tileAt(positionMs: Long): Tile? {
        if (tiles.isEmpty()) return null
        var lo = 0
        var hi = tiles.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val t = tiles[mid]
            when {
                positionMs < t.startMs -> hi = mid - 1
                positionMs >= t.endMs -> lo = mid + 1
                else -> return t
            }
        }
        return tiles.getOrNull(lo.coerceAtMost(tiles.lastIndex))
    }

    companion object {
        private val TIME = Regex("""(\d+):(\d{2}):(\d{2})[.,](\d{3})""")

        fun parse(vtt: String, baseUrl: String = ""): Storyboard {
            val tiles = mutableListOf<Tile>()
            val lines = vtt.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if ("-->" in line) {
                    val (start, end) = line.split("-->").map { parseTime(it.trim()) }
                    val target = lines.getOrNull(i + 1)?.trim().orEmpty()
                    val url = target.substringBefore("#xywh=")
                    val box = target.substringAfter("#xywh=", "").split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (start != null && end != null && url.isNotBlank() && box.size == 4) {
                        val absolute = if (url.startsWith("http")) url else baseUrl.substringBeforeLast('/') + "/" + url
                        tiles += Tile(start, end, absolute, box[0], box[1], box[2], box[3])
                    }
                    i += 2
                } else i++
            }
            return Storyboard(tiles.sortedBy { it.startMs })
        }

        private fun parseTime(text: String): Long? {
            val m = TIME.find(text) ?: return null
            val (h, min, s, ms) = m.destructured
            return ((h.toLong() * 60 + min.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
        }
    }
}

/** Laadt storyboards en hun sprites één keer en houdt ze in het geheugen. */
object StoryboardLoader {

    private val boards = ConcurrentHashMap<String, Storyboard>()
    private val sprites = ConcurrentHashMap<String, Bitmap>()

    suspend fun load(http: SchatkamerHttpClient, url: String): Storyboard? = boards[url] ?: withContext(Dispatchers.IO) {
        runCatching { Storyboard.parse(http.getHtml(url, memoMs = 60 * 60_000L), url) }.getOrNull()
            ?.takeIf { it.tiles.isNotEmpty() }
            ?.also { boards[url] = it }
    }

    /** De uitsnede voor één tegel. */
    suspend fun bitmap(http: SchatkamerHttpClient, tile: Storyboard.Tile): Bitmap? = withContext(Dispatchers.IO) {
        val sprite = sprites[tile.url] ?: runCatching {
            http.client.newCall(Request.Builder().url(tile.url).build()).execute().use { r ->
                r.body?.byteStream()?.let(BitmapFactory::decodeStream)
            }
        }.getOrNull()?.also { if (sprites.size > 6) sprites.clear(); sprites[tile.url] = it } ?: return@withContext null
        runCatching {
            Bitmap.createBitmap(
                sprite, tile.x.coerceAtMost(sprite.width - 1), tile.y.coerceAtMost(sprite.height - 1),
                tile.w.coerceAtMost(sprite.width - tile.x), tile.h.coerceAtMost(sprite.height - tile.y)
            )
        }.getOrNull()
    }
}
