package org.peyilo.pagecurl.control

import kotlin.math.*
import org.peyilo.pagecurl.model.*
import org.peyilo.pagecurl.math.AppleMath

/** UIKit 16H81 scalar control algorithm. Does not know page bitmaps or platform events. */
object CurlControl {
    private const val HALF_PI=PI/2
    fun type(c: CurlConfig): Int {
        val t=c.kind.nativeValue;val s=c.binding.nativeValue;val d=c.direction.nativeValue
        return if((s==1||s==2)&&d==1||(s==16||s==32)&&d==0)if(t==1||t==4)4 else 3 else t
    }
    private fun vertical(c: CurlConfig)=c.binding.axis==CurlAxis.Vertical
    private fun travel(c: CurlConfig): Double {val size=if(vertical(c))c.height else c.width;return if(c.binding.middle)size else size+size}
    private fun base(c: CurlConfig): Double {
        val s=c.binding.nativeValue;val d=c.direction.nativeValue
        return when {s==1||s==3||s==4&&d==0->PI;s==16||s==4&&d==1->0.0;s==2||s==8&&d==0->1.5*PI;else->HALF_PI}
    }
    fun frame(c: CurlConfig): CurlRect {
        var w=c.width;var h=c.height;var x=0.0;var y=0.0
        if(c.binding==CurlBinding.MiddleHorizontal){w*=.5;if(c.direction==CurlDirection.Forward)x=w}
        if(c.binding==CurlBinding.MiddleVertical){h*=.5;if(c.direction==CurlDirection.Forward)y=h}
        return CurlRect(x,y,w,h)
    }
    fun reference(c: CurlConfig): CurlPoint {
        val t=type(c);if(t==1||t==4)return c.initial
        val s=c.binding.nativeValue;val d=c.direction.nativeValue
        return when {s==1||s==3||s==4&&d==0->c.initial.copy(x=c.width);s==16||s==4&&d==1->c.initial.copy(x=0.0)
            s==2||s==8&&d==0->c.initial.copy(y=c.height);else->c.initial.copy(y=0.0)}
    }
    fun endpoints(c: CurlConfig): CurlEndpoints {
        val t=type(c);val auto=t==1||t==4;val size=if(vertical(c))c.height else c.width;val b=base(c)
        val fromRadius=if(c.binding.middle)if(auto)150.0 else size*.02032520325203252 else size*if(auto).1524390243902439 else .04065040650406504
        val toRadius=if(c.binding.middle)if(auto)1.0 else size*.0010162601626016261 else size*.3048780487804878
        val delta=if(auto)if(b>HALF_PI).17453292519943295 else -.17453292519943295 else -.17453292519943295
        val time=if(t<3)0.0 else 1.0
        return CurlEndpoints(CurlValues(time,fromRadius,b+delta),CurlValues(1-time,toRadius,b))
    }
    fun begin(c: CurlConfig): CurlBegin {
        val e=endpoints(c);val t=type(c);val explicit=t==1||t==4
        return CurlBegin(e.from.renderValues(),if(explicit)e.to else update(c,c.initial),if(explicit).4 else .25,explicit,t)
    }
    fun duration(c: CurlConfig,velocity: Double,complete: Boolean,time: Double): Double {
        if(velocity==0.0)return .25
        var p=time.toFloat().toDouble();if(complete)p=1-p;if(type(c)>2)p=1-p
        return p*travel(c)/abs(velocity).coerceIn(2048.0,4096.0)
    }
    fun compatible(oldType: Int,oldDirection: Int,oldState: Int,newType: Int,newDirection: Int)=
        (oldType==1||oldType==4||oldState==5)&&(oldType<3)==(newType<3)&&oldDirection==newDirection

