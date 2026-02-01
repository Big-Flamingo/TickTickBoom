package com.flamingo.ticktickboom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    textColor: Color,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(200.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(borderWidth, borderColor, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
    }
}

@Composable
fun RowScope.StyleButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    colors: AppColors,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) colors.surface else colors.surface.copy(alpha = 0.3f)
    val borderColor = if (isSelected) color else colors.border
    val contentColor = if (isSelected) color else colors.textSecondary

    Column(modifier = Modifier
        .weight(1f)
        .height(100.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(bgColor)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        .clickable { onClick() }
        .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = contentColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
    }
}

@Composable
fun TimeInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {} // NEW: Action callback
) {
    Column(modifier = modifier.background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessTime, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) },
            // NEW: Add ImeAction.Done
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            // NEW: Trigger onDone when Enter is pressed
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(color = colors.text, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun VolumeSlider(
    label: String,
    value: Float,
    color: Color,
    colors: AppColors,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (label.contains("TIMER")) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Warning, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
            }
            Text("${(value * 100).toInt()}%", color = colors.textSecondary, fontSize = 10.sp, fontFamily = CustomFont)
        }
        Slider(value = value, onValueChange = onValueChange, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha=0.7f), inactiveTrackColor = colors.border))
    }
}