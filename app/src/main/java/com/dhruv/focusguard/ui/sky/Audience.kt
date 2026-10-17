package com.dhruv.focusguard.ui.sky

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.R
import com.dhruv.focusguard.guard.Haptics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class StagePhase { Idle, Running, Paused, Finished }

val HandFont = FontFamily(Font(R.font.patrick_hand))

/**
 * One member of the crowd. Position and size are fractions of the stage box,
 * so the whole line-up scales to whatever width the phone gives it.
 */
private class Member(
    val fx: Float,
    val fy: Float,
    val fr: Float,
    val squat: Float,          // how wide vs tall this one is
    val main: Boolean = false,
    val seed: Int
) {
    val pose = BubblePose()
    var lift = 0f; var liftV = 0f
    var sq = 0f; var sqV = 0f
    var tilt = 0f; var tiltV = 0f
    var nextHop = 1.5f + seed * 0.37f
    var nextBlink = 1f + seed * 0.61f
    var blinkAt = -10f
    var reactUntil = -1f
    var lookAt: Offset? = null
    var lookUntil = -1f
    var queuedHopAt = -1f
    var queuedPower = 0f
    var cheering = false
}

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val born: Float, val life: Float, val kind: Int, val color: Color, val size: Float,
    var rot: Float, val spin: Float
)

private const val HEART = 0
private const val SPARK = 1
private const val CONFETTI = 2
private const val NOTE = 3

private val ConfettiColors = listOf(
    Color(0xFFB79CFF), Color(0xFFF7A8D8), Color(0xFF9FC1FE), Color(0xFFFFD27A), Color(0xFF8EE0C8)
)

/** A word one of them shouts, floating up from their head. */
private class Shout(val text: String, val x: Float, val y: Float, val born: Float, val tilt: Float)

/** The line-up: seven fans and the big one in the middle. */
private fun lineUp(): List<Member> = listOf(
    Member(0.085f, 0.70f, 0.052f, 1.06f, seed = 1),
    Member(0.185f, 0.66f, 0.068f, 1.04f, seed = 2),
    Member(0.300f, 0.675f, 0.062f, 1.08f, seed = 3),
    Member(0.700f, 0.675f, 0.064f, 1.05f, seed = 4),
    Member(0.815f, 0.66f, 0.058f, 1.07f, seed = 5),
    Member(0.915f, 0.705f, 0.050f, 1.05f, seed = 6),
    Member(0.500f, 0.575f, 0.108f, 1.03f, main = true, seed = 0)
)

/** Who joins the cheering first as progress builds. */
private val CheerOrder = intArrayOf(2, 3, 1, 4, 0, 5)
private val CheerCount = intArrayOf(0, 1, 2, 4, 5, 6)

private val RunningShouts = listOf("Go!", "Nice!", "Keep going!", "You've got this!", "Yes!", "Focus!", "So close!")
private val FinishShouts = listOf("You did it!", "Amazing!", "Champion!", "Woohoo!", "Proud of you!")
private val IdleShouts = listOf("Ready?", "Let's go!", "We're here!")
private val PausedShouts = listOf("We'll wait", "Take your time", "Still here")

/** The note the crowd holds up. */
fun calloutFor(phase: StagePhase, progress: Float): String = when (phase) {
    StagePhase.Idle -> "Ready when you are!"
    StagePhase.Paused -> "We'll wait for you"
    StagePhase.Finished -> "You did it!"
    StagePhase.Running -> when {
        progress >= 0.9f -> "Nearly done!"
        progress >= 0.75f -> "Almost there!"
        progress >= 0.5f -> "Halfway there!"
        progress >= 0.25f -> "You're doing great!"
        else -> "Let's go!"
    }
}

/**
 * The audience: a crowd of jelly bubbles who watch you work.
 *
 *  - More of them cheer as [cheer] climbs from 0 to 5, and they shout as they go.
 *  - Each change of [wave] sends a stadium wave down the row (milestones).
 *  - Paused, they hold their breath; finished, they go wild with confetti.
 *  - Tap one: it giggles, throws hearts and its neighbours turn to look. Tap the
 *    big one and the whole crowd waves. Drag along the row to bounce each in turn.
 */
