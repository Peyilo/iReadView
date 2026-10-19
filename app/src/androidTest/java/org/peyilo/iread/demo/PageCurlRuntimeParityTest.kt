package org.peyilo.iread.demo

import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.api.effect.PageCurlOptions

/** AGSL and GLES share the material equations; compare on a GPU with continuous-color pages. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=33)
class PageCurlRuntimeParityTest {
    @Test fun runtimeMaterialMatchesGlesOrientationAndLighting() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val width=160;val height=256
            val current=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val target=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            for(y in 0 until height)for(x in 0 until width) {
                current.setPixel(x,y,Color.rgb(50+x,60+y/2,90))
                target.setPixel(x,y,Color.rgb(190,50+x/2,50+y/2))
            }
            val old=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val glClass=Class.forName("org.peyilo.ireadview.internal.effect.BoundPageCurlRenderer")
            val gl=glClass.getConstructor().newInstance()
            glClass.getMethod("upload",Bitmap::class.java,Bitmap::class.java).invoke(gl,current,target)
            glClass.getMethod("bindOutput",Bitmap::class.java).invoke(gl,old)
            val gpuClass=Class.forName("org.peyilo.ireadview.internal.effect.BoundPageCurlRuntime")
            val gpu=gpuClass.getConstructor().newInstance()
            gpuClass.getMethod("bind",Bitmap::class.java,Bitmap::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).invoke(gpu,current,target,width,height)
            val floats=arrayOf(Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType)
            val render=glClass.getMethod("render",*floats,Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,PageCurlOptions::class.java,Int::class.javaPrimitiveType)
            val update=gpuClass.getMethod("update",*floats,Boolean::class.javaPrimitiveType,PageCurlOptions::class.java,Int::class.javaPrimitiveType)
            val draw=gpuClass.getMethod("draw",Canvas::class.java)
            val reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN)
            val node=RenderNode("runtime-parity").apply { setPosition(0,0,width,height) }
            val renderer=HardwareRenderer().apply { setSurface(reader.surface);setContentRoot(node) }
            try {
                val a=IntArray(width*height)
                for(tilt in listOf(-.55f,0f,.55f))for(progress in listOf(.1f,.5f,.9f))for(rtl in listOf(false,true)) {
                    val options=PageCurlOptions()
                    render.invoke(gl,progress,width.toFloat()/height,tilt,rtl,true,options,Color.WHITE)
                    update.invoke(gpu,progress,width.toFloat()/height,tilt,rtl,options,Color.WHITE)
                    val canvas=node.beginRecording(width,height)
                    draw.invoke(gpu,canvas);node.endRecording()
                    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                    var image=reader.acquireLatestImage()
                    val until=SystemClock.uptimeMillis()+3000
                    while(image==null && SystemClock.uptimeMillis()<until) { SystemClock.sleep(5);image=reader.acquireLatestImage() }
                    requireNotNull(image).use { captured ->
                        old.getPixels(a,0,width,0,0,width,height)
                        val plane=captured.planes[0];val buffer=plane.buffer
                        var deltaTotal=0L;var severe=0
                        for(y in 0 until height)for(x in 0 until width) {
                            val source=a[(height-1-y)*width+x]
                            val at=y*plane.rowStride+x*plane.pixelStride
                            for(channel in 0..2) {
                                val expected=source ushr (16-8*channel) and 255
                                val actual=buffer.get(at+channel).toInt() and 255
                                val delta=kotlin.math.abs(expected-actual)
                                deltaTotal+=delta;if(delta>12)severe++
                            }
                        }
                        val mean=deltaTotal.toDouble()/(width*height*3)
                        assertTrue("tilt=$tilt p=$progress rtl=$rtl mean=$mean severe=$severe",mean<2.0 && severe<width*height*.02)
                    }
                }
            } finally {
                renderer.destroy();reader.close();node.discardDisplayList()
                gpuClass.getMethod("clearPages").invoke(gpu);glClass.getMethod("close").invoke(gl)
                current.recycle();target.recycle();old.recycle()
            }
        }
    }
}
