package org.peyilo.ireadview

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.EffectResourceUnavailable
import org.peyilo.ireadview.spi.presentation.ReadViewport

@RunWith(AndroidJUnit4::class)
class SnapshotIsolationDeviceTest {
    private class Surface(var color:Int):SnapshotPreferredSurface {
        override val widthPx=160
        override val heightPx=240
        override val preferSnapshot=true
        override fun draw(canvas:Canvas) { canvas.drawColor(color) }
    }
    private class Host(private val partialOnly:Boolean=false):PageTurnEffectHost {
        var leases=0
        var attempts=0
        override fun requestRedraw()=Unit
        override fun borrowBitmap(widthPx:Int,heightPx:Int):BitmapLease? {
            attempts++
            if(if(partialOnly) attempts%2==0 else widthPx>40 || heightPx>60)return null
            leases++
            return object:BitmapLease {
                override val bitmap=Bitmap.createBitmap(widthPx,heightPx,Bitmap.Config.ARGB_8888)
                private var closed=false
                override fun close() { if(!closed) { closed=true;leases--;bitmap.recycle() } }
            }
        }
    }
    private val factories get()=listOf(PageTurnEffects.slide(),PageTurnEffects.cover(),PageTurnEffects.iBookSlide())
    @Test fun boundedSnapshotsSurviveTheLiveCompositorBecomingTransparent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for(factory in factories) {
                val current=Surface(Color.RED);val target=Surface(Color.BLUE)
                val host=Host();val effect=factory.create()
                val output=Bitmap.createBitmap(160,240,Bitmap.Config.ARGB_8888)
                try {
                    effect.attach(host)
                    effect.start(PageTurnContext(1,current,target,ReadViewport(160,240,1f,1f),TurnDirection.Forward))
                    assertTrue((effect as SnapshotPageTurnEffect).usesPageSnapshots)
                    assertEquals(2,host.leases)
                    current.color=Color.TRANSPARENT;target.color=Color.TRANSPARENT
                    val frame=PageTurnFrame(.5f,TurnPhase.Dragging)
                    effect.draw(Canvas(output),frame)
                    var red=0;var blue=0
                    for(y in 0 until 240)for(x in 0 until 160) {
                        val pixel=output.getPixel(x,y)
                        if(Color.red(pixel)>150 && Color.blue(pixel)<80)red++
                        if(Color.blue(pixel)>150 && Color.red(pixel)<80)blue++
                    }
                    assertTrue("${factory.id}: turning page must remain opaque",red>10000)
                    assertTrue("${factory.id}: target page must remain opaque",blue>10000)
                } finally { effect.detach();output.recycle() }
                assertEquals(0,host.leases)
            }
        }
    }
    @Test fun failedIsolationReleasesPartialLeasesAndRequestsResourceFallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for(factory in factories) {
                val host=Host(partialOnly=true);val effect=factory.create()
                effect.attach(host)
                try {
                    effect.start(PageTurnContext(1,Surface(Color.RED),Surface(Color.BLUE),
                        ReadViewport(160,240,1f,1f),TurnDirection.Forward))
                    fail("${factory.id} must not silently reuse live compositor commands")
                } catch(_:EffectResourceUnavailable) {
                    assertEquals(0,host.leases)
                } finally { effect.abort(TurnAbortReason.Failed);effect.detach() }
                assertEquals(0,host.leases)
            }
        }
    }
}
