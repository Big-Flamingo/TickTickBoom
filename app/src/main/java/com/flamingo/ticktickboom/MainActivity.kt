package com.flamingo.ticktickboom

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.view.ViewAnimationUtils
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    // 1. Hold our new controller at the Activity level
    private lateinit var audioController: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        // --- NEW: Custom Splash Screen Exit Animation ---
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val iconView = splashScreenViewProvider.iconView

            // --- ACT 1: MOVEMENT & SCALING ---
            // 1. Get the screen's pixel density
            val density = resources.displayMetrics.density

            // 2. Define your movement in 'dp' instead of raw pixels
            // (Tweak this number until it matches your Compose UI)
            val moveUpInDp = -384f

            // 3. Multiply them to get the perfect pixel count for THIS specific phone!
            val translationYValue = moveUpInDp * density

            val moveUpAnim = ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, 0f, translationYValue)
            val shrinkXAnim = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0.22f)
            val shrinkYAnim = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0.22f)

            val movementSet = AnimatorSet()
            movementSet.duration = 400L
            movementSet.interpolator = AnticipateInterpolator()
            movementSet.playTogether(moveUpAnim, shrinkXAnim, shrinkYAnim)

            // --- ACT 2: CIRCULAR COLLAPSE (Fade Removed!) ---

            // Calculate the screen center
            val centerX = splashScreenView.width / 2

            // Shift the collapse center UP to match the bomb's new location
            val centerY = (splashScreenView.height / 2) + translationYValue.toInt()

            val startRadius = hypot(splashScreenView.width.toDouble(), splashScreenView.height.toDouble()).toFloat()
            val endRadius = 0f

            val circularCollapse = ViewAnimationUtils.createCircularReveal(
                splashScreenView,
                centerX,
                centerY,
                startRadius,
                endRadius
            )

            val collapseSet = AnimatorSet()
            collapseSet.duration = 400L

            // --- THE FIX: We removed fadeIconAnim, so we just play() the collapse by itself! ---
            collapseSet.play(circularCollapse)

            // --- THE MASTER DIRECTOR ---
            // Play Act 1, wait for it to finish, then play Act 2
            val masterSet = AnimatorSet()
            masterSet.playSequentially(movementSet, collapseSet)
            masterSet.doOnEnd { splashScreenViewProvider.remove() }
            masterSet.start()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 2. Initialize it using applicationContext (which never leaks!)
        audioController = AudioController(applicationContext)

        val prefs = getSharedPreferences("bomb_timer_prefs", MODE_PRIVATE)
        audioController.timerVolume = prefs.getFloat("vol_timer", 0.8f)
        audioController.explosionVolume = prefs.getFloat("vol_explode", 1.0f)

        setContent {
            MaterialTheme {
                // --- THE FIX: Modern ViewModel DSL! ---
                // No custom factory class required, completely type-safe, and zero warnings!
                val viewModel: BombViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            BombViewModel(audioController)
                        }
                    }
                )
                BombApp(viewModel, audioController) // <-- PASSED IN
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) audioController.stopAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) audioController.release()
    }
}

@Composable
fun BombApp(viewModel: BombViewModel, audioController: AudioController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

    // 1. COLLECT THE STATE
    val state by viewModel.state.collectAsState()

    // --- NEW: Safely read the latest state without triggering a recomposition! ---
    val currentState by rememberUpdatedState(state)

    // --- NEW: LIFECYCLE INTERRUPT HANDLER ---
    val lifecycleOwner = LocalLifecycleOwner.current

    // Notice we ONLY pass lifecycleOwner now. This means the observer attaches ONCE and stays there!
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                // 1. Pause the ticking bomb
                if (currentState.appState == AppState.RUNNING && !currentState.isPaused) {
                    viewModel.processIntent(GameIntent.TogglePause)
                }
                // 2. Pause the Hen Explosion animation!
                else if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && !currentState.isHenPaused) {
                    viewModel.processIntent(GameIntent.ToggleHenPause)
                }
            }
            else if (event == Lifecycle.Event.ON_RESUME) {
                // 3. Auto-resume the Hen Explosion when returning to the app!
                if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && currentState.isHenPaused) {
                    viewModel.processIntent(GameIntent.ToggleHenPause)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Clean up the observer if the app is destroyed
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val colors = if (isDarkMode) {
        AppColors(Slate950, Slate900, Slate800, Color.White, TextGray, SmokeLight)
    } else {
        AppColors(Slate50, Color.White, Slate200, Slate900, Color.Gray, SmokeDark)
    }

    LaunchedEffect(isDarkMode) {
        val activity = context as ComponentActivity
        val transparent = android.graphics.Color.TRANSPARENT

        // Android 15+ API: Automatically handles the contrast of the clock and battery icons!
        val style = if (isDarkMode) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }

        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    fun toggleTheme() {
        isDarkMode = !isDarkMode
        prefs.edit { putBoolean("dark_mode", isDarkMode) }
        audioController.playClick()
    }

    val configuration = LocalConfiguration.current

    fun toggleLanguage() {
        audioController.playClick()
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

            // --- THE FIX: Removed .systemBarsPadding() so the screens can draw everywhere! ---
            Box(modifier = Modifier.fillMaxSize()) {

                // 3. DRAW THE CORRECT SCREEN BASED ON THE VIEWMODEL'S STATE
                when (state.appState) {
                    AppState.SETUP -> SetupScreen(
                        colors = colors,
                        isDarkMode = isDarkMode,
                        audio = audioController,
                        onToggleTheme = { toggleTheme() },
                        onStart = { settings -> viewModel.processIntent(GameIntent.StartTimer(settings)) },
                        onToggleLanguage = { toggleLanguage() }
                    )

                    AppState.RUNNING -> BombScreen(
                        state = state,
                        colors = colors,
                        isDarkMode = isDarkMode,
                        audio = audioController,
                        onIntent = { intent -> viewModel.processIntent(intent) }
                    )

                    AppState.EXPLODED -> ExplosionScreen(
                        colors = colors,
                        state = state,
                        audio = audioController,
                        onIntent = { intent -> viewModel.processIntent(intent) }
                    )
                }
            }
        }
    }
}