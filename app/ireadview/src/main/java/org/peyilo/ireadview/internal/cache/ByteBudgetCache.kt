package org.peyilo.ireadview.internal.cache

/** Small access-ordered cache. The displayed working set is held by the presentation, not this cache. */
internal class ByteBudgetCache<K, V>(budgetBytes: Long, private val sizeOf: (V) -> Long?) {
    private data class Entry<V>(val value: V, val bytes: Long)
    private val values = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    var budgetBytes: Long = budgetBytes
        set(value) { require(value >= 0); field = value; trim() }
    init { require(budgetBytes >= 0) }
    var sizeBytes: Long = 0
        private set
    operator fun get(key: K): V? = values[key]?.value
    fun put(key: K, value: V) {
        remove(key)
        val bytes = sizeOf(value)?.takeIf { it >= 0 }?.coerceAtLeast(1) ?: return
        if (bytes > budgetBytes) return
        // Evict before adding: sizeBytes + bytes can otherwise overflow Long.
        evictTo(budgetBytes - bytes)
        values[key] = Entry(value, bytes)
        sizeBytes += bytes
        trim()
    }
    fun remove(key: K) { values.remove(key)?.let { sizeBytes -= it.bytes } }
    fun removeWhere(predicate: (K) -> Boolean) {
        values.keys.filter(predicate).forEach { remove(it) }
    }
    fun clear() { values.clear(); sizeBytes = 0 }
    fun trim() = evictTo(budgetBytes)
    private fun evictTo(limit: Long) {
        val iterator = values.entries.iterator()
        while (sizeBytes > limit && iterator.hasNext()) {
            sizeBytes -= iterator.next().value.bytes
            iterator.remove()
        }
    }
}
