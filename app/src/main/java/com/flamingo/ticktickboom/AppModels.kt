package com.flamingo.ticktickboom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class AppState { SETUP, RUNNING, EXPLODED }

enum class PlayMode { SOLO, GROUP }

@Immutable
data class Player(
    val id: String, // Unique ID so we can track them perfectly
    val name: String,
    val timeLeft: Float, // This is their saved time while waiting
    val isEliminated: Boolean = false,
    val isAbsent: Boolean = false // Checked off during setup
)

@Immutable
data class GroupPreset(
    val id: String,
    val presetName: String,
    val players: List<Player>,
    val defaultTime: Float, // The starting time for everyone
    val resetOnExplosion: Boolean
)

data class TimerSettings(val minSeconds: Int, val maxSeconds: Int, val style: String = "C4")

@Immutable
data class Particle(
    val id: Int,
    val dirX: Float, // <-- Replaces angle
    val dirY: Float, // <-- Replaces angle
    val velocity: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float
)

data class SmokeParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float,
    var life: Float,
    val maxLife: Float
)

// --- MOVED FROM VISUALS.KT (Renamed from Local...) ---

data class VisualParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float
)

data class VisualText(
    val text: String, val x: Float, var y: Float, // <-- Changed to var
    val color: Color, val gradientColors: List<Color>? = null,
    var alpha: Float = 1f, var life: Float = 1.0f, val fontSize: Float // <-- Changed to var
)