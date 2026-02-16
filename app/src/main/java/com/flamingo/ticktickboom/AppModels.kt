package com.flamingo.ticktickboom

import androidx.compose.ui.graphics.Color

enum class AppState { SETUP, RUNNING, EXPLODED }

data class TimerSettings(val minSeconds: Int, val maxSeconds: Int, val style: String = "C4")

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
    val text: String, val x: Float, val y: Float,
    val color: Color, val gradientColors: List<Color>? = null,
    val alpha: Float = 1f, val life: Float = 1.0f, val fontSize: Float
)