package org.peyilo.ireadview.spi.content

/** Built-in strategies exposed as public contracts for decoration and composition. */
object BlockLayoutProviders {
    /** Fresh, stateless provider for TextContentBlock (including headings and linked text).
     * Call on layout workers using the engine-supplied context; do not add engine padding.
     * A wrapper must use its own id/version and forward selection/accessibility geometry
     * when decorating fragments. Register only the wrapper; built-in fallback remains automatic.
     */
    @JvmStatic
    fun text(): BlockLayoutProvider = org.peyilo.ireadview.internal.layout.TextBlockLayoutProvider()
}
