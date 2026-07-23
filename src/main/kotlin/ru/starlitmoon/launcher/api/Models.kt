package ru.starlitmoon.launcher.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val error: String? = null,
    val applicationStatus: String? = null,
    val cabinetBlocked: Boolean? = null,
)

@Serializable
data class UserDto(val name: String, val uuid: String? = null)

@Serializable
data class LoginResponse(val ok: Boolean = false, val user: UserDto? = null, val admin: Boolean = false)

@Serializable
data class MeResponse(
    val ok: Boolean = false,
    val user: UserDto? = null,
    val admin: Boolean = false,
    val cabinet: CabinetDto? = null,
)

@Serializable
data class CabinetDto(
    val found: Boolean = false,
    val player: CabinetPlayerDto? = null,
    val privacy: PrivacyDto? = null,
    val sections: List<PrivacySectionDto> = emptyList(),
    val notificationPrefs: Map<String, Boolean>? = null,
    val notificationChannels: List<NotifyChannelDto> = emptyList(),
    val badges: CabinetBadgesDto? = null,
    val commentsEnabled: Boolean? = null,
    val skinUrl: String? = null,
    val skinTextureHash: String? = null,
    val profileStatus: String? = null,
)

@Serializable
data class CabinetBadgesDto(
    val owned: List<BadgeDto> = emptyList(),
    val activeBadgeId: String? = null,
    val badgeVisible: Boolean = false,
    val activeBadge: BadgeDto? = null,
)

@Serializable
data class NotifyChannelDto(
    val id: String? = null,
    val label: String? = null,
    val hint: String? = null,
    val enabled: Boolean = true,
    val available: Boolean = true,
    val disabledReason: String? = null,
)

@Serializable
data class PrivacyDto(
    val hidden: List<String> = emptyList(),
    val showBankBalance: Boolean? = null,
    val showActivity: Boolean? = null,
)

@Serializable
data class PrivacySectionDto(
    val id: String? = null,
    val label: String? = null,
    val hint: String? = null,
    val visible: Boolean = true,
    val hidden: Boolean = false,
)

@Serializable
data class CabinetPlayerDto(
    val name: String? = null,
    val uuid: String? = null,
    val online: Boolean? = null,
    val banned: Boolean? = null,
    val banReason: String? = null,
    val warnCount: Int? = null,
    val ranks: List<String> = emptyList(),
    val profileStatus: String? = null,
    val stats: PlayerStatsDto? = null,
    val discord: SocialLinkDto? = null,
    val telegram: SocialLinkDto? = null,
    val skinUrl: String? = null,
    val skinTextureHash: String? = null,
    val badgesOwned: List<String> = emptyList(),
    val activeBadgeId: String? = null,
    val badgeVisible: Boolean? = null,
    val activeBadge: BadgeDto? = null,
    val commentsEnabled: Boolean? = null,
)

@Serializable
data class PlayerStatsDto(
    val playtimeMinutes: Long? = null,
    val deaths: Int? = null,
    val mobKills: Int? = null,
    val playerKills: Int? = null,
    val blocksMined: Long? = null,
    val blocksPlaced: Long? = null,
    val level: Int? = null,
    val firstJoin: String? = null,
    val lastJoin: String? = null,
)

@Serializable
data class SocialLinkDto(
    val id: String? = null,
    val username: String? = null,
    val displayName: String? = null,
)

