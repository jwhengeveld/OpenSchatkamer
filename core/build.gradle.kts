// Publieke spelerinstellingen van de Schatkamer-website (window.__CONFIG__) en de
// EZDRM-licentieservers. De app leest de actuele waarden tijdens het gebruik uit de
// site; deze waarden zijn de terugval. Overschrijfbaar met -Pschatkamer.<naam>=...
fun prop(name: String, fallback: String): String =
    (project.findProperty("schatkamer.$name") as String?)?.takeIf { it.isNotBlank() } ?: fallback

val buildValues = mapOf(
    "CAST_RECEIVER_ID" to prop("castReceiverId", "377B8439"),
    "THEOPLAYER_CDN_KEY" to prop("theoplayerCdnKey", "fb21074d-a7cd-4fef-9206-e490101d6570"),
    "THEOPLAYER_LICENSE" to prop(
        "theoplayerLicense",
        "sZP7IYe6T6fk0le_IufrTmzz3KCtFSxKTuC-TSIgIOzLTDXgIS3l0LCc0Kh6FOPlUY3zWokgbgjNIOf9flPKIDao0K0oFDCr3oh-3u5tTOz_TSXgFD3g3uB_Iuhi3LRi3ZfVfK4_bQgZCYxNWoryIQXzImf90SRz0uRt3uai0u5i0Oi6Io4pIYP1UQgqWgjeCYxgflEc3LRk0laiTuRr0La_FOPeWok1dDrLYtA1Ioh6TgV6v6fVfKxqWQPrFK3qWmfVfKcqCoXVdQjLUOfVfG3pID49sY3iCYPZWtbpCoj-f6i6UQ1gWZrVdYIgf6i6UQ1gWtAVCYggb6rlWoz6Ymi6IQj-CDgpbkjLWt4ZCoh6TgV6v6fVfKxqWQPrFK3qWmfVfKcqCoXVdQjLUOfVfG3pID49sY3iCYPZWtbpCoj-f6i6UQ1gWZrVdYIgf6i6UQ1gWtAVCYggb6rlWoz6Ymi6box7bZf9DZPEUQkV3mPUFOPLIDreYog-bwPgbt3NWo_6TGxZUDhVfKIgCYxkbK4LflNWYYz"
    ),
    "WIDEVINE_LICENSE_URL" to prop("widevineLicenseUrl", "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145"),
    "PLAYREADY_LICENSE_URL" to prop("playreadyLicenseUrl", "https://playready.ezdrm.com/cency/preauth.aspx?pX=164FB4"),
    "FAIRPLAY_LICENSE_URL" to prop("fairplayLicenseUrl", "https://fps.ezdrm.com/api/licenses/auth?px=C7BD8D"),
    "FAIRPLAY_CERTIFICATE_URL" to prop("fairplayCertificateUrl", "https://player.cdn.beeldengeluid.nl/drm/fairplay.cer")
)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "nl.openschatkamer.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        buildValues.forEach { (key, value) -> buildConfigField("String", key, "\"$value\"") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Gedeelde speleropbouw voor telefoon en tv (HLS, Widevine, clip).
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.exoplayer.hls)

    // Gedeelde ViewModels voor telefoon en tv.
    api(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}
