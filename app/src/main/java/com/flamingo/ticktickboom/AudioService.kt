package com.flamingo.ticktickboom

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

object AudioService {
    private var soundPool: SoundPool? = null
    // Sound IDs
    private var clickId = 0
    private var tickId = 0
    private var clockId = 0
    private var fuseId = 0
    private var dingId = 0
    private var explosionId = 0
    // NEW: Frog Mode Sounds
    private var wobbleId = 0
    private var croakId = 0

    // Stream IDs (for controlling loops)
    private var fuseStreamId = 0
    private var isLoaded = false

    // MediaPlayer for optional longer explosion layers
    private var explosionPlayer: MediaPlayer? = null

    var timerVolume = 0.8f
    var explosionVolume = 1.0f

    fun init(context: Context) {
        if (soundPool != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attributes)
            .build()

        clickId = soundPool!!.load(context, R.raw.click, 1)
        tickId = soundPool!!.load(context, R.raw.tick, 1)
        clockId = soundPool!!.load(context, R.raw.clock, 1)
        fuseId = soundPool!!.load(context, R.raw.fuse, 1)
        dingId = soundPool!!.load(context, R.raw.ding, 1)
        explosionId = soundPool!!.load(context, R.raw.explosion, 1)

        // NEW: Load Easter Egg sounds
        // Make sure files are named "wobble.mp3" and "croak.mp3" (lowercase)
        wobbleId = soundPool!!.load(context, R.raw.wobble, 1)
        croakId = soundPool!!.load(context, R.raw.croak, 1)

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isLoaded = true
        }
    }

    fun playClick() {
        if (isLoaded) soundPool?.play(clickId, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun playTick() {
        if (isLoaded) soundPool?.play(tickId, timerVolume, timerVolume, 1, 0, 1f)
    }

    fun playClockTick() {
        if (isLoaded) soundPool?.play(clockId, timerVolume, timerVolume, 1, 0, 1f)
    }

    fun playDing() {
        if (isLoaded) soundPool?.play(dingId, timerVolume, timerVolume, 1, 0, 1f)
    }

    // NEW: Play Wobble (Max volume for UI feedback)
    fun playWobble() {
        if (isLoaded) soundPool?.play(wobbleId, 1.0f, 1.0f, 1, 0, 1f)
    }

    // NEW: Play Croak (Respects timer volume slider)
    fun playCroak() {
        if (isLoaded) soundPool?.play(croakId, timerVolume, timerVolume, 1, 0, 1f)
    }

    fun startFuse(startMuffled: Boolean = false) {
        if (fuseStreamId != 0) return
        if (isLoaded) {
            val vol = if (startMuffled) timerVolume * 0.15f else timerVolume
            fuseStreamId = soundPool?.play(fuseId, vol, vol, 1, -1, 1f) ?: 0
        }
    }

    fun dimFuse() {
        if (fuseStreamId != 0) {
            val muffledVol = timerVolume * 0.15f
            soundPool?.setVolume(fuseStreamId, muffledVol, muffledVol)
        }
    }

    fun stopFuse() {
        if (fuseStreamId != 0) {
            soundPool?.stop(fuseStreamId)
            fuseStreamId = 0
        }
    }

    fun playExplosion(context: Context) {
        stopFuse()
        if (isLoaded) soundPool?.play(explosionId, explosionVolume, explosionVolume, 2, 0, 1f)

        try {
            if (explosionPlayer != null) {
                explosionPlayer?.release()
            }
            explosionPlayer = MediaPlayer.create(context, R.raw.explosion)
            explosionPlayer?.setVolume(explosionVolume, explosionVolume)
            explosionPlayer?.start()
            explosionPlayer?.setOnCompletionListener {
                it.release()
                explosionPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}