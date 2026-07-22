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
import ru.starlitmoon.launcher.api.AdminMeResponse
import ru.starlitmoon.launcher.api.MeResponse
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
    var isBootLoading by mutableStateOf(false)
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
    var meData by mutableStateOf<MeResponse?>(null)
    var adminMe by mutableStateOf<AdminMeResponse?>(null)
    var launchProgress by mutableStateOf<String?>(null)
    var onlinePlayers by mutableStateOf<List<String>>(emptyList())

    private var statusJob: Job? = null
    private var booted = false

    fun boot() {
        if (booted) return
        booted = true
        // UI сразу, сеть в фоне
        scope.launch {
            val sessionJob = async(Dispatchers.IO) {
                runCatching { api.restoreSession() }.getOrNull()
            }
            val publicJob = async(Dispatchers.IO) { refreshPublicData() }
            if (config.checkUpdatesOnStart) {
                launch { checkForUpdates() }
            }
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
            }
                .onSuccess { update ->
                    updateInfo = update
                    if (update != null) updateDismissed = false
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
            errorMessage = "Не удалось открыть страницу загрузки"
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
            }
                .onSuccess { applySession(it) }
                .onFailure { handleError(it) }
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
            launchProgress = "Подготовка клиента…"
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                mc.launch(userName, config.minecraftVersionId) { progress ->
                    scope.launch { launchProgress = progress }
                }
            }
            launchProgress = null
            if (result.success) {
                infoMessage = "Игра запущена. На сервере: /login <пароль>"
            } else {
                errorMessage = result.message
            }
            isLoading = false
        }
    }

    fun openDiscordLogin() {
        runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI(api.discordLoginUrl()))
        }.onFailure {
            errorMessage = "Не удалось открыть браузер для Discord"
        }
    }

    fun refreshAdminAccess() {
        if (!isAdmin) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.adminMe() }
            }
                .onSuccess { adminMe = it }
                .onFailure { handleError(it) }
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
        if (me.admin) refreshAdminAccess()
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
                        error.applicationStatus?.let { append(" (заявка: $it)") }
                    }
                } else {
                    error.message
                }
            }
            else -> errorMessage = error.message ?: "Неизвестная ошибка"
        }
    }

    fun dispose() {
        statusJob?.cancel()
        api.close()
        mc.close()
        updateChecker.close()
    }
}
