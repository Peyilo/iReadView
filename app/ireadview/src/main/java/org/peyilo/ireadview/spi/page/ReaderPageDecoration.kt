package org.peyilo.ireadview.spi.page

import android.content.Context
import android.view.View

/** Main-thread factory. Create a fresh, unparented View for each page; prepared pages coexist.
 * Release partial resources if creation fails before returning the decoration.
 */
fun interface ReaderPageDecorationFactory {
    fun create(context: Context): ReaderPageDecoration
}

/** Any Android View or ViewGroup, with page-scoped data and cleanup callbacks. */
interface ReaderPageDecoration {
    /** Stable identity for this decoration's lifetime. May be any View or ViewGroup.
     * Uses normal Android event dispatch; the wrapper does not install touch/click listeners.
     * Custom gesture Views own their input stream by handling ACTION_DOWN and subsequent events.
     * Unconsumed input may reach the Widget's ordinary tap/page-turn handling.
     */
    val view: View
    /** Main thread; may run repeatedly, including before position/pageCount are known.
     * Keep the measured height stable when filling in page numbers, and replace old listeners.
     * Slot visibility/height follow ChromeStyle; other appearance belongs to this decoration.
     * PageStyle.padding surrounds all slots and the body on the shared page root;
     * the View's own padding remains independent.
     */
    fun bind(context: ReaderPageContext)
    /** Release listeners, tasks and page references. May also follow partial/failed binding. */
    fun unbind() {}
    /** Opt in to the Widget's existing clock; do not create a separate polling timer. */
    val needsTimeUpdates: Boolean get() = false
    /** Main-thread update for the active visible slot. Hidden candidates receive bind time only.
     * If this callback closes or rebinds the page, the wrapper stops the remaining slot updates.
     */
    fun onTimeChanged(timeMillis: Long) {}
}

/** Java-friendly defaults. Implement a stable [view] and [bind]; override cleanup when
 * binding installs listeners/tasks, and opt into time updates only when needed.
 * Resource and input ownership remain the same as [ReaderPageDecoration].
 */
abstract class ReaderPageDecorationAdapter : ReaderPageDecoration {
    override fun unbind() {}
    override val needsTimeUpdates: Boolean get() = false
    override fun onTimeChanged(timeMillis: Long) {}
}
