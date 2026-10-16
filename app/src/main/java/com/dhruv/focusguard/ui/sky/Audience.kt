package com.dhruv.focusguard.ui.sky

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.dhruv.focusguard.R
import com.dhruv.focusguard.guard.Haptics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class StagePhase { Idle, Running, Paused, Finished }

val HandFont = FontFamily(Font(R.font.patrick_hand))

// The stage covers mockup y 1095..1365 across the full width.
const val STAGE_TOP = 1095
const val STAGE_H = 270

private class Member(
    val cx: Float, val cy: Float, val rx: Float, val ry: Float,
    val ref: Face,
    val main: Boolean = false,
    val back: Boolean = false,
    val seed: Int
) {
    val pose = BubblePose()
    var lift = 0f; var liftV = 0f
    var sq = 0f; var sqV = 0f
    var tilt = 0f; var tiltV = 0f
    var nextHop = 2f + seed * 0.37f
    var nextBlink = 1f + seed * 0.61f
    var blinkAt = -10f
    var reactUntil = -1f
    var lookAt: Offset? = null
    var lookUntil = -1f
    var queuedHopAt = -1f
    var queuedPower = 0f
}

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val born: Float, val life: Float, val kind: Int, val color: Color, val size: Float,
    var rot: Float, val spin: Float
)

private const val HEART = 0
private const val SPARK = 1
private const val CONFETTI = 2

private val ConfettiColors = listOf(
    Color(0xFFB79CFF), Color(0xFFF7A8D8), Color(0xFF9FC1FE), Color(0xFFFFD27A), Color(0xFF8EE0C8)
)

/** The mockup's line-up, in mockup pixels. Faces are the ones the mockup shows. */
private fun lineUp(): List<Member> = listOf(
    Member(318f, 1262f, 27f, 25f, Face(Eyes.Open, Mouth.Small), back = true, seed = 7),
    Member(172f, 1301f, 39f, 34f, Face(Eyes.Open, Mouth.Small), seed = 1),
    Member(256f, 1284f, 51f, 46f, Face(Eyes.Wink, Mouth.Grin), seed = 2),
    Member(362f, 1286f, 50f, 45f, Face(Eyes.Open, Mouth.Grin), seed = 3),
    Member(625f, 1292f, 46f, 42f, Face(Eyes.Happy, Mouth.Smile), seed = 4),
    Member(705f, 1290f, 41f, 39f, Face(Eyes.Happy, Mouth.Small), seed = 5),
    Member(781f, 1300f, 44f, 39f, Face(Eyes.Happy, Mouth.Small), seed = 6),
    Member(501f, 1253f, 85f, 75f, Face(Eyes.Happy, Mouth.Laugh), main = true, seed = 0)
)

/** Order in which the crowd joins in as you make progress. */
private val CheerOrder = intArrayOf(3, 4, 2, 5, 1, 6)
private val CheerCount = intArrayOf(0, 1, 2, 4, 5, 6)

/** What the little note above the crowd says. */
fun calloutFor(phase: StagePhase, progress: Float): String = when (phase) {
    StagePhase.Idle -> "Ready when you are!"
    StagePhase.Paused -> "We'll wait for you"
    StagePhase.Finished -> "You did it!"
    StagePhase.Running -> when {
        progress >= 0.75f -> "Almost there!"
        progress >= 0.5f -> "Halfway there!"
        progress >= 0.25f -> "You're doing great!"
        else -> "Let's go!"
    }
}

