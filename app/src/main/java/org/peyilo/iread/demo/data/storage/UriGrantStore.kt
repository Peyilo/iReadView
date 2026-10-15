package org.peyilo.iread.demo.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Application-owned queue: Activity cancellation must not interrupt a grant transaction. */
internal class UriGrantStore private constructor(context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("reader-uri-grants", Context.MODE_PRIVATE)
    private val tasks = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (task in tasks) try { task() } catch (error: Exception) {
                Log.w("UriGrantStore", "Grant maintenance failed; retained for a later attempt", error)
            }
        }
    }

    /** False means persistence failed; transient permission may still allow this session. */
    fun retain(uri: Uri, flags: Int): CompletableDeferred<Boolean> {
        val result = CompletableDeferred<Boolean>()
        tasks.trySend {
            result.complete(runCatching {
                if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 ||
                    flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return@runCatching false
                val key = uri.toString()
                val owned = owned()
                val existing = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                // Grants already owned by other app features/older versions remain outside this ledger.
                if (!existing) {
                    // Durable intent precedes the provider call, including process death between them.
                    check(preferences.edit().putStringSet("owned-read", owned + key).commit())
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                true
            }.getOrElse {
                Log.w("UriGrantStore", "Could not retain document read permission", it)
                false
            })
        }
        return result
    }

    /** Caller submits a main-thread snapshot after its history mutation; queue order is significant. */
    fun cleanup(retained: Set<String>, flushHistory: () -> Boolean) {
        val keep = retained.toSet()
        tasks.trySend {
            if (!flushHistory()) return@trySend
            val owned = owned().toMutableSet()
            val actual = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
            for (key in owned.toList()) {
                if (key in keep) continue
                try {
                    if (key in actual) resolver.releasePersistableUriPermission(Uri.parse(key), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    owned.remove(key)
                } catch (error: Exception) {
                    Log.w("UriGrantStore", "Could not retire an unused read permission", error)
                }
            }
            // A failed commit leaves a harmless stale ledger item; the next pass checks actual grants.
            if (!preferences.edit().putStringSet("owned-read", owned).commit())
                Log.w("UriGrantStore", "Could not persist grant cleanup")
        }
    }

    private fun owned(): Set<String> = preferences.getStringSet("owned-read", emptySet())!!.toSet()

    companion object {
        @Volatile private var instance: UriGrantStore? = null
        fun get(context: Context): UriGrantStore = instance ?: synchronized(this) {
            instance ?: UriGrantStore(context.applicationContext).also { instance = it }
        }
    }
}
