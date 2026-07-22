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
    val skinUrl: String? = null,
    val profileStatus: String? = null,
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
    val user: UserDto? = null,
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
    val status: String? = null,
    val tag: String? = null,
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
