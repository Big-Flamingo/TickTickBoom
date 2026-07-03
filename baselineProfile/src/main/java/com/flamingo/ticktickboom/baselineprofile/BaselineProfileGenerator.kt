package com.flamingo.ticktickboom.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.flamingo.ticktickboom",
        includeInStartupProfile = true
    ) {
        startActivityAndWait()

        // ==========================================================
        // PHASE 1: C4 SPARKS & RANDOM MODE EXPLOSION
        // ==========================================================

        // 1. Wait for the Random Tab hit-box to render
        device.wait(Until.hasObject(By.desc("Random Tab")), 5000)

        // 2. Click the exact center of the hit-box
        device.findObject(By.desc("Random Tab"))?.click()
        device.waitForIdle()

        // 3. Tap "DIGITAL" to ensure the C4 Electric Shaders are tested!
        device.findObject(By.textContains("DIGITAL"))?.click()

        // Arm the system
        device.findObject(By.desc("Arm System and Start Timer"))?.click()

        // Wait for the Bomb Screen to load
        device.wait(Until.hasObject(By.desc("Abort")), 2000)

        // Find the voltage panel
        val dangerPanel = device.findObject(By.textContains("VOLTAGE"))
            ?: device.findObject(By.desc("Trigger Shock"))

        // 4. Trigger the C4 Sparks!
        dangerPanel?.click()
        Thread.sleep(300)
        dangerPanel?.click()

        // Wait until the Explosion Screen officially loads
        device.wait(Until.hasObject(By.desc("Restart Button")), 15000)

        // Sleep for 3.5 seconds to let the C4 explosion particle math play out fully
        Thread.sleep(3500)

        // Click Restart to go back to the Setup Screen
        device.findObject(By.desc("Restart Button"))?.click()
        device.wait(Until.hasObject(By.desc("Arm System and Start Timer")), 2000)


        // ==========================================================
        // PHASE 2: FUSE MATH & GROUP MODE VICTORY SCREEN
        // ==========================================================

        // Switch to the Group Mode Tab via hit-box
        device.findObject(By.desc("Group Tab"))?.click()
        device.waitForIdle()

        // 5. Tap "FUSE" to ensure our newly optimized math is tested
        device.findObject(By.textContains("FUSE"))?.click()
        device.waitForIdle()

        // Because of the temporary hack in GroupPresetManager, "Baseline Test" is selected.
        // Arm the system
        device.findObject(By.desc("Arm System and Start Timer"))?.click()

        // Wait until the Explosion / Victory Screen officially loads
        device.wait(Until.hasObject(By.desc("Restart Button")), 15000)

        // Sleep for 3.5 seconds to let the explosion particles AND the Victory Confetti physics play out fully
        Thread.sleep(3500)

        // Click Restart to navigate back to the Setup Screen
        device.findObject(By.desc("Restart Button"))?.click()

        // Wait to ensure we successfully returned to the Setup Screen
        device.wait(Until.hasObject(By.desc("Arm System and Start Timer")), 2000)
    }
}