    fun update(c: CurlConfig,point: CurlPoint): CurlValues {
        val s=c.binding.nativeValue;val d=c.direction.nativeValue;val t=type(c);val auto=t==1||t==4;val mid=c.binding.middle
        val ref=reference(c);val x=point.x.coerceIn(0.0,c.width);val y=point.y.coerceIn(0.0,c.height)
        var ox=ref.x;var oy=ref.y
        if(s and 1!=0)ox= -c.width else if(s==4)ox=if(d==1)c.width else 0.0 else if(s and 16!=0)ox=c.width+c.width
        if(s and 2!=0&&s!=3)oy= -c.height else if(s==8)oy=if(d==1)c.height else 0.0 else if(s and 32!=0)oy=c.height+c.height
        val dist=travel(c);val p=(abs(if(vertical(c))y-oy else x-ox)/dist).coerceIn(0.0,1.0);val progress=1-p
        val margin=if(auto)0.0 else .1;val ceiling=if(auto||mid)1.0 else .9
        val rawAngle=when {s==1||s==3||s==4&&d==0->atan2(ref.y-y,ref.x-min(x,c.width-dist*margin))+PI
            s==16||s==4&&d==1->atan2(y-ref.y,max(x,dist*margin)-ref.x)
            s==2||s==8&&d==0->{val v=atan2(ref.x-x,min(y,c.height-dist*margin)-ref.y)+HALF_PI;if(v<0)v+2*PI else v}
            else->{val v=atan2(x-ref.x,ref.y-max(y,dist*margin))+1.5*PI;if(v-2*PI>0)v-2*PI else v}}
        val w=if(s==4)c.width*.5 else c.width;val h=if(s==8)c.height*.5 else c.height
        val maxda=if(vertical(c))atan2(h.toFloat(),(w*.5).toFloat()).toDouble() else atan2(w.toFloat(),(h*.5).toFloat()).toDouble()
        val b=base(c);val da=(rawAngle-b).coerceIn(-maxda,maxda);val angle=b+da;val e=endpoints(c)
        val peak=dist*.1524390243902439;val k=if(mid).5 else .25
        var radius=if(progress<k)e.from.radius+(peak-e.from.radius)*(progress/k)
            else peak+((progress-k)/(1-k))*(dist*(if(mid).0010162601626016261 else .3048780487804878)-peak)
        var bound=if(auto)min(e.from.radius,e.to.radius) else (if(vertical(c))c.height else c.width)*(if(mid).5 else 1.0)*.08791208791208792
        if(mid){val actual=hypot(x-ox,y-oy)/dist;if(actual<=.25)bound*=1+(.25-actual)*-4}
        radius=min(dist*.2,max(min(min(e.from.radius,e.to.radius),bound),radius))
        val result=inputTime(c,progress,dist,radius,bound,angle,da,x,y,ref.x,ref.y)
        return CurlValues(result.first.coerceIn(margin,ceiling),result.second,angle)
    }
    private data class Transform(val a: Double=1.0,val b: Double=0.0,val c: Double=0.0,val d: Double=1.0,val x: Double=0.0,val y: Double=0.0) {
        fun concat(o: Transform)=Transform(a*o.a+b*o.c,a*o.b+b*o.d,c*o.a+d*o.c,c*o.b+d*o.d,(x*o.a+y*o.c)+o.x,(x*o.b+y*o.d)+o.y)
        fun point(px: Double,py: Double)=CurlPoint(x+(px*a+py*c),y+(px*b+py*d))
        fun rect(r: CurlRect): CurlRect {val p=listOf(point(r.x,r.y),point(r.x+r.width,r.y),point(r.x,r.y+r.height),point(r.x+r.width,r.y+r.height))
            val l=p.minOf{it.x};val t=p.minOf{it.y};return CurlRect(l,t,p.maxOf{it.x}-l,p.maxOf{it.y}-t)}
    }
    private fun inputTime(c: CurlConfig,progress: Double,dist: Double,initialRadius: Double,minr: Double,angle: Double,da: Double,tx: Double,ty: Double,rx: Double,ry: Double): Pair<Double,Double> {
        val type=type(c);val s=c.binding.nativeValue;val d=c.direction.nativeValue
        var q=if(type<3)0 else -2
        if(s and 1!=0&&d==0||s and 1==0&&d==0&&(s==4||s and 16!=0))q=if(type<3)-2 else 0
        else if(vertical(c))q=if(d==0)if(type<3)-3 else -1 else if(type<3)-1 else -3
        var a=Transform();val bounds=CurlRect(0.0,0.0,c.width,c.height);var r=bounds
        if(q and 1!=0){a=Transform(x= -c.width*.5,y= -c.height*.5).concat(Transform(cos(-HALF_PI),sin(-HALF_PI),-sin(-HALF_PI),cos(-HALF_PI)))
            r=a.rect(bounds);a=a.concat(Transform(x=r.width*.5,y=r.height*.5))}
        if(q<= -2)a=a.concat(Transform(x= -r.width)).concat(Transform(a= -1.0))
        val th=if(da==0.0)0.0 else abs(q.toDouble()*HALF_PI+angle)
        val p=a.point(tx,ty);val ref=a.point(rx,ry);r=a.rect(bounds);val px=p.x;var py=p.y
        if(py<ref.y)py=r.y+((r.y+r.height)-py)
        var radius=initialRadius;var center=0.0;var half=0.0;var twop=0.0;var curl: Double
        if(da!=0.0){val(sn,cs)=AppleMath.sincos(th.toFloat());val invcs=1f/cs;val invcs2=(invcs*invcs).toDouble();val invsn=1f/sn
            var term=radius*(2*PI);val hsin=AppleMath.sin((th*.5).toFloat()).pow(6f).toDouble();val z=(invsn*invsn).toDouble()*2
            term*=((cs.toDouble()+1)+z*hsin);term+=(py*8)*(cs*cs).toDouble()*sn.toDouble();term-=(radius*8)*(sn*sn).toDouble()
            val angular=(px/sn.toDouble())*AppleMath.sin((th*4).toFloat()).toDouble();curl=((angular+term)*invcs2)*.125
        }else{val p0=progress*dist;half=dist*.5;center=p0-half;val v=((half-abs(center))+radius*-PI)*.5;curl=(if(center<0)p0 else half)-v;twop=p0+p0}
        val absolute=abs(th);val(ss,cc)=AppleMath.sincos(absolute.toFloat());val pagew=if(c.binding.middle)r.width*.5 else r.width
        val projected=curl*cc.toDouble()+(curl*ss.toDouble())*AppleMath.tan(absolute.toFloat()).toDouble()
        if(projected>pagew){curl=pagew*cc.toDouble()
            val nr=if(da!=0.0){val trig=px*AppleMath.sin((th*4).toFloat()).toDouble();val(sn,cs)=AppleMath.sincos(th.toFloat());val invcs=1/cs.toDouble();val invcsf=invcs.toFloat();val invcs2=(invcsf*invcsf).toDouble()
                val numerator=(curl*8-(1/sn.toDouble())*trig*invcs2)-(py*8)*sn.toDouble()
                val tan=AppleMath.tan(th.toFloat());val isn=1f/sn;val ics=1f/cs;var den=invcs*PI+(invcs2*PI-(tan*tan).toDouble()*4)
                val add=((AppleMath.sin((th*.5).toFloat()).pow(6f).toDouble()*(2*PI))*(isn*isn).toDouble())*(ics*ics).toDouble();den=add+den;numerator/(den+den)
            }else{var a1=(curl+curl)-half;a1-=center;var a2=half+((curl+curl)-twop);a2=center+a2;(if(center>=0)a1 else a2)/PI}
            radius=max(nr,minr)
        }
        val page=frame(c);val diag=hypot(page.width,page.height);val diagAngle=if(vertical(c))atan2(page.width,page.height) else atan2(page.height,page.width)
        val projection=diag*AppleMath.cos((absolute-diagAngle).toFloat()).toDouble()
        return curl/(radius+abs(projection)) to radius
    }
}
