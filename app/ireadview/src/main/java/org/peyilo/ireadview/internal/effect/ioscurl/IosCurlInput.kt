package org.peyilo.ireadview.internal.effect.ioscurl

import android.view.MotionEvent
import org.peyilo.ireadview.api.effect.TurnPoint
import org.peyilo.ireadview.spi.presentation.ReadViewport
import org.peyilo.ireadview.internal.effect.input.*
import org.peyilo.ioscurl.gesture.*
import org.peyilo.ioscurl.model.*

/** Android event/density adapter. The recognizers and navigation decisions live in ioscurl. */
internal class IosCurlInput(private val viewport: ReadViewport,vertical: Boolean,private val available: (Boolean)->Boolean): EffectInputSession {
    private val density=viewport.density.toDouble()
    private val pan=CurlPan(viewport.widthPx/density,viewport.heightPx/density,if(vertical)CurlAxis.Vertical else CurlAxis.Horizontal)
    private val tap=CurlTap()
    private var beganDirection: Int?=null
    private var recognized=false
    private var lastPoint=TurnPoint(0f,0f)
    private fun forward(direction: Int)=(direction==0) != (pan.axis==CurlAxis.Horizontal&&viewport.rightToLeft)
    override fun event(event: MotionEvent,paddingX: Int,paddingY: Int): EffectInputUpdate {
        fun touch(kind: CurlTouchKind,index: Int)=CurlTouch(kind,event.getPointerId(index),
            CurlPoint((event.getX(index)-paddingX)/density,(event.getY(index)-paddingY)/density),event.eventTime/1000.0)
        val index=event.actionIndex
        val kind=when(event.actionMasked){MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN->CurlTouchKind.Down
            MotionEvent.ACTION_MOVE->CurlTouchKind.Move;MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->CurlTouchKind.Up
            else->CurlTouchKind.Cancel}
        if(beganDirection==null){pan.incomingForward=available(forward(0));pan.incomingReverse=available(forward(1))}
        var result=pan.snapshot()
        // MotionEvent contains all pointers. The UIKit single-pointer adapter samples primary first.
        if(kind==CurlTouchKind.Move){
            val primary=(0 until event.pointerCount).firstOrNull{event.getPointerId(it)==pan.trackedPointer}
            if(primary!=null){val t=touch(kind,primary);result=pan.event(t);tap.event(t)}
        }else{val t=touch(kind,index);result=pan.event(t);tap.event(t)}
        lastPoint=TurnPoint((result.location.x*density).toFloat(),(result.location.y*density).toFloat())
        var begin: Boolean?=null
        if(result.state==CurlRecognition.Began||result.state==CurlRecognition.Changed){
            recognized=true
            if(beganDirection==null&&result.commandBegin){beganDirection=result.beginDirection;pan.manualUncommitted=true;begin=forward(result.beginDirection)}
        }
        val terminal=(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL||
            event.actionMasked==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(index)==pan.trackedPointer)
        if(terminal){
            val commit=beganDirection?.let{result.state==CurlRecognition.Ended&&result.end.direction==it}
            val velocity=if(pan.axis==CurlAxis.Vertical)TurnPoint(0f,(result.end.suggestedVelocity*density).toFloat())
                else TurnPoint((result.end.suggestedVelocity*density).toFloat(),0f)
            return EffectInputUpdate(begin,lastPoint,recognized,commit,velocity,!recognized&&tap.state==CurlRecognition.Ended,true)
        }
        return EffectInputUpdate(begin,lastPoint,recognized||result.state==CurlRecognition.Failed)
    }
}
