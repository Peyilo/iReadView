package org.peyilo.ireadview.internal.effect

import android.annotation.TargetApi
import android.graphics.*
import org.peyilo.ireadview.api.effect.PageCurlOptions

/** Same material shader on the window GPU; no EGL readback or CPU bitmap upload per frame. */
@TargetApi(33)
internal class BoundPageCurlRuntime {
    private val shader = RuntimeShader(source())
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@BoundPageCurlRuntime.shader }
    private val rect = RectF()
    private val empty = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
    fun bind(current:Bitmap,target:Bitmap,width:Int,height:Int) {
        rect.set(0f,0f,width.toFloat(),height.toFloat())
        fun texture(bitmap:Bitmap):BitmapShader {
            bitmap.setHasMipMap(true)
            return BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height) })
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
        }
        shader.setInputShader("currentPage",texture(current));shader.setInputShader("nextPage",texture(target))
        shader.setFloatUniform("pageSize",width.toFloat(),height.toFloat())
    }
    fun update(progress:Float,aspect:Float,tilt:Float,rtl:Boolean,options:PageCurlOptions,tint:Int) {
        shader.setFloatUniform("progress",progress);shader.setFloatUniform("aspect",aspect)
        shader.setFloatUniform("tilt",tilt);shader.setFloatUniform("radiusFraction",options.radiusFraction)
        shader.setFloatUniform("shadowStrength",options.shadowStrength);shader.setFloatUniform("lightSize",options.lightSize)
        shader.setFloatUniform("lighting",1f);shader.setFloatUniform("rightToLeft",if(rtl)1f else 0f)
        shader.setFloatUniform("backContentAlpha",options.backContentAlpha)
        shader.setFloatUniform("backTint",Color.red(tint)/255f,Color.green(tint)/255f,Color.blue(tint)/255f)
    }
    fun draw(canvas:Canvas) { canvas.drawRect(rect,paint) }
    fun clearPages() {
        val blank=BitmapShader(empty,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
        shader.setInputShader("currentPage",blank);shader.setInputShader("nextPage",blank)
    }
    private fun source():String {
        val fragment=boundPageCurlFragment
        val from=fragment.indexOf("vec4 samplePage(")
        val to=fragment.indexOf("const float PI",from)
        val sample=fragment.substring(from,to)
        val current=sample.replace("samplePage(sampler2D page,vec2 q)","sampleCurrent(vec2 q)")
            .replace("texture2D(page,coord)","currentPage.eval(coord*pageSize)")
        val next=sample.replace("samplePage(sampler2D page,vec2 q)","sampleNext(vec2 q)")
            .replace("texture2D(page,coord)","nextPage.eval(coord*pageSize)")
        return fragment.replaceRange(from,to,current+next)
            .replace("precision highp float;","")
            .replace("varying vec2 screenUv;","float2 screenUv; float2 fragmentCoord; uniform float2 pageSize;")
            .replace("#define color gl_FragColor","float4 color;")
            .replace("uniform sampler2D","uniform shader")
            .replace("#define uv pageUv()","")
            .replace(Regex("\\buv\\b"),"pageUv()")
            .replace("samplePage(currentPage,","sampleCurrent(").replace("samplePage(nextPage,","sampleNext(")
            .replace("gl_FragCoord.xy","fragmentCoord")
            .replace("void main(){shadePage();color.rgb*=color.a;}",
                "half4 main(float2 coord){screenUv=float2(coord.x/pageSize.x,1.-coord.y/pageSize.y);fragmentCoord=float2(coord.x,pageSize.y-coord.y);shadePage();return half4(color.rgb*color.a,color.a);}")
            .replace(Regex("\\bvec([234])\\b"),"float$1")
    }
}
