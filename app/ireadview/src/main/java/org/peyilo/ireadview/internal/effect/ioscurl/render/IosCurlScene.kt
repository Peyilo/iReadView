package org.peyilo.ireadview.internal.effect.ioscurl.render

import androidx.core.os.trace
import org.peyilo.pagecurl.mesh.CurlMesh
import org.peyilo.pagecurl.mesh.CurlMeshInput
import org.peyilo.pagecurl.model.CurlRect

internal data class IosCurlScene(
    val input: CurlMeshInput,
    val texture: Int,
    val underlays: List<Pair<Int, CurlRect>>,
) {
    fun draw(mesh: CurlMesh, renderer: IosCurlRenderer) =
        trace("iRead.curl.frame") {
            val frame = trace("iRead.curl.mesh") { mesh.build(input) }
            renderer.render(
                frame,
                input.width.toFloat(),
                input.height.toFloat(),
                texture,
                1 - texture,
                underlays,
            )
        }
}
