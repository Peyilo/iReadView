package org.peyilo.pagecurl.layout

import org.peyilo.pagecurl.model.*

/** Source identity is separate from page numbering and texture ownership. */
enum class CurlPageSource(val nativeValue: Int) { Outgoing0(0), Outgoing1(1), Incoming0(2), Incoming1(3) }
data class CurlStaticPage(val source: CurlPageSource,val frame: CurlRect)
data class CurlPagePass(val source: CurlPageSource,val frame: CurlRect,val front: Boolean,val back: Boolean,
    val mirrorContent: Boolean=false,val transparentBackMaterial: Boolean=false)
data class CurlPageLayout(val passes: List<CurlPagePass>,val underlays: List<CurlStaticPage>,val outgoing: List<CurlStaticPage>,val incoming: List<CurlStaticPage>)

/** Supports edge and middle bindings in the portable core; Android currently exposes one sheet. */
object CurlLayout {
    fun build(c: CurlConfig,rect: CurlRect=CurlRect(0.0,0.0,c.width,c.height)): CurlPageLayout {
        val middle=c.binding.middle;val d=c.direction.nativeValue
        val minimum=c.binding==CurlBinding.Left||c.binding==CurlBinding.Top
        fun half(first: Boolean): CurlRect = when(c.binding){
            CurlBinding.MiddleHorizontal->rect.copy(x=rect.x+if(first)0.0 else rect.width*.5,width=rect.width*.5)
            CurlBinding.MiddleVertical->rect.copy(y=rect.y+if(first)0.0 else rect.height*.5,height=rect.height*.5)
            else->rect
        }
        val frame=half(d==1)
        val front=if(minimum)if(d==1)2 else 0 else if(middle)if(d==1)0 else 1 else if(d==1)0 else 2
        val back=if(c.doubleSided)if(middle)if(d==1)3 else 2 else 3 else -1
        val sources=CurlPageSource.entries
        val passes=mutableListOf(CurlPagePass(sources[front],frame,true,back<0))
        if(back>=0)passes+=CurlPagePass(sources[back],frame,false,true,true,true)
        val count=if(middle)2 else 1
        val outgoing=(0 until count).map{CurlStaticPage(sources[it],half(it==0&&(middle||!minimum)))}
        val incoming=outgoing.mapIndexed{i,p->p.copy(source=sources[i+2])}
        val under=outgoing.filter{it.source.nativeValue!=front&&it.source.nativeValue!=back}.toMutableList()
        if(d==0&&(minimum||middle))under.add(0,CurlStaticPage(if(middle)CurlPageSource.Incoming1 else CurlPageSource.Incoming0,half(false)))
        if(d==1&&(middle||!minimum))under.add(0,CurlStaticPage(CurlPageSource.Incoming0,half(true)))
        return CurlPageLayout(passes,under,outgoing,incoming)
    }
    fun incomingPages(c: CurlConfig,current: Int): IntArray {
        val sign=if(c.direction==CurlDirection.Forward)1 else -1
        return if(c.binding.middle)intArrayOf(current+2*sign,current+2*sign+1)
        else if(c.doubleSided)intArrayOf(current+2*sign,current+sign) else intArrayOf(current+sign)
    }
    fun sourcePage(c: CurlConfig,current: Int,source: CurlPageSource): Int =
        if(source.nativeValue<2)current+source.nativeValue else incomingPages(c,current)[source.nativeValue-2]
}
