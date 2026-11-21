package com.kutlusoy.nearly

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The launcher icon, its round variant and the logo of the splash screen must exist and show the white pin in the middle.
// The unit tests use the debug build, so the picture shows the amber debug icon.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LauncherIconTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun render(id: Int, size: Int = 432): Bitmap {
        val drawable = assertNotNull(ContextCompat.getDrawable(context, id))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun launcherIconsAreAdaptiveAndShowThePin() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            assertTrue(ContextCompat.getDrawable(context, id) is AdaptiveIconDrawable)
            val bitmap = render(id)
            val center = bitmap.getPixel(216, 240)
            assertTrue(Color.red(center) > 240 && Color.green(center) > 240 && Color.blue(center) > 240, "the pin is white in the middle")
            val corner = bitmap.getPixel(70, 70)
            assertTrue(Color.alpha(corner) == 255 && !(Color.red(corner) > 240 && Color.green(corner) > 240 && Color.blue(corner) > 240), "the background is colored")
            File("build/screenshots").mkdirs()
            File("build/screenshots/launcher-${resources(id)}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun resources(id: Int) = context.resources.getResourceEntryName(id)

    @Test
    fun pinAndNotificationIconsAreThere() {
        for (id in listOf(R.drawable.ic_nearly_pin, R.drawable.ic_stat_nearly, R.drawable.ic_launcher_foreground)) {
            val bitmap = render(id, 96)
            var opaque = 0
            for (x in 0 until 96) for (y in 0 until 96) if (Color.alpha(bitmap.getPixel(x, y)) > 200) opaque++
            assertTrue(opaque > 300, "the pin has visible pixels")
        }
    }
}