@Composable
fun AudienceStage(
    phase: StagePhase,
    cheer: Int,
    wave: Int,
    callout: String,
    haptics: Haptics?,
    modifier: Modifier = Modifier,
    referenceFaces: Boolean = false
) {
    val members = remember { lineUp() }
    val particles = remember { ArrayList<Particle>() }
    val shouts = remember { ArrayList<Shout>() }
    val frame = remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val state = remember { StageState() }
    val phaseNow by rememberUpdatedState(phase)
    val cheerNow by rememberUpdatedState(cheer.coerceIn(0, 5))
    val calloutNow by rememberUpdatedState(callout)

    fun hop(m: Member, power: Float) {
        m.liftV = -power
        m.sqV = -3.5f * power / 2f
        m.tiltV += (Random.nextFloat() - 0.5f) * 40f
    }

    fun burst(m: Member, n: Int, kind: Int) {
        repeat(n) { i ->
            val a = (-90f + (i - n / 2f) * (120f / n.coerceAtLeast(1))) * (Math.PI / 180f).toFloat()
            particles += Particle(
                m.fx, m.fy - m.fr * 1.1f,
                cos(a) * (0.09f + Random.nextFloat() * 0.07f), sin(a) * (0.26f + Random.nextFloat() * 0.18f),
                state.time, 1.2f + Random.nextFloat() * 0.5f, kind,
                if (kind == HEART) (if (i % 2 == 0) Color(0xFFF07AAE) else Color(0xFFB59BFF)) else Color.White,
                if (kind == HEART) 0.030f + Random.nextFloat() * 0.016f else 0.022f + Random.nextFloat() * 0.016f,
                0f, 0f
            )
        }
    }

    fun shout(m: Member, words: List<String>) {
        if (shouts.size > 2) return
        // Above the head, nudged away from the edges and from the note on the right.
        val x = if (m.main) 0.26f else m.fx.coerceIn(0.14f, 0.62f)
        shouts += Shout(words[Random.nextInt(words.size)], x, m.fy - m.fr * 2.1f, state.time, (Random.nextFloat() - 0.5f) * 14f)
    }

    fun doWave(power: Float) {
        members.sortedBy { it.fx }.forEachIndexed { i, m ->
            m.queuedHopAt = state.time + i * 0.085f
            m.queuedPower = power
        }
    }

    LaunchedEffect(wave) {
        if (wave > 0) {
            doWave(2.3f)
            haptics?.tick()
            members.firstOrNull { it.main }?.let { shout(it, RunningShouts) }
        }
    }

    LaunchedEffect(phase) {
        if (phase == StagePhase.Finished) {
            state.finishedAt = state.time
            doWave(3.0f)
            members.forEach { burst(it, 5, HEART) }
            members.firstOrNull { it.main }?.let { shout(it, FinishShouts) }
        }
        if (phase == StagePhase.Running) members.firstOrNull { it.main }?.let { shout(it, IdleShouts) }
    }

    // One clock drives the crowd. The physics live in plain fields and only
    // `frame` is state, read inside the Canvas — so this repaints without
    // recomposing anything above it.
    LaunchedEffect(Unit) {
        if (!SkyMotion.enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                state.time += dt
                val t = state.time
                val ph = phaseNow
                val cheering = CheerCount[cheerNow]
                for ((index, m) in members.withIndex()) {
                    if (m.queuedHopAt in 0f..t) {
                        hop(m, m.queuedPower)
                        particles += Particle(
                            m.fx, m.fy - m.fr * 1.2f, 0f, -0.13f, t, 0.9f, SPARK, Color.White, 0.026f, 0f, 0f
                        )
                        m.queuedHopAt = -1f
                    }
                    val rank = CheerOrder.indexOf(index)
                    m.cheering = ph == StagePhase.Finished ||
                        (ph == StagePhase.Running && (m.main && cheerNow >= 2 || rank in 0 until cheering))
                    if (t >= m.nextHop) {
                        when {
                            ph == StagePhase.Finished && t - state.finishedAt < 7f -> {
                                hop(m, 2.1f + Random.nextFloat() * 0.9f)
                                if (Random.nextFloat() < 0.5f) burst(m, 2, HEART)
                                if (Random.nextFloat() < 0.3f) shout(m, FinishShouts)
                                m.nextHop = t + 0.6f + Random.nextFloat() * 0.6f
                            }
                            m.cheering -> {
                                hop(m, 1.4f + Random.nextFloat() * 0.7f)
                                if (Random.nextFloat() < 0.3f) burst(m, 1, HEART)
                                if (Random.nextFloat() < 0.22f) shout(m, RunningShouts)
                                m.nextHop = t + (2.8f - cheerNow * 0.3f) + Random.nextFloat() * 2f
                            }
                            ph == StagePhase.Paused -> {
                                if (Random.nextFloat() < 0.25f) shout(m, PausedShouts)
                                m.nextHop = t + 2.5f + Random.nextFloat() * 2.5f
                            }
                            else -> m.nextHop = t + 1.5f + Random.nextFloat() * 2f
                        }
                    }
                    m.liftV += (-m.lift * 150f - m.liftV * 8f) * dt
                    m.lift += m.liftV * dt
                    m.sqV += (-m.sq * 260f - m.sqV * 11f) * dt
                    m.sq += m.sqV * dt
                    m.tiltV += (-m.tilt * 120f - m.tiltV * 9f) * dt
                    m.tilt += m.tiltV * dt
                    if (t >= m.nextBlink) {
                        m.blinkAt = t
                        m.nextBlink = t + 2.2f + Random.nextFloat() * 3.5f
                    }
                    val bt = (t - m.blinkAt) / 0.16f
                    m.pose.eyeOpen = if (bt in 0f..1f) abs(bt * 2f - 1f) else 1f
                    val bob = sin(t * 1.9f + m.seed * 1.3f) * 0.025f
                    val nervous = if (ph == StagePhase.Paused) sin(t * 20f + m.seed) * 0.7f else 0f
                    m.pose.lift = m.lift + bob
                    m.pose.squashX = 1f + m.sq * 0.45f
                    m.pose.squashY = 1f - m.sq * 0.6f
                    m.pose.tilt = m.tilt + nervous
                    m.pose.leafSway = sin(t * 1.3f) * 0.5f + m.tiltV * 0.004f
                    m.pose.glow = if (m.cheering) 0.25f + 0.25f * (0.5f + 0.5f * sin(t * 3f + m.seed)) else 0f
                    val target = m.lookAt
                    m.pose.look = if (target != null && t < m.lookUntil) {
                        val d = Offset(target.x - m.fx, target.y - m.fy)
                        val len = hypot(d.x, d.y).coerceAtLeast(0.001f)
                        Offset(d.x / len, d.y / len)
                    } else when (ph) {
                        StagePhase.Paused -> Offset(0f, 0.25f)
                        StagePhase.Finished -> Offset.Zero
                        else -> Offset((0.5f - m.fx) * 1.6f, -0.9f)   // up at the timer
                    }
                }
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    if (t - p.born > p.life) { it.remove(); continue }
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += (if (p.kind == CONFETTI) 0.38f else 0.17f) * dt
                    p.vx *= 0.985f
                    p.rot += p.spin * dt
                }
                shouts.removeAll { t - it.born > 1.8f }
                if (ph == StagePhase.Finished && t - state.finishedAt < 5f && Random.nextFloat() < 0.7f) {
                    particles += Particle(
                        0.05f + Random.nextFloat() * 0.9f, -0.15f,
                        (Random.nextFloat() - 0.5f) * 0.18f, 0.12f + Random.nextFloat() * 0.18f,
                        t, 2.6f, CONFETTI, ConfettiColors[Random.nextInt(ConfettiColors.size)],
                        0.020f + Random.nextFloat() * 0.014f, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 600f
                    )
                }
                frame.longValue = now
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                fun hit(p: Offset): Member? {
                    val fx = p.x / size.width
                    val fy = p.y / size.height
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    return members.minByOrNull {
                        val dx = (fx - it.fx) * w / (it.fr * w)
                        val dy = (fy - it.fy) * h / (it.fr * w)
                        dx * dx + dy * dy
                    }?.takeIf {
                        val dx = (fx - it.fx) * w / (it.fr * w)
                        val dy = (fy - it.fy) * h / (it.fr * w)
                        dx * dx + dy * dy < 2.2f
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var current = hit(down.position)
                    current?.let { m ->
                        haptics?.click()
                        hop(m, if (m.main) 2.2f else 1.9f)
                        burst(m, if (m.main) 7 else 4, HEART)
                        m.reactUntil = state.time + 1.3f
                        shout(m, if (phaseNow == StagePhase.Finished) FinishShouts else RunningShouts)
                        members.forEach { o ->
                            if (o !== m) { o.lookAt = Offset(m.fx, m.fy); o.lookUntil = state.time + 1.2f }
                        }
                        if (m.main) doWave(2.0f)
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val now = hit(ch.position)
                        if (now != null && now !== current) {
                            haptics?.tick()
                            hop(now, 1.3f)
                            now.reactUntil = state.time + 0.8f
                            if (Random.nextFloat() < 0.6f) burst(now, 1, SPARK)
                        }
                        current = now
                        ch.consume()
                    }
                }
            }
    ) {
        frame.longValue   // repaint every frame
        val w = size.width
        val h = size.height
        val t = state.time
        fun px(fx: Float, fy: Float) = Offset(fx * w, fy * h)

        drawGround(w, h)

        // The handwritten note the crowd holds up, with its arrow and heart
        drawCallout(measurer, calloutNow, w, h, t)

        val ordered = members.sortedBy { if (it.main) 1 else 0 }
        for (m in ordered) {
            val index = members.indexOf(m)
            val face = when {
                referenceFaces && m.main -> Face(Eyes.Happy, Mouth.Laugh)
                referenceFaces -> Face(Eyes.Happy, Mouth.Smile)
                t < m.reactUntil -> Face(Eyes.Happy, Mouth.Laugh)
                else -> faceFor(m, index, phase, cheer.coerceIn(0, 5))
            }
            val layout = if (m.main) FaceLayout(eyeDx = 0.28f, eyeDy = -0.22f, eyeSize = 1.0f, mouthDy = 0.06f, mouthSize = 1.15f, blushDx = 0.49f, blushDy = 0.09f)
            else FaceLayout(eyeDx = 0.34f, eyeDy = -0.18f, eyeSize = 1.05f, mouthDy = 0.02f, blushDx = 0.47f, blushDy = 0.02f)
            val r = m.fr * w
            drawJelly(
                c = px(m.fx, m.fy),
                rx = r * m.squat, ry = r,
                face = face,
                pose = m.pose,
                layout = layout,
                sprouts = if (m.main) Sprouts.LeafPairs else Sprouts.None,
                warmth = (m.seed % 3) * 0.14f,
                time = t + m.seed,
                brightness = if (m.main) 0.65f else 0.25f,
                twinkles = if (m.main) 9 else 0
            )
            // A ring of light under whoever is cheering
            if (m.cheering) {
                val pulse = 0.5f + 0.5f * sin(t * 3.4f + m.seed)
                val c = px(m.fx, m.fy + m.fr * 0.95f)
                scale(1f, 0.22f, pivot = c) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFFFE9B8).copy(alpha = 0.35f + 0.25f * pulse), Color.Transparent),
                            center = c, radius = r * 1.5f
                        ),
                        radius = r * 1.5f, center = c
                    )
                }
            }
        }

        for (p in particles) {
            val age = (t - p.born) / p.life
            val a = (1f - age).coerceIn(0f, 1f)
            val c = px(p.x, p.y)
            val s = p.size * w
            when (p.kind) {
                HEART -> heartShape(c, s * (0.7f + age * 0.5f), p.color.copy(alpha = a))
                SPARK -> sparkle(c, s * (0.6f + age), p.color.copy(alpha = a))
                else -> rotate(p.rot, pivot = c) {
                    drawRect(p.color.copy(alpha = a), topLeft = Offset(c.x - s / 2, c.y - s / 4), size = Size(s, s / 2))
                }
            }
        }

        // What they are shouting
        for (sh in shouts) {
            val age = (t - sh.born) / 1.8f
            val a = (1f - age * age).coerceIn(0f, 1f)
            val pos = px(sh.x, sh.y - age * 0.16f)
            val style = TextStyle(fontFamily = HandFont, fontSize = (w * 0.040f).toSp(), color = Sky.Hand.copy(alpha = a))
            val laid: TextLayoutResult = measurer.measure(sh.text, style)
            rotate(sh.tilt, pivot = pos) {
                drawText(laid, topLeft = Offset(pos.x - laid.size.width / 2f, pos.y - laid.size.height))
            }
        }
    }
}

