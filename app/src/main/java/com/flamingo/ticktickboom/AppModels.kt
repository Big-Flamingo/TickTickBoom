package com.flamingo.ticktickboom

import androidx.compose.ui.graphics.Color

enum class AppState { SETUP, RUNNING, EXPLODED }

data class TimerSettings(val minSeconds: Int, val maxSeconds: Int, val style: String = "C4")

data class Particle(
    val id: Int,
    val angle: Double,
    val velocity: Float,
    val size: Float,
    val color: Color,
    val delay: Float,
    val rotationSpeed: Float
)

data class Spark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float
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