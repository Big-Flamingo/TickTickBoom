package com.flamingo.ticktickboom.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.flamingo.ticktickboom",
            profileBlock = {
                // 1. Start the app
                startActivityAndWait()

                // 2. Find the "ARM SYSTEM" button and click it
                // This forces Compose to render the complex Canvas bomb screens!
                val armButton = device.findObject(By.text("ARM SYSTEM"))
                if (armButton != null) {
                    armButton.click()
                    // Replaced waitForIdle() with a hard 5-second wait
                    // to guarantee we record the entire explosion!
                    Thread.sleep(5000)
                }
            }
        )
    }
}