private class StageState {
    var time = 0f
    var finishedAt = -1f
}

private fun faceFor(m: Member, index: Int, phase: StagePhase, cheer: Int): Face {
    val rank = CheerOrder.indexOf(index)
    val cheering = rank in 0 until CheerCount[cheer]
    return when (phase) {
        StagePhase.Idle -> if (m.main) Face(Eyes.Open, Mouth.Smile)
        else listOf(Face(Eyes.Open, Mouth.Small), Face(Eyes.Open, Mouth.Smile), Face(Eyes.Sleepy, Mouth.Small))[index % 3]
        StagePhase.Paused -> if (m.main) Face(Eyes.Wide, Mouth.Oh)
        else listOf(Face(Eyes.Wide, Mouth.Oh), Face(Eyes.Open, Mouth.Wobbly), Face(Eyes.Wide, Mouth.Small))[index % 3]
        StagePhase.Finished -> if (m.main) Face(Eyes.Stars, Mouth.Laugh)
        else if (index % 2 == 0) Face(Eyes.Happy, Mouth.Laugh) else Face(Eyes.Hearts, Mouth.Grin)
        StagePhase.Running -> when {
            m.main -> if (cheer >= 3) Face(Eyes.Happy, Mouth.Laugh) else Face(Eyes.Happy, Mouth.Grin)
            cheering -> if (index % 2 == 0) Face(Eyes.Happy, Mouth.Grin) else Face(Eyes.Wink, Mouth.Grin)
            else -> Face(Eyes.Open, Mouth.Smile)
        }
    }
}

