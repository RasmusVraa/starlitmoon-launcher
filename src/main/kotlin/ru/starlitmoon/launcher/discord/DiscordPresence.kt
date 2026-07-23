package ru.starlitmoon.launcher.discord

import io.github.pandier.kpresence.KPresenceClient
import io.github.pandier.kpresence.activity.ActivityType
import io.github.pandier.kpresence.logger.KPresenceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discord Rich Presence for the StarlitMoon launcher.
 *
 * Uses a dedicated IO scope (not the UI/Swing dispatcher) so connect/update/stop
 * cannot deadlock the window thread.
 */
class DiscordPresence(
    @Suppress("UNUSED_PARAMETER") parentScope: CoroutineScope? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)
    private var client: KPresenceClient? = null
    private var sessionStartedAt: Long = 0L
    private var playStartedAt: Long? = null
    private var applyJob: Job? = null

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var pending: PresenceSnapshot? = null

    fun start(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            stop()
            return
        }
        if (!started.compareAndSet(false, true)) {
            flush()
            return
        }
        sessionStartedAt = System.currentTimeMillis()
        scope.launch {
            mutex.withLock {
                runCatching {
                    val c = KPresenceClient(APPLICATION_ID) {
                        parentScope = scope
                        logger = KPresenceLogger.Dummy
                        autoReconnect = true
                    }
                    client = c
                    c.connect()
                }.onFailure {
                    started.set(false)
                    client = null
                }
            }
            // Give Discord IPC a moment to finish handshake.
            delay(500)
            applyPending(allowAvatar = true, includeAssets = true)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled) {
            started.set(false)
            start(true)
        } else {
            stop()
        }
    }

    fun update(
        loggedIn: Boolean,
        username: String,
        playing: Boolean,
        packName: String?,
        avatarImageUrl: String? = null,
    ) {
        if (!enabled) return
        pending = PresenceSnapshot(
            loggedIn = loggedIn,
            username = username,
            playing = playing,
            packName = packName,
            avatarImageUrl = avatarImageUrl,
        )
        flush()
    }

    private fun flush() {
        if (!enabled) return
        applyJob?.cancel()
        applyJob = scope.launch {
            // If connect is still in progress, wait briefly.
            repeat(10) {
                if (client != null || !started.get()) return@repeat
                delay(100)
            }
            applyPending(allowAvatar = true, includeAssets = true)
        }
    }

    private suspend fun applyPending(allowAvatar: Boolean, includeAssets: Boolean) {
        if (!enabled) return
        val snap = pending ?: return
        val c = client ?: return

        if (snap.playing) {
            if (playStartedAt == null) playStartedAt = System.currentTimeMillis()
        } else {
            playStartedAt = null
        }

        val nick = snap.username.trim().ifBlank { "игрок" }
        val pack = snap.packName?.trim()?.takeIf { it.length >= 2 } ?: "сборка не выбрана"
        val avatar = snap.avatarImageUrl
            ?.takeIf { allowAvatar }
            ?.trim()
            ?.takeIf { it.length in 8..256 && it.startsWith("https://") }

        val result = runCatching {
            c.update {
                type = ActivityType.PLAYING
                if (snap.playing) {
                    details = clip(pack)
                    timestamps {
                        start = playStartedAt ?: now()
                    }
                } else if (snap.loggedIn) {
                    details = clip(nick)
                    state = clip("В лаунчере · $pack")
                    timestamps {
                        start = sessionStartedAt.takeIf { it > 0 } ?: now()
                    }
                } else {
                    details = "StarlitMoon Launcher"
                    state = "На экране входа"
                    timestamps {
                        start = sessionStartedAt.takeIf { it > 0 } ?: now()
                    }
                }
                if (includeAssets) {
                    assets {
                        largeImage = LOGO_URL
                        largeText = "StarlitMoon"
                        if (avatar != null) {
                            smallImage = avatar
                            smallText = clip(nick)
                        }
                    }
                }
                button("Сайт", SITE_URL)
                button("Discord", DISCORD_INVITE)
            }
        }

        when {
            result.isFailure && avatar != null -> applyPending(allowAvatar = false, includeAssets = true)
            result.isFailure && includeAssets -> applyPending(allowAvatar = false, includeAssets = false)
        }
    }

    /**
     * Clears activity and disconnects. Must finish before process exit,
     * otherwise Discord keeps the last Rich Presence.
     */
    fun stop() {
        started.set(false)
        playStartedAt = null
        pending = null
        applyJob?.cancel()
        applyJob = null
        val c = client
        client = null
        if (c == null) return
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(2_000) {
                runCatching { c.update(null) }
                runCatching { c.disconnect().await() }
            }
            runCatching { c.close() }
        }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private data class PresenceSnapshot(
        val loggedIn: Boolean,
        val username: String,
        val playing: Boolean,
        val packName: String?,
        val avatarImageUrl: String?,
    )

    companion object {
        /** Public Discord application id (OAuth client id of the StarlitMoon app). */
        const val APPLICATION_ID: Long = 1251248989865508884L
        private const val SITE_URL = "https://starlit-moon.ru"
        private const val DISCORD_INVITE = "https://discord.gg/TFM3shUuR2"
        /** Small PNG (~10KB). The full site logo.png is ~1MB and breaks Discord's image proxy. */
        private const val LOGO_URL = "https://starlit-moon.ru/rpc-logo.png"

        private fun clip(value: String, max: Int = 128): String {
            val t = value.trim().replace(Regex("\\s+"), " ")
            return when {
                t.length < 2 -> "—"
                t.length > max -> t.take(max - 1) + "…"
                else -> t
            }
        }
    }
}
