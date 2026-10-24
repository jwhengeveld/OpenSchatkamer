package nl.openschatkamer.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * De route die het profiel vastlegt, met de afstandsbediening: opstarten, door
 * de rijen van Start, de zijbalk in en naar Zoeken en Ontdekken.
 * Draai met ./gradlew :app:generateReleaseBaselineProfile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "nl.openschatkamer.tv", includeInStartupProfile = true) {
        pressHome()
        // startActivityAndWait() kan op nieuwere Android-versies de start niet bevestigen.
        device.executeShellCommand("am start -W -n $packageName/.MainActivity")
        Thread.sleep(6_000)
        fun key(code: Int, times: Int = 1) = repeat(times) { device.pressKeyCode(code); Thread.sleep(300) }
        key(KeyEvent.KEYCODE_DPAD_DOWN, 4)
        key(KeyEvent.KEYCODE_DPAD_RIGHT, 5)
        key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        key(KeyEvent.KEYCODE_DPAD_LEFT, 8)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(2_000)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        Thread.sleep(2_000)
        key(KeyEvent.KEYCODE_DPAD_DOWN, 4)
    }
}
