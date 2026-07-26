package ru.starlitmoon.launcher.minecraft

/**
 * Shared cancel / pause / speed-limit state for pack downloads.
 * Checked from IO threads during HTTP reads.
 */
class DownloadControl {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    var paused: Boolean = false

    /** 0 = unlimited. Soft cap in bytes/sec. */
    @Volatile
    var speedLimitBps: Long = 0L

    private var windowStartNs = System.nanoTime()
    private var windowBytes = 0L

    fun cancel() {
        cancelled = true
        paused = false
    }

    fun reset() {
        cancelled = false
        paused = false
        windowStartNs = System.nanoTime()
        windowBytes = 0L
    }

    fun togglePause() {
        if (!cancelled) paused = !paused
    }

    /** Blocks while paused; throws if cancelled. */
    fun checkpoint() {
        if (cancelled) throw DownloadCancelledException()
        while (paused && !cancelled) {
            Thread.sleep(80)
        }
        if (cancelled) throw DownloadCancelledException()
    }

    /** Call after each successful read of [n] bytes to enforce speed limit (thread-safe). */
    fun throttle(n: Int) {
        if (n <= 0) return
        checkpoint()
        val limit = speedLimitBps
        if (limit <= 0L) return
        var sleepMs = 0L
        synchronized(this) {
            windowBytes += n
            val elapsedNs = System.nanoTime() - windowStartNs
            val elapsedSec = elapsedNs / 1_000_000_000.0
            if (elapsedSec < 0.05) return
            val allowed = limit * elapsedSec
            if (windowBytes > allowed) {
                val over = windowBytes - allowed
                sleepMs = ((over / limit.toDouble()) * 1000.0).toLong().coerceIn(1L, 2_000L)
                windowStartNs = System.nanoTime()
                windowBytes = 0L
            } else if (elapsedSec >= 1.0) {
                windowStartNs = System.nanoTime()
                windowBytes = 0L
            }
        }
        if (sleepMs <= 0L) return
        var left = sleepMs
        while (left > 0 && !cancelled) {
            checkpoint()
            val step = minOf(left, 50L)
            Thread.sleep(step)
            left -= step
        }
    }
}

class DownloadCancelledException : Exception("Скачивание отменено")
