package org.peyilo.iread.demo

import android.graphics.Rect
import android.os.SystemClock
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.peyilo.iread.demo.ui.reader.ReaderActivity
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.input.*
import org.peyilo.ireadview.api.model.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.source.MemoryBookSource
import org.peyilo.ireadview.widget.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Full Demo/Widget/input/transaction integration, independent of the numerical oracle tests. */
@RunWith(AndroidJUnit4::class)
class IosCurlDemoTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    @Test fun actualReaderTracksCancelsCommitsAndHandlesAxesWithoutFallback() {
        ActivityScenario.launch(ReaderActivity::class.java).use { scenario->
            await(scenario){it.readerState.phase==ReaderPhase.Ready}
            request<ReaderResult<ModeChange>>(scenario){r,c->r.setReadingMode(ReadingMode.PAGED,c)}
            val book="kotlin-curl-${System.nanoTime()}"
            val source=MemoryBookSource(BookDescriptor(book,"Kotlin curl","v1",listOf(TocEntry.Chapter(ChapterDescriptor("ch","Chapter","v1")))),
                listOf(ChapterContent(book,"ch","v1",(0 until 100).map{TextBlock("p$it","Page curl Kotlin integration $it. ".repeat(80))})))
            assertTrue(request<OpenResult>(scenario){r,c->r.openBook(source,callback=c)} is OpenResult.Opened)
            val fallback=mutableListOf<String>()
            scenario.onActivity {
                val r=it.findViewById<ReaderView>(R.id.reader)
                r.setPageTurnEffect(PageTurnEffects.iosCurl());r.setPageTurnOptions(PageTurnOptions(bitmapBudgetBytes=32L*1024*1024))
                r.setInputOptions(ReaderInputOptions())
                r.addReaderListener(object:ReaderListener {
                    override fun onEffectFallback(view:BookReadView,requestedId:String,appliedId:String,reason:EffectFallbackReason){fallback+="$requestedId:$reason"}
                })
            }
            request<NavigationResult<ReadingPosition>>(scenario){r,c->r.nextPage(NavigationOptions(),c)}
            val afterAuto=position(scenario)
            drag(scenario,.9f,.18f,commit=true)
            assertNotEquals(afterAuto,position(scenario))
            val afterForward=position(scenario)
            drag(scenario,.1f,.84f,commit=true)
            assertEquals(afterAuto,position(scenario))
            drag(scenario,.9f,.45f,commit=false)
            assertEquals(afterAuto,position(scenario))
            // Core recognizes the first pointer throughout an active second-pointer sequence.
            drag(scenario,.9f,.18f,commit=true,secondPointer=true)
            assertEquals(afterForward,position(scenario))
            scenario.onActivity{it.findViewById<ReaderView>(R.id.reader).layoutDirection=View.LAYOUT_DIRECTION_RTL}
            val beforeRtl=position(scenario);drag(scenario,.1f,.84f,commit=true)
            assertNotEquals(beforeRtl,position(scenario))
            scenario.onActivity{val r=it.findViewById<ReaderView>(R.id.reader);r.layoutDirection=View.LAYOUT_DIRECTION_LTR;r.setInputOptions(ReaderInputOptions(pagingAxis=PagingAxis.Vertical))}
            val beforeVertical=position(scenario);drag(scenario,.9f,.16f,commit=true,vertical=true)
            assertNotEquals(beforeVertical,position(scenario))
            scenario.onActivity{val r=it.findViewById<ReaderView>(R.id.reader);assertEquals("ios_curl",r.pageTurnEffectId);assertTrue("fallbacks=$fallback",fallback.isEmpty());r.cancelTransition()}
            // Resource fallback must preserve the already-owned original gesture decision.
            // The move is shorter than the generic commit fraction; it still commits under
            // the original release algorithm. No bitmap allocation can fit this budget.
            scenario.onActivity{val r=it.findViewById<ReaderView>(R.id.reader);r.setInputOptions(ReaderInputOptions());r.setPageTurnOptions(PageTurnOptions(bitmapBudgetBytes=1))}
            val beforeFallback=position(scenario);drag(scenario,.6f,.4f,commit=true)
            assertNotEquals(beforeFallback,position(scenario))
            scenario.onActivity{val r=it.findViewById<ReaderView>(R.id.reader);assertEquals("none",r.pageTurnEffectId)
                assertEquals(listOf("ios_curl:ResourcesUnavailable"),fallback)}
        }
    }
    @Test fun realSettingsSelectAndRestoreIosKotlinFactory() {
        ActivityScenario.launch(ReaderActivity::class.java).use{scenario->
            await(scenario){it.readerState.phase==ReaderPhase.Ready}
            scenario.onActivity{it.javaClass.getDeclaredMethod("showReaderSettings").apply{isAccessible=true}.invoke(it)}
            SystemClock.sleep(500)
            onView(withText("iOSCurl")).inRoot(isDialog()).perform(click())
            await(scenario){it.pageTurnEffectId=="ios_curl"&&it.readingMode==ReadingMode.PAGED}
            scenario.onActivity{it.javaClass.getDeclaredMethod("showReaderSettings").apply{isAccessible=true}.invoke(it)}
            onView(withText("iOSCurl")).inRoot(isDialog()).check{view,error->if(error!=null)throw error;assertTrue(view.isSelected)}
            onView(withText("GoogleCurl")).inRoot(isDialog()).perform(click())
            await(scenario){it.pageTurnEffectId=="google_curl"&&it.readingMode==ReadingMode.PAGED}
        }
    }
    private fun position(scenario:ActivityScenario<ReaderActivity>):ReadingPosition? {var p:ReadingPosition?=null;scenario.onActivity{p=it.findViewById<ReaderView>(R.id.reader).currentPosition};return p}
    private fun drag(scenario:ActivityScenario<ReaderActivity>,from:Float,to:Float,commit:Boolean,vertical:Boolean=false,secondPointer:Boolean=false) {
        val rect=Rect();scenario.onActivity{it.findViewById<ReaderView>(R.id.reader).getGlobalVisibleRect(rect)}
        val down=SystemClock.uptimeMillis()
        fun pointer(action:Int,fraction:Float,multi:Boolean=false){
            val x=rect.left+rect.width()*(if(vertical).65f else fraction);val y=rect.top+rect.height()*(if(vertical)fraction else .65f)
            val event=if(!multi)MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                else MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,
                    arrayOf(MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER},MotionEvent.PointerProperties().apply{id=7;toolType=MotionEvent.TOOL_TYPE_FINGER}),
                    arrayOf(MotionEvent.PointerCoords().apply{this.x=x;this.y=y;pressure=1f;size=1f},MotionEvent.PointerCoords().apply{this.x=x+20;this.y=y-20;pressure=1f;size=1f}),
                    0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            try{assertTrue(instrument.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
            SystemClock.sleep(32)
        }
        pointer(MotionEvent.ACTION_DOWN,from)
        pointer(MotionEvent.ACTION_MOVE,(from+to)*.5f)
        await(scenario){it.navigationState==ReadNavigationState.Dragging}
        if(secondPointer){pointer(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),(from+to)*.5f,true)
            pointer(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),(from+to)*.5f,true)}
        SystemClock.sleep(300)
        pointer(MotionEvent.ACTION_MOVE,to)
        if(!commit){pointer(MotionEvent.ACTION_MOVE,.65f);pointer(MotionEvent.ACTION_MOVE,.85f);pointer(MotionEvent.ACTION_MOVE,.96f)}
        pointer(MotionEvent.ACTION_UP,if(commit)to else .96f)
        await(scenario){it.navigationState==ReadNavigationState.Idle}
    }
    private fun await(scenario:ActivityScenario<ReaderActivity>,predicate:(ReaderView)->Boolean) {
        val until=SystemClock.uptimeMillis()+30000
        while(true){var done=false;scenario.onActivity{done=predicate(it.findViewById(R.id.reader))};if(done)return
            check(SystemClock.uptimeMillis()<until){"Reader did not reach expected state"};SystemClock.sleep(25)}
    }
    private fun <T> request(scenario:ActivityScenario<ReaderActivity>,action:(ReaderView,ReaderCallback<T>)->Unit):T {
        val result=LinkedBlockingQueue<ReaderCompletion<T>>()
        scenario.onActivity{action(it.findViewById(R.id.reader),ReaderCallback{v->result.add(v)})}
        val completion=result.poll(30,TimeUnit.SECONDS)?:error("Reader request timeout")
        return (completion as? ReaderCompletion.Completed<T>)?.value?:error("Reader request $completion")
    }
}
