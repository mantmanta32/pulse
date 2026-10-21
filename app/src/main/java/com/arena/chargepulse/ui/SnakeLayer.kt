package com.arena.chargepulse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Ambient layer: a glowing snake that roams the screen hunting colorful bubbles.
// When its head reaches a bubble, the bubble pops (ring + particles) and the snake grows;
// it slowly digests back over time. Purely decorative, no input, drawn behind the UI.

private val TAU = (2.0 * PI).toFloat()

private val BubblePalette = listOf(
    Color(0xFF47DEFF), Color(0xFF8F6BFF), Color(0xFFFF6BD6),
    Color(0xFFFFC24B), Color(0xFF5BFFB0), Color(0xFF4187FF)
)

private class Bubble(
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: Color,
    val seed: Float,
    val rise: Float
)

private class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val maxLife: Float,
    var life: Float,
    val radius: Float,
    val color: Color
)

private class PopRing(var x: Float, var y: Float, val startRadius: Float, val color: Color) {
    var t = 0f
}

private class SnakeWorld {
    var width = 0f
    var height = 0f
    private var density = 1f
    private var time = 0f

    private var headX = 0f
    private var headY = 0f
    private var angle = 0f
    private var desiredSegments = 12
    private val history = mutableListOf<Offset>()
    private val segments = mutableListOf<Offset>()

    private val bubbles = mutableListOf<Bubble>()
    private val particles = mutableListOf<Particle>()
    private val rings = mutableListOf<PopRing>()
    private var digestTimer = 0f
    private var tongueTimer = 0f
    private var tongueOn = 0f

    fun resize(w: Float, h: Float, d: Float) {
        val firstLayout = width == 0f
        width = w
        height = h
        density = d
        if (firstLayout && w > 0f) {
            headX = w * 0.25f
            headY = h * 0.62f
            angle = 0.3f
            history.add(Offset(headX, headY))
            repeat(4) { bubbles.add(newBubble(minDistanceFromHead = 0f)) }
        }
        headX = headX.coerceIn(0f, w)
        headY = headY.coerceIn(0f, h)
    }

    fun tick(rawDt: Float) {
        if (width == 0f || height == 0f) return
        val dt = min(rawDt, 0.05f)
        time += dt

        val speedPx = 150f * density
        val turnRate = 3.4f

        // Steer toward the nearest bubble.
        var desired = angle
        bubbles.minByOrNull { (it.x - headX) * (it.x - headX) + (it.y - headY) * (it.y - headY) }
            ?.let { desired = atan2(it.y - headY, it.x - headX) }

        // Near an edge? Head back toward the center instead.
        val margin = 48f * density
        if (headX < margin || headY < margin || headX > width - margin || headY > height - margin) {
            desired = atan2(height / 2f - headY, width / 2f - headX)
        }

        val diff = ((desired - angle + TAU / 2f) % TAU + TAU) % TAU - TAU / 2f
        angle += diff.coerceIn(-turnRate * dt, turnRate * dt)

        // Serpentine wiggle on top of the steering direction.
        val heading = angle + sin(time * 3.1f) * 0.5f
        headX += cos(heading) * speedPx * dt
        headY += sin(heading) * speedPx * dt
        headX = headX.coerceIn(8f, width - 8f)
        headY = headY.coerceIn(8f, height - 8f)

        // Record the path the body follows.
        val last = history.firstOrNull()
        if (last == null || Offset(headX - last.x, headY - last.y).getDistance() > 1f) {
            history.add(0, Offset(headX, headY))
        }
        val spacing = spacingPx()
        val maxPathLength = (desiredSegments + 2) * spacing
        var accumulated = 0f
        var cut = history.size
        for (i in 0 until history.size - 1) {
            accumulated += Offset(history[i].x - history[i + 1].x, history[i].y - history[i + 1].y).getDistance()
            if (accumulated > maxPathLength) { cut = i + 2; break }
        }
        if (cut < history.size) history.subList(cut, history.size).clear()
        placeSegments(spacing)

        // Eat bubbles the head touches.
        val headR = headRadiusPx()
        bubbles.removeAll { b ->
            val d = Offset(b.x - headX, b.y - headY).getDistance()
            if (d < headR + b.radius * 0.8f) {
                eat(b)
                true
            } else {
                false
            }
        }

        // Bubbles drift slowly upward and sway.
        for (b in bubbles) {
            b.y -= b.rise * density * dt
            b.x += sin(time * 1.2f + b.seed) * 8f * density * dt
            if (b.y < -b.radius) {
                b.y = height + b.radius
                b.x = Random.nextFloat() * width
            }
            b.x = b.x.coerceIn(b.radius, width - b.radius)
        }

        // Slowly digest back to the base length.
        digestTimer += dt
        if (digestTimer > 6f) {
            digestTimer = 0f
            if (desiredSegments > 12) desiredSegments--
        }

        // Occasional tongue flick.
        tongueTimer += dt
        if (tongueOn > 0f) {
            tongueOn -= dt
        } else if (tongueTimer > 2.4f) {
            tongueTimer = 0f
            tongueOn = 0.35f
        }

        // Particles and pop rings.
        particles.removeAll { p ->
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            p.life <= 0f
        }
        rings.removeAll { r ->
            r.t += dt / 0.5f
            r.t >= 1f
        }
    }

