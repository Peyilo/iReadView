package org.peyilo.ireadview

import org.junit.Assert.*
import org.junit.Test
import org.peyilo.ireadview.api.effect.*
import org.peyilo.ireadview.internal.effect.googlecurl.GoogleCurlGeometry

class CurlCatalogTest {
    @Test fun onlyTheTwoSupportedCurlFactoriesArePublished() {
        val curlFactories = PageTurnEffects::class.java.methods.map { it.name }
            .filter { it.endsWith("Curl") || it == "curl" }.toSet()
        assertEquals(setOf("iosCurl", "googleCurl"), curlFactories)
        for (id in listOf("none", "slide", "cover", "ibook_slide", "ios_curl", "google_curl")) {
            val factory = requireNotNull(PageTurnEffects.fromId(id))
            assertEquals(id, factory.id)
            assertEquals(id, PageTurnEffects.builtInId(factory))
        }
        for (removed in listOf("curl", "page_curl", "unified_curl")) assertNull(PageTurnEffects.fromId(removed))
    }

    @Test fun googleWaveKeepsFlatAndOffscreenEndpointsAcrossItsAmplitudeRange() {
        val vertices = FloatArray(2)
        for (radius in listOf(.005f, .08f, .3f)) {
            val geometry = GoogleCurlGeometry(radius)
            geometry.update(0f)
            for (column in 0..8) {
                val u = column / 8f
                assertEquals(255, geometry.project(u, .5f, 400f, 800f, vertices, 0))
                assertEquals(u * 400, vertices[0], .001f)
                assertEquals(400f, vertices[1], .001f)
            }
            geometry.update(1f)
            geometry.project(1f, .5f, 400f, 800f, vertices, 0)
            assertTrue("Last edge must leave the viewport", vertices[0] <= .001f)
        }
    }
}
