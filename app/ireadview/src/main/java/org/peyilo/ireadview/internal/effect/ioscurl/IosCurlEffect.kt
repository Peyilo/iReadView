package org.peyilo.ireadview.internal.effect.ioscurl

import android.content.Context
import android.graphics.Canvas
import android.view.View
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.*
import org.peyilo.ireadview.internal.effect.input.*
import org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlRendering
import org.peyilo.ireadview.internal.effect.ioscurl.render.IosCurlScene
import org.peyilo.ireadview.spi.presentation.ReadViewport
import org.peyilo.pagecurl.control.*
import org.peyilo.pagecurl.gesture.CurlNavigation
import org.peyilo.pagecurl.layout.*
import org.peyilo.pagecurl.mesh.*
import org.peyilo.pagecurl.model.*

/** Transaction adapter: Kotlin algorithms know neither Android pages nor the navigation manager. */
internal class IosCurlEffect(private val options: IosCurlOptions) :
    SnapshotPageTurnEffect, PageTurnDynamics, EffectInputProvider, ViewPageTurnEffect {
    private var host: PageTurnEffectHost? = null
    private var context: PageTurnContext? = null
    private val snapshots = PageTurnSnapshots()
    private var material = CurlMaterial()
    private val rendering = IosCurlRendering(options.output)
    private var config: CurlConfig? = null
    private var layout: CurlPageLayout? = null
    private var begin: CurlBegin? = null
    private var values = CurlValues(0.0, 1.0, 0.0)
    private var elapsed = 0.0
    private var settleFrom = values
    private var settleTo = values
    private var duration = .25
    private var automatic = false
    private var texture = 0
    private var underlays = emptyList<Pair<Int, CurlRect>>()
    internal val hasRenderedFrame
        get() = rendering.hasFrame

    override val requiresCanvasRedraw
        get() = !rendering.usesGpu

    override val usesPageSnapshots
        get() = rendering.ready

    override fun createRenderView(context: Context): View =
        rendering.createView(context) { host?.requestRedraw() }

    override val warmingUp
        get() = !automatic && context != null && elapsed < .25

    override fun attach(host: PageTurnEffectHost) {
        this.host = host
    }

    override fun supports(direction: TurnDirection, viewport: ReadViewport) = viewport.isValid

    override fun start(context: PageTurnContext) {
        clear()
        rendering.beginPreparation()
        this.context = context
        val viewport = context.viewport
        val density = viewport.density.toDouble()
        val w = viewport.widthPx / density
        val h = viewport.heightPx / density
        val reverse =
            (context.direction == TurnDirection.Backward) !=
                (!context.vertical && viewport.rightToLeft)
        val binding =
            if (options.binding == IosCurlBinding.Minimum)
                if (context.vertical) CurlBinding.Top else CurlBinding.Left
            else if (context.vertical) CurlBinding.Bottom else CurlBinding.Right
        automatic = context.touchOrigin == null
        val initial =
            context.touchOrigin?.let { CurlPoint(it.xPx / density, it.yPx / density) }
                ?: CurlPoint(
                    0.0,
                    0.0,
                )
        val config =
            CurlConfig(
                w,
                h,
                initial,
                binding,
                if (reverse) CurlDirection.Reverse else CurlDirection.Forward,
                if (automatic) CurlKind.Automatic else CurlKind.Manual,
                false,
            )
        this.config = config
        val layout = CurlLayout.build(config)
        this.layout = layout
        begin = CurlControl.begin(config)
        values = requireNotNull(begin).from
        fun sourceTexture(source: CurlPageSource) = if (source.nativeValue < 2) 0 else 1
        texture = sourceTexture(layout.passes.single().source)
        underlays = layout.underlays.map { sourceTexture(it.source) to it.frame }
        val owner = host ?: throw EffectResourceUnavailable()
        try {
            val front = if (texture == 0) context.current else context.target
            val background = (front as? PageBackgroundSurface)?.backgroundColor
            if (
                background != null &&
                    android.graphics.Color.alpha(background) > 0 &&
                    rendering.capture(owner, context)
            ) {
                material = IosCurlMaterial.fromBackground(background, options.shadowOpacity)
                return
            }
            if (!snapshots.capture(owner, context)) throw EffectResourceUnavailable()
            val first = requireNotNull(snapshots.current)
            val second = requireNotNull(snapshots.target)
            material =
                IosCurlMaterial.resolve(
                    if (texture == 0) context.current else context.target,
                    if (texture == 0) first else second,
                    options.shadowOpacity,
                    background,
                )
            val width = first.width
            val height = first.height
            rendering.upload(
                first,
                second,
                if (rendering.canAccelerate) snapshots.retainForUpload() else null,
                context.current.isOpaque && context.target.isOpaque,
            )
            snapshots.close()
            rendering.bindOutput(owner, width, height, viewport.widthPx, viewport.heightPx)
        } catch (e: OutOfMemoryError) {
            clear()
            throw EffectResourceUnavailable()
        } catch (e: Throwable) {
            clear()
            throw e
        }
    }

    override fun update(frame: PageTurnFrame) {
        val config = config ?: return
        elapsed = frame.elapsedSeconds
        if (!automatic) {
            val density = requireNotNull(context).viewport.density.toDouble()
            val point =
                frame.touch?.let { CurlPoint(it.xPx / density, it.yPx / density) } ?: config.initial
            val target = CurlControl.update(config, point).renderValues()
            values =
                if (elapsed < .25)
                    CurlTiming.interpolate(
                        requireNotNull(begin).from,
                        target,
                        CurlControl.type(config),
                        false,
                        elapsed / .25,
                        .25,
                    )
                else target
        }
        render()
    }

    override fun beginSettling(commit: Boolean, velocity: TurnPoint?): Long {
        val config = config ?: return 0
        settleFrom = values.renderValues()
        if (automatic) {
            val b = requireNotNull(begin)
            settleFrom = b.from
            settleTo = b.to
            duration = b.durationSeconds
        } else {
            val endpoints = CurlControl.endpoints(config)
            settleTo = if (commit) endpoints.to else endpoints.from
            val speed =
                velocity?.let { if (config.binding.axis == CurlAxis.Vertical) it.yPx else it.xPx }
                    ?: 0f
            duration =
                CurlControl.duration(
                    config,
                    speed / requireNotNull(context).viewport.density.toDouble(),
                    commit,
                    settleFrom.time,
                )
        }
        return (duration * 1000).toLong().coerceAtLeast(1)
    }

    override fun updateSettling(frame: PageTurnFrame, elapsedFraction: Double) {
        val config = config ?: return
        values =
            CurlTiming.interpolate(
                settleFrom,
                settleTo,
                CurlControl.type(config),
                !automatic,
                elapsedFraction,
                duration,
            )
        render()
    }

    private fun render() {
        val config = config ?: return
        val ctx = context ?: return
        rendering.render(
            IosCurlScene(
                CurlMeshInput(
                    values.renderValues(),
                    config.width,
                    config.height,
                    ctx.viewport.density.toDouble().coerceIn(.1, 4.0),
                    material = material,
                    shadowSoftness = options.shadowSoftness.toDouble(),
                ),
                texture,
                underlays,
            )
        )
    }

    override fun draw(canvas: Canvas, frame: PageTurnFrame) = rendering.draw(canvas)

    override fun inputSession(
        viewport: ReadViewport,
        vertical: Boolean,
        available: (Boolean) -> Boolean,
    ): EffectInputSession = IosCurlInput(viewport, vertical, available)

    override fun tapForward(viewport: ReadViewport, vertical: Boolean, point: TurnPoint): Boolean? {
        val density = viewport.density.toDouble()
        val direction =
            CurlNavigation.tap(
                if (vertical) CurlAxis.Vertical else CurlAxis.Horizontal,
                viewport.widthPx / density,
                viewport.heightPx / density,
                CurlPoint(point.xPx / density, point.yPx / density),
                tablet = minOf(viewport.widthPx, viewport.heightPx) / density >= 600,
            )
        return if (direction < 0) null else (direction == 0) != (!vertical && viewport.rightToLeft)
    }

    private fun clear() {
        try {
            rendering.end()
        } finally {
            snapshots.close()
            context = null
            config = null
            layout = null
            begin = null
            elapsed = 0.0
            underlays = emptyList()
        }
    }

    override fun finish(outcome: TurnOutcome) = clear()

    override fun abort(reason: TurnAbortReason) = clear()

    override fun detach() {
        try {
            clear()
        } finally {
            try {
                rendering.close()
            } finally {
                host = null
            }
        }
    }
}
