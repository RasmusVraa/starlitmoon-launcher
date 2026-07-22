package ru.starlitmoon.launcher.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class StoredSession(
    val cookieValue: String,
    val userName: String? = null,
    val isAdmin: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
)

class SessionStore(private val config: LauncherConfig) {
    private val json = Json { ignoreUnknownKeys = true }

    private val sessionFile: Path
        get() = config.dataDir.resolve("session.json")

    fun read(): StoredSession? {
        if (!sessionFile.exists()) return null
        return runCatching {
            json.decodeFromString<StoredSession>(sessionFile.readText())
        }.getOrNull()
    }

    fun save(cookieValue: String, userName: String?, isAdmin: Boolean) {
        config.dataDir.createDirectories()
        val session = StoredSession(cookieValue, userName, isAdmin)
        sessionFile.writeText(json.encodeToString(session))
    }

    fun clear() {
        if (sessionFile.exists()) sessionFile.toFile().delete()
    }

    companion object {
        const val COOKIE_NAME = "starlit_session"
    }
}
