package org.peyilo.ireadview.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Parcel
import android.os.Parcelable
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.graphics.Rect
import android.widget.FrameLayout
import kotlinx.coroutines.*
import org.peyilo.ireadview.api.event.*
import org.peyilo.ireadview.api.state.*
import org.peyilo.ireadview.internal.container.NavigationController
import org.peyilo.ireadview.internal.releaseReaderResources
import org.peyilo.ireadview.internal.container.SupersededNavigation
import org.peyilo.ireadview.internal.effect.PageTurnContentPlayback
import org.peyilo.ireadview.internal.effect.PageTurnEffectManager
import org.peyilo.ireadview.internal.effect.PageTurnGesture
import org.peyilo.ireadview.internal.effect.input.EffectInputSession
import org.peyilo.ireadview.internal.state.ReaderConfigurationState
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.api.input.*
import org.peyilo.ireadview.R
import org.peyilo.ireadview.spi.presentation.*
import java.util.concurrent.atomic.AtomicReference

/** Common navigation and View lifetime. Subclasses supply content without replacing commit logic. */
abstract class BaseReadView<L : Any> @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lifetime = SupervisorJob()
    internal val readerScope: CoroutineScope get() = CoroutineScope(lifetime + Dispatchers.Main.immediate)
    protected var contentGeneration: Long = 0
        private set
    protected var isReleased: Boolean = false
        private set
    private var adapter: ReadContentAdapter<L>? = null
    private var adapterContentView: View? = null
    private var adapterBindingSequence = 0L
    private val listeners = LinkedHashSet<ReadViewListener<L>>()
    private var tapListener: ReaderTapListener? = null
    private var actionHandler: ReaderActionHandler? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressTask: Runnable? = null
    private var longPressConsumed = false
    private var gestureCancelled = false
    private var contentGestureOwned = false
    private var pageGesture: PageTurnGesture? = null
    private var pageGestureJob: Job? = null
    private var gestureAttempted = false
    private var velocityTracker: VelocityTracker? = null
    private var effectInput: EffectInputSession? = null
    private val handledKeys = HashSet<Int>()
    private var lastTurnUptime: Long? = null
    private var lastCapabilities: ReadNavigationCapabilities? = null
    private var lastCapabilitiesGeneration = -1L
    private var availabilityFailureGeneration = -1L
    var inputOptions: ReaderInputOptions = ReaderInputOptions()
        private set
    private var inputExplicitlySet = false
    private var turnExplicitlySet = false
    private var effectExplicitlySet = false
    private var effectConfigurationSequence = 0L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val effectsDelegate = lazy {
        PageTurnEffectManager(
            context,
            hardwareAccelerated = { this@BaseReadView.isHardwareAccelerated },
            fallback = { id, reason ->
                postEffectEvent({ onPageTurnEffectFallback(id, reason) }) {
                    it.onEffectFallback(this, id, "none", reason)
                }
            },
            captureConfigurationOwnership = ::captureEffectConfigurationOwnership,
            diagnostic = { error, notify ->
                // Logging remains available during release, when listener ownership has ended.
                Log.w("iReadView", error.message, error.cause)
                if (notify) postEffectEvent({ onPageTurnEffectError(error) }) {
                    it.onError(
                        this,
                        error
                    )
                }
            },
            mountOverlay = { overlay ->
                if (overlay.parent == null) addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            })
    }
    private val effects by effectsDelegate
    val pageTurnEffectId get() = effects.effectId
    val pageTurnOptions get() = effects.options
    protected val builtInPageTurnEffectId: String?
        get() = if (effectsDelegate.isInitialized()) PageTurnEffects.builtInId(effects.factory) else "none"

    init {
        isFocusable = true; isFocusableInTouchMode = true
    }

    fun setInputOptions(options: ReaderInputOptions): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        inputExplicitlySet = true
        if (inputOptions == options) return ReaderResult.Success(Unit)
        stopPageGesture()
        inputOptions = options
        return ReaderResult.Success(Unit)
    }

    protected fun setInitialInputOptions(options: ReaderInputOptions) {
        inputOptions = options
    }

    protected fun setInitialPageTurnOptions(options: PageTurnOptions) {
        effectConfigurationSequence++
        effects.setOptions(options)
    }

    // Initial configuration must not dispatch into subclass mode hooks during construction.
    protected fun setInitialPageTurnEffect(factory: PageTurnEffectFactory) {
        val sequence = ++effectConfigurationSequence
        val generation = contentGeneration
        effects.install(factory, enabled = false) {
            effectConfigurationIsCurrent(
                sequence,
                generation
            )
        }
    }

    protected open fun isPageTurnEffectActive(): Boolean = true
    protected open fun onPageTurnEffectFallback(id: String, reason: EffectFallbackReason) {}
    protected open fun onPageTurnEffectError(error: ReaderError) {}
    internal open fun onNavigationCleanupError(error: ReaderError, stillCurrent: () -> Boolean) {}
    protected open fun turnDirection(target: ReadNavigation<L>, from: L?, to: L): TurnDirection =
        if (target == ReadNavigation.Previous) TurnDirection.Backward else TurnDirection.Forward

    fun setPageTurnEffect(factory: PageTurnEffectFactory): ReaderResult<EffectSelection> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        effectExplicitlySet = true
        val sequence = ++effectConfigurationSequence
        val generation = contentGeneration
        fun current() = effectConfigurationIsCurrent(sequence, generation)
        stopPageGesture()
        if (!current()) return ReaderResult.Superseded
        navigation.interrupt(superseded = true)
        if (!current()) return ReaderResult.Superseded
        val enabled = isPageTurnEffectActive()
        if (!current()) return ReaderResult.Superseded
        val selection =
            effects.install(factory, enabled, ::current) ?: return ReaderResult.Superseded
        return ReaderResult.Success(selection)
    }

    fun setPageTurnOptions(options: PageTurnOptions): ReaderResult<Unit> {
        checkMainThread()
        if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
        turnExplicitlySet = true
        val sequence = ++effectConfigurationSequence
        val generation = contentGeneration
        fun current() = effectConfigurationIsCurrent(sequence, generation)
        stopPageGesture()
        if (!current()) return ReaderResult.Superseded
        navigation.interrupt(superseded = true)
        if (!current()) return ReaderResult.Superseded
        effects.abort(TurnAbortReason.EffectChanged)
        if (!current()) return ReaderResult.Superseded
        effects.setOptions(options)
        return ReaderResult.Success(Unit)
    }

    private val navigation = NavigationController<L>(
        { contentGeneration },
        { change ->
            if (change.reason == PositionChangeReason.UserNavigation || change.reason == PositionChangeReason.ProgrammaticNavigation) {
                lastTurnUptime = SystemClock.uptimeMillis()
            }
            val generation = contentGeneration
            onCommittedLocationChanged(change)
            if (contentGeneration == generation && currentLocation == change.to) dispatch({ currentLocation == change.to }) {
                it.onLocationChanged(
                    this,
                    change
                )
            }
            if (!isReleased && contentGeneration == generation && currentLocation == change.to && change.to != null && change.reason != PositionChangeReason.Scroll && isShown) sendAccessibilityEvent(
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            )
        },
        { state ->
            val generation = contentGeneration
            onNavigationStateChanged(state)
            if (contentGeneration == generation && navigationState == state) dispatch({ navigationState == state }) {
                it.onNavigationStateChanged(
                    this,
                    state
                )
            }
            if (!isReleased && contentGeneration == generation && navigationState == state) notifyNavigationCapabilitiesChanged()
        },
        { error, stillCurrent ->
            Log.w("iReadView", error.message, error.cause)
            post {
                fun current() = !isReleased && stillCurrent()
                if (current()) {
                    onNavigationCleanupError(error, ::current)
                    dispatch(::current) { it.onError(this, error) }
                }
            }
        }
    )
    val currentLocation: L? get() = navigation.location
    val navigationState: ReadNavigationState get() = navigation.state
    val navigationCapabilities: ReadNavigationCapabilities
        get() {
            checkMainThread()
            val common = when {
                isReleased -> NavigationAvailability.Released
                currentLocation == null -> NavigationAvailability.NotReady
                navigationState != ReadNavigationState.Idle -> NavigationAvailability.Busy
                adapter == null -> NavigationAvailability.NotReady
                else -> null
            }
            if (common != null) return ReadNavigationCapabilities(common, common)
            val observedAdapter = adapter!!
            val observedGeneration = contentGeneration
            fun query(target: ReadNavigation<L>): NavigationAvailability = try {
                observedAdapter.navigationAvailability(target)
            } catch (error: Exception) {
                if (availabilityFailureGeneration != contentGeneration) {
                    availabilityFailureGeneration = contentGeneration
                    Log.w("iReadView", "Navigation availability query failed", error)
                }
                NavigationAvailability.Unknown
            }

            val observed = ReadNavigationCapabilities(
                query(ReadNavigation.Previous),
                query(ReadNavigation.Next)
            )
            if (isReleased) return ReadNavigationCapabilities(
                NavigationAvailability.Released,
                NavigationAvailability.Released
            )
            if (observedGeneration != contentGeneration || observedAdapter !== adapter) return ReadNavigationCapabilities()
            if (navigationState != ReadNavigationState.Idle) return ReadNavigationCapabilities(
                NavigationAvailability.Busy,
                NavigationAvailability.Busy
            )
            return observed
        }

    protected open fun onNavigationCapabilitiesChanged(capabilities: ReadNavigationCapabilities) {}
    private fun notifyNavigationCapabilitiesChanged() {
        if (isReleased) return
        val generation = contentGeneration
        val observedAdapter = adapter
        val binding = adapterBindingSequence
        fun current() =
            !isReleased && generation == contentGeneration && observedAdapter === adapter && binding == adapterBindingSequence

        val capabilities = navigationCapabilities
        if (!current()) return
        if (lastCapabilitiesGeneration == generation && lastCapabilities == capabilities) return
        lastCapabilitiesGeneration = generation; lastCapabilities = capabilities
        onNavigationCapabilitiesChanged(capabilities)
        dispatch(::current) { it.onNavigationCapabilitiesChanged(this, capabilities) }
        if (current() && isShown) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    protected abstract fun createContentAdapter(): ReadContentAdapter<L>
    protected open fun onCommittedLocationChanged(change: ReadLocationChange<L>) {}
    protected open fun onNavigationStateChanged(state: ReadNavigationState) {}
    protected open fun onContainerAttached() {}
    protected open fun onContainerDetached() {}
    protected open fun onContainerViewportChanged() {}
    protected open fun onRelease() {}
    protected open fun onContentTap(xPx: Float, yPx: Float): Boolean = false
    protected open fun onContentLongPress(xPx: Float, yPx: Float): Boolean = false
    protected open fun isContentGestureBlocked(): Boolean = false
    protected open fun onContentTouch(event: MotionEvent): Boolean = false
    protected fun dispatchContentTap(xPx: Float, yPx: Float) {
        val generation = contentGeneration
        val viewport = viewportSnapshot()
        val tap = ReaderTap(
            ((xPx - paddingLeft) / viewport.widthPx.coerceAtLeast(1)).coerceIn(0f, 1f),
            ((yPx - paddingTop) / viewport.heightPx.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
        if (onContentTap(xPx, yPx) || tapListener?.onTap(tap) == true) return
        if (isReleased || generation != contentGeneration) return
        if (inputOptions.tapPagingEnabled && !isContentGestureBlocked() && currentLocation != null) {
            val vertical = inputOptions.pagingAxis == PagingAxis.Vertical
            val provider = effects.inputProvider
            if (provider != null) {
                provider.tapForward(viewport, vertical, touchPoint(xPx, yPx))?.let {
                    navigateFromInput(it, animated = true)
                    return
                }
                performClick()
                return
            }
            val coordinate = if (vertical) tap.y else tap.x
            val edge = inputOptions.tapEdgeFraction
            if (coordinate < edge || coordinate > 1f - edge) {
                var forward = coordinate > 1f - edge
                if (!vertical && layoutDirection == LAYOUT_DIRECTION_RTL) forward = !forward
                navigateFromInput(forward, animated = !vertical)
                return
            }
        }
        performClick()
    }

    protected fun ensureContentAdapter(): ReadContentAdapter<L> {
        checkMainThread()
        check(!isReleased) { "Widget has been released" }
        return adapter ?: createContentAdapter().also { created ->
            adapter = created
            val binding = ++adapterBindingSequence
            val host = object : ReadContentHost<L> {
                private fun current() =
                    !isReleased && adapter === created && binding == adapterBindingSequence

                override val generation get() = contentGeneration
                override val viewport get() = viewportSnapshot()
                override val committedLocation get() = currentLocation
                override suspend fun requestNavigation(target: ReadNavigation<L>): NavigationResult<L> {
                    checkMainThread()
                    if (isReleased) return NavigationResult.Rejected(ReaderRejection.Released)
                    if (!current()) return NavigationResult.Superseded
                    return navigate(target)
                }

                override fun reportVisiblePosition(
                    location: L,
                    generation: Long
                ): ReaderResult<Unit> {
                    checkMainThread()
                    if (isReleased) return ReaderResult.Rejected(ReaderRejection.Released)
                    if (!current()) return ReaderResult.Superseded
                    return navigation.reportVisible(location, generation)
                }

                override fun reportError(error: ReaderError) {
                    checkMainThread()
                    if (current()) dispatch(::current) { it.onError(this@BaseReadView, error) }
                }

                override fun reportNavigationCapabilitiesChanged(generation: Long) {
                    checkMainThread()
                    if (current() && generation == contentGeneration) notifyNavigationCapabilitiesChanged()
                }
            }
            val contentView = created.contentView
            adapterContentView = contentView
            addView(contentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            created.attach(host)
            created.onViewportChanged(viewportSnapshot())
        }
    }

    suspend fun navigate(
        target: ReadNavigation<L>,
        options: NavigationOptions = NavigationOptions()
    ): NavigationResult<L> =
        navigateWithReason(target, options, PositionChangeReason.ProgrammaticNavigation)

    protected suspend fun navigateWithReason(
        target: ReadNavigation<L>,
        options: NavigationOptions,
        reason: PositionChangeReason
    ): NavigationResult<L> =
        navigateInternal(target, options, reason, null)

    /** Checks request ownership before candidate display; ownership may change as display completes. */
    protected suspend fun navigateWithReason(
        target: ReadNavigation<L>, options: NavigationOptions,
        reason: PositionChangeReason, isCurrent: () -> Boolean
    ): NavigationResult<L> =
        navigateInternal(target, options, reason, null, isCurrent)

    private suspend fun navigateInternal(
        target: ReadNavigation<L>, options: NavigationOptions, reason: PositionChangeReason,
        gesture: PageTurnGesture?, isCurrent: (() -> Boolean)? = null
    ): NavigationResult<L> {
        checkMainThread()
        if (isReleased) return NavigationResult.Rejected(ReaderRejection.Released)
        if (isCurrent?.invoke() == false) return NavigationResult.Superseded
        val ordinaryNavigation =
            reason == PositionChangeReason.UserNavigation || reason == PositionChangeReason.ProgrammaticNavigation
        if (ordinaryNavigation && !options.force) {
            if (navigationState != ReadNavigationState.Idle) return NavigationResult.Rejected(
                ReaderRejection.Busy
            )
            if (options.respectInterval && lastTurnUptime?.let { SystemClock.uptimeMillis() - it < effects.options.minimumTurnIntervalMs } == true) {
                return NavigationResult.Rejected(ReaderRejection.Throttled)
            }
        }
        if (options.force) {
            stopPageGesture()
            if (effectsDelegate.isInitialized()) effects.abort(TurnAbortReason.Superseded)
        }
        val content = ensureContentAdapter()
        val transitioning = object : ReadContentAdapter<L> by content {
            override suspend fun prepare(target: ReadNavigation<L>): ReadPreparation<L> {
                currentCoroutineContext().ensureActive()
                if (isCurrent?.invoke() == false) throw SupersededNavigation()
                return content.prepare(target)
            }

            override suspend fun show(candidate: PreparedRead<L>): L {
                currentCoroutineContext().ensureActive()
                if (isCurrent?.invoke() == false) throw SupersededNavigation()
                val playback = PageTurnContentPlayback(content.contentView, content as? ReadVisualHandoff<*>)
                val show: suspend () -> L = {
                    playback.setActive(false)
                    @Suppress("UNCHECKED_CAST")
                    (content as? ReadVisualHandoff<L>)?.prepareVisualHandoff(candidate)
                    currentCoroutineContext().ensureActive()
                    if (candidate.generation != contentGeneration || isCurrent?.invoke() == false) throw SupersededNavigation()
                    content.show(candidate)
                }
                if ((!options.animated && gesture == null) || !ordinaryNavigation || !isPageTurnEffectActive() || currentLocation == null || currentLocation == candidate.target) return show()
                try {
                    return effects.transition(
                        content.currentSurface,
                        candidate.surface,
                        viewportSnapshot(),
                        turnDirection(target, currentLocation, candidate.target),
                        { snapshotsReady ->
                            if (effects.overlay.parent == null) addView(
                                effects.overlay,
                                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                            )
                            if (indexOfChild(effects.overlay) != childCount - 1) effects.overlay.bringToFront()
                            playback.setActive(snapshotsReady)
                        },
                        show,
                        gesture,
                        options.animated,
                        inputOptions.pagingAxis == PagingAxis.Vertical
                    ) { navigation.reportTransitionState(it) }
                } finally {
                    playback.setActive(false)
                }
            }
        }
        return ownedOperation {
            navigation.navigate(transitioning, target, options, reason) {
                if (gesture == null || gesture.decision != null) ReadNavigationState.Settling else ReadNavigationState.Dragging
            }
        }
    }

    suspend fun nextPage(options: NavigationOptions = NavigationOptions()) =
        navigate(ReadNavigation.Next, options)

    suspend fun previousPage(options: NavigationOptions = NavigationOptions()) =
        navigate(ReadNavigation.Previous, options)

    suspend fun firstPage() = navigate(ReadNavigation.First)
    suspend fun lastPage() = navigate(ReadNavigation.Last)

    fun navigate(
        target: ReadNavigation<L>,
        options: NavigationOptions,
        callback: ReaderCallback<NavigationResult<L>>
    ): ReaderRequest =
        submit(callback) { navigate(target, options) }

    fun nextPage(
        options: NavigationOptions,
        callback: ReaderCallback<NavigationResult<L>>
    ): ReaderRequest = submit(callback) { nextPage(options) }

    fun previousPage(
        options: NavigationOptions,
        callback: ReaderCallback<NavigationResult<L>>
    ): ReaderRequest = submit(callback) { previousPage(options) }

    fun firstPage(callback: ReaderCallback<NavigationResult<L>>): ReaderRequest =
        submit(callback) { firstPage() }

    fun lastPage(callback: ReaderCallback<NavigationResult<L>>): ReaderRequest =
        submit(callback) { lastPage() }

    fun addReadViewListener(listener: ReadViewListener<L>) {
        checkMainThread(); if (!isReleased) listeners.add(listener)
    }

    fun removeReadViewListener(listener: ReadViewListener<L>) {
        checkMainThread(); listeners.remove(listener)
    }

    fun setReaderTapListener(listener: ReaderTapListener?) {
        checkMainThread(); if (!isReleased) tapListener = listener
    }

    fun setReaderActionHandler(handler: ReaderActionHandler?) {
        checkMainThread()
        if (isReleased || actionHandler === handler) return
        actionHandler = handler
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    /** Synchronous host dispatch, independent of paging readiness and animation. */
    fun requestReaderAction(action: ReaderAction): Boolean {
        checkMainThread()
        if (isReleased || !isEnabled) return false
        val handler = actionHandler ?: return false
        return runCatching { handler.onAction(this, action) }
            .onFailure { Log.w("iReadView", "Reader action handler failed: $action", it) }
            .getOrDefault(false)
    }

    fun cancelTransition(): Boolean {
        checkMainThread()
        val wasPending = navigationState != ReadNavigationState.Idle
        stopPageGesture()
        return navigation.interrupt() || wasPending
    }

    protected fun invalidateContentGeneration(superseded: Boolean = true) {
        cancelLongPress()
        stopPageGesture()
        lastTurnUptime = null
        handledKeys.clear()
        contentGeneration++
        navigation.interrupt(superseded)
    }

    protected fun resetCommittedLocation(reason: PositionChangeReason = PositionChangeReason.Close) =
        navigation.reset(reason)

    protected fun clearContentAdapter() = detachAdapter()

    protected suspend fun <T> ownedOperation(block: suspend () -> T): T = coroutineScope {
        val operation = async(start = CoroutineStart.LAZY) { block() }
        val handle = lifetime.invokeOnCompletion { operation.cancel() }
        try {
            operation.await()
        } finally {
            handle.dispose()
        }
    }

    protected fun <T> submit(callback: ReaderCallback<T>, block: suspend () -> T): ReaderRequest {
        checkMainThread()
        val result = AtomicReference<ReaderCompletion<T>?>(null)
        // Public callback requests start after the current event, even when called on Main.
        val request = readerScope.launch(context = Dispatchers.Main, start = CoroutineStart.LAZY) {
            result.set(ReaderCompletion.Completed(block()))
        }
        request.invokeOnCompletion {
            val completion = result.get() ?: ReaderCompletion.Cancelled
            mainHandler.post {
                try {
                    callback.onComplete(completion)
                } catch (error: Exception) {
                    Log.w("iReadView", "Reader request callback failed", error)
                }
            }
        }
        request.start()
        return object : ReaderRequest {
            override fun cancel() {
                request.cancel()
            }

            override val isCompleted get() = request.isCompleted
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isReleased) return
        if (!lifetime.isActive) lifetime = SupervisorJob()
        val attachedLifetime = lifetime
        val generation = contentGeneration
        val content = ensureContentAdapter()
        val binding = adapterBindingSequence
        fun current() = !isReleased && lifetime === attachedLifetime && attachedLifetime.isActive &&
                generation == contentGeneration && adapter === content && binding == adapterBindingSequence
        if (!current()) return
        if (effectsDelegate.isInitialized()) {
            val enabled = isPageTurnEffectActive()
            if (!current()) return
            effects.reattach(enabled)
        }
        if (!current()) return
        onContainerAttached()
    }

    override fun onDetachedFromWindow() {
        try {
            if (!isReleased) {
                val oldLifetime = lifetime
                releaseReaderResources(
                    { invalidateContentGeneration(superseded = false) },
                    { oldLifetime.cancel() },
                    { onContainerDetached() },
                    { detachAdapter() },
                    { if (effectsDelegate.isInitialized()) effects.detach() }
                )
            }
        } finally {
            super.onDetachedFromWindow()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isReleased && (w != oldw || h != oldh)) {
            val invalidate = oldw > 0 && oldh > 0
            val generation = contentGeneration + if (invalidate) 1 else 0
            if (invalidate) invalidateContentGeneration()
            if (isReleased || generation != contentGeneration) return
            val content = adapter
            val binding = adapterBindingSequence
            content?.onViewportChanged(viewportSnapshot())
            if (isReleased || generation != contentGeneration || adapter !== content || binding != adapterBindingSequence) return
            onContainerViewportChanged()
        }
    }

    fun release() {
        checkMainThread()
        if (isReleased) return
        isReleased = true
        listeners.clear(); tapListener = null; actionHandler = null
        releaseReaderResources(
            { invalidateContentGeneration(superseded = false) },
            { lifetime.cancel() },
            { onRelease() },
            { navigation.reset() },
            { detachAdapter() },
            { if (effectsDelegate.isInitialized()) effects.detach() }
        )
    }

    private fun detachAdapter() {
        val old = adapter
        val oldView = adapterContentView
        adapter = null
        adapterContentView = null
        adapterBindingSequence++
        try {
            old?.detach()
        } finally {
            // Capture the bound View before plugin cleanup; getters may change or throw afterwards.
            // A reentrant binding may explicitly adopt the same View, which now belongs to it.
            if (oldView != null && oldView.parent === this && adapterContentView !== oldView) removeView(
                oldView
            )
        }
    }

    protected fun viewportSnapshot(): ReadViewport {
        val metrics = resources.displayMetrics
        return ReadViewport(
            (width - paddingLeft - paddingRight).coerceAtLeast(0),
            (height - paddingTop - paddingBottom).coerceAtLeast(0),
            metrics.density,
            resources.configuration.fontScale,
            layoutDirection == LAYOUT_DIRECTION_RTL
        )
    }

    protected fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "Reader Widget API must be called on the main thread" }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isReleased) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) contentGestureOwned = false
        val consumed = onContentTouch(event)
        if (consumed || contentGestureOwned) {
            contentGestureOwned =
                event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            if (pageGesture != null || effectInput != null) stopPageGesture()
            cancelLongPress(); longPressConsumed = false
            parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
            return true
        }
        if (effectInput != null || effects.inputProvider != null) return onEffectInput(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelLongPress(); longPressConsumed = false; gestureCancelled = false
                gestureAttempted = false
                recycleVelocityTracker()
                if (navigationState != ReadNavigationState.Idle) {
                    gestureCancelled = true; return true
                }
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                requestFocus()
                downX = event.x; downY = event.y
                val generation = contentGeneration
                longPressTask = Runnable {
                    longPressTask = null
                    if (!isReleased && generation == contentGeneration && onContentLongPress(
                            downX,
                            downY
                        )
                    ) {
                        longPressConsumed = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                }.also {
                    mainHandler.postDelayed(
                        it,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureCancelled || longPressConsumed) return true
                velocityTracker?.addMovement(event)
                if (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop) cancelLongPress()
                if (pageGesture == null && !gestureAttempted && isPagingDrag(event)) startPageGesture(
                    event
                )
                pageGesture?.move(touchPoint(event.x, event.y))
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (gestureCancelled) {
                    recycleVelocityTracker(); return true
                }
                if (longPressConsumed) {
                    longPressConsumed = false; recycleVelocityTracker(); return true
                }
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (isPagingDrag(event)) {
                    if (pageGesture == null && !gestureAttempted) startPageGesture(event)
                }
                if (pageGesture != null) {
                    pageGesture?.move(touchPoint(event.x, event.y))
                    velocityTracker?.computeCurrentVelocity(
                        1000,
                        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
                    )
                    val gesture = pageGesture!!
                    val velocity = gesture.signedVelocity(
                        (if (gesture.vertical) velocityTracker?.yVelocity else velocityTracker?.xVelocity)
                            ?: 0f
                    )
                    val threshold =
                        effects.options.flingVelocityDpPerSecond * resources.displayMetrics.density
                    val commit =
                        velocity > -threshold && (gesture.progress >= effects.options.commitDistanceFraction ||
                                velocity >= threshold && gesture.progress * gesture.extent > touchSlop)
                    releasePageGesture(commit)
                } else if (!gestureAttempted && kotlin.math.abs(dx) <= touchSlop && kotlin.math.abs(
                        dy
                    ) <= touchSlop
                ) {
                    dispatchContentTap(event.x, event.y)
                }
                recycleVelocityTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                cancelLongPress(); longPressConsumed = false; gestureCancelled = true
                releasePageGesture(false)
                recycleVelocityTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Optional effect recognizers stay behind a generic session; the Widget owns arbitration,
     * long-press selection, candidate preparation, commit/cancel and lifecycle interruption. */
    private fun onEffectInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelLongPress(); longPressConsumed = false; gestureCancelled =
                false; gestureAttempted = false
            recycleVelocityTracker()
            if (navigationState != ReadNavigationState.Idle) {
                gestureCancelled = true; return true
            }
            downX = event.x; downY = event.y
            val viewport = viewportSnapshot()
            effectInput = effects.inputProvider?.inputSession(
                viewport,
                inputOptions.pagingAxis == PagingAxis.Vertical
            ) { forward ->
                if (!inputOptions.gesturePagingEnabled || isContentGestureBlocked() || !isPageTurnEffectActive() || currentLocation == null) false
                else {
                    val caps = navigationCapabilities
                    val value = if (forward) caps.next else caps.previous
                    value == NavigationAvailability.Available || value == NavigationAvailability.Unknown
                }
            }
            requestFocus()
            val generation = contentGeneration
            longPressTask = Runnable {
                longPressTask = null
                if (!isReleased && generation == contentGeneration && onContentLongPress(
                        downX,
                        downY
                    )
                ) {
                    longPressConsumed = true; effectInput = null
                    parent?.requestDisallowInterceptTouchEvent(true)
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            }.also { mainHandler.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
        }
        if (gestureCancelled || longPressConsumed) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                cancelLongPress(); effectInput = null; longPressConsumed = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        val update = effectInput?.event(event, paddingLeft, paddingTop) ?: return true
        if (update.moved || event.pointerCount > 1) cancelLongPress()
        update.beginForward?.let { forward ->
            if (!gestureAttempted) {
                val vertical = inputOptions.pagingAxis == PagingAxis.Vertical
                val point = update.point ?: touchPoint(event.x, event.y)
                beginPageGesture(forward, vertical, point, point)
            }
        }
        update.point?.let { pageGesture?.move(it) }
        if (update.terminal) {
            cancelLongPress()
            val gesture = pageGesture
            if (gesture != null) {
                gesture.release(update.release == true, update.velocity)
                if (update.release != true && !gesture.started) pageGestureJob?.cancel()
            } else if (!gestureAttempted && update.tap) dispatchContentTap(event.x, event.y)
            effectInput = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun touchPoint(x: Float, y: Float) = TurnPoint(x - paddingLeft, y - paddingTop)
    private fun isPagingDrag(event: MotionEvent): Boolean {
        val horizontal = kotlin.math.abs(event.x - downX)
        val vertical = kotlin.math.abs(event.y - downY)
        // The configured paging axis wins an exact diagonal tie. Once acquired,
        // the gesture keeps its original axis throughout dragging and settling.
        return if (inputOptions.pagingAxis == PagingAxis.Vertical) vertical > touchSlop && vertical >= horizontal
        else horizontal > touchSlop && horizontal >= vertical
    }

    private fun startPageGesture(event: MotionEvent) {
        val vertical = inputOptions.pagingAxis == PagingAxis.Vertical
        val delta = if (vertical) event.y - downY else event.x - downX
        val forward =
            if (!vertical && layoutDirection == LAYOUT_DIRECTION_RTL) delta > 0 else delta < 0
        beginPageGesture(forward, vertical, touchPoint(downX, downY), touchPoint(event.x, event.y))
    }

    private fun beginPageGesture(
        forward: Boolean,
        vertical: Boolean,
        origin: TurnPoint,
        touch: TurnPoint
    ) {
        gestureAttempted = true
        val viewport = viewportSnapshot()
        val extent = (if (vertical) viewport.heightPx else viewport.widthPx).toFloat()
        if (isContentGestureBlocked() || !isPageTurnEffectActive() || !inputOptions.gesturePagingEnabled || currentLocation == null ||
            navigationState != ReadNavigationState.Idle || extent <= 0
        ) return
        val sign =
            if (forward != (!vertical && layoutDirection == LAYOUT_DIRECTION_RTL)) -1f else 1f
        val gesture = PageTurnGesture(origin, forward, vertical, sign, extent)
        gesture.move(touch)
        pageGesture = gesture
        parent?.requestDisallowInterceptTouchEvent(true)
        val job = readerScope.launch(start = CoroutineStart.LAZY) {
            try {
                navigateInternal(
                    if (gesture.forward) ReadNavigation.Next else ReadNavigation.Previous,
                    NavigationOptions(animated = !gesture.vertical || effects.inputProvider != null),
                    PositionChangeReason.UserNavigation,
                    gesture
                )
            } finally {
                if (pageGesture === gesture) {
                    pageGesture = null; pageGestureJob = null
                }
            }
        }
        pageGestureJob = job
        job.start()
    }

    private fun releasePageGesture(commit: Boolean) {
        val gesture = pageGesture ?: return
        gesture.release(commit)
        if (!commit && !gesture.started) pageGestureJob?.cancel()
    }

    private fun stopPageGesture() {
        cancelLongPress()
        effectInput = null
        gestureCancelled = true
        pageGesture?.release(false)
        pageGestureJob?.cancel()
        pageGesture = null; pageGestureJob = null
        recycleVelocityTracker()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle(); velocityTracker = null
    }

    private fun navigateFromInput(forward: Boolean, animated: Boolean = true) {
        readerScope.launch {
            navigateWithReason(
                if (forward) ReadNavigation.Next else ReadNavigation.Previous,
                NavigationOptions(animated = animated), PositionChangeReason.UserNavigation
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in handledKeys) return true
        if (isReleased || !isFocused || currentLocation == null || isContentGestureBlocked() ||
            event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
        ) return super.onKeyDown(keyCode, event)
        val volume =
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (if (volume) !inputOptions.volumeKeyPagingEnabled else !inputOptions.keyboardPagingEnabled) return super.onKeyDown(
            keyCode,
            event
        )
        val forward = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> false
            KeyEvent.KEYCODE_DPAD_RIGHT -> layoutDirection != LAYOUT_DIRECTION_RTL
            KeyEvent.KEYCODE_DPAD_LEFT -> layoutDirection == LAYOUT_DIRECTION_RTL
            KeyEvent.KEYCODE_SPACE -> !event.isShiftPressed
            else -> return super.onKeyDown(keyCode, event)
        }
        handledKeys.add(keyCode)
        if (event.repeatCount == 0 && !event.isCanceled) navigateFromInput(
            forward,
            inputOptions.pagingAxis != PagingAxis.Vertical
        )
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (handledKeys.remove(keyCode)) true else super.onKeyUp(keyCode, event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) handledKeys.clear()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!isReleased && isEnabled && actionHandler != null) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.iread_action_contents,
                    context.getString(R.string.iread_accessibility_contents)
                )
            )
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.iread_action_settings,
                    context.getString(R.string.iread_accessibility_settings)
                )
            )
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.iread_action_exit,
                    context.getString(R.string.iread_accessibility_exit)
                )
            )
        }
        if (!isReleased && isEnabled && currentLocation != null && navigationState == ReadNavigationState.Idle && !isContentGestureBlocked()) {
            val capabilities = navigationCapabilities
            fun actionable(value: NavigationAvailability) =
                value == NavigationAvailability.Available || value == NavigationAvailability.Unknown
            info.isScrollable = actionable(capabilities.next) || actionable(capabilities.previous)
            if (actionable(capabilities.next)) info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    context.getString(R.string.iread_accessibility_next_page)
                )
            )
            if (actionable(capabilities.previous)) info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    context.getString(R.string.iread_accessibility_previous_page)
                )
            )
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val hostAction = when (action) {
            R.id.iread_action_contents -> ReaderAction.ShowTableOfContents
            R.id.iread_action_settings -> ReaderAction.ShowSettings
            R.id.iread_action_exit -> ReaderAction.ExitReader
            else -> null
        }
        if (hostAction != null) return requestReaderAction(hostAction)
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (isReleased || !isEnabled || currentLocation == null || navigationState != ReadNavigationState.Idle || isContentGestureBlocked()) return false
            val availability =
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) navigationCapabilities.next else navigationCapabilities.previous
            if (availability != NavigationAvailability.Available && availability != NavigationAvailability.Unknown) return false
            navigateFromInput(
                action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                inputOptions.pagingAxis != PagingAxis.Vertical
            )
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onSaveInstanceState(): Parcelable = ConfigurationSavedState(
        super.onSaveInstanceState(),
        ReaderConfigurationState(
            inputOptions,
            if (effectsDelegate.isInitialized()) effects.options else PageTurnOptions(),
            builtInPageTurnEffectId
        )
    )

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is ConfigurationSavedState) {
            super.onRestoreInstanceState(state); return
        }
        super.onRestoreInstanceState(state.superState)
        if (isReleased) return
        state.configuration?.let { saved ->
            if (!inputExplicitlySet) saved.input?.let { setInitialInputOptions(it) }
            if (!turnExplicitlySet) saved.turn?.let { setInitialPageTurnOptions(it) }
            if (!effectExplicitlySet) saved.builtInEffectId?.let { PageTurnEffects.fromId(it) }
                ?.let { setInitialPageTurnEffect(it) }
        }
    }

    internal class ConfigurationSavedState : androidx.customview.view.AbsSavedState {
        val configuration: ReaderConfigurationState?

        constructor(superState: Parcelable?, configuration: ReaderConfigurationState) : super(
            superState ?: EMPTY_STATE
        ) {
            this.configuration = configuration
        }

        constructor(parcel: Parcel, loader: ClassLoader? = null) : super(
            parcel,
            loader ?: ConfigurationSavedState::class.java.classLoader
        ) {
            configuration = ReaderConfigurationState.fromBundle(
                parcel.readBundle(
                    loader ?: javaClass.classLoader
                )
            )
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags); out.writeBundle(configuration?.toBundle())
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.ClassLoaderCreator<ConfigurationSavedState> =
                object : Parcelable.ClassLoaderCreator<ConfigurationSavedState> {
                    override fun createFromParcel(source: Parcel) = ConfigurationSavedState(source)
                    override fun createFromParcel(source: Parcel, loader: ClassLoader?) =
                        ConfigurationSavedState(source, loader)

                    override fun newArray(size: Int): Array<ConfigurationSavedState?> =
                        arrayOfNulls(size)
                }
        }
    }

    final override fun cancelLongPress() {
        super.cancelLongPress(); longPressTask?.let { mainHandler.removeCallbacks(it) }; longPressTask =
            null
    }

    override fun performClick(): Boolean {
        super.performClick(); return true
    }

    /** Configuration attempts and deferred events share content/configuration ownership. */
    private fun effectConfigurationIsCurrent(sequence: Long, generation: Long) =
        !isReleased && generation == contentGeneration && sequence == effectConfigurationSequence

    internal fun captureEffectConfigurationOwnership(): () -> Boolean {
        val generation = contentGeneration
        val sequence = effectConfigurationSequence
        return { effectConfigurationIsCurrent(sequence, generation) }
    }

    private fun postEffectEvent(hook: () -> Unit, event: (ReadViewListener<L>) -> Unit) {
        val current = captureEffectConfigurationOwnership()
        post {
            if (current()) {
                hook()
                dispatch(current, event)
            }
        }
    }

    private fun dispatch(
        stillCurrent: () -> Boolean = { true },
        event: (ReadViewListener<L>) -> Unit
    ) {
        val generation = contentGeneration
        listeners.toList().forEach { listener ->
            if (contentGeneration == generation && !isReleased && stillCurrent()) {
                try {
                    event(listener)
                } catch (error: Exception) {
                    Log.w("iReadView", "Reader listener failed", error)
                }
            }
        }
    }
}
