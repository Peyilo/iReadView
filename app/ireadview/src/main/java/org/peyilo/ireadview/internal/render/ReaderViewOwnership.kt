package org.peyilo.ireadview.internal.render

import android.view.View
import java.util.IdentityHashMap

/** Main-thread reservations shared by all Widget factory views, including unattached candidates. */
internal object ReaderViewOwnership {
    private val owners = IdentityHashMap<View, Any>()

    fun acquire(view: View, owner: Any): Boolean {
        if (view.parent != null || owners.containsKey(view)) return false
        owners[view] = owner
        return true
    }

    /** A stale cleanup cannot release another binding's reservation. */
    fun release(view: View, owner: Any) {
        if (owners[view] === owner) owners.remove(view)
    }
}
