package com.flamingo.ticktickboom

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat

class AudioController(context: Context) {
    private var soundPool: SoundPool? = null

    private var vibrator: Vibrator? = null

    // --- SOUND IDs ---
    private var tickSoundId: Int = 0
    private var clockSoundId: Int = 0
    private var explosionSoundId: Int = 0
    private var fuseSoundId: Int = 0
    private var croakSoundId: Int = 0
    private var croakFastSoundId: Int = 0
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

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(32)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.let {
            tickSoundId = it.load(context, R.raw.tick, 1)
            clockSoundId = it.load(context, R.raw.clock, 1)
            explosionSoundId = it.load(context, R.raw.explosion, 1)
            fuseSoundId = it.load(context, R.raw.fuse, 1)
            croakSoundId = it.load(context, R.raw.croak, 1)
            croakFastSoundId = it.load(context, R.raw.croak_fast, 1)
            bombCroakSoundId = it.load(context, R.raw.bomb_croak, 1)
            flailSoundId = it.load(context, R.raw.flail, 1)
            dingSoundId = it.load(context, R.raw.ding, 1)
            clickSoundId = it.load(context, R.raw.click, 1)
            alertSoundId = it.load(context, R.raw.alert, 1)
            beepSoundId = it.load(context, R.raw.beep, 1)
            zapSoundId = it.load(context, R.raw.zap, 1) // <-- LOAD THE ZAP
            fizzleSoundId = it.load(context, R.raw.fizzle, 1)
            flintSoundId = it.load(context, R.raw.flint, 1)
            glassSoundId = it.load(context, R.raw.glass, 1)
            boingSoundId = it.load(context, R.raw.boing, 1)

            henBokSoundId = it.load(context, R.raw.hen_bok, 1)
            henFlySoundId = it.load(context, R.raw.hen_fly, 1)
            eggCrackSoundId = it.load(context, R.raw.egg_crack, 1)
            henHoldingSoundId = it.load(context, R.raw.hen_holding, 1)
            // NEW: Load the pained cluck
            painedCluckSoundId = it.load(context, R.raw.pained_cluck, 1)
            slideSoundId = it.load(context, R.raw.hen_slide, 1)
            flapSoundId = it.load(context, R.raw.flap, 1)
            whistleSoundId = it.load(context, R.raw.whistle, 1)
        }

        // Grab the vibrator using AndroidX (Automatically handles API 31+ deprecations!)
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
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

    // --- THE NEW, CLEAN CROAK FUNCTION ---
    fun playCroak(isFast: Boolean = false) {
        val soundId = if (isFast) croakFastSoundId else croakSoundId
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

        // Modern Vibration (Guaranteed to be API 26+ by our build.gradle!)
        vibrator?.let { vib ->
            // --- THE FIX: Removed the Build.VERSION check entirely! ---
            if (vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 400, 100, 200), -1))
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