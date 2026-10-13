package org.peyilo.ireadview.api.state

/** A main-thread observation, not a reservation. Unknown may be resolved by preparing a request. */
enum class NavigationAvailability { Available, AtBoundary, Unknown, NotReady, Busy, Released }

data class ReadNavigationCapabilities(
    val previous: NavigationAvailability = NavigationAvailability.NotReady,
    val next: NavigationAvailability = NavigationAvailability.NotReady
)