    private fun eat(b: Bubble) {
        desiredSegments = min(desiredSegments + 1, 26)
        rings.add(PopRing(b.x, b.y, b.radius, b.color))
        repeat(12) {
            val a = Random.nextFloat() * TAU
            val speed = (60f + Random.nextFloat() * 110f) * density
            val life = 0.4f + Random.nextFloat() * 0.3f
            particles.add(
                Particle(
                    x = b.x, y = b.y,
                    vx = cos(a) * speed, vy = sin(a) * speed,
                    maxLife = life, life = life,
                    radius = (1.5f + Random.nextFloat() * 2f) * density,
                    color = if (it % 3 == 0) Color.White else b.color
                )
            )
        }
        bubbles.add(newBubble(minDistanceFromHead = 0.3f * min(width, height)))
    }

    private fun newBubble(minDistanceFromHead: Float): Bubble {
        var x = 0f
        var y = 0f
        var attempts = 0
        do {
            x = width * (0.12f + Random.nextFloat() * 0.76f)
            y = height * (0.12f + Random.nextFloat() * 0.76f)
            attempts++
        } while (minDistanceFromHead > 0f &&
            Offset(x - headX, y - headY).getDistance() < minDistanceFromHead &&
            attempts < 8
        )
        return Bubble(
            x = x, y = y,
            radius = (7f + Random.nextFloat() * 5f) * density,
            color = BubblePalette[Random.nextInt(BubblePalette.size)],
            seed = Random.nextFloat() * TAU,
            rise = 9f + Random.nextFloat() * 8f
        )
    }

