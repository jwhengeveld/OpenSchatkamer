package nl.openschatkamer.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem

/**
 * Een eigen rij op het startscherm van Android TV / Google TV met wat er nu in
 * de app uitgelicht staat (zoals "Deze week, 50 jaar geleden"). Wordt ververst
 * als de app opent; de app haalt niets op de achtergrond op.
 */
object PreviewChannel {

    private const val CHANNEL_ID = "uitgelicht"

    @SuppressLint("RestrictedApi")
    suspend fun publish(context: Context, items: List<SchatkamerItem>) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || items.isEmpty()) return@withContext
        runCatching {
            val helper = PreviewChannelHelper(context)
            val channelId = helper.allChannels.firstOrNull { it.internalProviderId == CHANNEL_ID }?.id ?: run {
                val logo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
                val channel = androidx.tvprovider.media.tv.PreviewChannel.Builder()
                    .setDisplayName(context.getString(R.string.channel_name))
                    .setAppLinkIntentUri(Uri.parse("openschatkamer://home"))
                    .setInternalProviderId(CHANNEL_ID)
                    .apply { if (logo != null) setLogo(logo) }
                    .build()
                helper.publishDefaultChannel(channel)
            }
            // Oude inhoud weg, nieuwe erin.
            context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
            items.distinctBy { it.id }.take(20).forEachIndexed { index, item ->
                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(if (item.isAudio) TvContractCompat.PreviewPrograms.TYPE_TRACK else TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(item.title)
                    .setDescription(listOfNotNull(item.seriesTitle, item.broadcaster.ifBlank { null }, item.year.ifBlank { null }).joinToString(" · "))
                    .setPosterArtUri(Uri.parse(item.imageUrl.ifBlank { "android.resource://${context.packageName}/drawable/openschatkamer_tv_banner" }))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setIntentUri(Uri.parse(if (item.kind == ItemKind.SERIES) "openschatkamer://series/${item.seriesId ?: item.id}" else "openschatkamer://program/${item.id}"))
                    .setInternalProviderId(item.id)
                    .setWeight(1000 - index)
                    .build()
                helper.publishPreviewProgram(program)
            }
        }
    }
}
