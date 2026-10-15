package org.peyilo.ireadview

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.internal.effect.*
import kotlin.math.*

class IBookShadowFootprintTest {
    private class Region {
        private val triangles=ArrayList<DoubleArray>()
        private var triangle=DoubleArray(6)
        private var offset=0
        fun moveTo(x:Double,y:Double){triangle=DoubleArray(6);offset=0;lineTo(x,y)}
        fun lineTo(x:Double,y:Double){triangle[offset++]=x;triangle[offset++]=y}
        fun closePath(){triangles.add(triangle)}
        fun contains(x:Double,y:Double):Boolean {
            for(t in triangles){
                if(x<minOf(t[0],t[2],t[4])||x>maxOf(t[0],t[2],t[4])||y<minOf(t[1],t[3],t[5])||y>maxOf(t[1],t[3],t[5]))continue
                fun cross(a:Int,b:Int)=(t[b]-t[a])*(y-t[a+1])-(t[b+1]-t[a+1])*(x-t[a])
                if(cross(0,2)>=-1e-7&&cross(2,4)>=-1e-7&&cross(4,0)>=-1e-7)return true
            }
            return false
        }
    }
    private fun union(v: FloatArray, indices: ShortArray, count: Int): Region {
        val path = Region()
        for (i in 0 until count step 3) {
            val a=indices[i].toInt()*2;var b=indices[i+1].toInt()*2;var c=indices[i+2].toInt()*2
            val area=(v[b]-v[a]).toDouble()*(v[c+1]-v[a+1])-(v[b+1]-v[a+1]).toDouble()*(v[c]-v[a])
            if(area<0){val swap=b;b=c;c=swap}
            path.moveTo(v[a].toDouble(),v[a+1].toDouble());path.lineTo(v[b].toDouble(),v[b+1].toDouble())
            path.lineTo(v[c].toDouble(),v[c+1].toDouble());path.closePath()
        }
        return path
    }
    private fun footprintPath(f: IBookShadowFootprint): Region {
        val path=Region();val v=f.coordinates
        for(i in 0 until f.valueCount step 6){
            path.moveTo(v[i].toDouble(),v[i+1].toDouble());path.lineTo(v[i+2].toDouble(),v[i+3].toDouble())
            path.lineTo(v[i+4].toDouble(),v[i+5].toDouble());path.closePath()
            val area=(v[i+2]-v[i]).toDouble()*(v[i+5]-v[i+1])-(v[i+3]-v[i+1]).toDouble()*(v[i+4]-v[i])
            assertTrue("Footprint contours must not cancel",area>0)
        }
        return path
    }
    @Test fun visibleReceiverAloneLeavesAnUnrelatedStrip() {
        var leaks=0;var retained=0;var poses=0;var worst=0
        val report=File("build/reports/ibook-shadow").also{it.mkdirs()}
        for(origin in listOf(0f,100f,400f)) for(progress in listOf(.2f,.4f,.6f,.8f)) for(dy in listOf(-160f,160f)) {
            val g=IBookCurlGeometry(200f,400f,200*(1-2*progress),origin+dy,origin,.08f)
            val front=IBookCurlMesh(64);val back=IBookCurlMesh(64)
            front.update(g,200f,400f,200,400,false);back.update(g,200f,400f,200,400,true)
            val frontPath=union(front.vertices,front.indices,front.indexCount)
            val backPath=union(back.vertices,back.indices,back.indexCount)
            val footprint=IBookShadowFootprint();footprint.addMesh(g,front,200f,400f,200,400);footprint.addMesh(g,back,200f,400f,200,400)
            val support=footprintPath(footprint)
            val strip=IBookCurlShadow();strip.update(g,200f,400f,20f,.4f,1f)
            fun field(name:String)=strip.javaClass.getDeclaredField(name).also{it.isAccessible=true}.get(strip)!!
            if(!(field("visible") as Boolean)) continue
            val vertices=field("vertices") as FloatArray;val indices=field("indices") as ShortArray
            val texture=field("texture") as FloatArray;val colors=field("colors") as IntArray
            val stripPath=union(vertices,indices,indices.size)
            val rejected=ArrayList<String>();var poseLeaks=0
            for(row in 0 until 100) for(column in 0 until 50) {
                val x=column*4.0+2;val y=row*4.0+2
                if(!stripPath.contains(x,y)||frontPath.contains(x,y)||backPath.contains(x,y)) continue
                var alpha=0.0
                for(i in indices.indices step 3){
                    val a=indices[i].toInt();val b=indices[i+1].toInt();val c=indices[i+2].toInt()
                    val ax=vertices[a*2].toDouble();val ay=vertices[a*2+1].toDouble()
                    val bx=vertices[b*2].toDouble();val by=vertices[b*2+1].toDouble()
                    val cx=vertices[c*2].toDouble();val cy=vertices[c*2+1].toDouble()
                    val det=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
                    if(abs(det)<1e-10)continue
                    val u=((by-cy)*(x-cx)+(cx-bx)*(y-cy))/det
                    val v=((cy-ay)*(x-cx)+(ax-cx)*(y-cy))/det;val w=1-u-v
                    if(minOf(u,v,w)<-1e-6)continue
                    val tex=u*texture[a*2]+v*texture[b*2]+w*texture[c*2]
                    val q=-3+6*(tex-.5)/1023
                    val amplitude=(u*(colors[a] ushr 24)+v*(colors[b] ushr 24)+w*(colors[c] ushr 24))/255
                    alpha=maxOf(alpha,amplitude*IBookShadowLight.softProfile(q))
                }
                if(alpha<1.0/255)continue
                if(!support.contains(x,y)){leaks++;poseLeaks++;rejected.add("[$x,$y]")}
                else retained++
            }
            poses++
            if(poseLeaks>worst){
                worst=poseLeaks
                fun floats(v:FloatArray,count:Int)=v.take(count).joinToString(",","[","]")
                fun shorts(v:ShortArray,count:Int)=v.take(count).joinToString(",","[","]")
                report.resolve("fixture.json").writeText("""{"origin":$origin,"progress":$progress,"dy":$dy,"width":200,"height":400,"front":${floats(front.vertices,front.valueCount)},"frontIndices":${shorts(front.indices,front.indexCount)},"back":${floats(back.vertices,back.valueCount)},"backIndices":${shorts(back.indices,back.indexCount)},"footprint":${floats(footprint.coordinates,footprint.valueCount)},"strip":${floats(vertices,vertices.size)},"stripIndices":${shorts(indices,indices.size)},"rejected":[${rejected.joinToString(",")}]}""")
            }
        }
        assertTrue("The old receiver-only clip must reproduce a leaking strip",leaks>0)
        assertTrue("The footprint must retain a real next-page shadow",retained>0)
        println("FOOTPRINT_CHECK poses=$poses oldVisibleShadowSamplesOutsidePaperProjection=$leaks retainedValidSamples=$retained worstPoseLeaks=$worst")
    }
}
