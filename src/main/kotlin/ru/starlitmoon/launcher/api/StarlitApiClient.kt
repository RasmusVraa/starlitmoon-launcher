package ru.starlitmoon.launcher.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
private data class LoginRequest(val nickname: String, val password: String)

@Serializable
private data class ProfileStatusBody(val text: String)

@Serializable
private data class PrivacyBody(val section: String, val visible: Boolean)

@Serializable
private data class NotifyPrefsBody(val channel: String, val enabled: Boolean)

@Serializable
private data class BadgeBody(val activeBadgeId: String? = null, val visible: Boolean)

@Serializable
private data class PlayerPatchBody(
    val banned: Boolean? = null,
    val banReason: String? = null,
    val warnCount: Int? = null,
    val profileStatus: String? = null,
)

@Serializable
private data class BankPatchBody(val balance: Long)

@Serializable
private data class NotifySendBody(
    val all: Boolean = false,
    val players: List<String> = emptyList(),
    val title: String,
    val message: String,
    val deliverIngame: Boolean = true,
)

class StarlitApiClient(
    private val config: LauncherConfig = LauncherConfig.load(),
    private val sessionStore: SessionStore = SessionStore(config),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 12_000
        }
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
        val cookie = extractSessionCookie(response) ?: throw StarlitApiException(response.status, "Не удалось войти")
        if (!response.status.isSuccess()) throw parseError(response)
        val login = response.body<LoginResponse>()
        sessionStore.save(cookie, login.user?.name ?: nickname.trim(), login.admin)
        return me(cookie)
    }

    suspend fun logout() {
        sessionCookie()?.let { cookie ->
            runCatching {
                client.post("$baseUrl/api/auth/logout") { header("Cookie", cookieHeader(cookie)) }
            }
        }
        sessionStore.clear()
    }

    suspend fun me(cookie: String? = sessionCookie()): MeResponse {
        val value = cookie ?: throw StarlitApiException(HttpStatusCode.Unauthorized, "Не авторизован")
        val response = client.get("$baseUrl/api/auth/me") { header("Cookie", cookieHeader(value)) }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun notifications(): List<NotificationDto> {
        val cookie = sessionCookie() ?: return emptyList()
        val response = client.get("$baseUrl/api/auth/notifications") { header("Cookie", cookieHeader(cookie)) }
        if (!response.status.isSuccess()) return emptyList()
        return runCatching { response.body<NotificationsResponse>().notifications }.getOrDefault(emptyList())
    }

    suspend fun setProfileStatus(text: String): MeResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/auth/profile-status") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(ProfileStatusBody(text))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return me(cookie)
    }

    suspend fun setPrivacy(section: String, visible: Boolean): MeResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/auth/privacy") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(PrivacyBody(section, visible))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return me(cookie)
    }

    suspend fun setNotificationPref(channel: String, enabled: Boolean): MeResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/auth/notification-prefs") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(NotifyPrefsBody(channel, enabled))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return me(cookie)
    }

    suspend fun setBadge(activeBadgeId: String?, visible: Boolean): MeResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/auth/badge") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(BadgeBody(activeBadgeId, visible))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return me(cookie)
    }

    suspend fun adminMe(): AdminMeResponse = authedGet("/api/admin/me")
    suspend fun adminStats(): AdminStatsResponse = authedGet("/api/admin/stats")
    suspend fun adminPlayers(query: String = ""): AdminPlayersResponse =
        authedGet("/api/admin/players") { if (query.isNotBlank()) parameter("q", query) }

    suspend fun adminPlayer(id: String): AdminPlayerDetailDto? =
        authedGet<AdminPlayerDetailResponse>("/api/admin/players/$id").player

    suspend fun patchAdminPlayer(id: String, banned: Boolean? = null, banReason: String? = null, warnCount: Int? = null, profileStatus: String? = null) {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/admin/players/$id") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(PlayerPatchBody(banned, banReason, warnCount, profileStatus))
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun adminApplications(status: String = "pending"): AdminApplicationsResponse =
        authedGet("/api/admin/applications") { parameter("status", status) }

    suspend fun acceptApplication(id: String) = authedPost("/api/admin/applications/$id/accept")
    suspend fun rejectApplication(id: String) = authedPost("/api/admin/applications/$id/reject")

    suspend fun adminAccounts(query: String = ""): AdminAccountsResponse =
        authedGet("/api/admin/accounts") { if (query.isNotBlank()) parameter("q", query) }

    suspend fun adminBank(): AdminBankResponse = authedGet("/api/admin/bank")

    suspend fun setBankBalance(nick: String, balance: Long) {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/admin/bank/$nick") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(BankPatchBody(balance))
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun adminClans(status: String = "pending"): AdminClansResponse =
        authedGet("/api/admin/clans") { parameter("status", status) }

    suspend fun approveClan(id: String) = authedPost("/api/admin/clans/$id/approve")
    suspend fun rejectClan(id: String) = authedPost("/api/admin/clans/$id/reject")

    suspend fun sendNotification(title: String, message: String, all: Boolean, players: List<String> = emptyList()) {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/notifications/send") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(NotifySendBody(all, players, title, message, true))
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun serverVersion(): String {
        val response = client.get("$baseUrl/api/server-version")
        if (!response.status.isSuccess()) return config.defaultMcVersion
        return response.body<ServerVersionResponse>().version
    }

    suspend fun fetchServerStatus(): ServerStatus {
        val host = config.serverHost
        return runCatching {
            val response = client.get("https://api.mcsrvstat.us/3/$host")
            if (!response.status.isSuccess()) return ServerStatus.offline(host)
            val raw = response.body<McsrvstatResponse>()
            ServerStatus(host, raw.online == true, raw.players?.online ?: 0, raw.players?.max ?: config.defaultMaxPlayers, raw.version?.trim().orEmpty())
        }.getOrElse { ServerStatus.offline(host) }
    }

    suspend fun fetchOnlinePlayers(): PlayersResponse {
        val response = client.get("$baseUrl/api/players")
        if (!response.status.isSuccess()) return PlayersResponse()
        return response.body()
    }

    fun avatarUrl(player: String, uuid: String? = null): String {
        val p = java.net.URLEncoder.encode(player, Charsets.UTF_8)
        return if (uuid.isNullOrBlank()) "$baseUrl/api/avatar?player=$p&size=64"
        else "$baseUrl/api/avatar?player=$p&uuid=$uuid&size=64"
    }

    fun close() = client.close()

    private suspend inline fun <reified T> authedGet(path: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): T {
        val cookie = needCookie()
        val response = client.get("$baseUrl$path") {
            header("Cookie", cookieHeader(cookie))
            block()
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    private suspend fun authedPost(path: String) {
        val cookie = needCookie()
        val response = client.post("$baseUrl$path") { header("Cookie", cookieHeader(cookie)) }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    private fun needCookie(): String =
        sessionCookie() ?: throw StarlitApiException(HttpStatusCode.Unauthorized, "Не авторизован")

    private suspend fun parseError(response: HttpResponse): StarlitApiException {
        val body = runCatching { response.body<ApiError>() }.getOrNull()
        return StarlitApiException(response.status, body?.error ?: "Ошибка ${response.status.value}", body?.applicationStatus, body?.cabinetBlocked == true)
    }

    private fun extractSessionCookie(response: HttpResponse): String? {
        for (header in response.headers.getAll("Set-Cookie").orEmpty()) {
            val part = header.substringBefore(';').trim()
            if (part.startsWith("${SessionStore.COOKIE_NAME}=")) return part.substringAfter('=')
        }
        return null
    }

    private fun cookieHeader(value: String) = "${SessionStore.COOKIE_NAME}=$value"
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
private data class McsrvstatResponse(val online: Boolean? = null, val version: String? = null, val players: McsrvstatPlayers? = null)

@Serializable
private data class McsrvstatPlayers(val online: Int? = null, val max: Int? = null)
