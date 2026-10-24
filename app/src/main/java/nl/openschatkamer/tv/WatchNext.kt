package nl.openschatkamer.tv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.SchatkamerItem

/**
 * "Verder kijken" op het startscherm van Android TV / Google TV: wat je half
 * hebt gekeken staat daar, en een klik hervat het in de app. Uitgekeken
 * uitzendingen worden weer weggehaald.
 */
object WatchNext {

    fun programUri(id: String): Uri = Uri.parse("openschatkamer://program/$id?play=1")

    @SuppressLint("RestrictedApi")
    suspend fun update(context: Context, item: SchatkamerItem, positionMs: Long, durationMs: Long, finished: Boolean) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || item.id.isBlank()) return@withContext
            runCatching {
                val resolver = context.contentResolver
                val existing = find(context, item.id)
                if (finished || durationMs <= 0) {
                    existing?.let { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
                    return@runCatching
                }
                val program = WatchNextProgram.Builder()
                    .setType(if (item.isAudio) TvContractCompat.WatchNextPrograms.TYPE_TRACK else TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setTitle(item.seriesTitle?.takeIf { it.isNotBlank() } ?: item.title)
                    .setEpisodeTitle(item.title)
                    .setDescription(item.description.take(250))
                    .setPosterArtUri(Uri.parse(item.imageUrl.ifBlank { "android.resource://${context.packageName}/drawable/openschatkamer_tv_banner" }))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                    .setDurationMillis(durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setLastPlaybackPositionMillis(positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setInternalProviderId(item.id)
                    .setIntentUri(programUri(item.id))
                    .build()
                if (existing != null) {
                    resolver.update(TvContractCompat.buildWatchNextProgramUri(existing), program.toContentValues(), null, null)
                } else {
                    resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
                }
            }
        }

    private fun find(context: Context, programId: String): Long? =
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == programId) return c.getLong(0)
            }
            null
        }
}
