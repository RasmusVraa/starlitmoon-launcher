package ru.starlitmoon.launcher.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.inputStream

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
private data class ArchiveInitBody(val fileName: String, val size: Long)

@Serializable
private data class ArchiveCompleteBody(val uploadId: String)

@Serializable
private data class NotifyPrefsBody(val channel: String, val enabled: Boolean)

@Serializable
private data class BadgeBody(val activeBadgeId: String? = null, val visible: Boolean)

@Serializable
private data class CommentsEnabledBody(val enabled: Boolean)

@Serializable
private data class PlayerPatchBody(
    val banned: Boolean? = null,
    val banReason: String? = null,
    val warnCount: Int? = null,
    val profileStatus: String? = null,
    val ranks: List<String>? = null,
)

@Serializable
private data class BankPatchBody(val balance: Long)

@Serializable
private data class NotifySendBody(
    val all: Boolean = false,
    val players: List<String> = emptyList(),
    val title: String,
    val message: String,
    val href: String? = null,
    val deliverIngame: Boolean = true,
)

@Serializable
private data class BadgeCreateBody(
    val emoji: String,
    val name: String,
    val description: String? = null,
)

@Serializable
private data class ProductBody(
    val id: String? = null,
    val name: String,
    val price: Int,
    val description: String? = null,
    val icon: String? = null,
    val commands: List<String> = emptyList(),
)

@Serializable
private data class MapSettingsBody(val visibility: String)

@Serializable
private data class MapMarkerBody(
    val ownerName: String,
    val name: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val iconId: String? = null,
)

@Serializable
private data class ContestEntryPatchBody(val status: String? = null, val adminNote: String? = null)

@Serializable
private data class ContestSettingsBody(val enabled: Boolean? = null, val page: ContestPagePatch? = null)

@Serializable
private data class ContestPagePatch(val title: String? = null, val seasonName: String? = null)

@Serializable
private data class WikiPageCreateBody(val title: String, val slug: String? = null, val published: Boolean = false)

@Serializable
private data class WikiPagePatchBody(
    val title: String? = null,
    val slug: String? = null,
    val published: Boolean? = null,
    val blocks: List<WikiBlockPatch>? = null,
)

@Serializable
private data class WikiBlockPatch(val type: String, val data: WikiBlockDataPatch)

@Serializable
private data class WikiBlockDataPatch(val text: String)

@Serializable
private data class ModpackCreateBody(
    val name: String,
    val slug: String? = null,
    val loader: String = "vanilla",
    val mcVersion: String,
    val description: String? = null,
)

@Serializable
private data class ModpackPatchBody(val name: String? = null, val enabled: Boolean? = null)

@Serializable
private data class SiteSettingsBody(val serverVersion: String)

@Serializable
private data class AdminAccessBody(val nickname: String? = null, val permissions: List<String>)

@Serializable
private data class RconExecBody(val command: String, val serverId: String? = null)

@Serializable
private data class ResetPasswordBody(val password: String? = null)

@Serializable
private data class SkinUploadBody(val image: String)

@Serializable
private data class CapeUploadBody(
    val image: String? = null,
    val clear: Boolean = false,
)

@Serializable
data class SkinUploadResponse(
    val ok: Boolean = false,
    val skinUrl: String? = null,
    val skinTextureHash: String? = null,
    val applyJobId: String? = null,
    val warning: String? = null,
    val message: String? = null,
    val error: String? = null,
    val cabinet: CabinetDto? = null,
)

@Serializable
data class CapeUploadResponse(
    val ok: Boolean = false,
    val capeUrl: String? = null,
    val capeTextureHash: String? = null,
    val message: String? = null,
    val error: String? = null,
    val cabinet: CabinetDto? = null,
)

@Serializable
private data class TreasuryPayoutBody(
    val toCode: String,
    val amount: Long,
    val reasonId: String,
    val reasonNote: String? = null,
)

