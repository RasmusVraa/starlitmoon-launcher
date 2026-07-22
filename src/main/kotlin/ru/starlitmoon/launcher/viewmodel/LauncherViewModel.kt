package ru.starlitmoon.launcher.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.api.AdminApplicationDto
import ru.starlitmoon.launcher.api.AdminMeResponse
import ru.starlitmoon.launcher.api.AdminPlayerDto
import ru.starlitmoon.launcher.api.AdminStatsResponse
import ru.starlitmoon.launcher.api.MeResponse
import ru.starlitmoon.launcher.api.NotificationDto
import ru.starlitmoon.launcher.api.ServerStatus
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.api.StarlitApiException
import ru.starlitmoon.launcher.minecraft.MinecraftLauncher
import ru.starlitmoon.launcher.update.UpdateChecker
import ru.starlitmoon.launcher.update.UpdateInfo

enum class LauncherTab {
    Play,
    Cabinet,
    Admin,
    Settings,
}

class LauncherViewModel(
    private val scope: CoroutineScope,
    private val api: StarlitApiClient = StarlitApiClient(),
    private val mc: MinecraftLauncher = MinecraftLauncher(),
    private val config: LauncherConfig = LauncherConfig.load(),
    private val updateChecker: UpdateChecker = UpdateChecker(config),
) {
    var isRefreshingStatus by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isCheckingUpdates by mutableStateOf(false)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
    var updateDismissed by mutableStateOf(false)
    var isLoggedIn by mutableStateOf(false)
    var isAdmin by mutableStateOf(false)
    var userName by mutableStateOf("")
    var userUuid by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var infoMessage by mutableStateOf<String?>(null)
    var currentTab by mutableStateOf(LauncherTab.Play)
    var serverStatus by mutableStateOf(ServerStatus.offline(config.serverHost))
    var serverVersion by mutableStateOf(config.defaultMcVersion)
    var clientVersion by mutableStateOf(config.minecraftVersionId)
    var meData by mutableStateOf<MeResponse?>(null)
    var adminMe by mutableStateOf<AdminMeResponse?>(null)
    var adminStats by mutableStateOf<AdminStatsResponse?>(null)
    var adminPlayers by mutableStateOf<List<AdminPlayerDto>>(emptyList())
    var adminApps by mutableStateOf<List<AdminApplicationDto>>(emptyList())
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var launchProgress by mutableStateOf<String?>(null)
    var onlinePlayers by mutableStateOf<List<String>>(emptyList())
    var adminSearch by mutableStateOf("")

    private var statusJob: Job? = null
    private var booted = false

    fun boot() {
        if (booted) return
        booted = true
        scope.launch {
            val sessionJob = async(Dispatchers.IO) {
                runCatching { api.restoreSession() }.getOrNull()
            }
            val publicJob = async(Dispatchers.IO) { refreshPublicData() }
            if (config.checkUpdatesOnStart) launch { checkForUpdates() }
            val restored = sessionJob.await()
            if (restored?.user != null) applySession(restored)
            publicJob.await()
            startStatusPolling()
        }
    }

    fun checkForUpdates() {
        scope.launch {
            isCheckingUpdates = true
            runCatching {
                withContext(Dispatchers.IO) { updateChecker.checkForUpdate().getOrThrow() }
            }.onSuccess { update ->
                updateInfo = update
                if (update != null) {
                    updateDismissed = false
                    infoMessage = "Доступна версия ${update.latestVersion}"
                } else {
                    infoMessage = "Установлена актуальная версия"
                }
            }.onFailure {
                errorMessage = "Не удалось проверить обновления"
            }
            isCheckingUpdates = false
        }
    }

    fun downloadUpdate() {
        val update = updateInfo ?: return
        val url = update.installerUrl ?: update.releasePageUrl
        runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        }.onFailure {
            errorMessage = "Не удалось открыть загрузку"
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    fun login(nickname: String, password: String) {
        if (nickname.isBlank() || password.isBlank()) {
            errorMessage = "Введите ник и пароль"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) { api.login(nickname, password) }
            }.onSuccess {
                applySession(it)
                infoMessage = "Вход выполнен"
            }.onFailure { handleError(it) }
            isLoading = false
        }
    }

    fun logout() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) { runCatching { api.logout() } }
            isLoggedIn = false
            isAdmin = false
            userName = ""
            userUuid = null
            meData = null
            adminMe = null
            adminStats = null
            adminPlayers = emptyList()
            adminApps = emptyList()
            notifications = emptyList()
            if (currentTab == LauncherTab.Admin || currentTab == LauncherTab.Cabinet) {
                currentTab = LauncherTab.Play
            }
            isLoading = false
        }
    }

    fun play() {
        if (!isLoggedIn) {
            errorMessage = "Сначала войдите в аккаунт"
            currentTab = LauncherTab.Cabinet
            return
        }
        scope.launch {
            isLoading = true
            launchProgress = "Подготовка…"
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                mc.launch(userName, config.minecraftVersionId) { progress ->
                    scope.launch { launchProgress = progress }
                }
            }
            if (result.success) {
                clientVersion = runCatching {
                    withContext(Dispatchers.IO) { mc.resolveClientVersion(config.minecraftVersionId) }
                }.getOrDefault(config.minecraftVersionId)
                infoMessage = "Игра запущена. Введите /login на сервере"
            } else {
                errorMessage = result.message
            }
            launchProgress = null
            isLoading = false
        }
    }

    fun refreshAdmin() {
        if (!isAdmin) return
        scope.launch {
            isLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    adminMe = api.adminMe()
                    adminStats = runCatching { api.adminStats() }.getOrNull()
                    adminPlayers = runCatching { api.adminPlayers(adminSearch).players }.getOrDefault(emptyList())
                    adminApps = runCatching {
                        api.adminApplications().filter { it.status == "pending" }
                    }.getOrDefault(emptyList())
                }
            }.onFailure { handleError(it) }
            isLoading = false
        }
    }

    fun searchAdminPlayers(query: String) {
        adminSearch = query
        if (!isAdmin) return
        scope.launch {
            adminPlayers = withContext(Dispatchers.IO) {
                runCatching { api.adminPlayers(query).players }.getOrDefault(emptyList())
            }
        }
    }

    fun refreshCabinet() {
        if (!isLoggedIn) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    meData = api.me()
                    notifications = api.notifications()
                }
            }.onFailure { handleError(it) }
        }
    }

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    private fun applySession(me: MeResponse) {
        isLoggedIn = true
        isAdmin = me.admin
        userName = me.user?.name.orEmpty()
        userUuid = me.user?.uuid
        meData = me
        scope.launch {
            notifications = withContext(Dispatchers.IO) {
                runCatching { api.notifications() }.getOrDefault(emptyList())
            }
        }
        if (me.admin) refreshAdmin()
    }

    private suspend fun refreshPublicData() {
        isRefreshingStatus = true
        try {
            coroutineScope {
                val versionDeferred = async(Dispatchers.IO) {
                    runCatching { api.serverVersion() }.getOrDefault(config.defaultMcVersion)
                }
                val statusDeferred = async(Dispatchers.IO) { api.fetchServerStatus() }
                val playersDeferred = async(Dispatchers.IO) {
                    runCatching { api.fetchOnlinePlayers().online.map { it.name } }.getOrDefault(emptyList())
                }
                serverVersion = versionDeferred.await()
                serverStatus = statusDeferred.await()
                onlinePlayers = playersDeferred.await()
            }
        } finally {
            isRefreshingStatus = false
        }
    }

    private fun startStatusPolling() {
        statusJob?.cancel()
        statusJob = scope.launch {
            while (true) {
                delay(60_000)
                withContext(Dispatchers.IO) { refreshPublicData() }
            }
        }
    }

    private fun handleError(error: Throwable) {
        when (error) {
            is StarlitApiException -> {
                errorMessage = if (error.cabinetBlocked) {
                    buildString {
                        append(error.message)
                        error.applicationStatus?.let { append(" ($it)") }
                    }
                } else {
                    error.message
                }
            }
            else -> errorMessage = error.message ?: "Ошибка"
        }
    }

    fun dispose() {
        statusJob?.cancel()
        api.close()
        mc.close()
        updateChecker.close()
    }
}
