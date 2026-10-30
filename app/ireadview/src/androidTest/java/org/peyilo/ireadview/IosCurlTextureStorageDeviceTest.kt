package org.peyilo.ireadview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlRenderer
import org.peyilo.pagecurl.mesh.CurlMesh
import org.peyilo.pagecurl.mesh.CurlMeshInput
import org.peyilo.pagecurl.model.CurlValues

@RunWith(AndroidJUnit4::class)
class IosCurlTextureStorageDeviceTest {
    @Test
    fun changedPixelsDimensionsAndFormatsMatchFreshAllocations() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            IosCurlRenderer(retainContext = true).use { reused ->
                for ((step, config) in
                    listOf(
                            Bitmap.Config.ARGB_8888,
                            Bitmap.Config.ARGB_8888,
                            Bitmap.Config.RGB_565,
                            Bitmap.Config.RGB_565,
                            Bitmap.Config.ARGB_8888,
                        )
                        .withIndex()) {
                    val width = if (step < 3) 160 else 96
                    val height = width * 3 / 2
                    val current = Bitmap.createBitmap(width, height, config)
                    val target = Bitmap.createBitmap(width, height, config)
                    val actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        current.eraseColor(if (step % 2 == 0) Color.RED else Color.BLUE)
                        Canvas(current)
                            .drawRect(
                                0f,
                                0f,
                                width / 2f,
                                height / 2f,
                                Paint().apply { color = Color.WHITE },
                            )
                        target.eraseColor(if (step % 2 == 0) Color.GREEN else Color.YELLOW)
                        val frame =
                            CurlMesh()
                                .build(
                                    CurlMeshInput(
                                        CurlValues(.5, 20.0, .45),
                                        width.toDouble(),
                                        height.toDouble(),
                                    )
                                )
                        reused.upload(current, target)
                        reused.bindOutput(actual)
                        reused.render(frame, width.toFloat(), height.toFloat(), 0, 1, emptyList())
                        IosCurlRenderer().use { fresh ->
                            fresh.upload(current, target)
                            fresh.bindOutput(expected)
                            fresh.render(
                                frame,
                                width.toFloat(),
                                height.toFloat(),
                                0,
                                1,
                                emptyList(),
                            )
                        }
                        assertTrue("stale pixels/storage at step $step", expected.sameAs(actual))
                    } finally {
                        current.recycle()
                        target.recycle()
                        actual.recycle()
                        expected.recycle()
                    }
                }
            }
        }
    }
}
