package com.dhruv.focusguard.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Line icons drawn to match the mockups (rounded 1.8px strokes on a 24 grid). */
object SkyIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d
        )

    /** An open book with lines of text on the right page. */
    val BookLines: ImageVector by lazy {
        icon("BookLines") {
            line {
                moveTo(12f, 6.2f); curveTo(10f, 4.9f, 7f, 4.6f, 3.4f, 5.1f); lineTo(3.4f, 18.4f)
                curveTo(7f, 17.9f, 10f, 18.2f, 12f, 19.5f)
                curveTo(14f, 18.2f, 17f, 17.9f, 20.6f, 18.4f); lineTo(20.6f, 5.1f)
                curveTo(17f, 4.6f, 14f, 4.9f, 12f, 6.2f); close()
            }
            line { moveTo(12f, 6.2f); lineTo(12f, 19.5f) }
            line { moveTo(14.6f, 9.2f); lineTo(18f, 9.2f) }
            line { moveTo(14.6f, 12.2f); lineTo(18f, 12.2f) }
            line { moveTo(14.6f, 15.2f); lineTo(18f, 15.2f) }
        }
    }

    /** A plain open book. */
    val OpenBook: ImageVector by lazy {
        icon("OpenBook") {
            line {
                moveTo(12f, 6.6f); curveTo(10f, 5.1f, 6.8f, 4.7f, 3.2f, 5.3f); lineTo(3.2f, 18.6f)
                curveTo(6.8f, 18f, 10f, 18.4f, 12f, 19.8f)
                curveTo(14f, 18.4f, 17.2f, 18f, 20.8f, 18.6f); lineTo(20.8f, 5.3f)
                curveTo(17.2f, 4.7f, 14f, 5.1f, 12f, 6.6f); close()
            }
            line { moveTo(12f, 6.6f); lineTo(12f, 19.8f) }
        }
    }

    /** A heart with a heartbeat line across it. */
    val HeartPulse: ImageVector by lazy {
        icon("HeartPulse") {
            line {
                moveTo(12f, 20f)
                curveTo(7.5f, 17f, 3.4f, 13.6f, 3.4f, 9.2f)
                curveTo(3.4f, 6.4f, 5.5f, 4.6f, 7.8f, 4.6f)
                curveTo(9.6f, 4.6f, 11.1f, 5.6f, 12f, 7f)
                curveTo(12.9f, 5.6f, 14.4f, 4.6f, 16.2f, 4.6f)
                curveTo(18.5f, 4.6f, 20.6f, 6.4f, 20.6f, 9.2f)
                curveTo(20.6f, 13.6f, 16.5f, 17f, 12f, 20f)
                close()
            }
            line {
                moveTo(4.2f, 12.2f); lineTo(8.4f, 12.2f); lineTo(9.8f, 9.8f); lineTo(11.8f, 14.8f)
                lineTo(13.6f, 10.6f); lineTo(14.8f, 12.2f); lineTo(19.8f, 12.2f)
            }
        }
    }
}

/** The little flame on the "High priority" chip: red outside, gold inside. */
@Composable
fun Flame(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outer = Path().apply {
            moveTo(w * 0.50f, h * 0.02f)
            cubicTo(w * 0.62f, h * 0.24f, w * 0.92f, h * 0.38f, w * 0.88f, h * 0.66f)
            cubicTo(w * 0.85f, h * 0.88f, w * 0.68f, h * 0.99f, w * 0.50f, h * 0.99f)
            cubicTo(w * 0.30f, h * 0.99f, w * 0.12f, h * 0.86f, w * 0.12f, h * 0.64f)
            cubicTo(w * 0.12f, h * 0.46f, w * 0.24f, h * 0.36f, w * 0.30f, h * 0.24f)
            cubicTo(w * 0.34f, h * 0.36f, w * 0.38f, h * 0.42f, w * 0.44f, h * 0.44f)
            cubicTo(w * 0.42f, h * 0.30f, w * 0.44f, h * 0.14f, w * 0.50f, h * 0.02f)
            close()
        }
        drawPath(outer, Brush.verticalGradient(listOf(Color(0xFFFF8A5B), Color(0xFFF2554A), Color(0xFFE5404A)), startY = 0f, endY = h))
        val inner = Path().apply {
            moveTo(w * 0.52f, h * 0.48f)
            cubicTo(w * 0.62f, h * 0.60f, w * 0.72f, h * 0.70f, w * 0.68f, h * 0.82f)
            cubicTo(w * 0.65f, h * 0.93f, w * 0.57f, h * 0.97f, w * 0.50f, h * 0.97f)
            cubicTo(w * 0.40f, h * 0.97f, w * 0.32f, h * 0.90f, w * 0.33f, h * 0.79f)
            cubicTo(w * 0.34f, h * 0.68f, w * 0.44f, h * 0.62f, w * 0.52f, h * 0.48f)
            close()
        }
        drawPath(inner, Brush.verticalGradient(listOf(Color(0xFFFFD36B), Color(0xFFFFB13D)), startY = h * 0.48f, endY = h))
    }
}
