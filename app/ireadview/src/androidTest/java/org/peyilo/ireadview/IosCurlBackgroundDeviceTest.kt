package org.peyilo.ireadview

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.ioscurl.IosCurlMaterial
import org.peyilo.ireadview.spi.presentation.ReadViewport
import org.peyilo.pagecurl.mesh.CurlMaterial

@RunWith(AndroidJUnit4::class)
class IosCurlBackgroundDeviceTest {
    private class Surface(private val color: Int, private val metadata: Boolean = true) : PageBackgroundSurface {
        override val widthPx = 160
        override val heightPx = 240
        var reads = 0
        override val backgroundColor: Int? get() { reads++; return color.takeIf { metadata } }
        override fun draw(canvas: Canvas) { canvas.drawColor(color) }
    }
    private class Host : PageTurnEffectHost {
        var leases = 0
        override fun requestRedraw() = Unit
        override fun borrowBitmap(widthPx: Int, heightPx: Int): BitmapLease {
            leases++
            return object : BitmapLease {
                override val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                private var closed = false
                override fun close() { if (!closed) { closed = true; leases--; bitmap.recycle() } }
            }
        }
    }
    @Test fun metadataAndDominantSnapshotSamplingRetainThemeRgbWithoutDoubleTint() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (color in listOf(Color.WHITE, 0xffeedfc1.toInt(), 0xff19292f.toInt())) {
                for (metadata in listOf(false, true)) {
                    val surface = Surface(color, metadata)
                    val bitmap = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
                    try {
                        surface.draw(Canvas(bitmap))
                        // Minority ink must not turn a light paper sample into grey.
                        Canvas(bitmap).drawRect(8f, 8f, 25f, 35f, Paint().apply { this.color = Color.BLACK })
                        val material = IosCurlMaterial.resolve(surface, bitmap)
                        assertEquals(Color.red(color) / 255.0, material.back.red, 0.0)
                        assertEquals(Color.green(color) / 255.0, material.back.green, 0.0)
                        assertEquals(Color.blue(color) / 255.0, material.back.blue, 0.0)
                        assertEquals(CurlMaterial().front, material.front)
                        assertEquals(CurlMaterial().backShade, material.backShade)
                        assertEquals(CurlMaterial().back.alpha, material.back.alpha, 0.0)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
    @Test fun sampleTheActualTurningFrontOnlyOnceForBothBindingsAxesAndRtl() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (binding in IosCurlBinding.entries) for (vertical in listOf(false, true))
                for (rtl in listOf(false, true)) for (direction in TurnDirection.entries) {
                    val current = Surface(0xff19292f.toInt())
                    val target = Surface(0xffead2a1.toInt())
                    val host = Host()
                    val effect = PageTurnEffects.iosCurl(IosCurlOptions(binding)).create()
                    try {
                        effect.attach(host)
                        effect.start(PageTurnContext(1, current, target, ReadViewport(160, 240, 1f, 1f, rtl),
                            direction, TurnPoint(145f, 60f), vertical))
                        effect.update(PageTurnFrame(.4f, TurnPhase.Dragging, TurnPoint(65f, 160f), .3))
                        effect.update(PageTurnFrame(.5f, TurnPhase.Dragging, TurnPoint(50f, 180f), .4))
                        val reverse = (direction == TurnDirection.Backward) != (!vertical && rtl)
                        val fromCurrent = if (binding == IosCurlBinding.Minimum) !reverse else reverse
                        assertEquals(if (fromCurrent) 1 else 0, current.reads)
                        assertEquals(if (fromCurrent) 0 else 1, target.reads)
                    } finally { effect.detach() }
                    assertEquals(0, host.leases)
                }
        }
    }
    @Test fun nightPaperBackDoesNotRenderAFixedWhitePanel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = Host()
            val effect = PageTurnEffects.iosCurl().create()
            val output = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
            try {
                effect.attach(host)
                effect.start(PageTurnContext(1, Surface(0xff19292f.toInt()), Surface(0xff20dd20.toInt()),
                    ReadViewport(160, 240, 1f, 1f), TurnDirection.Forward, TurnPoint(152f, 55f)))
                val frame = PageTurnFrame(.5f, TurnPhase.Dragging, TurnPoint(50f, 155f), 1.0)
                effect.update(frame)
                effect.draw(Canvas(output), frame)
                var white = 0
                for (y in 0 until 240) for (x in 0 until 160) {
                    val pixel = output.getPixel(x, y)
                    if (Color.red(pixel) > 180 && Color.green(pixel) > 180 && Color.blue(pixel) > 180) white++
                }
                assertEquals("Night paper must not acquire a white back", 0, white)
            } finally { effect.detach(); output.recycle() }
            assertEquals(0, host.leases)
        }
    }
}
