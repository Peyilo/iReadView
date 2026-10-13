package org.peyilo.ireadview.api.source

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import java.io.File
import java.io.InputStream

/** Opens a fresh stream on every call. The source owns and closes each returned stream. */
fun interface ReaderInput {
    @Throws(java.io.IOException::class)
    fun openStream(): InputStream

    companion object {
        @JvmStatic fun fromStream(opener: () -> InputStream): ReaderInput = ReaderInput { opener() }
        @JvmStatic fun fromFile(file: File): ReaderInput = ReaderInput { file.inputStream() }
        @JvmStatic fun fromAsset(context: Context, path: String): ReaderInput {
            val assets = context.applicationContext.assets
            return ReaderInput { assets.open(path) }
        }
        @JvmStatic fun fromResource(resources: Resources, resourceId: Int): ReaderInput =
            ReaderInput { resources.openRawResource(resourceId) }
        /** Permissions and persisted grants belong to the host. */
        @JvmStatic fun fromUri(context: Context, uri: Uri): ReaderInput = fromUri(context.applicationContext.contentResolver, uri)
        @JvmStatic fun fromUri(resolver: ContentResolver, uri: Uri): ReaderInput = ReaderInput {
            resolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open reader input: $uri")
        }
    }
}