@Serializable
data class BadgeDto(
    val id: String? = null,
    val emoji: String? = null,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class AdminMeResponse(
    val loggedIn: Boolean = false,
    val admin: Boolean = false,
    val permissions: List<String> = emptyList(),
    val permissionDefs: List<PermissionDefDto> = emptyList(),
    val consoleServerIds: List<String>? = null,
    val consoleManage: Boolean = false,
    val user: UserDto? = null,
)

@Serializable
data class PermissionDefDto(
    val id: String,
    val label: String? = null,
    val description: String? = null,
)

@Serializable
data class ServerVersionResponse(val version: String)

@Serializable
data class PlayersResponse(val online: List<PlayerOnlineDto> = emptyList(), val count: Int = 0)

@Serializable
data class PlayerOnlineDto(val name: String, val uuid: String? = null)

@Serializable
data class AdminStatsResponse(
    val players: Int? = null,
    val online: Int? = null,
    val banned: Int? = null,
    val warned: Int? = null,
    val accounts: Int? = null,
    val orders: Int? = null,
    val pendingApplications: Int? = null,
    val pendingClans: Int? = null,
    val totalBankBalance: Long? = null,
    val treasuryBalance: Long? = null,
    val badges: Int? = null,
    val bankCards: Int? = null,
)

@Serializable
data class AdminPlayersResponse(val total: Int = 0, val players: List<AdminPlayerDto> = emptyList())

@Serializable
data class AdminPlayerDto(
    val name: String? = null,
    val uuid: String? = null,
    val online: Boolean = false,
    val banned: Boolean = false,
    val warnCount: Int = 0,
    val ranks: List<String> = emptyList(),
    val lastSeen: String? = null,
)

@Serializable
data class AdminPlayerDetailResponse(val player: AdminPlayerDetailDto? = null)

@Serializable
data class AdminPlayerDetailDto(
    val name: String? = null,
    val uuid: String? = null,
    val online: Boolean = false,
    val banned: Boolean = false,
    val banReason: String? = null,
    val warnCount: Int = 0,
    val ranks: List<String> = emptyList(),
    val profileStatus: String? = null,
)

@Serializable
data class AdminApplicationsResponse(
    val applications: List<AdminApplicationDto> = emptyList(),
    val pending: Int = 0,
)

@Serializable
data class AdminApplicationDto(
    val id: String? = null,
    val minecraftNick: String? = null,
    val discordNick: String? = null,
    val status: String? = null,
)

@Serializable
data class AdminAccountsResponse(val accounts: List<AdminAccountDto> = emptyList())

@Serializable
data class AdminAccountDto(
    val name: String? = null,
    val uuid: String? = null,
    val crypto: String? = null,
)

@Serializable
data class AdminBankResponse(val cards: List<AdminBankCardDto> = emptyList())

@Serializable
data class AdminBankCardDto(
    val ownerName: String? = null,
    val balance: Long? = null,
    val cardCode: String? = null,
)

@Serializable
data class AdminClansResponse(val clans: List<AdminClanDto> = emptyList())

@Serializable
data class AdminClanDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val status: String? = null,
    val tag: String? = null,
    val owner: String? = null,
    val description: String? = null,
    val prefixFormatted: String? = null,
    val members: List<String> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class NotificationsResponse(val notifications: List<NotificationDto> = emptyList(), val unreadCount: Int = 0)

@Serializable
data class NotificationDto(
    val id: String? = null,
    val title: String? = null,
    val message: String? = null,
    val read: Boolean = false,
)

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class ResetPasswordResponse(
    val ok: Boolean = false,
    val nickname: String? = null,
    val password: String? = null,
    val resetId: String? = null,
    val message: String? = null,
)

@Serializable
data class RconExecResponse(
    val ok: Boolean = false,
    val command: String? = null,
    val response: String? = null,
    val executedBy: String? = null,
    val serverId: String? = null,
    val at: String? = null,
)

@Serializable
data class ConsoleOutputResponse(
    val ok: Boolean = false,
    val text: String? = null,
    val error: String? = null,
    val screenSession: String? = null,
    val host: String? = null,
    val fetchedAt: String? = null,
    val serverId: String? = null,
    val serverName: String? = null,
)

@Serializable
data class AdminTreasuryResponse(
    val ok: Boolean = false,
    val treasury: AdminTreasuryCardDto? = null,
    val payoutReasons: List<TreasuryPayoutReasonDto> = emptyList(),
    val penaltiesPending: Int = 0,
    val penaltiesPaidTotal: Long = 0,
)

@Serializable
data class AdminTreasuryCardDto(
    val cardCode: String? = null,
    val balance: Long? = null,
    val ownerName: String? = null,
    val isTreasury: Boolean? = null,
)

@Serializable
data class TreasuryPayoutReasonDto(
    val id: String? = null,
    val label: String? = null,
)

@Serializable
data class AdminBadgesResponse(
    val total: Int = 0,
    val badges: List<AdminBadgeDto> = emptyList(),
)

@Serializable
data class AdminBadgeDto(
    val id: String? = null,
    val emoji: String? = null,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class AdminProductsResponse(
    val total: Int = 0,
    val products: List<AdminProductDto> = emptyList(),
)

@Serializable
data class AdminProductDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val price: Int? = null,
    val icon: String? = null,
    val commands: List<String> = emptyList(),
)

@Serializable
data class AdminOrdersResponse(
    val total: Int = 0,
    val orders: List<AdminOrderDto> = emptyList(),
)

@Serializable
data class AdminOrderDto(
    val id: String? = null,
    val nickname: String? = null,
    val productId: String? = null,
    val productName: String? = null,
    val price: Int? = null,
    val status: String? = null,
    val delivered: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class ModpacksResponse(
    val ok: Boolean = false,
    val packs: List<ModpackDto> = emptyList(),
)

@Serializable
data class ModpackResponse(
    val ok: Boolean = false,
    val pack: ModpackDto? = null,
)

@Serializable
data class ModpackDto(
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
    val description: String? = null,
    val loader: String? = null,
    val mcVersion: String? = null,
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val hasArchive: Boolean = false,
    val archive: ModpackArchiveDto? = null,
    val modsCount: Int = 0,
    val mods: List<ModpackModDto> = emptyList(),
    /** Опционально: версия Fabric Loader / NeoForge. Пусто = последняя подходящая. */
    val loaderVersion: String? = null,
)

@Serializable
data class ModpackArchiveDto(
    val fileName: String? = null,
    val size: Long? = null,
    val sha256: String? = null,
    val uploadedAt: String? = null,
    val url: String? = null,
    val originalName: String? = null,
)

@Serializable
data class ModpackModDto(
    val id: String? = null,
    val fileName: String? = null,
    val size: Long? = null,
    val sha256: String? = null,
    val url: String? = null,
)

@Serializable
data class ModpackArchiveInitResponse(
    val ok: Boolean = false,
    val uploadId: String? = null,
    val chunkSize: Int = 16 * 1024 * 1024,
    val maxBytes: Long? = null,
    val error: String? = null,
)

@Serializable
data class ModpackArchiveChunkResponse(
    val ok: Boolean = false,
    val received: Long? = null,
    val error: String? = null,
)

@Serializable
data class ModpackArchiveCompleteResponse(
    val ok: Boolean = false,
    val pack: ModpackDto? = null,
    val error: String? = null,
)

// --- Map ---

@Serializable
data class AdminMapSettingsResponse(
    val visibility: String? = null,
    val embedUrl: String? = null,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
)

@Serializable
data class AdminMapMarkersResponse(
    val ok: Boolean = false,
    val markers: List<AdminMapMarkerDto> = emptyList(),
    val total: Int = 0,
    val icons: List<AdminMapIconDto> = emptyList(),
    val worlds: List<AdminMapWorldDto> = emptyList(),
)

@Serializable
data class AdminMapMarkerDto(
    val id: String? = null,
    val ownerName: String? = null,
    val name: String? = null,
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val iconId: String? = null,
    val squaremarkerId: Int? = null,
)

@Serializable
data class AdminMapIconDto(val id: String? = null, val label: String? = null)

@Serializable
data class AdminMapWorldDto(val id: String? = null, val label: String? = null)

// --- Contest ---

@Serializable
data class AdminContestResponse(
    val total: Int = 0,
    val totalAll: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val winner: Int = 0,
    val entries: List<AdminContestEntryDto> = emptyList(),
    val settings: AdminContestSettingsDto? = null,
)

@Serializable
data class AdminContestEntryDto(
    val id: String? = null,
    val minecraftNick: String? = null,
    val teamMembers: List<String> = emptyList(),
    val baseName: String? = null,
    val description: String? = null,
    val coordinates: String? = null,
    val buildOrigin: String? = null,
    val status: String? = null,
    val adminNote: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AdminContestSettingsDto(
    val enabled: Boolean = true,
    val page: AdminContestPageDto? = null,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
)

@Serializable
data class AdminContestPageDto(
    val title: String? = null,
    val lead: String? = null,
    val seasonName: String? = null,
)

// --- Wiki ---

@Serializable
data class AdminWikiResponse(
    val spaceTitle: String? = null,
    val spaceDescription: String? = null,
    val updatedAt: String? = null,
    val pages: List<AdminWikiPageDto> = emptyList(),
)

@Serializable
data class AdminWikiPageDto(
    val id: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val published: Boolean = true,
    val adminOnly: Boolean = false,
    val parentId: String? = null,
    val blocks: List<AdminWikiBlockDto> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class AdminWikiBlockDto(
    val id: String? = null,
    val type: String? = null,
    val data: AdminWikiBlockDataDto? = null,
)

@Serializable
data class AdminWikiBlockDataDto(val text: String? = null)

@Serializable
data class AdminWikiPageResponse(val ok: Boolean = false, val page: AdminWikiPageDto? = null, val error: String? = null)

// --- Access ---

@Serializable
data class AdminAdminsResponse(
    val total: Int = 0,
    val admins: List<AdminAdminDto> = emptyList(),
    val permissionDefs: List<PermissionDefDto> = emptyList(),
)

@Serializable
data class AdminAdminDto(
    val nickname: String? = null,
    val permissions: List<String> = emptyList(),
    val consoleServerIds: List<String>? = null,
    val addedAt: String? = null,
    val addedBy: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PermissionDefsResponse(val permissionDefs: List<PermissionDefDto> = emptyList())

// --- Site settings ---

@Serializable
data class SiteSettingsResponse(val serverVersion: String? = null)

// --- MC servers (console) ---

@Serializable
data class McServersResponse(
    val ok: Boolean = false,
    val servers: List<McServerDto> = emptyList(),
    val consoleManage: Boolean = false,
)

@Serializable
data class McServerDto(
    val id: String? = null,
    val name: String? = null,
    val screenSession: String? = null,
)

// --- Badges (list uses total/badges already) ---

@Serializable
data class AdminBadgeResponse(val ok: Boolean = false, val badge: AdminBadgeDto? = null, val error: String? = null)

@Serializable
data class AdminModpacksResponse(val ok: Boolean = false, val packs: List<ModpackDto> = emptyList())
