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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.viewmodel.compose.viewModel

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
                // Instantiates our ViewModel and keeps it alive across screen rotations!
                val viewModel: BombViewModel = viewModel()
                BombApp(viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) AudioService.stopAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) AudioService.release()
    }
}

@Composable
fun BombApp(viewModel: BombViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

    // 1. COLLECT THE STATE: The UI will automatically redraw whenever the ViewModel updates this!
    val state by viewModel.state.collectAsState()

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

    val configuration = LocalConfiguration.current

    fun toggleLanguage() {
        val currentLang = java.util.Locale.getDefault().language
        val targetLang = if (currentLang == "en") "zh-TW" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(targetLang))
    }

    // 2. ROUTE BACK PRESSES TO INTENTS
    BackHandler(enabled = state.appState != AppState.SETUP) {
        if (state.appState == AppState.RUNNING) viewModel.processIntent(GameIntent.Abort)
        else if (state.appState == AppState.EXPLODED) viewModel.processIntent(GameIntent.Reset)
    }

    val currentLocale = ConfigurationCompat.getLocales(configuration)[0]
    val isChinese = currentLocale?.language == "zh"
    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, isChinese) {
        androidx.compose.ui.unit.Density(density = currentDensity.density, fontScale = if (isChinese) currentDensity.fontScale * 1.2f else currentDensity.fontScale)
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides customDensity) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {

            // 3. DRAW THE CORRECT SCREEN BASED ON THE VIEWMODEL'S STATE
            when (state.appState) {
                AppState.SETUP -> SetupScreen(
                    colors = colors,
                    isDarkMode = isDarkMode,
                    onToggleTheme = { toggleTheme() },
                    onStart = { settings -> viewModel.processIntent(GameIntent.StartTimer(settings)) }, // Sends Intent!
                    onToggleLanguage = { toggleLanguage() }
                )

                AppState.RUNNING -> BombScreen(
                    state = state,
                    colors = colors,
                    isDarkMode = isDarkMode,
                    onIntent = { intent -> viewModel.processIntent(intent) } // Passes Intents to ViewModel!
                )

                AppState.EXPLODED -> ExplosionScreen(
                    colors = colors,
                    state = state,
                    onIntent = { intent -> viewModel.processIntent(intent) }
                )
            }
        }
    }
}