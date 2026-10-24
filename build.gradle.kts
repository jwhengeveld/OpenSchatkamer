plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// On macOS external volumes (exFAT/FAT32), reroute build directory to local APFS user home to prevent AppleDouble resource corruption
if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
    val localBuildBase = System.getenv("GRADLE_BUILD_DIR")
        ?: (System.getProperty("user.home") + "/.builds/Schatkamer")
    allprojects {
        layout.buildDirectory.set(file("$localBuildBase/${project.name}"))
    }
}