/**
 * The audience: a row of jelly bubbles who watch you work.
 *
 *  - More of them cheer (happy eyes, hopping) as [cheer] rises from 0 to 5.
 *  - Each time [wave] changes they do a stadium wave (milestones).
 *  - Paused: they get anxious. Finished: they go wild with confetti.
 *  - Tap one: it giggles and throws hearts, and its neighbours look over.
 *    Tap the big one: the whole crowd waves. Rub along the row: each one
 *    you pass bounces under your finger.
 *
 * Laid out in mockup pixels; place it at [STAGE_TOP] with [STAGE_H] height.
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
    val frame = remember { mutableLongStateOf(0L) }
    var time by remember { mutableStateOf(0f) }
    var override by remember { mutableStateOf<String?>(null) }
    var overrideUntil by remember { mutableStateOf(-1f) }
    val phaseNow by rememberUpdatedState(phase)
    val cheerNow by rememberUpdatedState(cheer.coerceIn(0, 5))
    var finishedAt by remember { mutableStateOf(-1f) }

    fun hop(m: Member, power: Float) {
        m.liftV = -power
        m.sqV = -3.5f * power / 2f
        m.tiltV += (Random.nextFloat() - 0.5f) * 40f
    }

    fun burst(m: Member, n: Int, kind: Int) {
        repeat(n) { i ->
            val a = (-90f + (i - n / 2f) * (120f / n.coerceAtLeast(1))) * (Math.PI / 180f).toFloat()
            particles += Particle(
                m.cx, m.cy - m.ry * 0.9f,
                cos(a) * (60f + Random.nextFloat() * 50f), sin(a) * (90f + Random.nextFloat() * 60f),
                time, 1.2f + Random.nextFloat() * 0.5f, kind,
                if (kind == HEART) (if (i % 2 == 0) Color(0xFFF07AAE) else Color(0xFFB59BFF)) else Color.White,
                if (kind == HEART) 9f + Random.nextFloat() * 5f else 6f + Random.nextFloat() * 5f,
                0f, 0f
            )
        }
    }

    fun doWave(power: Float) {
        members.filter { !it.back }.sortedBy { it.cx }.forEachIndexed { i, m ->
            m.queuedHopAt = time + i * 0.09f
            m.queuedPower = power
        }
    }

    // Stadium wave on each milestone
    LaunchedEffect(wave) {
        if (wave > 0) {
            doWave(2.2f)
            haptics?.tick()
        }
    }

    // Finishing: everyone goes wild
    LaunchedEffect(phase) {
        if (phase == StagePhase.Finished) {
            finishedAt = time
            doWave(2.8f)
            members.forEach { if (!it.back) burst(it, 4, HEART) }
        }
    }

    // One clock drives the whole crowd. Physics and particles live in plain
    // fields; only `frame` is state, read in the Canvas, so this repaints
    // without recomposing.
    LaunchedEffect(Unit) {
        if (!SkyMotion.enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                time += dt
                val t = time
                val ph = phaseNow
                val cheering = CheerCount[cheerNow]
                for ((index, m) in members.withIndex()) {
                    // queued hops (waves)
                    if (m.queuedHopAt in 0f..t) {
                        hop(m, m.queuedPower)
                        if (!m.back) particles += Particle(m.cx, m.cy - m.ry * 1.1f, 0f, -40f, t, 0.9f, SPARK, Color.White, 8f, 0f, 0f)
                        m.queuedHopAt = -1f
                    }
                    // spontaneous hops
                    val rank = CheerOrder.indexOf(index)
                    val isCheering = (m.main && ph == StagePhase.Running && cheerNow >= 2) ||
                        (rank in 0 until cheering && ph == StagePhase.Running) || ph == StagePhase.Finished
                    if (t >= m.nextHop) {
                        when {
                            ph == StagePhase.Finished && t - finishedAt < 6f -> {
                                hop(m, 2.0f + Random.nextFloat() * 0.8f)
                                if (Random.nextFloat() < 0.4f) burst(m, 2, HEART)
                                m.nextHop = t + 0.7f + Random.nextFloat() * 0.6f
                            }
                            isCheering -> {
                                hop(m, 1.3f + Random.nextFloat() * 0.6f)
                                if (Random.nextFloat() < 0.25f) burst(m, 1, HEART)
                                m.nextHop = t + (3.2f - cheerNow * 0.35f) + Random.nextFloat() * 2f
                            }
                            else -> m.nextHop = t + 1.5f + Random.nextFloat() * 2f
                        }
                    }
                    // springs
                    m.liftV += (-m.lift * 150f - m.liftV * 8f) * dt
                    m.lift += m.liftV * dt
                    m.sqV += (-m.sq * 260f - m.sqV * 11f) * dt
                    m.sq += m.sqV * dt
                    m.tiltV += (-m.tilt * 120f - m.tiltV * 9f) * dt
                    m.tilt += m.tiltV * dt
                    // blink
                    if (t >= m.nextBlink) {
                        m.blinkAt = t
                        m.nextBlink = t + 2.2f + Random.nextFloat() * 3.5f
                    }
                    val bt = (t - m.blinkAt) / 0.16f
                    m.pose.eyeOpen = if (bt in 0f..1f) abs(bt * 2f - 1f) else 1f
                    // pose
                    val bob = sin(t * 1.9f + m.seed * 1.3f) * 0.025f
                    val nervous = if (ph == StagePhase.Paused) sin(t * 23f + m.seed) * 0.6f else 0f
                    m.pose.lift = m.lift + bob
                    m.pose.squashX = 1f + m.sq * 0.45f
                    m.pose.squashY = 1f - m.sq * 0.6f
                    m.pose.tilt = m.tilt + nervous
                    m.pose.leafSway = sin(t * 1.3f) * 0.5f + m.tiltV * 0.004f
                    m.pose.glow = if (isCheering) 0.35f else 0f
                    val target = m.lookAt
                    m.pose.look = if (target != null && t < m.lookUntil) {
                        val d = Offset(target.x - m.cx, target.y - m.cy)
                        val len = hypot(d.x, d.y).coerceAtLeast(1f)
                        Offset(d.x / len, d.y / len)
                    } else when (ph) {
                        StagePhase.Paused -> Offset(0f, 0.2f)
                        StagePhase.Finished -> Offset.Zero
                        else -> Offset((512f - m.cx) / 700f, -0.9f)  // up at the timer
                    }
                }
                // particles
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    if (t - p.born > p.life) { it.remove(); continue }
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += (if (p.kind == CONFETTI) 140f else 60f) * dt
                    p.vx *= 0.985f
                    p.rot += p.spin * dt
                }
                // confetti while celebrating
                if (ph == StagePhase.Finished && t - finishedAt < 4f && Random.nextFloat() < 0.6f) {
                    particles += Particle(
                        100f + Random.nextFloat() * 824f, 1110f, (Random.nextFloat() - 0.5f) * 60f, 30f + Random.nextFloat() * 60f,
                        t, 2.4f, CONFETTI, ConfettiColors[Random.nextInt(ConfettiColors.size)], 7f + Random.nextFloat() * 5f,
                        Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 600f
                    )
                }
                if (override != null && t > overrideUntil) override = null
                frame.longValue = now
            }
        }
    }

    Box(modifier.size(1024.mp, STAGE_H.mp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun hit(p: Offset): Member? {
                        val mx = p.x / (MP * density)
                        val my = p.y / (MP * density) + STAGE_TOP
                        return members.filter { !it.back }.minByOrNull {
                            val dx = (mx - it.cx) / it.rx
                            val dy = (my - it.cy) / it.ry
                            dx * dx + dy * dy
                        }?.takeIf {
                            val dx = (mx - it.cx) / it.rx
                            val dy = (my - it.cy) / it.ry
                            dx * dx + dy * dy < 1.6f
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var current = hit(down.position)
                        current?.let { m ->
                            haptics?.click()
                            hop(m, if (m.main) 2.0f else 1.8f)
                            burst(m, if (m.main) 6 else 4, HEART)
                            m.reactUntil = time + 1.3f
                            members.forEach { o ->
                                if (o !== m) { o.lookAt = Offset(m.cx, m.cy); o.lookUntil = time + 1.2f }
                            }
                            if (m.main) {
                                doWave(1.9f)
                                override = listOf("Yay!", "Woohoo!", "We love it!", "Go go go!")[Random.nextInt(4)]
                                overrideUntil = time + 1.8f
                            }
                        }
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val now = hit(ch.position)
                            if (now != null && now !== current) {
                                haptics?.tick()
                                hop(now, 1.2f)
                                now.reactUntil = time + 0.8f
                                if (Random.nextFloat() < 0.5f) burst(now, 1, SPARK)
                            }
                            current = now
                            ch.consume()
                        }
                    }
                }
        ) {
            frame.longValue // repaint every frame
            val oy = STAGE_TOP
            fun p(x: Float, y: Float) = Offset(mpx(x), mpx(y - oy))
            drawGround(oy)

            // Arrow + heart under the note
            val arrow = Path().apply {
                moveTo(mpx(641), mpx(1176 - oy))
                cubicTo(mpx(643), mpx(1196 - oy), mpx(632), mpx(1213 - oy), mpx(614), mpx(1222 - oy))
            }
            drawPath(arrow, Sky.Hand.copy(alpha = 0.85f), style = Stroke(width = mpx(3.2f), cap = StrokeCap.Round))
            val tip = p(614f, 1222f)
            drawLine(Sky.Hand.copy(alpha = 0.85f), tip, p(615f, 1206f), strokeWidth = mpx(3.2f), cap = StrokeCap.Round)
            drawLine(Sky.Hand.copy(alpha = 0.85f), tip, p(629f, 1220f), strokeWidth = mpx(3.2f), cap = StrokeCap.Round)
            val beat = 1f + 0.08f * sin(time * 4f).coerceAtLeast(0f)
            heartShape(p(676f, 1167f), mpx(11f) * beat, Sky.Hand.copy(alpha = 0.9f))

            val ordered = members.sortedWith(compareBy({ !it.back }, { it.main }))
            for ((i, m) in ordered.withIndex()) {
                val index = members.indexOf(m)
                val face = when {
                    referenceFaces -> m.ref
                    time < m.reactUntil -> Face(Eyes.Happy, Mouth.Laugh)
                    else -> faceFor(m, index, phase, cheer.coerceIn(0, 5))
                }
                val layout = if (m.main) FaceLayout(eyeDx = 0.27f, eyeDy = -0.25f, eyeSize = 1.05f, mouthDy = 0.06f, blushDx = 0.48f, blushDy = 0.07f)
                else FaceLayout(eyeDx = 0.36f, eyeDy = -0.20f, eyeSize = 1.05f, mouthDy = 0.0f, blushDx = 0.46f, blushDy = 0.0f)
                drawJelly(
                    c = p(m.cx, m.cy),
                    rx = mpx(m.rx), ry = mpx(m.ry),
                    face = face,
                    pose = m.pose,
                    layout = layout,
                    sprouts = if (m.main) Sprouts.LeafPairs else Sprouts.None,
                    warmth = if (m.back) 0.2f else (m.seed % 3) * 0.15f,
                    time = time + i,
                    brightness = if (m.main) 0.6f else 0.2f,
                    feet = !m.back
                )
            }

            for (pt in particles) {
                val age = (time - pt.born) / pt.life
                val a = (1f - age).coerceIn(0f, 1f)
                val c = p(pt.x, pt.y)
                when (pt.kind) {
                    HEART -> heartShape(c, mpx(pt.size) * (0.7f + age * 0.5f), pt.color.copy(alpha = a))
                    SPARK -> sparkle(c, mpx(pt.size) * (0.6f + age), pt.color.copy(alpha = a))
                    else -> rotate(pt.rot, pivot = c) {
                        drawRect(pt.color.copy(alpha = a), topLeft = Offset(c.x - mpx(pt.size) / 2, c.y - mpx(pt.size) / 4), size = Size(mpx(pt.size), mpx(pt.size) / 2))
                    }
                }
            }
        }

        // The handwritten note
        val text = override ?: callout
        AnimatedContent(
            targetState = text,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.centerAt(669, 1127 - STAGE_TOP).rotate(-13f),
            label = "callout"
        ) { s ->
            Text(s, style = TextStyle(fontFamily = HandFont, fontSize = 27.ms, color = Sky.Hand))
        }
    }
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

/** The misty ledge the crowd stands on. */
private fun DrawScope.drawGround(oy: Int) {
    val c = Offset(mpx(500), mpx(1338 - oy))
    drawOval(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0xFFF3E6F7), Color(0xFFE7D6F1).copy(alpha = 0.7f), Color.Transparent),
            center = c, radius = mpx(430)
        ),
        topLeft = Offset(c.x - mpx(430), c.y - mpx(26)), size = Size(mpx(860), mpx(52))
    )
    // A few stones and grass tufts at the edges, as in the painting
    val stone = Color(0xFF8E8AAE)
    for ((x, w) in listOf(150f to 70f, 690f to 90f, 790f to 60f)) {
        drawOval(stone.copy(alpha = 0.55f), topLeft = Offset(mpx(x), mpx(1338 - oy)), size = Size(mpx(w), mpx(14)))
    }
    val grass = Color(0xFF7FA39A)
    for (x in listOf(338f, 352f, 668f, 683f, 740f)) {
        drawLine(grass.copy(alpha = 0.7f), Offset(mpx(x), mpx(1342 - oy)), Offset(mpx(x - 4), mpx(1328 - oy)), strokeWidth = mpx(2.5f), cap = StrokeCap.Round)
        drawLine(grass.copy(alpha = 0.7f), Offset(mpx(x + 4), mpx(1342 - oy)), Offset(mpx(x + 9), mpx(1330 - oy)), strokeWidth = mpx(2.5f), cap = StrokeCap.Round)
    }
}
