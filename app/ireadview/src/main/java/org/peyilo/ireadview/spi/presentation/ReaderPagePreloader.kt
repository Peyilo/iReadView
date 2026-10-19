package org.peyilo.ireadview.spi.presentation

import kotlinx.coroutines.*

/** Shared idle-only scheduling; callers supply backend preparation and validity checks.
 * Call cancel on interaction/navigation and stop before taking a prepared spare page.
 */
class ReaderPagePreloader {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job: Job?=null
    private var key: Any?=null
    fun schedule(key: Any, prepare: suspend CoroutineScope.() -> Unit) {
        if(this.key==key && job?.isActive==true)return
        cancel();this.key=key
        job=scope.launch {
            delay(24)
            try { prepare() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* Read-ahead is optional. Actual navigation reports errors. */ }
        }
    }
    fun cancel() { job?.cancel() }
    suspend fun stop() { job?.cancelAndJoin();job=null;key=null }
    fun close() { scope.cancel();job=null;key=null }
}
