package org.peyilo.pagecurl.gesture

import kotlin.math.*
import org.peyilo.pagecurl.model.*

data class CurlRelease(val direction: Int, val suggestedVelocity: Double)
object CurlNavigation {
    private fun axis(p: CurlPoint,axis: CurlAxis)=if(axis==CurlAxis.Vertical)p.y else p.x
    fun begin(axis: CurlAxis,translation: CurlPoint,velocity: CurlPoint): Int {
        val v=axis(velocity,axis);val primary=axis(translation,axis);val cross=if(axis==CurlAxis.Vertical)translation.x else translation.y
        if(abs(v)>300)return if(v>=0)1 else 0
        if(!(abs(primary)>abs(cross)))return -1
        return if(primary>=0)1 else 0
    }
    fun end(axis: CurlAxis,width: Double,height: Double,location: CurlPoint,velocity: CurlPoint): CurlRelease {
        val v=axis(velocity,axis)
        if(abs(v)>300)return CurlRelease(if(v>=0)1 else 0,v)
        return CurlRelease(if(axis(location,axis)>(if(axis==CurlAxis.Vertical)height else width)*.5)1 else 0,0.0)
    }
    fun tap(axis: CurlAxis,width: Double,height: Double,point: CurlPoint,middle: Boolean=false,tablet: Boolean=false): Int {
        val b=if(tablet)if(middle)82.0 else 110.0 else if(middle)60.0 else 80.0
        val bw=min(b,width*.5);val bh=min(b,height*.5)
        if(point.x<0||point.y<0||point.x>=width||point.y>=height)return -1
        if(axis==CurlAxis.Vertical){if(point.y<bh)return 1;if(point.y>=height-bh)return 0}
        else{if(point.x<bw)return 1;if(point.x>=width-bw)return 0}
        return -1
    }
}
