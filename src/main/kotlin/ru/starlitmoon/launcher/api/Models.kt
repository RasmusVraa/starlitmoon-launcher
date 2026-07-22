package ru.starlitmoon.launcher.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val error: String? = null,
    val applicationStatus: String? = null,
    val cabinetBlocked: Boolean? = null,
)

@Serializable
data class UserDto(
    val name: String,
    val uuid: String? = null,
)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val user: UserDto? = null,
    val admin: Boolean = false,
)

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
)

@Serializable
data class CabinetPlayerDto(
    val name: String? = null,
    val uuid: String? = null,
    val online: Boolean? = null,
    val banned: Boolean? = null,
    val warnCount: Int? = null,
    val ranks: List<String> = emptyList(),
    val profileStatus: String? = null,
    val lastSeen: String? = null,
    val playtimeMinutes: Long? = null,
)

@Serializable
data class AdminMeResponse(
    val loggedIn: Boolean = false,
    val admin: Boolean = false,
    val permissions: List<String> = emptyList(),
    val user: UserDto? = null,
)

@Serializable
data class ServerVersionResponse(
    val version: String,
)

@Serializable
data class PlayersResponse(
    val online: List<PlayerOnlineDto> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class PlayerOnlineDto(
    val name: String,
    val uuid: String? = null,
)

@Serializable
data class AdminStatsResponse(
    val players: Int? = null,
    val online: Int? = null,
    val banned: Int? = null,
    val accounts: Int? = null,
    val orders: Int? = null,
    val pendingApplications: Int? = null,
    val pendingClans: Int? = null,
    val totalBankBalance: Long? = null,
    val badges: Int? = null,
)

@Serializable
data class AdminPlayersResponse(
    val total: Int = 0,
    val players: List<AdminPlayerDto> = emptyList(),
)

@Serializable
data class AdminPlayerDto(
    val name: String? = null,
    val uuid: String? = null,
    val online: Boolean = false,
    val banned: Boolean = false,
    val warnCount: Int = 0,
    val ranks: List<String> = emptyList(),
)

@Serializable
data class AdminApplicationsResponse(
    val applications: List<AdminApplicationDto> = emptyList(),
)

@Serializable
data class AdminApplicationDto(
    val id: String? = null,
    val minecraftNick: String? = null,
    val status: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
)

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationDto> = emptyList(),
)

@Serializable
data class NotificationDto(
    val id: String? = null,
    val title: String? = null,
    val message: String? = null,
    val read: Boolean = false,
)
