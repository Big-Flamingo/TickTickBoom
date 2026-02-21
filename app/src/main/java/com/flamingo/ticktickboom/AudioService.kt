package com.flamingo.ticktickboom

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AudioService {
    private var soundPool: SoundPool? = null

    private var vibrator: Vibrator? = null

    // --- SOUND IDs ---
    private var tickSoundId: Int = 0
    private var clockSoundId: Int = 0
    private var explosionSoundId: Int = 0
    private var fuseSoundId: Int = 0
    private var croakSoundId: Int = 0
    private var croakHighSoundId: Int = 0
    private var croakLowSoundId: Int = 0
    private var croakFastSoundId: Int = 0
    private var croakFastHighSoundId: Int = 0
    private var croakFastLowSoundId: Int = 0
    private var bombCroakSoundId: Int = 0
    private var flailSoundId: Int = 0
    private var dingSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var alertSoundId: Int = 0
    private var beepSoundId: Int = 0
    private var fizzleSoundId: Int = 0
    private var flintSoundId: Int = 0
    private var glassSoundId: Int = 0
    private var boingSoundId: Int = 0
    private var zapSoundId: Int = 0 // <-- NEW SOUND ID

    // Hen Sounds
    private var henBokSoundId: Int = 0
    private var henFlySoundId: Int = 0
    private var eggCrackSoundId: Int = 0
    private var henHoldingSoundId: Int = 0
    private var painedCluckSoundId: Int = 0 // NEW
    private var slideSoundId: Int = 0
    private var flapSoundId: Int = 0
    private var whistleSoundId: Int = 0

    // Stream IDs
    private var fuseStreamId: Int = 0
    private var flailStreamId: Int = 0
    private var holdingStreamId: Int = 0
    private var henFlyStreamId: Int = 0
    private var slideStreamId: Int = 0
    private var flapStreamId: Int = 0
    private var whistleStreamId: Int = 0

    var timerVolume: Float = 1.0f
    var explosionVolume: Float = 1.0f

    private var appContext: Context? = null

    fun init(context: Context) {
        if (soundPool != null) return
        appContext = context.applicationContext

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(32)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.let {
            tickSoundId = it.load(appContext, R.raw.tick, 1)
            clockSoundId = it.load(appContext, R.raw.clock, 1)
            explosionSoundId = it.load(appContext, R.raw.explosion, 1)
            fuseSoundId = it.load(appContext, R.raw.fuse, 1)
            croakSoundId = it.load(appContext, R.raw.croak, 1)
            croakHighSoundId = it.load(appContext, R.raw.croak_high, 1)
            croakLowSoundId = it.load(appContext, R.raw.croak_low, 1)
            croakFastSoundId = it.load(appContext, R.raw.croak_fast, 1)
            croakFastHighSoundId = it.load(appContext, R.raw.croak_fast_high, 1)
            croakFastLowSoundId = it.load(appContext, R.raw.croak_fast_low, 1)
            bombCroakSoundId = it.load(appContext, R.raw.bomb_croak, 1)
            flailSoundId = it.load(appContext, R.raw.flail, 1)
            dingSoundId = it.load(appContext, R.raw.ding, 1)
            clickSoundId = it.load(appContext, R.raw.click, 1)
            alertSoundId = it.load(appContext, R.raw.alert, 1)
            beepSoundId = it.load(appContext, R.raw.beep, 1)
            zapSoundId = it.load(appContext, R.raw.zap, 1) // <-- LOAD THE ZAP
            fizzleSoundId = it.load(appContext, R.raw.fizzle, 1)
            flintSoundId = it.load(appContext, R.raw.flint, 1)
            glassSoundId = it.load(appContext, R.raw.glass, 1)
            boingSoundId = it.load(appContext, R.raw.boing, 1)

            henBokSoundId = it.load(appContext, R.raw.hen_bok, 1)
            henFlySoundId = it.load(appContext, R.raw.hen_fly, 1)
            eggCrackSoundId = it.load(appContext, R.raw.egg_crack, 1)
            henHoldingSoundId = it.load(appContext, R.raw.hen_holding, 1)
            // NEW: Load the pained cluck
            painedCluckSoundId = it.load(appContext, R.raw.pained_cluck, 1)
            slideSoundId = it.load(appContext, R.raw.hen_slide, 1)
            flapSoundId = it.load(appContext, R.raw.flap, 1)
            whistleSoundId = it.load(appContext, R.raw.whistle, 1)
        }

        // Grab the vibrator from Android once, and keep it on our desk!
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // --- PLAYER CONTROLS ---

    // --- SLIDE ---
    fun playHenSlide() {
        if (slideStreamId != 0) soundPool?.stop(slideStreamId)
        slideStreamId = soundPool?.play(slideSoundId, timerVolume, timerVolume, 1, 0, 1f) ?: 0
    }

    fun updateSlideVolume(fadeFraction: Float) {
        if (slideStreamId != 0) {
            val vol = timerVolume * fadeFraction
            soundPool?.setVolume(slideStreamId, vol, vol)
        }
    }

    fun stopSlide() {
        if (slideStreamId != 0) {
            soundPool?.stop(slideStreamId)
            slideStreamId = 0
        }
    }

    // --- WING FLAP ---
    fun playWingFlap(startVol: Float? = null) {
        if (flapStreamId != 0) soundPool?.stop(flapStreamId)
        val vol = startVol ?: timerVolume
        // We use priority 2 here so the flaps aren't interrupted by background ticks
        flapStreamId = soundPool?.play(flapSoundId, vol, vol, 2, -1, 1f) ?: 0 // Changed 0 to -1
    }

    fun stopWingFlap() {
        if (flapStreamId != 0) {
            soundPool?.stop(flapStreamId)
            flapStreamId = 0
        }
    }

    fun updateWingFlapVolume(vol: Float) {
        if (flapStreamId != 0) {
            val finalVol = vol * timerVolume
            soundPool?.setVolume(flapStreamId, finalVol, finalVol)
        }
    }

    // --- WHISTLE ---
    fun playWhistle() {
        if (whistleStreamId != 0) soundPool?.stop(whistleStreamId)
        whistleStreamId = soundPool?.play(whistleSoundId, timerVolume, timerVolume, 2, 0, 1f) ?: 0
    }

    fun stopWhistle() {
        if (whistleStreamId != 0) {
            soundPool?.stop(whistleStreamId)
            whistleStreamId = 0
        }
    }

    fun playPainedCluck() {
        val pitch = 0.8f + Math.random().toFloat() * 0.4f
        soundPool?.play(painedCluckSoundId, timerVolume, timerVolume, 1, 0, pitch)
    }

    // --- SOUNDPOOL SOUNDS ---
    fun playTick() { soundPool?.play(tickSoundId, timerVolume, timerVolume, 1, 0, 1f) }
    fun playClockTick() { soundPool?.play(clockSoundId, timerVolume * 0.5f, timerVolume * 0.5f, 1, 0, 1f) }
    fun playClick() { soundPool?.play(clickSoundId, timerVolume, timerVolume, 1, 0, 1f) }
    fun playGlassTap() { soundPool?.play(glassSoundId, timerVolume, timerVolume, 1, 0, 1f) }
    fun playZap() { soundPool?.play(zapSoundId, timerVolume, timerVolume, 1, 0, 1f) } // <-- PLAY THE ZAP
    fun playCrack() { soundPool?.play(eggCrackSoundId, timerVolume, timerVolume, 1, 0, 1.0f) }

    fun playLoudCluck() {
        if (henFlyStreamId != 0) soundPool?.stop(henFlyStreamId)
        // FIX: Replaced 1.0f with timerVolume so it obeys the user's settings!
        henFlyStreamId = soundPool?.play(henFlySoundId, timerVolume, timerVolume, 1, 0, 1.0f) ?: 0
    }

    fun stopLoudCluck() {
        if (henFlyStreamId != 0) {
            soundPool?.stop(henFlyStreamId)
            henFlyStreamId = 0
        }
    }

    fun playCroak(isFast: Boolean = false) {
        // Generate a random number between 0 and 2 (Zero Allocation!)
        val randomPick = kotlin.random.Random.nextInt(3)

        val soundId = if (isFast) {
            when (randomPick) {
                0 -> croakFastHighSoundId
                1 -> croakFastLowSoundId
                else -> croakFastSoundId // The original
            }
        } else {
            when (randomPick) {
                0 -> croakHighSoundId
                1 -> croakLowSoundId
                else -> croakSoundId // The original
            }
        }

        // Play the chosen sound with a mathematically locked 1.0f pitch!
        soundPool?.play(soundId, timerVolume, timerVolume, 2, 0, 1.0f)
    }

    fun playHenCluck() {
        val pitch = 0.9f + Math.random().toFloat() * 0.2f
        soundPool?.play(henBokSoundId, timerVolume, timerVolume, 1, 0, pitch)
    }

    fun playBombCroak() { soundPool?.play(bombCroakSoundId, timerVolume, timerVolume, 1, 0, 1f) }
    fun playAlert() { soundPool?.play(alertSoundId, timerVolume, timerVolume, 1, 0, 1f) }
    fun playDing() { soundPool?.play(dingSoundId, timerVolume, timerVolume, 1, 0, 1f) }

    fun playFlail() {
        if (flailStreamId != 0) soundPool?.stop(flailStreamId)
        flailStreamId = soundPool?.play(flailSoundId, timerVolume, timerVolume, 1, -1, 1f) ?: 0
    }

    fun stopFlail() {
        if (flailStreamId != 0) {
            soundPool?.stop(flailStreamId)
            flailStreamId = 0
        }
    }

    fun startHoldingCluck() {
        if (holdingStreamId != 0) return
        holdingStreamId = soundPool?.play(henHoldingSoundId, timerVolume, timerVolume, 100, -1, 1.0f) ?: 0
    }

    fun stopHoldingCluck() {
        if (holdingStreamId != 0) {
            soundPool?.stop(holdingStreamId)
            holdingStreamId = 0
        }
    }

    fun playPauseInteraction(style: String, isPausing: Boolean) {
        val soundId = when (style) {
            "C4" -> beepSoundId
            "FUSE" -> if (isPausing) fizzleSoundId else flintSoundId
            "DYNAMITE" -> glassSoundId
            "FROG" -> boingSoundId
            "HEN" -> boingSoundId
            else -> clickSoundId
        }
        soundPool?.play(soundId, timerVolume, timerVolume, 1, 0, 1f)
    }

    fun playExplosion() {
        stopAll()
        soundPool?.play(explosionSoundId, explosionVolume, explosionVolume, 2, 0, 1f)

        // Just hit the ON button for the machine we already grabbed!
        vibrator?.let { vib ->
            if (vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 400, 100, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(800)
                }
            }
        }
    }

    fun startFuse(startMuffled: Boolean) {
        if (fuseStreamId != 0) return
        val volume = if (startMuffled) timerVolume * 0.3f else timerVolume
        fuseStreamId = soundPool?.play(fuseSoundId, volume, volume, 1, -1, 1f) ?: 0
    }

    fun dimFuse() {
        if (fuseStreamId != 0) {
            soundPool?.setVolume(fuseStreamId, timerVolume * 0.3f, timerVolume * 0.3f)
        }
    }

    fun stopFuse() {
        if (fuseStreamId != 0) {
            soundPool?.stop(fuseStreamId)
            fuseStreamId = 0
        }
    }

    fun stopAll() {
        stopFuse()
        stopFlail()
        stopHoldingCluck()
        stopSlide()
        stopWingFlap()
        stopWhistle()
        stopLoudCluck()
    }

    fun release() {
        stopAll()
        soundPool?.release()
        soundPool = null
    }
}