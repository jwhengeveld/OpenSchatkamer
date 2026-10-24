package nl.openschatkamer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * De route die het profiel vastlegt: opstarten, door Start scrollen, zoeken
 * openen en terug. Draai met ./gradlew :mobile:generateReleaseBaselineProfile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "nl.openschatkamer.mobile", includeInStartupProfile = true) {
        pressHome()
        // startActivityAndWait() kan op nieuwere Android-versies de start niet bevestigen.
        device.executeShellCommand("am start -W -n $packageName/.MainActivity")
        device.wait(Until.hasObject(By.text("Zoeken")), 15_000)
        device.waitForIdle()
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(4) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20); device.waitForIdle() }
        repeat(2) { device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 20); device.waitForIdle() }
        device.findObject(By.text("Zoeken"))?.click()
        device.waitForIdle()
        Thread.sleep(2_000)
        device.findObject(By.text("Ontdekken"))?.click()
        device.waitForIdle()
        Thread.sleep(2_000)
        repeat(2) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20); device.waitForIdle() }
    }
}
