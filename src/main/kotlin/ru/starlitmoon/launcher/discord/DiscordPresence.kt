package ru.starlitmoon.launcher.discord

import io.github.pandier.kpresence.KPresenceClient
import io.github.pandier.kpresence.activity.ActivityType
import io.github.pandier.kpresence.logger.KPresenceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Discord Rich Presence for the StarlitMoon launcher.
 * Uses the public Discord application id (same as site OAuth client id).
 */
class DiscordPresence(
    private val parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val started = AtomicBoolean(false)
    private var client: KPresenceClient? = null
    private var sessionStartedAt: Long = 0L
    private var playStartedAt: Long? = null

    @Volatile
    private var enabled: Boolean = true

    fun start(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            stop()
            return
        }
        if (!started.compareAndSet(false, true)) return
        sessionStartedAt = System.currentTimeMillis()
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
        serverHost: String,
    ) {
        if (!enabled) return
        val c = client ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (playing) {
                    if (playStartedAt == null) playStartedAt = System.currentTimeMillis()
                } else {
                    playStartedAt = null
                }
                val nick = username.trim().ifBlank { "игрок" }
                val pack = packName?.trim()?.takeIf { it.length >= 2 } ?: "сборка не выбрана"
                val host = serverHost.trim().ifBlank { "StarlitMoon" }
                c.update {
                    type = ActivityType.PLAYING
                    name = "StarlitMoon"
                    if (playing) {
                        details = clip("Играет · $pack")
                        state = clip("Сервер $host")
                        timestamps {
                            start = playStartedAt ?: now()
                        }
                    } else if (loggedIn) {
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
                    assets {
                        largeImage = LOGO_URL
                        largeText = "StarlitMoon"
                        largeUrl = SITE_URL
                    }
                    button("Сайт", SITE_URL)
                    button("Discord", DISCORD_INVITE)
                }
            }
        }
    }

    fun clear() {
        val c = client ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { c.update(null) }
        }
    }

    fun stop() {
        started.set(false)
        playStartedAt = null
        val c = client
        client = null
        if (c != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { c.disconnect().await() }
                runCatching { c.close() }
            }
        }
    }

    companion object {
        /** Public Discord application id (OAuth client id of the StarlitMoon app). */
        const val APPLICATION_ID: Long = 1251248989865508884L
        private const val SITE_URL = "https://starlit-moon.ru"
        private const val DISCORD_INVITE = "https://discord.gg/TFM3shUuR2"
        private const val LOGO_URL = "https://starlit-moon.ru/logo.png"

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
