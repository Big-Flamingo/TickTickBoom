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

        // --- PHASE 1: C4 SPARKS & RANDOM MODE EXPLOSION ---

        // 1. Wait for the Random Tab hit-box to render
        device.wait(Until.hasObject(By.desc("Random Tab")), 5000)

        // 2. Click the exact center of the hit-box
        device.findObject(By.desc("Random Tab"))?.click()
        device.waitForIdle()

        // Tap "DIGITAL" to select the C4 Style
        device.findObject(By.textContains("DIGITAL"))?.click()

        // Arm the system
        device.findObject(By.desc("Arm System and Start Timer"))?.click()

        // Wait for the Bomb Screen to load
        device.wait(Until.hasObject(By.desc("Abort")), 2000)

        // --- THE FIX: Find the exact panel using your Semantics or Text! ---
        // (Change "VOLTAGE" to whatever one of the words is in your R.string.high_voltage_warning)
        val dangerPanel = device.findObject(By.textContains("VOLTAGE"))
            ?: device.findObject(By.desc("Trigger Shock")) // Fallback to your content description

        // Trigger the C4 Sparks!
        dangerPanel?.click()
        Thread.sleep(300)
        dangerPanel?.click()

        // 1. Wait until the Explosion Screen officially loads
        device.wait(Until.hasObject(By.desc("Restart Button")), 15000)

        // --- THE FIX: Force the profile to capture the heavy particle math! ---
        // Sleep for 3 or 4 seconds to let the explosion animation play out fully.
        Thread.sleep(3500)

        // 2. NOW click the exact center of the hit-box
        device.findObject(By.desc("Restart Button"))?.click()
        device.wait(Until.hasObject(By.desc("Arm System and Start Timer")), 2000)


        // --- PHASE 2: GROUP MODE & VICTORY SCREEN ---

        // Switch to the Group Mode Tab via hit-box
        device.findObject(By.desc("Group Tab"))?.click()
        device.waitForIdle()

        // Because of our hack, "Baseline Test" is automatically selected!
        // Arm the system
        device.findObject(By.desc("Arm System and Start Timer"))?.click()

        // 1. Wait until the Explosion Screen officially loads
        device.wait(Until.hasObject(By.desc("Restart Button")), 15000)

        // --- THE FIX: Force the profile to capture the heavy particle math! ---
        // Sleep for 3 or 4 seconds to let the explosion animation play out fully.
        Thread.sleep(3500)

        // 2. NOW click the exact center of the hit-box
        device.findObject(By.desc("Restart Button"))?.click()

        // Let the Victory Screen confetti render for 2 seconds so the compiler captures it
        Thread.sleep(2000)

        // Done!
    }
}