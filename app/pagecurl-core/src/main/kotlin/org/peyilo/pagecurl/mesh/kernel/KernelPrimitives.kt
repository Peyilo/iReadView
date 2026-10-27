@file:Suppress("FunctionName", "UNUSED_PARAMETER")
package org.peyilo.pagecurl.mesh.kernel

import kotlin.math.*
import org.peyilo.pagecurl.model.*
import org.peyilo.pagecurl.mesh.*
import org.peyilo.pagecurl.math.*
import org.peyilo.pagecurl.math.BinaryMath.fma

internal fun CurlMeshKernel.ld_u64(p: Long)=memory.long(p)
internal fun CurlMeshKernel.ld_u32(p: Long)=memory.word(p)
internal fun CurlMeshKernel.ld_u8(p: Long)=memory.byte(p)
internal fun CurlMeshKernel.ld_d(p: Long)=Double.fromBits(memory.long(p))
internal fun CurlMeshKernel.ld_f(p: Long)=Float.fromBits(memory.word(p).toInt())
internal fun CurlMeshKernel.st_u64(p: Long,v: Long)=memory.long(p,v)
internal fun CurlMeshKernel.st_u32(p: Long,v: Long)=memory.word(p,v)
internal fun CurlMeshKernel.st_u16(p: Long,v: Long)=memory.short(p,v)
internal fun CurlMeshKernel.st_u8(p: Long,v: Long)=memory.byte(p,v)
internal fun CurlMeshKernel.st_d(p: Long,v: Double)=memory.long(p,v.toRawBits())
internal fun CurlMeshKernel.st_f(p: Long,v: Float)=memory.word(p,v.toRawBits().toLong())
internal fun halfFloat(h: Long)=BinaryMath.halfToFloat(h.toInt())
internal fun floatHalf(f: Float)=BinaryMath.floatToHalf(f).toLong()
internal fun sqrtFloat(f: Float)=sqrt(f)
internal fun CurlMeshKernel.fcmp(a: Double,b: Double) {
    if(a.isNaN()||b.isNaN()){N=false;Z=false;C=true;V=true}
    else {N=a<b;Z=a==b;C=a>=b;V=false}
}
internal fun CurlMeshKernel.subflags(left: Long,right: Long,bits: Long) {
    val mask=if(bits==32L)0xffffffffL else -1L;val sign=1L shl (bits.toInt()-1)
    val a=left and mask;val b=right and mask;val r=(a-b) and mask
    N=r and sign!=0L;Z=r==0L;C=(a xor Long.MIN_VALUE)>=(b xor Long.MIN_VALUE);V=(a xor b) and (a xor r) and sign!=0L
}
internal fun CurlMeshKernel.addflags(left: Long,right: Long,bits: Long) {
    val mask=if(bits==32L)0xffffffffL else -1L;val sign=1L shl (bits.toInt()-1)
    val a=left and mask;val b=right and mask;val r=(a+b) and mask
    N=r and sign!=0L;Z=r==0L;C=(r xor Long.MIN_VALUE)<(a xor Long.MIN_VALUE);V=(a xor b).inv() and (a xor r) and sign!=0L
}
private fun sinPoly(theta: Double): Float {
    val a=theta.toFloat();val a2=a*a
    return a*fma(a2,fma(a2,.0054653966799378395f,-.15346269309520721f),.9844167828559875f)
}
internal fun CurlMeshKernel.displace(x: Double,center: Double,radius: Double,sineOut: Long,lo: Double,hi: Double): Float {
    val theta=(center-x)/radius+PI/2;val t=max(lo,min(theta,hi));val sine=sinPoly(t).toDouble();var result=0f
    if(!(theta<lo)) {
        val displacement: Double
        if(theta<hi)displacement=((theta-sine)*radius).toFloat().toDouble()
        else {
            val a=hi.toFloat();val a2=a*a
            val ns=a*fma(a2,fma(a2,-.0054653966799378395f,.15346269309520721f),-.9844167828559875f)
            val edge=((hi+ns.toDouble())*radius).toFloat().toDouble()
            val distance=fma(PI/2-hi,radius,center)-x
            val slope=fma(a2,fma(a2,fma(a2,.0009696679771877825f,-.0392276905477047f),.4953497052192688f),.0013932585716247559f)
            displacement=fma(distance,slope.toDouble(),edge)
        }
        val correction=((sinPoly(lo).toDouble()-lo)*radius).toFloat()
        result=(displacement+correction.toDouble()).toFloat()
    }
    if(sineOut!=0L)st_d(sineOut,sine)
    return result
}
internal fun CurlMeshKernel.line_clip(lo: Long,hi: Long,x0: Double,y0: Double,x1: Double,y1: Double,ox: Double,oy: Double,ix: Double,iy: Double) {
    var a=(x0-ox)*ix;var b=(x1-ox)*ix;var c=(y0-oy)*iy
    if(c>a&&iy.isFinite()||!ix.isFinite())a=c
    c=(y1-oy)*iy;if(c<b&&iy.isFinite()||!ix.isFinite())b=c
    st_d(lo,a);st_d(hi,b)
}
private fun CurlMeshKernel.emit(base: Long,index: Long,x: Float,y: Float,u: Float,v: Float,color: Long) {
    val p=base+index;st_f(p,x);st_f(p+4,y);st_f(p+8,0f);st_f(p+12,1f);st_f(p+16,u);st_f(p+20,v);st_u64(p+40,color)
}
internal fun CurlMeshKernel.strip_emit(center: Long,low: Long,high: Long,index: Long,cp: Long,px: Double,py: Double,u: Double,v: Double,dx: Double,dy: Double,du: Double,dv: Double,stack: Long) {
    val b0=ld_f(stack).toDouble();val b1=ld_f(stack+4).toDouble();val b2=ld_f(stack+8).toDouble();val b3=ld_f(stack+12).toDouble()
    val color=ld_u64(cp);val a=index*128;val b=a or 64L
    val x0=fma(dx,b0,px).toFloat();val y0=fma(dy,b0,py).toFloat();val x1=fma(dx,b1,px).toFloat();val y1=fma(dy,b1,py).toFloat()
    val x2=fma(dx,b2,px).toFloat();val y2=fma(dy,b2,py).toFloat();val x3=fma(dx,b3,px).toFloat();val y3=fma(dy,b3,py).toFloat()
    val u0=fma(du,b2,u).toFloat();val v0=fma(dv,b2,v).toFloat();val u1=fma(du,b3,u).toFloat();val v1=fma(dv,b3,v).toFloat()
    emit(low,a,x0,y0,u0,v0,0);emit(low,b,x2,y2,u0,v0,color);emit(center,a,x2,y2,u0,v0,color)
    emit(center,b,x3,y3,u1,v1,color);emit(high,a,x3,y3,u1,v1,color);emit(high,b,x1,y1,u1,v1,0)
}
internal fun CurlMeshKernel.apply_point(mp: Long,pp: Long) {
    val px=ld_d(pp);val py=ld_d(pp+8)
    val w=fma(px,ld_d(mp+24),py*ld_d(mp+56))+ld_d(mp+120)
    val t=if(abs(w-1)<1e-10)w else if(w>0)1/w else Double.POSITIVE_INFINITY
    st_d(pp,t*(ld_d(mp+96)+fma(px,ld_d(mp),py*ld_d(mp+32))))
    st_d(pp+8,t*(ld_d(mp+104)+fma(px,ld_d(mp+8),py*ld_d(mp+40))))
}
internal fun CurlMeshKernel.get_scale(mp: Long,xp: Long,yp: Long): Double {
    var x=fma(ld_d(mp),ld_d(mp),ld_d(mp+8)*ld_d(mp+8));var y=fma(ld_d(mp+32),ld_d(mp+32),ld_d(mp+40)*ld_d(mp+40))
    val w0=ld_d(mp+120);if(w0!=1.0){val w=1/(w0*w0);x*=w;y*=w}
    x=sqrt(x);y=sqrt(y);if(xp!=0L)st_d(xp,x);if(yp!=0L)st_d(yp,y);return (x+y)*.5
}
internal fun CurlMeshKernel.sincos(angle: Double,result: Long) {
    val (sn,cs)=AppleMath.sincos(angle);doubleArrayOf(cs,sn,-sn,cs,0.0,0.0).forEachIndexed { i,v->st_d(result+i*8,v) }
}
internal fun CurlMeshKernel.next_clip(result: Long): Long {
    if(clipped++!=0L)return 0
    val ints=longArrayOf(floor(clip.x).toLong(),floor(clip.y).toLong(),ceil(clip.x+clip.width).toLong(),ceil(clip.y+clip.height).toLong())
    ints.forEachIndexed{i,v->st_u32(result+i*4,v)}
    return if(clip.width>0&&clip.height>0)1 else 0
}
internal fun CurlMeshKernel.capture_draw(ctx: Long,primitive: Long,count: Long,indices: Long,vertices: Long,offset: Long,n: Long,planes: Long): Long {
    if(count==0L)return 0
    check(count in 0..10000&&n in 0..10000&&batches.size<128)
    val state=ld_u64(ctx+8);val vertexCount=(if(n!=0L)n else count).toInt()
    val index=if(indices==0L)null else ShortArray(count.toInt()){(ld_u8(indices+it*2) or (ld_u8(indices+it*2+1) shl 8)).toShort()}
    batches+=CurlDrawBatch(primitive.toInt(),ld_u8(state+16).toInt(),ld_u8(state+416).toInt(),ld_u8(state+417) and 1L!=0L,
        vertexCount,count.toInt(),memory.bytes(vertices,vertexCount*64),index,clip)
    return 0
}
