package com.flamingo.ticktickboom

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AudioService {
    private var soundPool: SoundPool? = null

    // Persistent MediaPlayers
    private var slidePlayer: MediaPlayer? = null
    private var wingFlapPlayer: MediaPlayer? = null
    private var whistlePlayer: MediaPlayer? = null

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

    // Hen Sounds
    private var henBokSoundId: Int = 0
    private var henFlySoundId: Int = 0
    private var eggCrackSoundId: Int = 0
    private var henHoldingSoundId: Int = 0
    private var painedCluckSoundId: Int = 0 // NEW

    // Stream IDs
    private var fuseStreamId: Int = 0
    private var flailStreamId: Int = 0
    private var holdingStreamId: Int = 0
    private var henFlyStreamId: Int = 0

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
            croakFastSoundId = it.load(appContext, R.raw.croak_fast, 1)
            bombCroakSoundId = it.load(appContext, R.raw.bomb_croak, 1)
            flailSoundId = it.load(appContext, R.raw.flail, 1)
            dingSoundId = it.load(appContext, R.raw.ding, 1)
            clickSoundId = it.load(appContext, R.raw.click, 1)
            alertSoundId = it.load(appContext, R.raw.alert, 1)
            beepSoundId = it.load(appContext, R.raw.beep, 1)
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
        }
    }

    // --- HELPER: Ensure Players ---
    private fun ensureSlidePlayer() {
        if (slidePlayer == null) {
            appContext?.let { ctx ->
                try {
                    slidePlayer = MediaPlayer.create(ctx, R.raw.hen_slide)
                    slidePlayer?.setOnCompletionListener { }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun ensureWingFlapPlayer() {
        if (wingFlapPlayer == null) {
            appContext?.let { ctx ->
                try {
                    wingFlapPlayer = MediaPlayer.create(ctx, R.raw.flap)
                    // FIX: No looping (User feedback: sound is long enough)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun ensureWhistlePlayer() {
        if (whistlePlayer == null) {
            appContext?.let { ctx ->
                try {
                    whistlePlayer = MediaPlayer.create(ctx, R.raw.whistle)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // --- PLAYER CONTROLS ---

    fun playHenSlide() {
        stopSlide()
        ensureSlidePlayer()
        try {
            slidePlayer?.setVolume(timerVolume, timerVolume)
            slidePlayer?.start()
        } catch (_: Exception) { }
    }

    fun updateSlideVolume(fadeFraction: Float) {
        try {
            if (slidePlayer?.isPlaying == true) {
                val vol = timerVolume * fadeFraction
                slidePlayer?.setVolume(vol, vol)
            }
        } catch (_: Exception) {}
    }

    fun stopSlide() {
        try {
            if (slidePlayer?.isPlaying == true) {
                slidePlayer?.pause()
                slidePlayer?.seekTo(0)
            }
        } catch (_: Exception) { }
    }

    // --- WING FLAP (MediaPlayer) ---
    fun playWingFlap(startVol: Float? = null) {
        ensureWingFlapPlayer()
        try {
            if (wingFlapPlayer?.isPlaying == false) {
                // FIX: Use specific start volume if provided (for resume), else max timerVolume
                val vol = startVol ?: timerVolume
                wingFlapPlayer?.setVolume(vol, vol)
                wingFlapPlayer?.start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopWingFlap() {
        try {
            if (wingFlapPlayer?.isPlaying == true) {
                wingFlapPlayer?.pause()
                wingFlapPlayer?.seekTo(0)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // FIX: Restored this function for the fade effect
    fun updateWingFlapVolume(vol: Float) {
        try {
            // Scale the requested volume (0..1) by the master timerVolume
            val finalVol = vol * timerVolume
            wingFlapPlayer?.setVolume(finalVol, finalVol)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- WHISTLE (MediaPlayer) ---
    fun playWhistle() {
        stopWhistle()
        ensureWhistlePlayer()
        try {
            whistlePlayer?.setVolume(timerVolume, timerVolume)
            whistlePlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopWhistle() {
        try {
            if (whistlePlayer?.isPlaying == true) {
                whistlePlayer?.pause()
                whistlePlayer?.seekTo(0)
            }
        } catch (e: Exception) { e.printStackTrace() }
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
    fun playCrack() { soundPool?.play(eggCrackSoundId, timerVolume, timerVolume, 1, 0, 1.0f) }

    fun playLoudCluck() {
        if (henFlyStreamId != 0) soundPool?.stop(henFlyStreamId)
        henFlyStreamId = soundPool?.play(henFlySoundId, 1.0f, 1.0f, 1, 0, 1.0f) ?: 0
    }

    fun stopLoudCluck() {
        if (henFlyStreamId != 0) {
            soundPool?.stop(henFlyStreamId)
            henFlyStreamId = 0
        }
    }

    fun playCroak(isFast: Boolean = false) {
        val soundId = if (isFast) croakFastSoundId else croakSoundId
        val pitch = 0.9f + Math.random().toFloat() * 0.2f
        soundPool?.play(soundId, timerVolume, timerVolume, 1, 0, pitch)
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

    fun playExplosion(context: Context) {
        stopAll()
        soundPool?.play(explosionSoundId, explosionVolume, explosionVolume, 2, 0, 1f)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 400, 100, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(800)
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
}