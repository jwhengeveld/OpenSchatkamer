package nl.beeldengeluid.schatkamer.brand

import androidx.annotation.DrawableRes

/**
 * Koppelt omroep-slugs aan transparante vector-drawables in het merkpakket.
 * Hierdoor hebben omroepen nooit meer een witte achtergrondrechthoek in de donkere modus.
 */
object BroadcasterLogos {

    @DrawableRes
    fun getLogoRes(slug: String): Int? = when (slug.trim().lowercase()) {
        "eo" -> R.drawable.ic_broadcaster_eo
        "nos" -> R.drawable.ic_broadcaster_nos
        "avrotros" -> R.drawable.ic_broadcaster_avrotros
        "bnnvara" -> R.drawable.ic_broadcaster_bnnvara
        "vpro" -> R.drawable.ic_broadcaster_vpro
        "kro-ncrv" -> R.drawable.ic_broadcaster_kro_ncrv
        "ntr" -> R.drawable.ic_broadcaster_ntr
        "omroep-max", "max" -> R.drawable.ic_broadcaster_omroep_max
        "human" -> R.drawable.ic_broadcaster_human
        "wnl" -> R.drawable.ic_broadcaster_wnl
        "powned" -> R.drawable.ic_broadcaster_powned
        "npo" -> R.drawable.ic_broadcaster_npo
        else -> null
    }
}