/** The misty ledge the crowd stands on: a soft band of light. */
private fun DrawScope.drawGround(w: Float, h: Float) {
    val c = Offset(w * 0.5f, h * 0.805f)
    scale(1f, 0.1f, pivot = c) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.92f), 0.55f to Color(0xFFF6EAF8).copy(alpha = 0.7f), 1f to Color.Transparent,
                center = c, radius = w * 0.52f
            ),
            radius = w * 0.52f, center = c
        )
    }
}

/** "Almost there!" with its curling arrow and a beating heart. */
private fun DrawScope.drawCallout(measurer: TextMeasurer, text: String, w: Float, h: Float, t: Float) {
    val anchor = Offset(w * 0.785f, h * 0.135f)
    val style = TextStyle(fontFamily = HandFont, fontSize = (w * 0.047f).toSp(), color = Sky.Hand)
    val laid = measurer.measure(text, style)
    rotate(-12f, pivot = anchor) {
        drawText(laid, topLeft = Offset(anchor.x - laid.size.width / 2f, anchor.y - laid.size.height / 2f))
    }
    val from = Offset(anchor.x - w * 0.075f, anchor.y + h * 0.17f)
    val to = Offset(anchor.x - w * 0.175f, anchor.y + h * 0.46f)
    val arrow = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x + w * 0.01f, from.y + h * 0.14f, to.x + w * 0.03f, to.y - h * 0.10f, to.x, to.y)
    }
    drawPath(arrow, Sky.Hand.copy(alpha = 0.85f), style = Stroke(width = w * 0.007f, cap = StrokeCap.Round))
    drawLine(Sky.Hand.copy(alpha = 0.85f), to, Offset(to.x + w * 0.004f, to.y - h * 0.075f), strokeWidth = w * 0.007f, cap = StrokeCap.Round)
    drawLine(Sky.Hand.copy(alpha = 0.85f), to, Offset(to.x + w * 0.052f, to.y - h * 0.012f), strokeWidth = w * 0.007f, cap = StrokeCap.Round)
    val beat = 1f + 0.10f * sin(t * 4f).coerceAtLeast(0f)
    heartShape(Offset(anchor.x + w * 0.020f, anchor.y + h * 0.175f), w * 0.021f * beat, Sky.Hand.copy(alpha = 0.9f))
}
