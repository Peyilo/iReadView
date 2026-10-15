package org.peyilo.ireadview

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.ireadview.internal.effect.*

@RunWith(AndroidJUnit4::class)
class IBookShadowClipDeviceTest {
    @Test fun actualCanvasClipRejectsShadowOutsideProjectedPaper() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"ibook-shadow-check").also{it.mkdirs()}
        var oldLeaks=0;var newLeaks=0;var retained=0;var poses=0
        val shadow=IBookCurlShadow()
        for(origin in listOf(0f,100f,400f)) for(progress in listOf(.2f,.4f,.6f,.8f)) for(dy in listOf(-160f,160f)) {
            val g=IBookCurlGeometry(200f,400f,200*(1-2*progress),origin+dy,origin,.08f)
            val front=IBookCurlMesh(64);val back=IBookCurlMesh(64)
            front.update(g,200f,400f,200,400,false);back.update(g,200f,400f,200,400,true)
            val fp=Path();val bp=Path();front.outline(fp);back.outline(bp)
            val footprint=IBookShadowFootprint();val support=Path()
            footprint.addMesh(g,front,200f,400f,200,400);footprint.addMesh(g,back,200f,400f,200,400);footprint.outline(support)
            shadow.update(g,200f,400f,20f,.4f,1f)
            val before=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
            val after=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
            val mask=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
            val oldCanvas=Canvas(before);oldCanvas.clipOutPath(fp);oldCanvas.clipOutPath(bp);shadow.draw(oldCanvas,200f,400f)
            val newCanvas=Canvas(after);clipIBookNextShadow(newCanvas,support,fp,bp,RectF(0f,0f,200f,400f));shadow.draw(newCanvas,200f,400f)
            Canvas(mask).drawPath(support,Paint().apply{color=Color.WHITE})
            val a=IntArray(80000);val b=IntArray(80000);val m=IntArray(80000)
            before.getPixels(a,0,200,0,0,200,400);after.getPixels(b,0,200,0,0,200,400);mask.getPixels(m,0,200,0,0,200,400)
            for(i in a.indices){
                // Ignore a one-pixel silhouette boundary to allow raster rounding.
                val x=i%200;val y=i/200
                var nearSupport=false
                for(oy in -1..1)for(ox in -1..1){val xx=x+ox;val yy=y+oy
                    if(xx in 0..199&&yy in 0..399&&(m[yy*200+xx] ushr 24)>0)nearSupport=true}
                if((a[i] ushr 24)>0&&!nearSupport)oldLeaks++
                if((b[i] ushr 24)>0&&!nearSupport)newLeaks++
                if((b[i] ushr 24)>0)retained++
            }
            if(origin==0f&&progress==.6f&&dy==160f){
                File(folder,"before.png").outputStream().use{before.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(folder,"after.png").outputStream().use{after.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(folder,"support.png").outputStream().use{mask.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            before.recycle();after.recycle();mask.recycle();poses++
        }
        shadow.release()
        val result="poses=$poses oldOutsidePixels=$oldLeaks newOutsidePixels=$newLeaks retainedShadowPixels=$retained"
        File(folder,"result.txt").writeText(result)
        assertTrue(result,oldLeaks>0);assertEquals(result,0,newLeaks);assertTrue(result,retained>0)
    }
}
