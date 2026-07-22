package ru.starlitmoon.launcher.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig

class StarlitApiException(
    val status: HttpStatusCode,
    message: String,
    val applicationStatus: String? = null,
    val cabinetBlocked: Boolean = false,
) : Exception(message)

@Serializable
private data class LoginRequest(
    val nickname: String,
    val password: String,
)

class StarlitApiClient(
    private val config: LauncherConfig = LauncherConfig.load(),
    private val sessionStore: SessionStore = SessionStore(config),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
    }

    val baseUrl: String get() = config.apiBaseUrl.trimEnd('/')

    fun sessionCookie(): String? = sessionStore.read()?.cookieValue

    suspend fun restoreSession(): MeResponse? {
        val stored = sessionStore.read() ?: return null
        return try {
            val me = me(stored.cookieValue)
            sessionStore.save(stored.cookieValue, me.user?.name, me.admin)
            me
        } catch (_: StarlitApiException) {
            sessionStore.clear()
            null
        }
    }

    suspend fun login(nickname: String, password: String): MeResponse {
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(nickname.trim(), password))
        }
        val cookie = extractSessionCookie(response) ?: throw StarlitApiException(
            response.status,
            "Сервер не вернул сессию",
        )
        if (!response.status.isSuccess()) {
            throw parseError(response)
        }
        val login = response.body<LoginResponse>()
        sessionStore.save(cookie, login.user?.name ?: nickname.trim(), login.admin)
        return me(cookie)
    }

    suspend fun logout() {
        val cookie = sessionStore.read()?.cookieValue
        if (cookie != null) {
            runCatching {
                client.post("$baseUrl/api/auth/logout") {
                    header("Cookie", cookieHeader(cookie))
                }
            }
        }
        sessionStore.clear()
    }

    suspend fun me(cookie: String? = sessionCookie()): MeResponse {
        val value = cookie ?: throw StarlitApiException(HttpStatusCode.Unauthorized, "Не авторизован")
        val response = client.get("$baseUrl/api/auth/me") {
            header("Cookie", cookieHeader(value))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun adminMe(): AdminMeResponse {
        val cookie = sessionCookie() ?: throw StarlitApiException(HttpStatusCode.Unauthorized, "Не авторизован")
        val response = client.get("$baseUrl/api/admin/me") {
            header("Cookie", cookieHeader(cookie))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun adminStats(): AdminStatsResponse {
        val cookie = sessionCookie() ?: throw StarlitApiException(HttpStatusCode.Unauthorized, "Не авторизован")
        val response = client.get("$baseUrl/api/admin/stats") {
            header("Cookie", cookieHeader(cookie))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun serverVersion(): String {
        val response = client.get("$baseUrl/api/server-version")
        if (!response.status.isSuccess()) return config.defaultMcVersion
        return response.body<ServerVersionResponse>().version
    }

    suspend fun fetchServerStatus(): ServerStatus {
        val host = config.serverHost
        return runCatching {
            val response = client.get("https://api.mcsrvstat.us/3/${host}")
            if (!response.status.isSuccess()) return ServerStatus.offline(host)
            val raw = response.body<McsrvstatResponse>()
            ServerStatus(
                host = host,
                online = raw.online == true,
                playersOnline = raw.players?.online ?: 0,
                playersMax = raw.players?.max ?: config.defaultMaxPlayers,
                version = raw.version?.trim().orEmpty(),
            )
        }.getOrElse { ServerStatus.offline(host) }
    }

    suspend fun fetchOnlinePlayers(): PlayersResponse {
        val response = client.get("$baseUrl/api/players")
        if (!response.status.isSuccess()) return PlayersResponse()
        return response.body()
    }

    fun avatarUrl(player: String): String =
        "$baseUrl/api/avatar?player=${player.encodeURLParameter()}"

    fun adminUrl(): String = "$baseUrl/admin"

    fun discordLoginUrl(): String = "$baseUrl/api/auth/discord/start"

    fun close() = client.close()

    private suspend fun parseError(response: HttpResponse): StarlitApiException {
        val body = runCatching { response.body<ApiError>() }.getOrNull()
        return StarlitApiException(
            status = response.status,
            message = body?.error ?: "Ошибка ${response.status.value}",
            applicationStatus = body?.applicationStatus,
            cabinetBlocked = body?.cabinetBlocked == true,
        )
    }

    private fun extractSessionCookie(response: HttpResponse): String? {
        val cookies = response.headers.getAll("Set-Cookie").orEmpty()
        for (header in cookies) {
            val part = header.substringBefore(';').trim()
            if (part.startsWith("${SessionStore.COOKIE_NAME}=")) {
                return part.substringAfter('=')
            }
        }
        return null
    }

    private fun cookieHeader(value: String): String =
        "${SessionStore.COOKIE_NAME}=$value"
}

data class ServerStatus(
    val host: String,
    val online: Boolean,
    val playersOnline: Int,
    val playersMax: Int,
    val version: String,
) {
    companion object {
        fun offline(host: String) = ServerStatus(host, false, 0, 100, "")
    }
}

@Serializable
private data class McsrvstatResponse(
    val online: Boolean? = null,
    val version: String? = null,
    val players: McsrvstatPlayers? = null,
)

@Serializable
private data class McsrvstatPlayers(
    val online: Int? = null,
    val max: Int? = null,
)

private fun String.encodeURLParameter(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8)
