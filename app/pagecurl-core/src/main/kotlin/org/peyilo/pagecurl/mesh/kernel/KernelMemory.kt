package org.peyilo.pagecurl.mesh.kernel

/** Little-endian managed scratch arena. Addresses are array offsets, never native pointers. */
internal class KernelMemory {
    private val words = LongArray(0x30000 / 8)
    fun clear() { words.fill(0L) }
    fun long(address: Long): Long {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        return if (shift == 0) words[index] else (words[index] ushr shift) or (words[index + 1] shl (64 - shift))
    }
    fun long(address: Long, value: Long) {
        val index = (address ushr 3).toInt()
        val shift = (address.toInt() and 7) * 8
        if (shift == 0) words[index] = value else {
            words[index] = (words[index] and (-1L ushr (64 - shift))) or (value shl shift)
            words[index + 1] = (words[index + 1] and (-1L shl shift)) or (value ushr (64 - shift))
        }
    }
    fun byte(address: Long): Long = (words[(address ushr 3).toInt()] ushr ((address.toInt() and 7) * 8)) and 255
    fun byte(address: Long, value: Long) {
        val index = (address ushr 3).toInt(); val shift = (address.toInt() and 7) * 8
        words[index] = (words[index] and (255L shl shift).inv()) or ((value and 255) shl shift)
    }
    fun word(address: Long): Long = (long(address) and 0xffffffffL)
    fun word(address: Long, value: Long) {
        val index = (address ushr 3).toInt(); val shift = (address.toInt() and 7) * 8
        if (shift <= 32) words[index] = (words[index] and (0xffffffffL shl shift).inv()) or ((value and 0xffffffffL) shl shift)
        else for (i in 0..3) byte(address + i, value ushr (i * 8))
    }
    fun short(address: Long, value: Long) { byte(address, value); byte(address + 1, value ushr 8) }
    fun copy(to: Long, from: Long, count: Long) {
        if (to > from && to < from + count) for (i in count.toInt() - 1 downTo 0) byte(to + i, byte(from + i))
        else for (i in 0 until count.toInt()) byte(to + i, byte(from + i))
    }
    fun bytes(address: Long, size: Int) = ByteArray(size) { byte(address + it).toByte() }
}

/** A value vector; copies deliberately do not alias their source. */
internal class KernelVector(private var low: Long = 0, private var high: Long = 0) {
    fun u64(i: Int): Long = if (i == 0) low else high
    fun u64(i: Int, v: Long) { if (i == 0) low = v else high = v }
    fun d(i: Int): Double = Double.fromBits(u64(i))
    fun d(i: Int, v: Double) = u64(i, v.toRawBits())
    fun u32(i: Int): Long = (u64(i / 2) ushr ((i and 1) * 32)) and 0xffffffffL
    fun u32(i: Int, v: Long) {
        val shift = (i and 1) * 32
        u64(i / 2, (u64(i / 2) and (0xffffffffL shl shift).inv()) or ((v and 0xffffffffL) shl shift))
    }
    fun f(i: Int): Float = Float.fromBits(u32(i).toInt())
    fun f(i: Int, v: Float) = u32(i, v.toRawBits().toLong())
    fun h(i: Int): Long = (u64(i / 4) ushr ((i and 3) * 16)) and 65535
    fun h(i: Int, v: Long) {
        val shift = (i and 3) * 16
        u64(i / 4, (u64(i / 4) and (65535L shl shift).inv()) or ((v and 65535) shl shift))
    }
    fun copy() = KernelVector(low, high)
    fun set(other: KernelVector) { low = other.low; high = other.high }
    fun clear() { low = 0; high = 0 }
    fun load(memory: KernelMemory, offset: Long) { low = memory.long(offset); high = memory.long(offset + 8) }
    fun store(memory: KernelMemory, offset: Long) { memory.long(offset, low); memory.long(offset + 8, high) }
}
