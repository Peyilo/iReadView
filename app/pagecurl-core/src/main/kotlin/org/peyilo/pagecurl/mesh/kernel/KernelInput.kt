package org.peyilo.pagecurl.mesh.kernel

import kotlin.math.*
import org.peyilo.pagecurl.model.*
import org.peyilo.pagecurl.mesh.*
import org.peyilo.pagecurl.math.*
import org.peyilo.pagecurl.math.BinaryMath.fma

private fun color(color: CurlColor): Long {
    val values=doubleArrayOf(color.red,color.green,color.blue,color.alpha);var result=0L
    for(k in 0..3){val v=if(k==3)values[k] else values[k]*color.alpha;val q=fma(v,255.0,.5).coerceIn(0.0,255.0).toInt()
        val f=q.toFloat()*.003921568859368563f;result=result or (floatHalf(f) shl (k*16))}
    return result
}
internal fun CurlMeshKernel.build(input: CurlMeshInput): CurlMeshFrame {
    require(input.width.isFinite()&&input.height.isFinite()&&input.width in 1.0..4096.0&&input.height in 1.0..4096.0)
    require(input.scale.isFinite()&&input.scale in .1..4.0&&input.values.radius>0&&input.values.radius<=4096)
    require(input.values.time.isFinite()&&input.values.angle.isFinite()&&input.startAngle.isFinite()&&input.endAngle.isFinite())
    resetWorkspace()
    val i=0x200L;val o=0x400L;val ctx=0x1000L;val state=0x2000L;val gstate=0x2400L;val surface=0x2800L;val table=0x2c00L
    doubleArrayOf(.5,.13500000536441803,0.0).forEachIndexed{k,v->st_d(FADE_WEIGHTS+k*8,v)}
    doubleArrayOf(.33000001311302185,.6600000262260437,1.0).forEachIndexed{k,v->st_d(FADE_STEPS+k*8,v)}
    st_d(CLAMP_STEPS,170.0);st_d(CLAMP_STEPS+8,68.00000101327896);st_d(CLAMP_STEPS2,180.0);st_d(CLAMP_STEPS2+8,72.0000010728836)
    val width=ceil(input.width*input.scale).toInt();val height=ceil(input.height*input.scale).toInt()
    val sr=input.surfaceRect?:CurlRect(0.0,0.0,width.toDouble(),height.toDouble())
    val target=input.targetBounds?:CurlRect(0.0,0.0,sr.width,sr.height);val requested=input.clipRect?:target
    val l=max(target.x,requested.x);val t=max(target.y,requested.y)
    clip=CurlRect(l,t,max(0.0,min(target.x+target.width,requested.x+requested.width)-l),max(0.0,min(target.y+target.height,requested.y+requested.height)-t))
    st_d(i,input.values.time.coerceIn(0.0,1.0));st_d(i+8,input.values.angle);st_d(i+16,input.values.radius);st_d(i+24,input.startAngle);st_d(i+32,max(input.startAngle,input.endAngle))
    var (sn,cs)=AppleMath.sincos(input.values.angle.toFloat());if(abs(sn.toDouble())<.001||abs(cs.toDouble())<.001){sn=roundAway(sn);cs=roundAway(cs)}
    st_d(i+40,cs.toDouble());st_d(i+48,sn.toDouble());st_d(i+56,-sn.toDouble());st_d(i+64,cs.toDouble())
    val sb=input.shadowBounds?:if(input.width<=2||input.height<=2)CurlRect(1.0,1.0,0.0,0.0) else CurlRect(1.0,1.0,input.width-2,input.height-2)
    putRect(i+72,sb);st_u8(o,if(input.frontEnabled)1 else 0);st_u8(o+1,if(input.backEnabled)1 else 0)
    val inv=input.scale*(1/fma(input.scale,input.scale,-(0.0*0.0)))
    st_d(o+8,inv);st_d(o+48,inv);st_d(o+16,-0.0);st_d(o+40,-0.0);st_d(o+88,1.0);st_d(o+128,1.0)
    putRect(o+136,CurlRect(0.0,0.0,input.width,input.height));putRect(o+168,sr)
    var nx=fma(input.scale,cs.toDouble(),0.0*sn.toDouble());var ny=fma(0.0,cs.toDouble(),input.scale*sn.toDouble())
    var tx=fma(input.scale,-sn.toDouble(),0.0*cs.toDouble());var ty=fma(0.0,-sn.toDouble(),input.scale*cs.toDouble())
    if(abs(tx)<.0010000000474974513||abs(ty)<.0010000000474974513){
        val length=sqrt(fma(tx,tx,ty*ty).toFloat());tx=if(abs(tx)<.0010000000474974513)0.0 else BinaryMath.copySign(length,tx.toFloat()).toDouble()
        ty=if(abs(ty)<.0010000000474974513)0.0 else BinaryMath.copySign(length,ty.toFloat()).toDouble()
        val nnx=fma(inv,nx,-0.0*ny);val nny=fma(-0.0,nx,inv*ny);val ttx=fma(0.0,nx,-(inv*ny))
        st_d(i+40,nnx);st_d(i+48,nny);st_d(i+56,ttx);st_d(i+64,nnx)
        nx=fma(input.scale,nnx,0.0*nny);ny=fma(0.0,nnx,input.scale*nny)
    }
    st_d(o+200,nx);st_d(o+208,ny);st_d(o+216,tx);st_d(o+224,ty)
    st_u64(o+232,color(input.material.front));st_u64(o+248,color(input.material.back));st_u64(o+256,color(input.material.backShade));st_u64(o+272,color(input.material.shadow))
    st_u64(o+240,0x3c003c003c003c00L);st_u8(o+264,1);st_d(o+280,1.0)
    st_u64(ctx,table);st_u64(ctx+8,gstate);st_u64(ctx+224,state);st_u64(state+32,0x3c003c003c003c00L);st_u8(gstate+416,3);st_u8(gstate+417,1)
    longArrayOf(sr.x.toLong(),sr.y.toLong(),sr.width.toLong(),sr.height.toLong()).forEachIndexed{k,v->st_u32(surface+32+k*4,v)}
    x0=ctx;x1=i;x2=o;x3=surface;x4=0
    execute()
    return CurlMeshFrame(sr.width.toInt(),sr.height.toInt(),batches.toList())
}
private fun CurlMeshKernel.putRect(p: Long,r: CurlRect) {st_d(p,r.x);st_d(p+8,r.y);st_d(p+16,r.width);st_d(p+24,r.height)}
private fun roundAway(v: Float)=if(v<0)ceil(v-.5f) else floor(v+.5f)
