package com.flamingo.ticktickboom

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AudioService.init(this)

        val prefs = getSharedPreferences("bomb_timer_prefs", MODE_PRIVATE)
        AudioService.timerVolume = prefs.getFloat("vol_timer", 0.8f)
        AudioService.explosionVolume = prefs.getFloat("vol_explode", 1.0f)

        setContent {
            MaterialTheme {
                BombApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            AudioService.stopAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only release resources if the app is actually closing (not rotating)
        if (!isChangingConfigurations) {
            AudioService.release()
        }
    }
}

@Composable
fun BombApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

    val colors = if (isDarkMode) {
        AppColors(Slate950, Slate900, Slate800, Color.White, TextGray, SmokeLight)
    } else {
        AppColors(Slate50, Color.White, Slate200, Slate900, Color.Gray, SmokeDark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
        }
    }

    fun toggleTheme() {
        isDarkMode = !isDarkMode
        prefs.edit { putBoolean("dark_mode", isDarkMode) }
        AudioService.playClick()
    }

    // --- NEW: Helper to toggle language ---
    fun toggleLanguage() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLang = currentLocales.get(0)?.language ?: "en"

        // If current is English, switch to Chinese (Traditional), otherwise switch to English
        val newLocale = if (currentLang == "en") {
            LocaleListCompat.forLanguageTags("zh-TW")
        } else {
            LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(newLocale)
    }

    var appState by rememberSaveable { mutableStateOf(AppState.SETUP) }
    var duration by rememberSaveable { mutableIntStateOf(0) }
    var bombStyle by rememberSaveable { mutableStateOf("C4") }
    var startTime by rememberSaveable { mutableLongStateOf(0L) }

    var isPaused by rememberSaveable { mutableStateOf(false) }
    var totalPausedTime by rememberSaveable { mutableLongStateOf(0L) }
    var currentPauseStart by rememberSaveable { mutableLongStateOf(0L) }

    var explosionOrigin by remember { mutableStateOf(Offset.Zero) }

    fun handleStart(settings: TimerSettings) {
        duration = Random.nextInt(settings.minSeconds, settings.maxSeconds + 1)
        bombStyle = settings.style
        startTime = System.currentTimeMillis()
        isPaused = false
        totalPausedTime = 0L
        currentPauseStart = 0L
        appState = AppState.RUNNING
    }

    fun handleExplode() {
        AudioService.stopAll()
        appState = AppState.EXPLODED
    }

    fun handleReset() {
        AudioService.stopAll()
        appState = AppState.SETUP
    }

    fun handleAbort() {
        AudioService.stopAll()
        appState = AppState.SETUP
    }

    fun handleTogglePause() {
        if (appState != AppState.RUNNING) return
        isPaused = !isPaused
        AudioService.playPauseInteraction(bombStyle, isPaused)
        if (isPaused) {
            currentPauseStart = System.currentTimeMillis()
            if (bombStyle != "FROG") {
                AudioService.stopAll()
            }
        } else {
            if (currentPauseStart > 0) {
                totalPausedTime += (System.currentTimeMillis() - currentPauseStart)
            }
        }
    }

    BackHandler(enabled = appState != AppState.SETUP) {
        if (appState == AppState.RUNNING) handleAbort()
        else if (appState == AppState.EXPLODED) handleReset()
    }

    // --- 1. NEW: DETECT LANGUAGE AND BOOST FONT SCALE ---
    val configuration = LocalConfiguration.current
    val currentLocale = ConfigurationCompat.getLocales(configuration)[0]
    val isChinese = currentLocale?.language == "zh"

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, isChinese) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            // Boost Chinese fonts by 20% (1.2f). Leave English at 1.0f.
            fontScale = if (isChinese) currentDensity.fontScale * 1.2f else currentDensity.fontScale
        )
    }

    // --- 2. NEW: WRAP THE SURFACE IN THE CUSTOM DENSITY ---
    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides customDensity) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            when (appState) {
                AppState.SETUP -> SetupScreen(
                    colors,
                    isDarkMode,
                    { toggleTheme() },
                    { handleStart(it) },
                    { toggleLanguage() })

                AppState.RUNNING -> BombScreen(
                    duration = duration,
                    startTime = startTime,
                    style = bombStyle,
                    colors = colors,
                    isDarkMode = isDarkMode,
                    isPaused = isPaused,
                    totalPausedTime = totalPausedTime,
                    currentPauseStart = currentPauseStart,
                    onExplode = { handleExplode() },
                    onAbort = { handleAbort() },
                    onTogglePause = { handleTogglePause() },
                    onUpdateExplosionOrigin = { explosionOrigin = it }
                )

                AppState.EXPLODED -> ExplosionScreen(
                    colors,
                    bombStyle,
                    explosionOrigin
                ) { handleReset() }
            }
        }
    }
}