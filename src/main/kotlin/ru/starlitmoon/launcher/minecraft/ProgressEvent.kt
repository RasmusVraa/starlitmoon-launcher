package ru.starlitmoon.launcher.minecraft

/**
 * Structured progress event for sync / client preparation UI.
 */
data class ProgressEvent(
    val message: String,
    val fraction: Float? = null,
    val bytesDone: Long? = null,
    val bytesTotal: Long? = null,
    val filesDone: Int? = null,
    val filesTotal: Int? = null,
    val currentFile: String? = null,
    val threads: Int? = null,
    /** Instant / smoothed bytes per second from the producer thread. */
    val speedBps: Long? = null,
    val kind: Kind = Kind.Generic,
) {
    enum class Kind { Generic, Download, Extract, Verify }
}
