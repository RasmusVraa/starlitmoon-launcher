package ru.starlitmoon.launcher.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val player: JsonElement? = null,
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
data class HealthResponse(
    val ok: Boolean = false,
    val playersOnline: Int? = null,
    @SerialName("playersMax") val playersMax: Int? = null,
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
    val accounts: Int? = null,
    val orders: Int? = null,
    val online: Int? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
)