class StarlitApiClient(
    private val config: LauncherConfig = LauncherConfig.load(),
    private val sessionStore: SessionStore = SessionStore(config),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    val baseUrl: String get() = config.apiBaseUrl.trimEnd('/')
    fun sessionCookie(): String? = sessionStore.read()?.cookieValue

    fun cachedSession(): StoredSession? = sessionStore.read()

    suspend fun restoreSession(): MeResponse? {
        val stored = sessionStore.read() ?: return null
        return try {
            val me = me(stored.cookieValue)
            sessionStore.save(stored.cookieValue, me.user?.name, me.admin)
            me
        } catch (e: StarlitApiException) {
            // Сбрасываем сессию только при реальной невалидной авторизации.
            if (e.status == HttpStatusCode.Unauthorized) {
                sessionStore.clear()
            }
            null
        } catch (_: Exception) {
            // Сеть/таймаут — оставляем cookie, пользователь остаётся «вошедшим» после повтора.
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

    suspend fun setCommentsEnabled(enabled: Boolean): MeResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/auth/comments-enabled") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(CommentsEnabledBody(enabled))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return me(cookie)
    }

    suspend fun uploadSkin(dataUrl: String): SkinUploadResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/auth/skin") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(SkinUploadBody(dataUrl))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun uploadCape(dataUrl: String): CapeUploadResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/auth/cape") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(CapeUploadBody(image = dataUrl, clear = false))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun clearCape(): CapeUploadResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/auth/cape") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(CapeUploadBody(image = null, clear = true))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun adminMe(): AdminMeResponse = authedGet("/api/admin/me")
    suspend fun adminStats(): AdminStatsResponse = authedGet("/api/admin/stats")
    suspend fun adminPlayers(query: String = ""): AdminPlayersResponse =
        authedGet("/api/admin/players") { if (query.isNotBlank()) parameter("q", query) }

    suspend fun adminPlayer(id: String): AdminPlayerDetailDto? =
        authedGet<AdminPlayerDetailResponse>("/api/admin/players/$id").player

    suspend fun patchAdminPlayer(
        id: String,
        banned: Boolean? = null,
        banReason: String? = null,
        warnCount: Int? = null,
        profileStatus: String? = null,
        ranks: List<String>? = null,
    ) {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/admin/players/${encodePath(id)}") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(PlayerPatchBody(banned, banReason, warnCount, profileStatus, ranks))
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun deleteAdminPlayer(id: String, purge: Boolean = false) {
        val cookie = needCookie()
        val response = client.delete("$baseUrl/api/admin/players/${encodePath(id)}") {
            header("Cookie", cookieHeader(cookie))
            if (purge) parameter("purge", "1")
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun grantPlayerBadge(playerId: String, badgeId: String) =
        authedPost("/api/admin/players/${encodePath(playerId)}/badges/${encodePath(badgeId)}")

    suspend fun revokePlayerBadge(playerId: String, badgeId: String) =
        authedDelete("/api/admin/players/${encodePath(playerId)}/badges/${encodePath(badgeId)}")

    // --- Badges ---
    suspend fun createBadge(emoji: String, name: String, description: String?) =
        authedPostJson("/api/admin/badges", BadgeCreateBody(emoji, name, description?.ifBlank { null }))

    suspend fun updateBadge(id: String, emoji: String, name: String, description: String?) =
        authedPatch("/api/admin/badges/${encodePath(id)}", BadgeCreateBody(emoji, name, description?.ifBlank { null }))

    suspend fun deleteBadge(id: String) = authedDelete("/api/admin/badges/${encodePath(id)}")

    // --- Products / orders ---
    suspend fun createProduct(id: String, name: String, price: Int, description: String?, icon: String?, commands: List<String>) =
        authedPostJson("/api/admin/products", ProductBody(id.ifBlank { null }, name, price, description, icon?.ifBlank { null }, commands))

    suspend fun updateProduct(id: String, name: String, price: Int, description: String?, icon: String?, commands: List<String>) =
        authedPatch("/api/admin/products/${encodePath(id)}", ProductBody(null, name, price, description, icon?.ifBlank { null }, commands))

    suspend fun deleteProduct(id: String) = authedDelete("/api/admin/products/${encodePath(id)}")

    suspend fun deleteOrder(id: String) = authedDelete("/api/admin/orders/${encodePath(id)}")

    // --- Map ---
    suspend fun mapSettings(): AdminMapSettingsResponse = authedGet("/api/admin/map-settings")

    suspend fun setMapVisibility(visibility: String): AdminMapSettingsResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/admin/map-settings") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(MapSettingsBody(visibility))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun mapMarkers(query: String = ""): AdminMapMarkersResponse =
        authedGet("/api/admin/map-markers") { if (query.isNotBlank()) parameter("q", query) }

    suspend fun createMapMarker(ownerName: String, name: String, world: String, x: Double, y: Double, z: Double, iconId: String?) =
        authedPostJson("/api/admin/map-markers", MapMarkerBody(ownerName, name, world, x, y, z, iconId?.ifBlank { null }))

    suspend fun deleteMapMarker(id: String) = authedDelete("/api/admin/map-markers/${encodePath(id)}")

    // --- Contest ---
    suspend fun contest(status: String = ""): AdminContestResponse =
        authedGet("/api/admin/contest") { if (status.isNotBlank() && status != "all") parameter("status", status) }

    suspend fun contestSettings(): AdminContestSettingsDto = authedGet("/api/admin/contest/settings")

    suspend fun patchContestEntry(id: String, status: String) =
        authedPatch("/api/admin/contest/entries/${encodePath(id)}", ContestEntryPatchBody(status = status))

    suspend fun deleteContestEntry(id: String) = authedDelete("/api/admin/contest/entries/${encodePath(id)}")

    suspend fun updateContestSettings(enabled: Boolean, title: String, seasonName: String) =
        authedPatch("/api/admin/contest/settings", ContestSettingsBody(enabled, ContestPagePatch(title.ifBlank { null }, seasonName)))

    // --- Wiki ---
    suspend fun wiki(): AdminWikiResponse = authedGet("/api/admin/wiki")

    suspend fun createWikiPage(title: String, slug: String?, published: Boolean): AdminWikiPageResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/wiki/pages") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(WikiPageCreateBody(title, slug?.ifBlank { null }, published))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun updateWikiPage(id: String, title: String?, slug: String?, published: Boolean?, paragraph: String?) {
        val blocks = paragraph?.let { listOf(WikiBlockPatch("paragraph", WikiBlockDataPatch(it))) }
        authedPatch(
            "/api/admin/wiki/pages/${encodePath(id)}",
            WikiPagePatchBody(title?.ifBlank { null }, slug?.ifBlank { null }, published, blocks),
        )
    }

    suspend fun deleteWikiPage(id: String) = authedDelete("/api/admin/wiki/pages/${encodePath(id)}")

    // --- Modpacks (admin) ---
    suspend fun adminModpacks(): AdminModpacksResponse = authedGet("/api/admin/modpacks")

    suspend fun createModpack(name: String, slug: String?, loader: String, mcVersion: String, description: String?): ModpackDto? {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/modpacks") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(ModpackCreateBody(name, slug?.ifBlank { null }, loader, mcVersion, description?.ifBlank { null }))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return runCatching { response.body<ModpackResponse>().pack }.getOrNull()
    }

    suspend fun updateModpack(id: String, name: String? = null, enabled: Boolean? = null) =
        authedPatch("/api/admin/modpacks/${encodePath(id)}", ModpackPatchBody(name?.ifBlank { null }, enabled))

    suspend fun deleteModpack(id: String) = authedDelete("/api/admin/modpacks/${encodePath(id)}")

    suspend fun deleteModpackArchive(id: String) = authedDelete("/api/admin/modpacks/${encodePath(id)}/archive")

    // --- Site settings ---
    suspend fun siteSettings(): SiteSettingsResponse = authedGet("/api/admin/site-settings")

    suspend fun setServerVersion(version: String): SiteSettingsResponse {
        val cookie = needCookie()
        val response = client.patch("$baseUrl/api/admin/site-settings") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(SiteSettingsBody(version))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    // --- Access ---
    suspend fun listAdminsData(): AdminAdminsResponse = authedGet("/api/admin/admins")

    suspend fun permissionDefs(): PermissionDefsResponse = authedGet("/api/admin/permissions")

    suspend fun addAdmin(nickname: String, permissions: List<String>) =
        authedPostJson("/api/admin/admins", AdminAccessBody(nickname, permissions))

    suspend fun updateAdmin(nickname: String, permissions: List<String>) =
        authedPatch("/api/admin/admins/${encodePath(nickname)}", AdminAccessBody(null, permissions))

    suspend fun removeAdmin(nickname: String) = authedDelete("/api/admin/admins/${encodePath(nickname)}")

    // --- Console ---
    suspend fun mcServers(): McServersResponse = authedGet("/api/admin/mc-servers")

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

    suspend fun sendNotification(title: String, message: String, all: Boolean, players: List<String> = emptyList(), href: String? = null) {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/notifications/send") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(NotifySendBody(all, players, title, message, href?.trim()?.ifBlank { null }, true))
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    suspend fun resetAccountPassword(nick: String, password: String? = null): ResetPasswordResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/accounts/${encodePath(nick)}/reset-password") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordBody(password?.trim()?.ifBlank { null }))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun deleteAccount(nick: String) = authedDelete("/api/admin/accounts/${encodePath(nick)}")

    suspend fun rconExec(command: String, serverId: String? = null): RconExecResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/rcon/exec") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(RconExecBody(command.trim(), serverId?.trim()?.ifBlank { null }))
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun consoleOutput(serverId: String? = null): ConsoleOutputResponse {
        val cookie = needCookie()
        val response = client.get("$baseUrl/api/admin/console/output") {
            header("Cookie", cookieHeader(cookie))
            val sid = serverId?.trim()?.ifBlank { null }
            if (sid != null) parameter("serverId", sid)
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun treasury(): AdminTreasuryResponse = authedGet("/api/admin/treasury")

    suspend fun treasuryPayout(
        toCode: String,
        amount: Long,
        reasonId: String,
        reasonNote: String? = null,
    ): AdminTreasuryResponse {
        val cookie = needCookie()
        val response = client.post("$baseUrl/api/admin/treasury/payout") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(
                TreasuryPayoutBody(
                    toCode = toCode.trim().uppercase(),
                    amount = amount,
                    reasonId = reasonId.trim(),
                    reasonNote = reasonNote?.trim()?.ifBlank { null },
                ),
            )
        }
        if (!response.status.isSuccess()) throw parseError(response)
        return response.body()
    }

    suspend fun adminBadges(): AdminBadgesResponse = authedGet("/api/admin/badges")

    suspend fun adminProducts(): AdminProductsResponse = authedGet("/api/admin/products")

    suspend fun deleteApplication(id: String) = authedDelete("/api/admin/applications/${encodePath(id)}")

    suspend fun deleteClan(id: String) = authedDelete("/api/admin/clans/${encodePath(id)}")

    suspend fun deleteBankCard(nick: String) = authedDelete("/api/admin/bank/${encodePath(nick)}")

    suspend fun adminOrders(query: String = ""): AdminOrdersResponse =
        authedGet("/api/admin/orders") { if (query.isNotBlank()) parameter("q", query) }

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

    suspend fun listModpacks(): List<ModpackDto> {
        val response = client.get("$baseUrl/api/modpacks")
        if (!response.status.isSuccess()) {
            throw StarlitApiException(
                response.status,
                "Не удалось загрузить сборки (${response.status.value})",
            )
        }
        val body = response.body<ModpacksResponse>()
        if (!body.ok && body.packs.isEmpty()) {
            throw StarlitApiException(HttpStatusCode.BadGateway, "Список сборок пуст или недоступен")
        }
        return body.packs
    }

    suspend fun getModpack(idOrSlug: String): ModpackDto? {
        val key = idOrSlug.trim()
        if (key.isEmpty()) return null
        val response = client.get("$baseUrl/api/modpacks/${encodePath(key)}")
        if (!response.status.isSuccess()) return null
        return runCatching { response.body<ModpackResponse>().pack }.getOrNull()
    }

    /**
     * Admin: upload new ZIP for a pack (chunked). Updates remote archive sha → clients see «Требуется обновление».
     */
    suspend fun uploadModpackArchive(
        packId: String,
        file: Path,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): ModpackDto {
        val id = packId.trim()
        require(id.isNotEmpty()) { "Нет id сборки" }
        require(Files.isRegularFile(file)) { "Файл не найден" }
        val size = file.fileSize()
        require(size in 64..(3L * 1024 * 1024 * 1024)) { "Некорректный размер ZIP" }
        val cookie = needCookie()

        val initResponse = client.post("$baseUrl/api/admin/modpacks/${encodePath(id)}/archive/init") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = 120_000
            }
            setBody(ArchiveInitBody(file.name, size))
        }
        if (!initResponse.status.isSuccess()) throw parseError(initResponse)
        val init = initResponse.body<ModpackArchiveInitResponse>()
        val uploadId = init.uploadId?.trim().orEmpty()
        if (!init.ok || uploadId.isEmpty()) {
            throw StarlitApiException(HttpStatusCode.BadRequest, init.error ?: "Не удалось начать загрузку")
        }
        val chunkSize = init.chunkSize.coerceIn(1024 * 1024, 32 * 1024 * 1024)
        val totalChunks = ((size + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

        Files.newInputStream(file).use { input ->
            val buf = ByteArray(chunkSize)
            var index = 0
            var sent = 0L
            while (true) {
                var filled = 0
                while (filled < chunkSize) {
                    val n = input.read(buf, filled, chunkSize - filled)
                    if (n <= 0) break
                    filled += n
                }
                if (filled <= 0) break
                val chunk = if (filled == buf.size) buf else buf.copyOf(filled)
                val chunkResponse = client.put("$baseUrl/api/admin/modpacks/${encodePath(id)}/archive/chunk") {
                    header("Cookie", cookieHeader(cookie))
                    header("X-Upload-Id", uploadId)
                    header("X-Chunk-Index", index.toString())
                    contentType(ContentType.Application.OctetStream)
                    timeout {
                        requestTimeoutMillis = 300_000
                        socketTimeoutMillis = 300_000
                    }
                    setBody(chunk)
                }
                if (!chunkResponse.status.isSuccess()) throw parseError(chunkResponse)
                sent += filled
                index++
                onProgress(
                    (sent.toFloat() / size.toFloat()).coerceIn(0f, 0.95f),
                    "Загрузка ${(sent * 100 / size).toInt()}% ($index/$totalChunks)",
                )
            }
        }

        onProgress(0.97f, "Завершение загрузки…")
        val completeResponse = client.post("$baseUrl/api/admin/modpacks/${encodePath(id)}/archive/complete") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 300_000 }
            setBody(ArchiveCompleteBody(uploadId))
        }
        if (!completeResponse.status.isSuccess()) throw parseError(completeResponse)
        val done = completeResponse.body<ModpackArchiveCompleteResponse>()
        if (!done.ok) {
            throw StarlitApiException(HttpStatusCode.BadRequest, done.error ?: "Не удалось завершить загрузку")
        }
        return done.pack ?: getModpack(id) ?: error("Архив загружен, но сборка не вернулась")
    }

    fun avatarUrl(
        player: String,
        uuid: String? = null,
        hash: String? = null,
        skinUrl: String? = null,
        size: Int = 64,
    ): String {
        val p = java.net.URLEncoder.encode(player, Charsets.UTF_8)
        val parts = mutableListOf("player=$p", "size=$size")
        if (!uuid.isNullOrBlank()) parts += "uuid=${java.net.URLEncoder.encode(uuid, Charsets.UTF_8)}"
        if (!hash.isNullOrBlank()) parts += "hash=${java.net.URLEncoder.encode(hash, Charsets.UTF_8)}"
        if (!skinUrl.isNullOrBlank()) parts += "url=${java.net.URLEncoder.encode(skinUrl, Charsets.UTF_8)}"
        return "$baseUrl/api/avatar?${parts.joinToString("&")}"
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

    private suspend inline fun <reified B> authedPostJson(path: String, body: B) {
        val cookie = needCookie()
        val response = client.post("$baseUrl$path") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    private suspend inline fun <reified B> authedPatch(path: String, body: B) {
        val cookie = needCookie()
        val response = client.patch("$baseUrl$path") {
            header("Cookie", cookieHeader(cookie))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    private suspend fun authedDelete(path: String) {
        val cookie = needCookie()
        val response = client.delete("$baseUrl$path") { header("Cookie", cookieHeader(cookie)) }
        if (!response.status.isSuccess()) throw parseError(response)
    }

    private fun encodePath(segment: String): String =
        java.net.URLEncoder.encode(segment.trim(), Charsets.UTF_8)

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
