package nl.openschatkamer.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun `versies vergelijken`() {
        assertTrue(AppUpdater.isNewer("2.3.1", "2.3.0"))
        assertTrue(AppUpdater.isNewer("v2.3.10", "2.3.9"))
        assertTrue(AppUpdater.isNewer("3.0", "2.9.9"))
        assertFalse(AppUpdater.isNewer("2.3.0", "2.3.0"))
        assertFalse(AppUpdater.isNewer("2.2.9", "2.3.0"))
    }

    @Test
    fun `juiste APK uit de release`() {
        val json = """{"tag_name":"v2.4.0","body":"Nieuw","html_url":"https://x/r","assets":[
            {"name":"openschatkamer-mobile-v2.4.0.apk","browser_download_url":"https://x/m.apk","size":10},
            {"name":"openschatkamer-tv-v2.4.0.apk","browser_download_url":"https://x/t.apk","size":20}]}"""
        val tv = AppUpdater.parse(json, "openschatkamer-tv-")!!
        assertEquals("2.4.0", tv.version)
        assertEquals("https://x/t.apk", tv.apkUrl)
        assertEquals(20L, tv.size)
        assertNull(AppUpdater.parse(json, "openschatkamer-watch-"))
    }
}
