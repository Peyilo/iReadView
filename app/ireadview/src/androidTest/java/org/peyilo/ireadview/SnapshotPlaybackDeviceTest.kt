package org.peyilo.ireadview

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.internal.effect.PageTurnContentPlayback
import org.peyilo.ireadview.spi.presentation.PreparedRead
import org.peyilo.ireadview.spi.presentation.ReadVisualHandoff

@RunWith(AndroidJUnit4::class)
class SnapshotPlaybackDeviceTest {
    @Test
    fun ordinaryContentRestoresOriginalAlpha() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val view = View(instrument.targetContext).apply { alpha = .7f }
            val playback = PageTurnContentPlayback(view, null)
            playback.setActive(true)
            playback.setActive(true)
            assertEquals(0f, view.alpha, 0f)
            playback.setActive(false)
            playback.setActive(false)
            assertEquals(.7f, view.alpha, .001f)
        }
    }

    @Test
    fun cooperativeContentOwnsSuppressionAndRecoversEvenWhenStartThrows() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val view = View(instrument.targetContext)
            val events = mutableListOf<Boolean>()
            var fail = false
            val content =
                object : ReadVisualHandoff<String> {
                    override fun setSnapshotPlayback(active: Boolean): Boolean {
                        events += active
                        if (active && fail) error("injected failure")
                        return true
                    }

                    override suspend fun prepareVisualHandoff(candidate: PreparedRead<String>) =
                        Unit
                }
            val playback = PageTurnContentPlayback(view, content)
            playback.setActive(true)
            playback.setActive(true)
            assertEquals(1f, view.alpha, 0f)
            playback.setActive(false)
            fail = true
            assertThrows(IllegalStateException::class.java) { playback.setActive(true) }
            playback.setActive(false)
            assertEquals(listOf(true, false, true, false), events)
            assertEquals(1f, view.alpha, 0f)
        }
    }
}
