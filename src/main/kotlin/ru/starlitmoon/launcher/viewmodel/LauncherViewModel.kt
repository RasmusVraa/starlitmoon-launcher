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
import ru.starlitmoon.launcher.api.AdminBadgeDto
import ru.starlitmoon.launcher.api.AdminBankCardDto
import ru.starlitmoon.launcher.api.AdminClanDto
import ru.starlitmoon.launcher.api.AdminMeResponse
import ru.starlitmoon.launcher.api.AdminOrderDto
import ru.starlitmoon.launcher.api.AdminPlayerDto
import ru.starlitmoon.launcher.api.AdminProductDto
import ru.starlitmoon.launcher.api.AdminStatsResponse
import ru.starlitmoon.launcher.api.AdminTreasuryResponse
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
    private val initialConfig: LauncherConfig = LauncherConfig.load(),
    private val updateChecker: UpdateChecker = UpdateChecker(initialConfig),
) {
    var configState by mutableStateOf(initialConfig)
    private var mc: MinecraftLauncher = MinecraftLauncher(configState)
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
    var serverStatus by mutableStateOf(ServerStatus.offline(initialConfig.serverHost))
    var serverVersion by mutableStateOf(initialConfig.defaultMcVersion)
    var meData by mutableStateOf<MeResponse?>(null)
    var adminMe by mutableStateOf<AdminMeResponse?>(null)
    var adminStats by mutableStateOf<AdminStatsResponse?>(null)
    var adminPlayers by mutableStateOf<List<AdminPlayerDto>>(emptyList())
    var adminApps by mutableStateOf<List<AdminApplicationDto>>(emptyList())
    var adminAccounts by mutableStateOf<List<AdminAccountDto>>(emptyList())
    var adminBank by mutableStateOf<List<AdminBankCardDto>>(emptyList())
    var adminClans by mutableStateOf<List<AdminClanDto>>(emptyList())
    var adminTreasury by mutableStateOf<AdminTreasuryResponse?>(null)
    var adminBadges by mutableStateOf<List<AdminBadgeDto>>(emptyList())
    var adminProducts by mutableStateOf<List<AdminProductDto>>(emptyList())
    var adminOrders by mutableStateOf<List<AdminOrderDto>>(emptyList())
    var adminConsoleOutput by mutableStateOf("")
    var adminConsoleError by mutableStateOf<String?>(null)
    var adminRconResponse by mutableStateOf("")
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var launchProgress by mutableStateOf<String?>(null)
    var launchProgressFraction by mutableStateOf<Float?>(null)
    var requestExit by mutableStateOf(false)
    var onlinePlayers by mutableStateOf<List<String>>(emptyList())
    var adminSearch by mutableStateOf("")
    var accountSearch by mutableStateOf("")
    var adminConsoleServerId by mutableStateOf("")
    var treasuryPayoutCode by mutableStateOf("")
    var treasuryPayoutAmount by mutableStateOf("")
    var treasuryPayoutReason by mutableStateOf("bonus")
    var treasuryPayoutNote by mutableStateOf("")
    var rconCommand by mutableStateOf("")
    var lastResetPassword by mutableStateOf<String?>(null)
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
            if (configState.checkUpdatesOnStart) launch { checkForUpdates(silent = true) }
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
            adminTreasury = null
            adminBadges = emptyList()
            adminProducts = emptyList()
            adminOrders = emptyList()
            adminConsoleOutput = ""
            adminConsoleError = null
            adminRconResponse = ""
            lastResetPassword = null
            notifications = emptyList()
            currentTab = LauncherTab.Play
            isLoading = false
        }
    }

    fun saveSettings(newConfig: LauncherConfig) {
        LauncherConfig.save(newConfig)
        configState = newConfig
        mc.close()
        mc = MinecraftLauncher(configState)
        infoMessage = "Настройки сохранены"
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
            launchProgressFraction = 0f
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                mc.launch(userName, configState.minecraftVersionId) { msg, frac ->
                    scope.launch {
                        launchProgress = msg
                        launchProgressFraction = frac
                    }
                }
            }
            if (result.success) {
                infoMessage = "Игра запущена. На сервере: /login"
                if (!configState.keepLauncherOpen) {
                    requestExit = true
                }
                result.process?.let { process ->
                    scope.launch(Dispatchers.IO) {
                        delay(5000)
                        if (!process.isAlive) {
                            val log = runCatching {
                                configState.dataDir.resolve("last-launch.log").toFile().readText().takeLast(800)
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
            launchProgressFraction = null
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
                    when (adminSubTab) {
                        0 -> adminStats = runCatching { api.adminStats() }.getOrNull()
                        1 -> adminPlayers = runCatching { api.adminPlayers(adminSearch).players }.getOrDefault(emptyList())
                        2 -> adminApps = runCatching { api.adminApplications("pending").applications }.getOrDefault(emptyList())
                        3 -> adminClans = runCatching { api.adminClans("pending").clans }.getOrDefault(emptyList())
                        4 -> adminBank = runCatching { api.adminBank().cards }.getOrDefault(emptyList())
                        6 -> adminAccounts = runCatching { api.adminAccounts(accountSearch).accounts }.getOrDefault(emptyList())
                        7 -> loadConsoleOutputInternal()
                        8 -> adminTreasury = runCatching { api.treasury() }.getOrNull()
                        9 -> adminBadges = runCatching { api.adminBadges().badges }.getOrDefault(emptyList())
                        10 -> {
                            adminProducts = runCatching { api.adminProducts().products }.getOrDefault(emptyList())
                            adminOrders = runCatching { api.adminOrders().orders }.getOrDefault(emptyList())
                        }
                    }
                }
            }.onFailure { handleError(it) }
            isLoading = false
        }
    }

    private suspend fun loadConsoleOutputInternal() {
        val sid = adminConsoleServerId.trim().ifBlank { null }
        runCatching { api.consoleOutput(sid) }
            .onSuccess {
                adminConsoleOutput = it.text.orEmpty()
                adminConsoleError = it.error
            }
            .onFailure {
                adminConsoleOutput = ""
                adminConsoleError = (it as? StarlitApiException)?.message ?: it.message
            }
    }

    fun searchAdminAccounts(q: String) {
        accountSearch = q
        scope.launch {
            adminAccounts = withContext(Dispatchers.IO) {
                runCatching { api.adminAccounts(q).accounts }.getOrDefault(emptyList())
            }
        }
    }

    fun resetAccountPassword(nick: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.resetAccountPassword(nick) } }
                .onSuccess {
                    lastResetPassword = it.password
                    infoMessage = "Пароль для $nick: ${it.password}"
                }
                .onFailure { handleError(it) }
        }
    }

    fun deleteAccount(nick: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteAccount(nick) } }
                .onSuccess {
                    infoMessage = "Аккаунт удалён: $nick"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun execRcon() {
        val cmd = rconCommand.trim()
        if (cmd.isBlank()) {
            errorMessage = "Введите команду"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.rconExec(cmd, adminConsoleServerId.trim().ifBlank { null })
                }
            }.onSuccess {
                adminRconResponse = it.response.orEmpty()
                infoMessage = "RCON выполнен"
                rconCommand = ""
            }.onFailure { handleError(it) }
        }
    }

    fun loadConsoleOutput() {
        scope.launch {
            isLoading = true
            runCatching { withContext(Dispatchers.IO) { loadConsoleOutputInternal() } }
                .onFailure { handleError(it) }
            isLoading = false
        }
    }

    fun treasuryPayout() {
        val code = treasuryPayoutCode.trim()
        val amount = treasuryPayoutAmount.toLongOrNull()
        if (code.isBlank() || amount == null || amount <= 0) {
            errorMessage = "Укажи код карты и сумму"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.treasuryPayout(code, amount, treasuryPayoutReason, treasuryPayoutNote.trim().ifBlank { null })
                }
            }.onSuccess {
                adminTreasury = it
                infoMessage = "Выплата $amount ◆ на $code"
                treasuryPayoutCode = ""
                treasuryPayoutAmount = ""
                treasuryPayoutNote = ""
            }.onFailure { handleError(it) }
        }
    }

    fun deleteApp(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteApplication(id) } }
                .onSuccess {
                    infoMessage = "Заявка удалена"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun deleteClan(id: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteClan(id) } }
                .onSuccess {
                    infoMessage = "Клан удалён"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun deleteBankCard(nick: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.deleteBankCard(nick) } }
                .onSuccess {
                    infoMessage = "Карта удалена: $nick"
                    refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    fun openAdminWebsite() {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://starlit-moon.ru/admin")) }
            .onFailure { errorMessage = "Не удалось открыть браузер" }
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
                val v = async(Dispatchers.IO) { runCatching { api.serverVersion() }.getOrDefault(configState.defaultMcVersion) }
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
