package org.peyilo.ireadview.spi.page

import android.view.View
import org.peyilo.ireadview.internal.presentation.ReaderPageOwnership

/** Main-thread reservation shared by built-in and extension presentations. Does not unbind the template. */
class ReaderPageLease private constructor(private val template: ReaderPageTemplate) :
    AutoCloseable {
    private var closed = false
    override fun close() {
        if (closed) return
        closed = true
        ReaderPageOwnership.release(template)
    }

    companion object {
        /** Reject a reused/malformed template without taking ownership or unbinding another reader's page. */
        @JvmStatic
        fun acquire(template: ReaderPageTemplate): ReaderPageLease {
            val root = template.root
            var node: View? = template.bodyHost
            while (node != null && node !== root) node = node.parent as? View
            require(root.parent == null && node === root) { "Page factory must return a fresh root containing bodyHost" }
            require(
                ReaderPageOwnership.acquire(
                    template,
                    root
                )
            ) { "Page template is already reserved" }
            return ReaderPageLease(template)
        }
    }
}
