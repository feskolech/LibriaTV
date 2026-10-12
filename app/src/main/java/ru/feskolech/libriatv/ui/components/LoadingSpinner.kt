package ru.feskolech.libriatv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * The app's loading spinner: an accent arc running round a faint ring. The TV Material library has no
 * progress indicator. The stroke follows the size, so a small one next to a title reads the same.
 */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val accent = MaterialTheme.colorScheme.primary
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(modifier.size(size)) {
        val stroke = Stroke(size.toPx() / 11, cap = StrokeCap.Round)
        drawArc(Color(0x33FFFFFF), 0f, 360f, false, style = Stroke(stroke.width))
        drawArc(accent, angle, 90f, false, style = stroke)
    }
}
