package com.flamingo.ticktickboom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// --- COLORS ---
val Slate950 = Color(0xFF020617) // Dark Mode Background
val Slate900 = Color(0xFF0F172A) // Dark Mode Surface
val Slate800 = Color(0xFF1E293B) // Dark Mode Border

val Slate50  = Color(0xFFF8FAFC) // Light Mode Background
val Slate200 = Color(0xFFE2E8F0) // Light Mode Border

// Visual Assets
val MetallicLight = Color(0xFFF1F5F9)
val MetallicDark = Color(0xFFE2E8F0)

// REVERTED: Original Screen Background (The casing)
val C4ScreenBg = Color(0xFF0B1120)

// NEW: The actual dark background for the digits
val LcdDarkBackground = Color(0xFF020408)

// Accents
val NeonRed = Color(0xFFEF4444)
val NeonCyan = Color(0xFF22D3EE)
val NeonOrange = Color(0xFFFB923C)
val TextGray = Color(0xFF94A3B8)

// Smoke Colors
val SmokeLight = Color(0xFFE2E8F0) // White smoke for dark mode
val SmokeDark = Color(0xFF334155)  // Dark smoke for light mode

// --- FONTS ---
val CustomFont = FontFamily(
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_bold, FontWeight.Normal)
)

// --- DATA CLASSES FOR THEME ---
data class AppColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val smokeColor: Color
)