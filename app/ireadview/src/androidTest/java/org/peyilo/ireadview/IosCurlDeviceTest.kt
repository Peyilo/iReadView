package org.peyilo.ireadview

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.PageTurnDynamics
import org.peyilo.ireadview.spi.presentation.ReadViewport
import java.io.File

@RunWith(AndroidJUnit4::class)
class IosCurlDeviceTest {
    @Test fun recoveredMeshShadersAndLeaseCleanupAcrossAllSingleBindings() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val directory=File(instrument.targetContext.cacheDir,"ios-curl-kotlin").apply{mkdirs()}
            val width=320;val height=458
            fun surface(target: Boolean)=object:PageSurface {
                override val widthPx=width;override val heightPx=height
                override fun draw(canvas:Canvas){
                    canvas.drawColor(if(target)0xff183ecc.toInt() else 0xffffd06a.toInt())
                    if(!target){val p=Paint().apply{color=0xffd82d36.toInt()};canvas.drawRect(0f,0f,width*.5f,height.toFloat(),p)
                        p.color=Color.BLACK;p.textSize=28f;canvas.drawText("CURRENT 01",24f,75f,p);p.textSize=18f;canvas.drawText("MIRROR THIS SHEET",24f,240f,p)}
                }
            }
            var leases=0
            val host=object:PageTurnEffectHost {
                override fun requestRedraw()=Unit
                override fun borrowBitmap(widthPx:Int,heightPx:Int):BitmapLease {
                    leases++;return object:BitmapLease {
                        override val bitmap=Bitmap.createBitmap(widthPx,heightPx,Bitmap.Config.ARGB_8888)
                        private var closed=false
                        override fun close(){if(!closed){closed=true;leases--;bitmap.recycle()}}
                    }
                }
            }
            var count=0
            for(binding in IosCurlBinding.entries)for(vertical in listOf(false,true))for(rtl in listOf(false,true))for(direction in TurnDirection.entries){
                val effect=PageTurnEffects.iosCurl(IosCurlOptions(binding)).create()
                val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                val origin=TurnPoint(if(direction==TurnDirection.Forward)304f else 16f,110f)
                try{
                    count++
                    effect.attach(host);effect.start(PageTurnContext(count.toLong(),surface(false),surface(true),ReadViewport(width,height,1f,1f,rtl),direction,origin,vertical))
                    val frame=PageTurnFrame(.5f,TurnPhase.Dragging,TurnPoint(if(direction==TurnDirection.Forward)100f else 220f,310f),1.0)
                    effect.update(frame);effect.draw(Canvas(image),frame)
                    var opaque=0;var blue=0;var paper=0
                    for(y in 0 until height step 2)for(x in 0 until width step 2){val c=image.getPixel(x,y)
                        if(Color.alpha(c)>200)opaque++
                        if(Color.blue(c)>Color.red(c)+40)blue++
                        if(Color.red(c)>Color.blue(c)+40)paper++}
                    assertTrue("No blank output: $binding $vertical $rtl $direction",opaque>width*height/5)
                    assertTrue("Both actual page sources remain available",blue+paper>width*height/8)
                    File(directory,"$binding-v$vertical-rtl$rtl-$direction.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
                    val dynamics=effect as PageTurnDynamics
                    assertTrue(dynamics.beginSettling(true,TurnPoint(-900f,0f))>0)
                    dynamics.updateSettling(PageTurnFrame(1f,TurnPhase.SettlingCommit),1.0)
                    effect.finish(TurnOutcome.Committed);effect.abort(TurnAbortReason.Cancelled)
                }finally{effect.detach();image.recycle()}
                assertEquals("Every transaction releases snapshots/output",0,leases)
            }
            File(directory,"result.txt").writeText("16 single binding/direction/axis/RTL combinations rendered through Kotlin mesh + GLES2. All leases released. No iPhone framebuffer equality claim.")
        }
    }
}
