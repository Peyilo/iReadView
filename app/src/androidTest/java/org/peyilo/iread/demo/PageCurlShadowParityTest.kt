package org.peyilo.iread.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.PageCurlOptions

/** Render both shader variants with GLES, including tilted/RTL and backward poses. */
@RunWith(AndroidJUnit4::class)
class PageCurlShadowParityTest {
    @Test fun conservativeShadowCullPreservesRenderedPixels() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val shaderClass=Class.forName("org.peyilo.ireadview.internal.effect.BoundPageCurlShaderKt")
            val optimized=shaderClass.getDeclaredMethod("getBoundPageCurlFragment").invoke(null) as String
            val original=optimized.replace(Regex("/\\* iread-shadow-cull-start \\*/[\\s\\S]*?/\\* iread-shadow-cull-end \\*/"), "")
            assertNotEquals(original,optimized)
            val rendererClass=Class.forName("org.peyilo.ireadview.internal.effect.BoundPageCurlRenderer")
            val first=rendererClass.getConstructor(String::class.java).newInstance(original)
            val second=rendererClass.getConstructor(String::class.java).newInstance(optimized)
            val current=Bitmap.createBitmap(240,400,Bitmap.Config.ARGB_8888)
            val target=Bitmap.createBitmap(240,400,Bitmap.Config.ARGB_8888)
            val oldOutput=Bitmap.createBitmap(240,400,Bitmap.Config.ARGB_8888)
            val newOutput=Bitmap.createBitmap(240,400,Bitmap.Config.ARGB_8888)
            val paint=Paint().apply { color=Color.BLACK;textSize=15f }
            Canvas(current).apply { drawColor(Color.rgb(241,230,203));repeat(22) { drawText("Page text $it 示例",8f,20f+it*17,paint) } }
            Canvas(target).apply { drawColor(Color.rgb(215,235,219));repeat(20) { drawText("Next page $it",20f,20f+it*19,paint) } }
            val upload=rendererClass.getMethod("upload",Bitmap::class.java,Bitmap::class.java)
            val bind=rendererClass.getMethod("bindOutput",Bitmap::class.java)
            val render=rendererClass.getMethod("render",Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,PageCurlOptions::class.java,Int::class.javaPrimitiveType)
            try {
                upload.invoke(first,current,target);upload.invoke(second,current,target)
                bind.invoke(first,oldOutput);bind.invoke(second,newOutput)
                for (options in listOf(PageCurlOptions(),PageCurlOptions(radiusFraction=.04f,lightSize=.65f),PageCurlOptions(radiusFraction=.20f,lightSize=.02f)))
                for (tilt in listOf(-.55f,0f,.55f)) for (progress in listOf(.05f,.25f,.5f,.75f,.95f)) {
                    val rtl=progress>.5f;val forward=tilt>=0
                    render.invoke(first,progress,.6f,tilt,rtl,forward,options,Color.WHITE)
                    render.invoke(second,progress,.6f,tilt,rtl,forward,options,Color.WHITE)
                    val a=IntArray(240*400);val b=IntArray(a.size)
                    oldOutput.getPixels(a,0,240,0,0,240,400);newOutput.getPixels(b,0,240,0,0,240,400)
                    var maxDelta=0
                    for(i in a.indices) for(channel in 0..3) {
                        val shift=channel*8
                        maxDelta=maxOf(maxDelta,kotlin.math.abs((a[i] ushr shift and 255)-(b[i] ushr shift and 255)))
                    }
                    assertTrue("tilt=$tilt p=$progress delta=$maxDelta",maxDelta<=2)
                }
            } finally {
                rendererClass.getMethod("close").invoke(first);rendererClass.getMethod("close").invoke(second)
                current.recycle();target.recycle();oldOutput.recycle();newOutput.recycle()
            }
        }
    }
}
