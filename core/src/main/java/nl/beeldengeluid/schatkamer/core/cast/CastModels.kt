package nl.beeldengeluid.schatkamer.core.cast

enum class ProtocolType(val displayName: String) {
    AIRPLAY("Apple AirPlay"),
    SCHATKAMER_TV("De Schatkamer TV"),
    GOOGLE_CAST("Google Cast"),
    MIRACAST("Miracast / Draadloos Scherm")
}

data class CastDevice(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val protocol: ProtocolType,
    val model: String? = null
)
