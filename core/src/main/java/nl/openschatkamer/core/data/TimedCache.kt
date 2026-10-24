package nl.openschatkamer.core.data

import android.os.SystemClock

/**
 * In-memory cache met houdbaarheidsduur en snapshot ondersteuning.
 */
class TimedCache<T>(
    private val ttlMs: Long,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    @Volatile private var value: T? = null
    @Volatile private var storedAt = 0L
    @Volatile private var seeded = false

    fun snapshot(): T? = value

    fun fresh(): T? {
        val v = value ?: return null
        if (seeded) return null
        return if (clock() - storedAt < ttlMs) v else null
    }

    fun put(v: T) {
        value = v
        storedAt = clock()
        seeded = false
    }

    fun seed(v: T) {
        if (value != null) return
        value = v
        seeded = true
    }

    fun clear() {
        value = null
        storedAt = 0L
        seeded = false
    }
}

const val CATALOG_TTL_MS = 5 * 60_000L
const val BACKGROUND_REFRESH_MS = 10 * 60_000L
