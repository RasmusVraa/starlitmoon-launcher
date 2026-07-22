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
import ru.starlitmoon.launcher.api.AdminAccountDto
import ru.starlitmoon.launcher.api.AdminApplicationDto
import ru.starlitmoon.launcher.api.AdminBankCardDto
import ru.starlitmoon.launcher.api.AdminClanDto
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

enum class LauncherTab { Play, Cabinet, Admin, Settings }

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
    var adminSubTab by mutableStateOf(0)
    var serverStatus by mutableStateOf(ServerStatus.offline(config.serverHost))
    var serverVersion by mutableStateOf(config.defaultMcVersion)
    var meData by mutableStateOf<MeResponse?>(null)
    var adminMe by mutableStateOf<AdminMeResponse?>(null)
    var adminStats by mutableStateOf<AdminStatsResponse?>(null)
    var adminPlayers by mutableStateOf<List<AdminPlayerDto>>(emptyList())
    var adminApps by mutableStateOf<List<AdminApplicationDto>>(emptyList())
    var adminAccounts by mutableStateOf<List<AdminAccountDto>>(emptyList())
    var adminBank by mutableStateOf<List<AdminBankCardDto>>(emptyList())
    var adminClans by mutableStateOf<List<AdminClanDto>>(emptyList())
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var launchProgress by mutableStateOf<String?>(null)
    var onlinePlayers by mutableStateOf<List<String>>(emptyList())
    var adminSearch by mutableStateOf("")
    var statusDraft by mutableStateOf("")
    var notifyTitle by mutableStateOf("")
    var notifyMessage by mutableStateOf("")

    private var statusJob: Job? = null
    private var booted = false

    fun boot() {
        if (booted) return
        booted = true
        scope.launch {
            val sessionJob = async(Dispatchers.IO) { runCatching { api.restoreSession() }.getOrNull() }
            val publicJob = async(Dispatchers.IO) { refreshPublicData() }
            if (config.checkUpdatesOnStart) launch { checkForUpdates(silent = true) }
            val restored = sessionJob.await()
            if (restored?.user != null) applySession(restored)
            publicJob.await()
            startStatusPolling()
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        scope.launch {
            isCheckingUpdates = true
            runCatching { withContext(Dispatchers.IO) { updateChecker.checkForUpdate().getOrThrow() } }
                .onSuccess { update ->
                    updateInfo = update
                    if (update != null) {
                        updateDismissed = false
                        if (!silent) infoMessage = "Доступна версия ${update.latestVersion}"
                    } else if (!silent) {
                        infoMessage = "Установлена актуальная версия"
                    }
                }
                .onFailure { if (!silent) errorMessage = "Не удалось проверить обновления" }
            isCheckingUpdates = false
        }
    }

    fun downloadUpdate() {
        val update = updateInfo ?: return
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(update.installerUrl ?: update.releasePageUrl)) }
            .onFailure { errorMessage = "Не удалось открыть загрузку" }
    }

    fun dismissUpdate() { updateDismissed = true }

    fun login(nickname: String, password: String) {
        if (nickname.isBlank() || password.isBlank()) {
            errorMessage = "Введите ник и пароль"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            runCatching { withContext(Dispatchers.IO) { api.login(nickname, password) } }
                .onSuccess {
                    applySession(it)
                    infoMessage = "Вход выполнен"
                    currentTab = LauncherTab.Play
                }
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
            adminStats = null
            adminPlayers = emptyList()
            adminApps = emptyList()
            adminAccounts = emptyList()
            adminBank = emptyList()
            adminClans = emptyList()
            notifications = emptyList()
            currentTab = LauncherTab.Play
            isLoading = false
        }
    }

    fun play() {
        if (!isLoggedIn) {
            errorMessage = "Сначала войдите"
            currentTab = LauncherTab.Cabinet
            return
        }
        scope.launch {
            isLoading = true
            launchProgress = "Подготовка…"
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                mc.launch(userName, config.minecraftVersionId) { p -> scope.launch { launchProgress = p } }
            }
            if (result.success) {
                infoMessage = "Игра запущена. На сервере: /login"
                result.process?.let { process ->
                    scope.launch(Dispatchers.IO) {
                        delay(5000)
                        if (!process.isAlive) {
                            val log = runCatching {
                                config.dataDir.resolve("last-launch.log").toFile().readText().takeLast(800)
                            }.getOrNull()
                            scope.launch {
                                errorMessage = "Игра закрылась. ${log?.lineSequence()?.lastOrNull() ?: "См. лог запуска"}"
                            }
                        }
                    }
                }
            } else {
                errorMessage = result.message
            }
            launchProgress = null
            isLoading = false
        }
    }

    fun refreshCabinet() {
        if (!isLoggedIn) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    meData = api.me()
                    notifications = api.notifications()
                    statusDraft = meData?.cabinet?.player?.profileStatus
                        ?: meData?.cabinet?.profileStatus.orEmpty()
                }
            }.onFailure { handleError(it) }
        }
    }

    fun saveProfileStatus() {
        scope.launch {
            isLoading = true
            runCatching { withContext(Dispatchers.IO) { api.setProfileStatus(statusDraft) } }
                .onSuccess {
                    meData = it
                    infoMessage = "Статус сохранён"
                }
                .onFailure { handleError(it) }
            isLoading = false
        }
    }

    fun setPrivacy(section: String, visible: Boolean) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setPrivacy(section, visible) } }
                .onSuccess { meData = it }
                .onFailure { handleError(it) }
        }
    }

    fun setNotifyPref(channel: String, enabled: Boolean) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setNotificationPref(channel, enabled) } }
                .onSuccess { meData = it }
                .onFailure { handleError(it) }
        }
    }

    fun setBadgeVisible(visible: Boolean) {
        scope.launch {
            val id = meData?.cabinet?.player?.activeBadgeId
            runCatching { withContext(Dispatchers.IO) { api.setBadge(id, visible) } }
                .onSuccess { meData = it }
                .onFailure { handleError(it) }
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
                    adminApps = runCatching { api.adminApplications("pending").applications }.getOrDefault(emptyList())
                    adminAccounts = runCatching { api.adminAccounts().accounts }.getOrDefault(emptyList())
                    adminBank = runCatching { api.adminBank().cards }.getOrDefault(emptyList())
                    adminClans = runCatching { api.adminClans("pending").clans }.getOrDefault(emptyList())
                }
            }.onFailure { handleError(it) }
            isLoading = false
        }
    }

    fun searchAdminPlayers(q: String) {
        adminSearch = q
        scope.launch {
            adminPlayers = withContext(Dispatchers.IO) {
                runCatching { api.adminPlayers(q).players }.getOrDefault(emptyList())
            }
        }
    }

    fun banPlayer(name: String, ban: Boolean) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.patchAdminPlayer(name, banned = ban, banReason = if (ban) "Через лаунчер" else null)
                }
            }.onSuccess {
                infoMessage = if (ban) "Забанен: $name" else "Разбанен: $name"
                refreshAdmin()
            }.onFailure { handleError(it) }
        }
    }

    fun warnPlayer(name: String, count: Int) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.patchAdminPlayer(name, warnCount = count) } }
                .onSuccess {
                    infoMessage = "Варны $name: $count"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun acceptApp(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.acceptApplication(id) } }
                .onSuccess {
                    infoMessage = "Заявка принята"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun rejectApp(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.rejectApplication(id) } }
                .onSuccess {
                    infoMessage = "Заявка отклонена"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun approveClan(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.approveClan(id) } }
                .onSuccess {
                    infoMessage = "Клан одобрен"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun rejectClan(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.rejectClan(id) } }
                .onSuccess {
                    infoMessage = "Клан отклонён"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun setBank(nick: String, balance: Long) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setBankBalance(nick, balance) } }
                .onSuccess {
                    infoMessage = "Баланс $nick: $balance"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun sendBroadcast() {
        if (notifyTitle.isBlank() || notifyMessage.isBlank()) {
            errorMessage = "Заполните заголовок и текст"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.sendNotification(notifyTitle, notifyMessage, all = true)
                }
            }.onSuccess {
                infoMessage = "Уведомление отправлено"
                notifyTitle = ""
                notifyMessage = ""
            }.onFailure { handleError(it) }
        }
    }

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    fun avatarUrl(): String = api.avatarUrl(userName, userUuid)

    private fun applySession(me: MeResponse) {
        isLoggedIn = true
        isAdmin = me.admin
        userName = me.user?.name.orEmpty()
        userUuid = me.user?.uuid
        meData = me
        statusDraft = me.cabinet?.player?.profileStatus ?: me.cabinet?.profileStatus.orEmpty()
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
                val v = async(Dispatchers.IO) { runCatching { api.serverVersion() }.getOrDefault(config.defaultMcVersion) }
                val s = async(Dispatchers.IO) { api.fetchServerStatus() }
                val p = async(Dispatchers.IO) {
                    runCatching { api.fetchOnlinePlayers().online.map { it.name } }.getOrDefault(emptyList())
                }
                serverVersion = v.await()
                serverStatus = s.await()
                onlinePlayers = p.await()
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
        errorMessage = when (error) {
            is StarlitApiException -> {
                if (error.cabinetBlocked) buildString {
                    append(error.message)
                    error.applicationStatus?.let { append(" ($it)") }
                } else error.message
            }
            else -> error.message ?: "Ошибка"
        }
    }

    fun dispose() {
        statusJob?.cancel()
        api.close()
        mc.close()
        updateChecker.close()
    }
}