    private fun placeSegments(spacing: Float) {
        segments.clear()
        if (history.isEmpty()) return
        segments.add(history[0])
        var need = spacing
        var i = 0
        while (segments.size < desiredSegments && i < history.size - 1) {
            val a = history[i]
            val b = history[i + 1]
            val d = Offset(a.x - b.x, a.y - b.y).getDistance()
            if (d > 0f) {
                while (need <= d && segments.size < desiredSegments) {
                    val t = need / d
                    segments.add(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
                    need += spacing
                }
                need -= d
            }
            i++
        }
        while (segments.size < desiredSegments) segments.add(segments.last())
    }

    private fun spacingPx() = 9f * density
    private fun headRadiusPx() = 10f * density

    fun draw(scope: DrawScope) {
        if (width == 0f || segments.isEmpty()) return

        // Bubbles: glassy colored balloons.
        for (b in bubbles) {
            val c = Offset(b.x, b.y)
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(b.color.copy(alpha = 0.50f), b.color.copy(alpha = 0.10f)),
                    center = Offset(b.x - b.radius * 0.3f, b.y - b.radius * 0.35f),
                    radius = b.radius * 1.35f
                ),
                radius = b.radius,
                center = c
            )
            scope.drawCircle(
                color = b.color.copy(alpha = 0.45f),
                radius = b.radius,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )
            scope.drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = b.radius * 0.18f,
                center = Offset(b.x - b.radius * 0.38f, b.y - b.radius * 0.42f)
            )
        }

        // Pop rings.
        for (ring in rings) {
            val r = ring.startRadius * (1f + ring.t * 1.6f)
            scope.drawCircle(
                color = ring.color.copy(alpha = (1f - ring.t) * 0.7f),
                radius = r,
                center = Offset(ring.x, ring.y),
                style = Stroke(width = 0.5f + 2.dp.toPx() * (1f - ring.t))
            )
        }

        // Particles.
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            scope.drawCircle(
                color = p.color.copy(alpha = a * 0.9f),
                radius = p.radius * (0.4f + 0.6f * a),
                center = Offset(p.x, p.y)
            )
        }

        // Snake body: tail to head along a cyan -> violet gradient.
        val n = segments.size
        val headR = headRadiusPx()
        val cHead = Color(0xFF47DEFF)
        val cMid = Color(0xFF8F6BFF)
        val cTail = Color(0xFF41337F)
        for (i in n - 1 downTo 0) {
            val f = if (n == 1) 0f else i / (n - 1f)
            val col = if (f < 0.5f) lerp(cHead, cMid, f * 2f) else lerp(cMid, cTail, (f - 0.5f) * 2f)
            val r = headR * (1f - 0.62f * f) * (if (i == 0) 1.15f else 1f)
            if (i == 0) scope.drawCircle(color = col.copy(alpha = 0.20f), radius = r * 2.1f, center = segments[i])
            scope.drawCircle(color = col, radius = r, center = segments[i])
        }

        // Heading: from the head toward where the body came from.
        val dir = if (n > 1) {
            val a = segments[0]
            val b = segments[1]
            val d = Offset(a.x - b.x, a.y - b.y)
            if (d.getDistance() > 0.001f) atan2(d.y, d.x) else angle
        } else angle
        val ex = cos(dir)
        val ey = sin(dir)
        val px = -ey
        val py = ex

        // Tongue flick.
        if (tongueOn > 0f) {
            val tip = Offset(headX + ex * headR * 2.1f, headY + ey * headR * 2.1f)
            val mid = Offset(headX + ex * headR * 1.2f, headY + ey * headR * 1.2f)
            scope.drawLine(
                color = Color(0xFFFF5E7A),
                start = mid,
                end = tip,
                strokeWidth = 1.6.dp.toPx()
            )
        }

        // Eyes.
        val eyeR = headR * 0.38f
        for (side in floatArrayOf(1f, -1f)) {
            val cx = headX + ex * headR * 0.35f + px * side * headR * 0.55f
            val cy = headY + ey * headR * 0.35f + py * side * headR * 0.55f
            scope.drawCircle(color = Color(0xFFEAF6FF), radius = eyeR, center = Offset(cx, cy))
            scope.drawCircle(
                color = Color(0xFF0B1230),
                radius = eyeR * 0.5f,
                center = Offset(cx + ex * eyeR * 0.35f, cy + ey * eyeR * 0.35f)
            )
        }
    }
}

@Composable
fun SnakeLayer(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val world = remember { SnakeWorld() }
    val frame = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (last >= 0L) world.tick((now - last) / 1_000_000_000f)
            last = now
            frame.intValue++
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            world.resize(it.width.toFloat(), it.height.toFloat(), density.density)
        }
    ) {
        // Reading the frame counter invalidates this draw every animation frame.
        frame.intValue
        world.draw(this)
    }
}
