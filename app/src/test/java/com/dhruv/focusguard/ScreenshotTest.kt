package com.dhruv.focusguard

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens to PNG on the JVM. `mock` sizes match the 1024x1536 design
 * mockups (393dp wide at 417dpi = 1024px); `phone` is a real 393x873dp phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h590dp-417dpi")
class ScreenshotTest {

    @Test
    fun smoke() {
        captureRoboImage("build/screens/smoke.png") {
            Box(Modifier.fillMaxSize().background(Color(0xFF8E9FDC))) {
                Text(
                    "Complete DSA practice set",
                    fontFamily = FontFamily(Font(R.font.google_sans_medium)),
                    fontSize = 20.sp
                )
            }
        }
    }
}
