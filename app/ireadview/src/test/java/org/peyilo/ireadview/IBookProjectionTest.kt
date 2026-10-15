package org.peyilo.ireadview

import org.junit.Test
import org.junit.Assert.*
import org.peyilo.ireadview.internal.effect.*
import kotlin.math.*

class IBookProjectionTest {
    @Test fun projectionJacobianMatchesSurfaceNormal() {
        var worst=0.0;var samples=0
        val out=Array(4){FloatArray(2)}
        for(origin in listOf(0f,400f,800f)) for(p in listOf(.1f,.4f,.8f)) {
            val g=IBookCurlGeometry(400f,800f,400f*(1-2*p),origin+100f,origin,.08f)
            for(row in 1..19) for(column in 1..9) {
                val x=column*40.0;val y=row*40.0;val e=.1
                g.projectMaterial(x+e,y,out[0],0);g.projectMaterial(x-e,y,out[1],0)
                g.projectMaterial(x,y+e,out[2],0);g.projectMaterial(x,y-e,out[3],0)
                val jacobian=((out[0][0]-out[1][0])*(out[2][1]-out[3][1])-
                    (out[2][0]-out[3][0])*(out[0][1]-out[1][1]))/(4*e*e)
                val s=g.nx*x+g.ny*y-g.axis
                val expected=if(s<=0) {
                    val u=if(g.frontBandWidth>0) ((s+g.frontBandWidth)/g.frontBandWidth).coerceIn(0.0,1.0) else 0.0
                    cos(.65*sin(PI*u))
                } else if(!g.conical) cos(minOf(PI,s/g.radius)) else {
                    val sa=g.arcEnd/PI;val phi=g.parameter(x,y)
                    sa*sa+(1-sa*sa)*cos(minOf(PI,phi/sa))
                }
                worst=maxOf(worst,abs(jacobian-expected));samples++
            }
        }
        assertTrue("Jacobian error $worst",worst<.01)
        println("JACOBIAN_DIAGNOSTIC samples=$samples maxError=$worst")
    }
    @Test fun oldOffsetRibbonReproducesFoldovers() {
        var folded=0;var cells=0
        for(origin in listOf(0f,100f,799f,800f)) for(p in listOf(.1f,.25f,.4f,.6f,.8f)) for(dy in listOf(-100f,0f,100f)) {
            val shadow=IBookFrontShadow()
            val g=IBookCurlGeometry(400f,800f,400f*(1-2*p),origin+dy,origin,.08f)
            shadow.update(g,400f,800f,30f,.35f,1f)
            fun field(name:String)=shadow.javaClass.getDeclaredField(name).also {it.isAccessible=true}.get(shadow)!!
            val n=field("nodes") as Int;val points=field("points") as FloatArray;val heights=field("heights") as DoubleArray
            if(n==0) continue
            val v=FloatArray(n*13*2)
            for(row in 0 until n) {
                val previous=(row+n-1)%n;val next=(row+1)%n
                val dx=(points[next*2]-points[previous*2]).toDouble()
                val dy2=(points[next*2+1]-points[previous*2+1]).toDouble()
                val length=hypot(dx,dy2);val nx=if(length>0) -dy2/length else 0.0;val ny=if(length>0) dx/length else 0.0
                val sigma=30*heights[row]/(heights[row]+30)
                for(column in 0..12) {
                    val q=-3.0+column*.5;val i=(row*13+column)*2
                    v[i]=(points[row*2]+nx*q*sigma).toFloat();v[i+1]=(points[row*2+1]+ny*q*sigma).toFloat()
                }
            }
            fun area(a0:Int,b0:Int,c0:Int):Double {
                val a=a0*2;val b=b0*2;val c=c0*2
                return (v[b]-v[a]).toDouble()*(v[c+1]-v[a+1])-(v[b+1]-v[a+1]).toDouble()*(v[c]-v[a])
            }
            for(row in 0 until n) for(column in 0..11) {
                val a=row*13+column;val b=((row+1)%n)*13+column
                cells++;if(area(a,b,a+1)*area(a+1,b,b+1)<0) folded++
            }
        }
        assertTrue("Old offset mesh must reproduce the defect",folded>0)
        println("OLD_SHADOW_REPRODUCTION cells=$cells foldedQuads=$folded")
    }
    @Test fun shadowGridHasNoFoldedQuads() {
        var crossings=0;var total=0;var detail=""
        fun field(shadow:IBookFrontShadow,name:String):Any = shadow.javaClass.getDeclaredField(name).also {it.isAccessible=true}.get(shadow)!!
        for(origin in listOf(0f,100f,799f,800f)) for(p in listOf(.1f,.25f,.4f,.6f,.8f)) for(dy in listOf(-100f,0f,100f)) {
            val g=IBookCurlGeometry(400f,800f,400f*(1-2*p),origin+dy,origin,.08f)
            val shadow=IBookFrontShadow();shadow.update(g,400f,800f,30f,.35f,1f)
            val v=field(shadow,"vertices") as FloatArray; val indices=field(shadow,"indices") as ShortArray
            val count=field(shadow,"indexCount") as Int
            fun area(i:Int):Double {
                val a=indices[i].toInt()*2;val b=indices[i+1].toInt()*2;val c=indices[i+2].toInt()*2
                return (v[b]-v[a]).toDouble()*(v[c+1]-v[a+1])-(v[b+1]-v[a+1]).toDouble()*(v[c]-v[a])
            }
            for(i in 0 until count step 6){total++;if(area(i)*area(i+3)<0){crossings++;detail="origin=$origin p=$p dy=$dy cell=${i/6}"}}
        }
        assertTrue("No shadow cells evaluated", total > 0)
        assertEquals(detail, 0, crossings)
        println("SHADOW_DIAGNOSTIC cells=$total foldedQuads=$crossings $detail")
    }
    @Test fun paperTrianglesPreserveFaceOrientationAndProjection() {
        var worst = 0.0; var flips = 0; var total = 0; var detail = ""
        val mapped = FloatArray(2)
        for (origin in listOf(0f, 100f, 400f, 799f, 800f))
        for (p in listOf(.01f, .1f, .25f, .4f, .6f, .8f, .99f))
        for (dy in listOf(-240f, -100f, 0f, 100f, 240f)) {
            val g = IBookCurlGeometry(400f,800f,400f*(1-2*p),origin+dy,origin,.08f)
            for (back in listOf(false,true)) {
                val mesh = IBookCurlMesh(64)
                mesh.update(g,400f,800f,400,800,back)
                for (i in 0 until mesh.indexCount step 3) {
                    val a=mesh.indices[i].toInt()*2; val b=mesh.indices[i+1].toInt()*2; val c=mesh.indices[i+2].toInt()*2
                    fun area(v:FloatArray)=(v[b]-v[a]).toDouble()*(v[c+1]-v[a+1])-(v[b+1]-v[a+1]).toDouble()*(v[c]-v[a])
                    val uvArea=area(mesh.texture); val xyArea=area(mesh.vertices)
                    if(abs(uvArea)<1e-5) continue
                    total++
                    if ((xyArea/uvArea > 0) == back) flips++
                    val x=(mesh.texture[a]+mesh.texture[b]+mesh.texture[c])/3.0
                    val y=(mesh.texture[a+1]+mesh.texture[b+1]+mesh.texture[c+1])/3.0
                    g.projectMaterial(x,y,mapped,0)
                    val error=hypot(mapped[0]-(mesh.vertices[a]+mesh.vertices[b]+mesh.vertices[c])/3.0,
                        mapped[1]-(mesh.vertices[a+1]+mesh.vertices[b+1]+mesh.vertices[c+1])/3.0)
                    if(error>worst){worst=error;detail="origin=$origin p=$p dy=$dy back=$back uv=($x,$y) J=${xyArea/uvArea}"}
                }
            }
        }
        assertEquals(detail, 0, flips)
        assertTrue("Projection interpolation error $worst: $detail", worst < .01)
        println("IBOOK_DIAGNOSTIC triangles=$total flips=$flips maxInterpolationError=$worst $detail")
    }
}
