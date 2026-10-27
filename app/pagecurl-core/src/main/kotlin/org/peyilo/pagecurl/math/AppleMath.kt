package org.peyilo.pagecurl.math

import kotlin.math.*
import org.peyilo.pagecurl.math.BinaryMath.fma
import org.peyilo.pagecurl.math.BinaryMath.copySign

/** Recovered libsystem_m polynomials. Scalar sin/cos and paired sincos intentionally differ. */
internal object AppleMath {
    private fun reduced(rh: Float, rl: Float, quadrant: Int): Float {
        val r = rh - rl
        val y = if (quadrant and 1 != 0) {
            val q = r*r
            var p = fma(q, 2.4372400730499066e-05f, -0.001388678909279406f)
            p = fma(q,p,0.04166663438081741f); p=q*p
            val rr=r.toDouble()*r
            fma(rr,p.toDouble(),fma(rr,-.5,1.0)).toFloat()
        } else {
            val error=(rh-r)-rl; val q=r*r
            val p=fma(q,2.7166636300535174e-06f,-0.00019839356536976993f)
            var z=fma(q,0.008333330042660236f,5.040594164285039e-09f)
            z=fma(q*q,p,z);z=q*z;z=fma(q,-0.1666666716337204f,z)
            fma(r,z,error)+r
        }
        return Float.fromBits(y.toRawBits() xor ((quadrant ushr 1) shl 31))
    }
    private fun large(x: Float, cosine: Boolean): Float {
        var a=x.toDouble()*0.3183098863810301
        val b=x.toDouble()*-1.972394111648692e-10
        if(cosine)a+=.5
        val n=round(a);val r=(a-n)+b
        val signed=Double.fromBits(r.toRawBits() xor (n.toLong() shl 63))
        val q=r*r;val p4=q-4.16477198291663;val p5=q-3.6493923712207774
        val p6=fma(q,p4,15.485466333375713);val p7=fma(q,p5,2.652203472786763)
        return ((signed*p6)*(0.07649248058790409*p7)).toFloat()
    }
    fun sin(x: Float): Float {
        val ab=x.toRawBits() and Int.MAX_VALUE
        if(ab<0x3f490fdb){
            if(ab<0x39800000)return fma(x,67108864f,x)*1.4901161193847656e-08f
            val q=x*x;var p=fma(q,2.7166636300535174e-06f,-0.00019839356536976993f)
            p=fma(q,p,0.008333330042660236f);p=fma(q,p,5.040594164285039e-09f);p=q*p;p=fma(q,-0.1666666716337204f,p)
            return fma(x,p,x)
        }
        if(ab>=0x41000000)return if(ab<0x42800000)large(x,false) else kotlin.math.sin(x)
        val n=round(x*0.6366197466850281f)
        return reduced(fma(-n,1.5707963705062866f,x),n*-4.371138828673793e-08f,n.toInt())
    }
    fun cos(input: Float): Float {
        val x=abs(input);val ab=x.toRawBits()
        if(ab<0x39800000)return (67108864f-x)*1.4901161193847656e-08f
        if(ab<0x3f490fdb){val q=x*x;var p=fma(q,2.4372400730499066e-05f,-0.001388678909279406f)
            p=fma(q,p,0.04166663438081741f);p=q*p;val xx=x.toDouble()*x
            return fma(p.toDouble(),xx,fma(xx,-.5,1.0)).toFloat()}
        if(ab>=0x40800000)return if(ab<0x42800000)large(x,true) else kotlin.math.cos(x)
        val n=round(x*0.6366197466850281f)
        return reduced(fma(-n,1.5707963705062866f,x),n*-4.371138828673793e-08f,n.toInt()+1)
    }
    fun tan(v: Float): Float {
        var x=v.toDouble();val ab=v.toRawBits() and Int.MAX_VALUE
        if(ab<0x39800000)return fma(v,67108864f,v)*1.4901161193847656e-08f
        if(ab<0x3f490fdb){val q=x*x
            var a=fma(q,0.0010933359443297335,0.010024745651428834)
            val b=fma(q,0.021601514863153856,0.05399723816130635)
            var c=fma(q,0.13333216629799713,0.33333334098837764);val qq=q*q
            a=fma(qq,0.003893782386938478,a);c=fma(qq,b,c);c=fma(qq*qq,a,c)
            return fma(x,q*c,x).toFloat()}
        if(abs(x)>=8)return kotlin.math.tan(v)
        val a=x*0.6366197727620602;val b=x*-3.944788223297384e-10;val biased=a+6755399441055744.0
        val bits=biased.toRawBits();val n=biased-6755399441055744.0;x=(a-n)+b;val q=x*x
        var p5=fma(q,5.2786026187254365,3.821272463971885)
        var p6=q-0.3443129754511736;var p7=q-1.535039353621607;var p16=q+0.9130117913337534
        p5=x*p5;p6=fma(q,p6,0.7533741597749263);p7=fma(q,p7,0.9278362959973533);p16=fma(q,p16,0.5880710422549965)
        val p=(p5*p6)*(p7*p16);return (if(bits and 1L != 0L)-1/p else p).toFloat()
    }
    fun sincos(x: Float): Pair<Float,Float> {
        val a=x.toDouble()*0.6366197723675814;val n=(a+copySign(.5,x.toDouble())).toInt()
        val r=a-n;val q=r*r
        var s=(r*0.00015820674370772955)*((q-14.11962047131294)*q+40.5713390182018)
        s=((q-15.469930309596)*q+244.7234259671355)*s
        var c=((q-8.36721486831633)*q+7.367313974172263)*0.0009036317034594685
        c=((q-14.71749014926)*q+150.210178361388)*c
        return when(n and 3){0->s.toFloat() to c.toFloat();1->c.toFloat() to -s.toFloat();2->-s.toFloat() to -c.toFloat();else->-c.toFloat() to s.toFloat()}
    }
    private val acosHi=doubleArrayOf(0.0018866627454690213,0.002105758391974362,0.008053538408076452,0.026496590400740767,0.11785201800307596,1.4142135559781581)
    private val acosLo=doubleArrayOf(0.03392107098559122,0.017005792259508826,0.03113191845966743,0.044596625218188204,0.07500103158302872,0.16666666294795343)
    fun acos(x: Float): Float {
        val a=abs(x.toDouble())
        if(a==1.0)return if(x>0)0f else 3.1415925323963165.toFloat()
        if(a>1||a.isNaN())return Float.NaN
        if(a<.001953125)return (PI/2-x.toDouble()).toFloat()
        if(a>.5){var t=(1f-a.toFloat()).toDouble();val c=acosHi
            val p=fma(t,c[0],c[1]);var q=fma(t,c[2],c[3]);var r=fma(t,c[4],c[5]);t=t*t;q=fma(t,p,q);r=fma(t,q,r)
            r*=sqrt((1f-a.toFloat()).toDouble());if(x<=0)r=PI-r;return r.toFloat()}
        val t=x.toDouble()*x;val c=acosLo;val p=fma(t,c[0],c[1]);var q=fma(t,c[2],c[3]);var r=fma(t,c[4],c[5])
        val tt=t*t;q=fma(tt,p,q);r=fma(tt,q,r);r=t*r;r=fma(x.toDouble(),r,x.toDouble());return (PI/2-r).toFloat()
    }
    private val sinCoefficients=doubleArrayOf(1.5896230157654656e-10,-2.5050747762857807e-08,2.7557313621385722e-06,-0.0001984126982958954,0.008333333333322118,-0.1666666666666663)
    private val cosCoefficients=doubleArrayOf(-1.1358536521387682e-11,2.087570084197473e-09,-2.755731417929674e-07,2.4801587288851704e-05,-0.0013888888888873056,0.041666666666666595)
    fun sincos(input: Double): Pair<Double,Double> {
        var x=input;val ax=abs(x);var n=0
        if(ax<1.0536712127723509e-8)return x to 1.0
        if(ax>=PI/4){if(ax>=524288.6169851497)return kotlin.math.sin(x) to kotlin.math.cos(x)
            var q=x*.6366197723675814;q+=6755399441055744.0;q-=6755399441055744.0;n=q.toInt()
            val a=q*-1.5707963267923333;val b=q*2.5633441515839558e-12;val c=q*-1.0562999066987428e-23
            x+=a;val y=x-b;x-=y;x-=b;x+=c;x=y+x}
        val t=x*x;var p=t*sinCoefficients[0]+sinCoefficients[1]
        for(k in 2..5)p=t*p+sinCoefficients[k]
        val s=x+(x*t)*p;p=t*cosCoefficients[0]+cosCoefficients[1]
        for(k in 2..5)p=t*p+cosCoefficients[k]
        p=t*p;p+=-.5;val c=t*p+1
        return when(n and 3){0->s to c;1->c to -s;2->-s to -c;else->-c to s}
    }
    // The 32 exp2 residual pairs are copied as numeric constants from the recovered implementation.
    private val expSteps = floatArrayOf(1.0f,0.0f,1.0218971967697144f,-6.792868845195699e-08f,1.0442737340927124f,6.677581865233151e-08f,1.0671404600143433f,-8.02199409122295e-08f,1.0905077457427979f,-1.7301026389304752e-08f,1.114386796951294f,-7.036898352907883e-08f,1.1387885808944702f,6.823633214025904e-08f,1.1637248992919922f,-5.0226599768166125e-08f,1.1892070770263672f,4.607128190059484e-08f,1.215247392654419f,-3.87892598041617e-08f,1.2418577671051025f,5.224081434107575e-08f,1.2690509557724f,1.6135405234152245e-09f,1.2968395948410034f,-4.4710159130545435e-08f,1.3252366781234741f,-3.806263748629135e-08f,1.3542555570602417f,-1.0784453330359156e-08f,1.3839099407196045f,-6.125157625547217e-08f,1.4142135381698608f,2.4690674393923473e-08f,1.4451807737350464f,3.31848220014308e-08f,1.4768261909484863f,-4.3968778840053346e-08f,1.5091644525527954f,-2.386006592303147e-08f,1.5422108173370361f,7.550105429743326e-09f,1.5759809017181396f,-5.182253914881585e-08f,1.610490322113037f,8.811392504526339e-09f,1.6457555294036865f,-4.4926309783477336e-08f,1.6817928552627563f,-2.1235901925820144e-08f,1.7186193466186523f,-4.0710116167019805e-08f,1.7562521696090698f,-7.586837824646864e-09f,1.7947090864181519f,-9.176098991758863e-09f,1.8340080976486206f,-8.841210430432511e-09f,1.8741676807403564f,-3.589484265376086e-08f,1.9152065515518188f,7.416331548881772e-09f,1.9571441411972046f,-1.254750348067546e-08f)
    fun exp(x: Float): Float {
        if(x.isNaN())return x+x;if(x>88.72283172607422f)return Float.POSITIVE_INFINITY;if(x< -103.97208404541016f)return 0f
        val z=x*1.4426950216293335f;val n=(z*32f).toLong();val fraction=z-n.toFloat()*.03125f
        var error=fma(-x,1.4426950216293335f,z);error=fma(x,1.925963033500011e-08f,-error)
        val index=(n.toInt() and 31)*2;val base=expSteps[index]
        var p=0.009618354961276054f*base;var q=expSteps[index+1]+error
        val u=fraction+1.7524261474609375f;q=fraction+q
        val v=fma(q,u,17.933971405029297f);val u2=q+4.0183539390563965f;p=q*p;p*=u2
        val e=n.toInt() shr 5
        if(e>=-125){val r=fma(p,v,base);return Float.fromBits(r.toRawBits()+(e shl 23))}
        val r=fma(p.toDouble(),v.toDouble(),base.toDouble());return Double.fromBits(r.toRawBits()+(e.toLong() shl 52)).toFloat()
    }
}
