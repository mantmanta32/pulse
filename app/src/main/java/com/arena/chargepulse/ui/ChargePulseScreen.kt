package com.arena.chargepulse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.arena.chargepulse.BatteryState
import kotlin.math.roundToInt

private val Cyan = Color(0xFF47DEFF)
private val DeepBlue = Color(0xFF4169FF)
private val Ink = Color(0xFF050811)
private val Glass = Color(0x1A9EDFFF)

@Composable
fun ChargePulseScreen(state: BatteryState) {
    val infinite = rememberInfiniteTransition(label = "ambient")
    val aura by infinite.animateFloat(
        initialValue = .35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(Ink).drawBehind {
            drawCircle(Brush.radialGradient(listOf(DeepBlue.copy(alpha = .22f * aura), Color.Transparent)), radius = size.minDimension * .76f, center = center.copy(y = size.height * .26f))
            drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = .10f), Color.Transparent)), radius = size.minDimension * .72f, center = center.copy(y = size.height * .87f))
        }.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            BatteryOrb(percentage = state.percentage, charging = state.isPlugged, glow = aura)
            Spacer(Modifier.height(42.dp))
            AnimatedContent(
                targetState = state.isPlugged,
                transitionSpec = { (fadeIn(tween(380)) togetherWith fadeOut(tween(180))) },
                label = "charging content"
            ) { plugged ->
                if (plugged) ChargingReadings(state) else NotPlugged()
            }
        }
    }
}

@Composable
private fun BatteryOrb(percentage: Int?, charging: Boolean, glow: Float) {
    val shown = percentage ?: 0
    val animated by animateFloatAsState(shown.toFloat(), tween(750, easing = FastOutSlowInEasing), label = "battery percentage")
    val boltShift by rememberInfiniteTransition(label = "bolt").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bolt travel"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(214.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = size.minDimension / 2f - 13.dp.toPx()
            if (charging) {
                drawCircle(Cyan.copy(alpha = .11f * glow), radius = base + 19.dp.toPx() * glow, center = c)
                drawCircle(Cyan.copy(alpha = .34f * (1f - glow)), radius = base + 31.dp.toPx() * glow, center = c, style = Stroke(1.2.dp.toPx()))
            }
            drawCircle(Color(0xFF101C31), base, c)
            drawArc(Color(0xFF263851), -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x-base, c.y-base), size = androidx.compose.ui.geometry.Size(base*2, base*2), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Cyan, -90f, 360f * (animated / 100f), false, topLeft = androidx.compose.ui.geometry.Offset(c.x-base, c.y-base), size = androidx.compose.ui.geometry.Size(base*2, base*2), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (charging) Text("ϟ", color = Cyan, fontSize = (31 + boltShift * 5).sp, fontWeight = FontWeight.Light, modifier = Modifier.graphicsLayer { translationY = boltShift * 3f })
            Text(if (percentage == null) "N/A" else "${animated.roundToInt()}%", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp)
        }
    }
}

@Composable
private fun ChargingReadings(state: BatteryState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        ReadingCard("VOLTAJ", state.voltage, "V", 5.2f)
        ReadingCard("AKIM", state.currentMa?.let { kotlin.math.abs(it).toFloat() }, "mA", 3000f)
    }
}

@Composable
private fun ReadingCard(label: String, value: Float?, unit: String, max: Float) {
    val target = value ?: 0f
    val animated by animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = label)
    val format = if (unit == "V") String.format(java.util.Locale.US, "%.2f", animated) else animated.roundToInt().toString()
    Column(
        modifier = Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Cyan.copy(.18f), spotColor = Cyan.copy(.14f)).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Glass, Color(0x120B1B35)))).drawBehind { drawRoundRect(Color.White.copy(.10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()), style = Stroke(1.dp.toPx())) }
            .padding(horizontal = 23.dp, vertical = 19.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Color(0xFF9DB1C9), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(if (value == null) "N/A" else format, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
            Spacer(Modifier.width(7.dp)); Text(unit, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRoundRect(Color(0xFF23324B), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawRoundRect(Brush.horizontalGradient(listOf(DeepBlue, Cyan)), size = androidx.compose.ui.geometry.Size(size.width * (animated / max).coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
    }
}

@Composable
private fun NotPlugged() {
    Text("Şarj takılı değil", color = Color(0xFFDDE8F7), fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = .4.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = DeepBlue.copy(.18f)).clip(RoundedCornerShape(24.dp)).background(Color(0x17132439)).padding(vertical = 22.dp))
}
