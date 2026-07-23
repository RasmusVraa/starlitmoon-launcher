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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import javax.swing.SwingUtilities
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.discord.DiscordPresence
import ru.starlitmoon.launcher.api.AdminAccountDto
import ru.starlitmoon.launcher.api.AdminAdminDto
import ru.starlitmoon.launcher.api.AdminApplicationDto
import ru.starlitmoon.launcher.api.AdminBadgeDto
import ru.starlitmoon.launcher.api.AdminBankCardDto
import ru.starlitmoon.launcher.api.AdminClanDto
import ru.starlitmoon.launcher.api.AdminContestEntryDto
import ru.starlitmoon.launcher.api.AdminContestSettingsDto
import ru.starlitmoon.launcher.api.AdminMapIconDto
import ru.starlitmoon.launcher.api.AdminMapMarkerDto
import ru.starlitmoon.launcher.api.AdminMapWorldDto
import ru.starlitmoon.launcher.api.AdminMeResponse
import ru.starlitmoon.launcher.api.AdminOrderDto
import ru.starlitmoon.launcher.api.AdminPlayerDto
import ru.starlitmoon.launcher.api.AdminProductDto
import ru.starlitmoon.launcher.api.AdminStatsResponse
import ru.starlitmoon.launcher.api.AdminTreasuryResponse
import ru.starlitmoon.launcher.api.AdminWikiPageDto
import ru.starlitmoon.launcher.api.McServerDto
import ru.starlitmoon.launcher.api.PermissionDefDto
import ru.starlitmoon.launcher.api.MeResponse
import ru.starlitmoon.launcher.api.ModpackDto
import ru.starlitmoon.launcher.api.NotificationDto
import ru.starlitmoon.launcher.api.ServerStatus
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.api.StarlitApiException
import ru.starlitmoon.launcher.minecraft.MinecraftLauncher
import ru.starlitmoon.launcher.minecraft.ModpackSync
import ru.starlitmoon.launcher.minecraft.OfflineSkinBridge
import ru.starlitmoon.launcher.minecraft.SkinLibrary
import ru.starlitmoon.launcher.minecraft.SkinManager
import ru.starlitmoon.launcher.update.LauncherSelfUpdater
import ru.starlitmoon.launcher.update.UpdateChecker
import ru.starlitmoon.launcher.update.UpdateInfo
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

enum class LauncherTab { Home, Builds, Cabinet, Skins, Settings, Admin }

class LauncherViewModel(
    private val scope: CoroutineScope,
    private val api: StarlitApiClient = StarlitApiClient(),
    private val initialConfig: LauncherConfig = LauncherConfig.load(),
) {
    var configState by mutableStateOf(initialConfig)
    private val updateChecker = UpdateChecker(configProvider = { configState })
    private val discordPresence = DiscordPresence(scope)

    private var mc: MinecraftLauncher = MinecraftLauncher(configState)
    var isRefreshingStatus by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isCheckingUpdates by mutableStateOf(false)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
    var updateDismissed by mutableStateOf(false)
    var isApplyingUpdate by mutableStateOf(false)
    var updateProgress by mutableStateOf<String?>(null)
    var updateProgressFraction by mutableStateOf<Float?>(null)
    // Restore session from disk before first frame — avoids login flash.
    private val cachedBootSession = runCatching { api.cachedSession() }.getOrNull()
    var isLoggedIn by mutableStateOf(
        cachedBootSession != null && !cachedBootSession.userName.isNullOrBlank(),
    )
    var isAdmin by mutableStateOf(cachedBootSession?.isAdmin == true)
    var userName by mutableStateOf(cachedBootSession?.userName.orEmpty())
    var userUuid by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var infoMessage by mutableStateOf<String?>(null)
    var currentTab by mutableStateOf(LauncherTab.Home)
    var adminSubTab by mutableStateOf(0)
    var modpacks by mutableStateOf<List<ModpackDto>>(emptyList())
    var isLoadingModpacks by mutableStateOf(false)
    var selectedModpack by mutableStateOf<ModpackDto?>(null)
    var packUiRevision by mutableStateOf(0)
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
    var isGameRunning by mutableStateOf(false)
    private var gameProcess: Process? = null
    private var gameWatchJob: Job? = null
    private var gameStopRequested: Boolean = false
    private var activeSkinBridge: OfflineSkinBridge? = null
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
    var notifyHref by mutableStateOf("")
    var notifyTargets by mutableStateOf("")

    // Admin: extended state
    var adminPermissionDefs by mutableStateOf<List<PermissionDefDto>>(emptyList())
    var adminClansFilter by mutableStateOf("pending")
    var adminAppsFilter by mutableStateOf("pending")
    var adminContest by mutableStateOf<List<AdminContestEntryDto>>(emptyList())
    var adminContestSettings by mutableStateOf<AdminContestSettingsDto?>(null)
    var adminContestFilter by mutableStateOf("all")
    var adminWikiPages by mutableStateOf<List<AdminWikiPageDto>>(emptyList())
    var adminMapVisibility by mutableStateOf("public")
    var adminMarkers by mutableStateOf<List<AdminMapMarkerDto>>(emptyList())
    var adminMapIcons by mutableStateOf<List<AdminMapIconDto>>(emptyList())
    var adminMapWorlds by mutableStateOf<List<AdminMapWorldDto>>(emptyList())
    var adminAdmins by mutableStateOf<List<AdminAdminDto>>(emptyList())
    var adminModpacks by mutableStateOf<List<ModpackDto>>(emptyList())
    var adminMcServers by mutableStateOf<List<McServerDto>>(emptyList())
    var serverVersionDraft by mutableStateOf("")

    var skinBusy by mutableStateOf(false)
    var skinCommand by mutableStateOf(
        configState.skinTextureUrl.let { if (it.isNotBlank()) "/skin set $it" else "" },
    )
    var skinLocalPath by mutableStateOf(configState.skinPath)
    var avatarRevision by mutableStateOf(0)
    var skinTextureHash by mutableStateOf<String?>(null)
    var skinLibraryEntries by mutableStateOf<List<ru.starlitmoon.launcher.minecraft.SkinLibraryEntry>>(emptyList())
    var activeSkinId by mutableStateOf<String?>(null)
    var activeSkinPath by mutableStateOf<Path?>(null)
    var activeCapePath by mutableStateOf<Path?>(null)
    var activeSkinSlim by mutableStateOf(false)

    private var skins = SkinManager(configState.skinsDir)
    private var skinLibrary = ru.starlitmoon.launcher.minecraft.SkinLibrary(configState)

    private var statusJob: Job? = null
    private var booted = false

    private fun refreshSkinLibraryState() {
        skinLibraryEntries = skinLibrary.list()
        activeSkinId = skinLibrary.activeId()
        val entry = skinLibrary.activeEntry()
        activeSkinPath = entry?.let { skinLibrary.skinPath(it) }?.takeIf { it.exists() }
            ?: configState.skinPath.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }?.takeIf { it.exists() }
        activeCapePath = entry?.let { skinLibrary.capePath(it) }
        activeSkinSlim = entry?.slim == true
        skinLocalPath = activeSkinPath?.toString().orEmpty()
        avatarRevision++
    }

    fun boot() {
        if (booted) return
        booted = true
        scope.launch {
            val sessionJob = async(Dispatchers.IO) { runCatching { api.restoreSession() }.getOrNull() }
            val publicJob = async(Dispatchers.IO) { refreshPublicData() }
            if (configState.checkUpdatesOnStart) launch { checkForUpdates(silent = true) }
            val restored = sessionJob.await()
            if (restored?.user != null) {
                applySession(restored)
            } else {
                // Мягкое восстановление: cookie ещё есть, но /me временно недоступен.
                val cached = withContext(Dispatchers.IO) { api.cachedSession() }
                if (cached != null && !cached.userName.isNullOrBlank()) {
                    isLoggedIn = true
                    userName = cached.userName.orEmpty()
                    isAdmin = cached.isAdmin
                    launch {
                        delay(2500)
                        runCatching { withContext(Dispatchers.IO) { api.restoreSession() } }
                            .onSuccess { me -> if (me?.user != null) applySession(me) }
                    }
                }
            }
            publicJob.await()
            fetchModpacks()
            startStatusPolling()
            discordPresence.start(configState.discordRpcEnabled)
            refreshDiscordPresence()
        }
    }

    fun fetchModpacks(force: Boolean = false) {
        scope.launch {
            if (!force && modpacks.isNotEmpty()) {
                // Already cached — refresh quietly in background without clearing UI.
                runCatching { withContext(Dispatchers.IO) { api.listModpacks() } }
                    .onSuccess { packs ->
                        if (packs.isNotEmpty()) {
                            modpacks = packs
                            val selectedId = configState.selectedModpackId
                            selectedModpack = packs.firstOrNull { it.id == selectedId || it.slug == selectedId }
                                ?: selectedModpack
                                ?: packs.firstOrNull()
                        }
                    }
                return@launch
            }
            isLoadingModpacks = true
            runCatching { withContext(Dispatchers.IO) { api.listModpacks() } }
                .onSuccess { packs ->
                    modpacks = packs
                    val selectedId = configState.selectedModpackId
                    selectedModpack = packs.firstOrNull { it.id == selectedId || it.slug == selectedId }
                        ?: packs.firstOrNull()
                    selectedModpack?.let { pack ->
                        if (configState.selectedModpackId.isBlank() ||
                            packs.none { it.id == selectedId || it.slug == selectedId }
                        ) {
                            selectModpack(pack, persistOnly = true)
                        }
                    }
                }
                .onFailure { /* keep previous cache */ }
            isLoadingModpacks = false
        }
    }

    fun selectModpack(pack: ModpackDto, persistOnly: Boolean = false) {
        selectedModpack = pack
        val mcVer = pack.mcVersion?.trim().orEmpty()
        val next = configState.copy(
            selectedModpackId = pack.id.orEmpty().ifBlank { pack.slug.orEmpty() },
            minecraftVersionId = mcVer.ifBlank { configState.minecraftVersionId },
        )
        runCatching { LauncherConfig.save(next) }
            .onFailure {
                errorMessage = "Не удалось сохранить выбор сборки"
                return
            }
        configState = next
        if (!persistOnly) {
            infoMessage = "Выбрана сборка: ${pack.name ?: pack.slug ?: "—"}"
        }
        refreshDiscordPresence()
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
                        infoMessage = "Установлена актуальная версия (${LauncherVersion.CURRENT})"
                    }
                }
                .onFailure { err ->
                    if (!silent) {
                        errorMessage = err.message?.takeIf { it.isNotBlank() }
                            ?: "Не удалось проверить обновления"
                    }
                }
            isCheckingUpdates = false
        }
    }

    fun downloadUpdate() {
        val update = updateInfo ?: return
        val url = update.installerUrl
        if (url.isNullOrBlank()) {
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(update.releasePageUrl)) }
                .onFailure { errorMessage = "Не удалось открыть страницу релиза" }
            return
        }
        if (isApplyingUpdate) return
        scope.launch {
            isApplyingUpdate = true
            updateDismissed = false
            updateProgress = "Подготовка обновления…"
            updateProgressFraction = 0f
            errorMessage = null
            runCatching {
                val paths = withContext(Dispatchers.IO) { LauncherSelfUpdater.resolveInstallPaths() }
                val name = update.installerName?.takeIf { it.endsWith(".exe", true) }
                    ?: "StarlitMoonLauncher-Setup-${update.latestVersion}.exe"
                val target = withContext(Dispatchers.IO) {
                    val dir = configState.dataDir.resolve("updates").also { it.createDirectories() }
                    dir.resolve(name)
                }
                withContext(Dispatchers.IO) {
                    LauncherSelfUpdater.downloadInstaller(url, target) { frac, label ->
                        SwingUtilities.invokeLater {
                            updateProgressFraction = frac
                            updateProgress = label
                        }
                    }
                }
                updateProgress = "Установка и перезапуск…"
                updateProgressFraction = 1f
                withContext(Dispatchers.IO) {
                    LauncherSelfUpdater.scheduleInstallAndRestart(
                        installer = target,
                        installDir = paths.installDir,
                        relaunchExe = paths.relaunchExe,
                        launcherPid = ProcessHandle.current().pid(),
                    )
                }
                infoMessage = "Обновление скачано — перезапуск…"
                delay(400)
                requestExit = true
            }.onFailure { err ->
                isApplyingUpdate = false
                updateProgress = null
                updateProgressFraction = null
                errorMessage = err.message?.takeIf { it.isNotBlank() }
                    ?: "Не удалось обновить лаунчер"
            }
        }
    }

    fun dismissUpdate() {
        if (isApplyingUpdate) return
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
            runCatching { withContext(Dispatchers.IO) { api.login(nickname, password) } }
                .onSuccess {
                    applySession(it)
                    infoMessage = "Вход выполнен"
                    currentTab = LauncherTab.Home
                    fetchModpacks()
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
            currentTab = LauncherTab.Home
            isLoading = false
            refreshDiscordPresence()
        }
    }

    fun saveSettings(newConfig: LauncherConfig, notify: Boolean = true) {
        if (newConfig == configState) return
        val rpcChanged = newConfig.discordRpcEnabled != configState.discordRpcEnabled
        LauncherConfig.save(newConfig)
        configState = newConfig
        mc.close()
        mc = MinecraftLauncher(configState)
        skins.close()
        skins = SkinManager(configState.skinsDir)
        if (rpcChanged) {
            discordPresence.setEnabled(newConfig.discordRpcEnabled)
            if (newConfig.discordRpcEnabled) refreshDiscordPresence()
        }
        if (notify) infoMessage = "Настройки сохранены"
    }

    fun installSkin(filePath: String) {
        addSkinToLibrary(filePath)
    }

    fun addSkinToLibrary(filePath: String, name: String? = null) {
        if (filePath.isBlank()) {
            errorMessage = "Выберите файл скина"
            return
        }
        if (userName.isBlank()) {
            errorMessage = "Сначала войдите"
            return
        }
        scope.launch {
            skinBusy = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val entry = skinLibrary.addFromFile(Path.of(filePath), name)
                    applyLibrarySkin(entry.id, upload = true)
                    entry
                }
            }.onSuccess {
                infoMessage = infoMessage ?: "Скин «${it.name}» добавлен и синхронизирован с сайтом"
            }.onFailure { handleError(it) }
            skinBusy = false
        }
    }

    fun selectLibrarySkin(id: String) {
        scope.launch {
            skinBusy = true
            runCatching {
                withContext(Dispatchers.IO) { applyLibrarySkin(id, upload = true) }
            }.onSuccess {
                infoMessage = infoMessage ?: "Скин выбран и синхронизирован с сайтом"
            }.onFailure { handleError(it) }
            skinBusy = false
        }
    }

    fun setLibraryCape(skinId: String, capePath: String?) {
        scope.launch {
            skinBusy = true
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    skinLibrary.setCape(skinId, capePath?.takeIf { it.isNotBlank() }?.let { Path.of(it) })
                    if (skinLibrary.activeId() == skinId) {
                        // Sync skin+cape to site for 3D in cabinet / public profile
                        applyLibrarySkin(skinId, upload = true)
                    } else {
                        refreshSkinLibraryState()
                    }
                }
            }.onSuccess {
                infoMessage = if (capePath.isNullOrBlank()) {
                    "Плащ снят и синхронизирован с сайтом"
                } else {
                    infoMessage ?: "Плащ установлен и синхронизирован с сайтом"
                }
            }.onFailure { handleError(it) }
            skinBusy = false
        }
    }

    fun removeLibrarySkin(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                skinLibrary.remove(id)
                skinLibrary.activeId()?.let { applyLibrarySkin(it, upload = false) }
                    ?: run { refreshSkinLibraryState() }
            }
            infoMessage = "Скин удалён"
        }
    }

    fun librarySkinPath(entry: ru.starlitmoon.launcher.minecraft.SkinLibraryEntry): Path =
        skinLibrary.skinPath(entry)

    fun libraryCapePath(entry: ru.starlitmoon.launcher.minecraft.SkinLibraryEntry): Path? =
        skinLibrary.capePath(entry)

    private suspend fun applyLibrarySkin(id: String, upload: Boolean) {
        val entry = skinLibrary.select(id)
        val path = skinLibrary.skinPath(entry)
        val prepared = skins.prepareFromFile(userName, path)
        var url = configState.skinTextureUrl
        var hash = skinTextureHash
        if (upload) {
            val uploaded = api.uploadSkin(prepared.dataUrl)
            url = uploaded.skinUrl.orEmpty().ifBlank { url }
            hash = uploaded.skinTextureHash ?: hash
            skinCommand = if (url.isNotBlank()) "/skin set $url" else ""
            if (uploaded.cabinet != null) {
                meData = meData?.copy(cabinet = uploaded.cabinet)
                skinTextureHash = uploaded.cabinet.player?.skinTextureHash
                    ?: uploaded.cabinet.skinTextureHash
                    ?: hash
                hash = skinTextureHash
            }
            // Sync cape (or clear) so site 3D cabinet/public profile match the launcher.
            val capeFile = skinLibrary.capePath(entry)
            runCatching {
                val capeResult = if (capeFile != null && capeFile.exists()) {
                    val capeBytes = Files.readAllBytes(capeFile)
                    SkinLibrary.validateCape(capeBytes)?.let { error(it) }
                    val b64 = java.util.Base64.getEncoder().encodeToString(capeBytes)
                    api.uploadCape("data:image/png;base64,$b64")
                } else {
                    api.clearCape()
                }
                if (capeResult.cabinet != null) {
                    meData = meData?.copy(cabinet = capeResult.cabinet)
                }
                if (!capeResult.message.isNullOrBlank()) {
                    infoMessage = capeResult.message
                }
            }.onFailure { err ->
                if (infoMessage.isNullOrBlank()) {
                    infoMessage = "Скин на сайте обновлён, плащ: ${err.message ?: "ошибка синхронизации"}"
                }
            }
            if (infoMessage.isNullOrBlank()) {
                if (!uploaded.warning.isNullOrBlank()) {
                    infoMessage = uploaded.warning
                } else if (!uploaded.message.isNullOrBlank()) {
                    infoMessage = uploaded.message
                }
            }
        }
        configState = configState.copy(
            skinPath = path.toString(),
            skinTextureUrl = url,
        )
        LauncherConfig.save(configState)
        skinTextureHash = hash
        refreshSkinLibraryState()
        avatarRevision++
        refreshDiscordPresence()
    }

    /** Push local active skin to the website if the site still has no custom skin (or hash differs). */
    private suspend fun syncActiveSkinToSite() {
        if (!isLoggedIn || userName.isBlank()) return
        val activeId = skinLibrary.activeId() ?: return
        val entry = skinLibrary.list().firstOrNull { it.id == activeId } ?: return
        val path = skinLibrary.skinPath(entry)
        if (!path.exists()) return
        val remoteUrl = meData?.cabinet?.player?.skinUrl.orEmpty()
            .ifBlank { meData?.cabinet?.skinUrl.orEmpty() }
            .ifBlank { configState.skinTextureUrl }
        val remoteHash = meData?.cabinet?.player?.skinTextureHash
            ?: meData?.cabinet?.skinTextureHash
            ?: skinTextureHash
        val prepared = skins.prepareFromFile(userName, path)
        val localHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(prepared.bytes)
            .joinToString("") { "%02x".format(it) }
        val siteHasCustom = remoteUrl.contains("/img/skins/custom/", ignoreCase = true)
        val remoteCape = meData?.cabinet?.player?.capeUrl.orEmpty()
        val localHasCape = skinLibrary.capePath(entry) != null
        val capeMismatch =
            localHasCape != remoteCape.contains("/img/capes/custom/", ignoreCase = true) ||
                (!localHasCape && remoteCape.isNotBlank())
        if (siteHasCustom && remoteHash != null && remoteHash.equals(localHash, ignoreCase = true) && !capeMismatch) {
            return
        }
        runCatching {
            applyLibrarySkin(activeId, upload = true)
        }.onFailure {
            // Don't block login on sync failure; user can re-select the skin.
        }
    }

    fun copySkinCommand() {
        val cmd = skinCommand
        if (cmd.isBlank()) return
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(cmd), null)
        }.onSuccess { infoMessage = "Команда скопирована" }
            .onFailure { errorMessage = "Не удалось скопировать команду" }
    }

    fun packFolder(pack: ModpackDto? = selectedModpack): Path {
        val p = pack ?: return configState.packsDir.also { it.createDirectories() }
        return ModpackSync.packDir(configState.dataDir, p).also { it.createDirectories() }
    }

    fun openPackFolder(pack: ModpackDto? = selectedModpack) {
        val target = packFolder(pack)
        runCatching {
            Desktop.getDesktop().open(target.toFile())
        }.onFailure {
            errorMessage = "Не удалось открыть папку: ${target.toAbsolutePath()}"
        }
    }

    fun packNeedsUpdate(pack: ModpackDto): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val tick = packUiRevision
        return ModpackSync.needsUpdate(configState.dataDir, pack)
    }

    fun reinstallModpack(pack: ModpackDto) {
        if (!pack.hasArchive || pack.archive?.url.isNullOrBlank()) {
            errorMessage = "У сборки нет ZIP-архива"
            return
        }
        scope.launch {
            isLoading = true
            launchProgress = "Переустановка сборки…"
            launchProgressFraction = 0f
            errorMessage = null
            val detail = withContext(Dispatchers.IO) {
                runCatching { api.getModpack(pack.id ?: pack.slug.orEmpty()) }.getOrNull()
            } ?: pack
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ModpackSync.syncArchive(configState.dataDir, detail, force = true) { msg, frac ->
                        reportLaunchProgress(msg, frac)
                    }
                }
            }
            if (result.isSuccess) {
                infoMessage = "Сборка «${detail.name ?: detail.slug}» переустановлена"
                packUiRevision++
                fetchModpacks(force = true)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Не удалось переустановить сборку"
            }
            launchProgress = null
            launchProgressFraction = null
            isLoading = false
        }
    }

    fun uploadModpackUpdate(pack: ModpackDto, zipPath: String) {
        if (!isAdmin) {
            errorMessage = "Нужны права администратора"
            return
        }
        val id = pack.id?.trim().orEmpty()
        if (id.isEmpty()) {
            errorMessage = "У сборки нет id"
            return
        }
        val path = Path.of(zipPath)
        scope.launch {
            isLoading = true
            launchProgress = "Публикация обновления…"
            launchProgressFraction = 0f
            errorMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    api.uploadModpackArchive(id, path) { frac, msg ->
                        reportLaunchProgress(msg, frac)
                    }
                }
            }.onSuccess { updated ->
                modpacks = modpacks.map { if (it.id == updated.id) updated else it }
                if (selectedModpack?.id == updated.id) selectedModpack = updated
                infoMessage = "Обновление «${updated.name ?: updated.slug}» опубликовано"
                packUiRevision++
                fetchModpacks(force = true)
            }.onFailure { handleError(it) }
            launchProgress = null
            launchProgressFraction = null
            isLoading = false
        }
    }

    private fun reportLaunchProgress(msg: String, frac: Float? = null) {
        val apply = {
            launchProgress = msg
            if (frac != null) launchProgressFraction = frac
        }
        if (SwingUtilities.isEventDispatchThread()) apply()
        else SwingUtilities.invokeLater(apply)
    }

    fun play() {
        if (!isLoggedIn) {
            errorMessage = "Сначала войдите"
            return
        }
        scope.launch {
            isLoading = true
            launchProgress = "Подготовка…"
            launchProgressFraction = 0f
            errorMessage = null
            yield()
            val pack = selectedModpack
                ?: modpacks.firstOrNull { it.id == configState.selectedModpackId || it.slug == configState.selectedModpackId }
            launchProgress = "Получение данных сборки…"
            yield()
            val detail = if (pack != null) {
                withTimeoutOrNull(20_000) {
                    withContext(Dispatchers.IO) {
                        runCatching { api.getModpack(pack.id ?: pack.slug.orEmpty()) }.getOrNull()
                    }
                } ?: pack
            } else {
                null
            }
            val versionId = detail?.mcVersion?.trim()?.ifBlank { null }
                ?: configState.minecraftVersionId
            val loader = detail?.loader?.lowercase()?.ifBlank { null } ?: "vanilla"
            val loaderVersion = detail?.loaderVersion?.trim()?.ifBlank { null }
            var instanceDir: Path? = null
            if (detail != null) {
                instanceDir = ModpackSync.packDir(configState.dataDir, detail).also { it.createDirectories() }
                if (detail.hasArchive && !detail.archive?.url.isNullOrBlank()) {
                    launchProgress = "Загрузка архива сборки…"
                    launchProgressFraction = 0.01f
                    yield()
                    val synced = runCatching {
                        withContext(Dispatchers.IO) {
                            ModpackSync.syncArchive(configState.dataDir, detail) { msg, frac ->
                                reportLaunchProgress(msg, frac)
                            }
                        }
                    }
                    if (synced.isFailure) {
                        errorMessage = synced.exceptionOrNull()?.message ?: "Не удалось скачать сборку"
                        launchProgress = null
                        launchProgressFraction = null
                        isLoading = false
                        return@launch
                    }
                } else if (loader != "vanilla") {
                    launchProgress = "Загрузка модов сборки…"
                    yield()
                    val synced = withContext(Dispatchers.IO) { syncLegacyModJars(detail, instanceDir!!) }
                    if (!synced) {
                        infoMessage = "Для «${detail.name}» ещё нет ZIP-архива. Запуск через $loader."
                    }
                }
                // Pack ZIPs often ship versions/<mc>/<mc>.jar; with NeoForge that jar becomes
                // automatic module `_1._21._1` and fights module `minecraft`.
                if (loader == "neoforge" || loader == "forge") {
                    withContext(Dispatchers.IO) {
                        scrubPackVanillaClientJar(instanceDir!!, versionId)
                    }
                }
            }
            launchProgress = "Подготовка клиента…"
            launchProgressFraction = launchProgressFraction ?: 0.05f
            yield()
            val result = withContext(Dispatchers.IO) {
                mc = MinecraftLauncher(configState)
                val skinPath = activeSkinPath
                    ?: configState.skinsDir.resolve("active.png").takeIf { it.exists() }
                    ?: configState.skinPath.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }
                    ?: configState.skinsDir.resolve("${userName.trim().lowercase()}.png")
                mc.launch(
                    username = userName,
                    preferredVersion = versionId,
                    instanceDir = instanceDir,
                    loader = loader,
                    loaderVersion = loaderVersion,
                    skinFile = skinPath,
                ) { msg, frac ->
                    reportLaunchProgress(msg, frac)
                }
            }
            if (result.success) {
                infoMessage = "Игра запущена"
                result.process?.let { attachGameProcess(it, result.skinBridge) }
                    ?: run {
                        result.skinBridge?.close()
                        if (!configState.keepLauncherOpen) requestExit = true
                    }
            } else {
                result.skinBridge?.close()
                errorMessage = result.message
            }
            launchProgress = null
            launchProgressFraction = null
            isLoading = false
        }
    }

    fun stopGame() {
        val process = gameProcess ?: run {
            isGameRunning = false
            return
        }
        gameStopRequested = true
        infoMessage = "Игра остановлена"
        scope.launch(Dispatchers.IO) {
            runCatching {
                process.destroy()
                if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            SwingUtilities.invokeLater {
                clearGameProcess()
            }
        }
    }

    private fun attachGameProcess(process: Process, skinBridge: OfflineSkinBridge?) {
        gameWatchJob?.cancel()
        activeSkinBridge?.close()
        activeSkinBridge = skinBridge
        gameProcess = process
        gameStopRequested = false
        isGameRunning = true
        refreshDiscordPresence()
        gameWatchJob = scope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            // Poll isAlive — waitFor alone can miss exits on some Windows/javaw setups.
            while (isActive && process.isAlive) {
                delay(800)
                // If the JVM process exited but handle is stale, also probe destroy signal.
                runCatching {
                    val handle = process.toHandle()
                    if (!handle.isAlive) return@runCatching
                }
            }
            val livedMs = System.currentTimeMillis() - startedAt
            val code = runCatching { process.exitValue() }.getOrDefault(0)
            val stoppedByUser = gameStopRequested
            val logTail = runCatching {
                configState.dataDir.resolve("last-launch.log").toFile().readText().takeLast(900)
            }.getOrNull()
            val looksLikeCrash = !stoppedByUser && (code != 0 || livedMs < 8_000)
            SwingUtilities.invokeLater {
                clearGameProcess()
                when {
                    stoppedByUser -> {
                        if (infoMessage.isNullOrBlank()) infoMessage = "Игра остановлена"
                    }
                    looksLikeCrash && !logTail.isNullOrBlank() -> {
                        errorMessage = "Игра закрылась. ${extractLaunchFailureHint(logTail)}"
                    }
                    !stoppedByUser -> {
                        infoMessage = "Игра закрыта"
                        if (!configState.keepLauncherOpen) requestExit = true
                    }
                }
            }
        }
    }

    private fun clearGameProcess() {
        gameWatchJob?.cancel()
        gameWatchJob = null
        gameProcess = null
        isGameRunning = false
        gameStopRequested = false
        activeSkinBridge?.close()
        activeSkinBridge = null
        refreshDiscordPresence()
    }

    private fun extractLaunchFailureHint(logTail: String): String {
        val lines = logTail.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val exception = lines.lastOrNull { line ->
            line.startsWith("Exception ") ||
                line.startsWith("Error ") ||
                line.contains("Exception:") ||
                line.contains("Error:") ||
                (line.contains("Missing required option") && !line.startsWith("at "))
        }
        if (exception != null) {
            return exception
                .substringAfter("Exception in thread \"main\" ")
                .substringAfter("Exception: ")
                .take(220)
        }
        return lines.lastOrNull { !it.startsWith("at ") }
            ?: lines.lastOrNull()
            ?: "См. лог запуска"
    }

    /** Legacy fallback: per-jar mods into the pack instance mods/ folder. */
    private fun scrubPackVanillaClientJar(instanceDir: Path, mcVersion: String) {
        val id = mcVersion.trim().ifBlank { return }
        val jar = instanceDir.resolve("versions").resolve(id).resolve("$id.jar")
        runCatching { Files.deleteIfExists(jar) }
    }

    private suspend fun syncLegacyModJars(pack: ModpackDto, instanceDir: Path): Boolean {
        val mods = pack.mods.filter { !it.url.isNullOrBlank() && !it.fileName.isNullOrBlank() }
        if (mods.isEmpty()) return false
        val modsDir = instanceDir.resolve("mods")
        modsDir.createDirectories()
        var ok = 0
        for (mod in mods) {
            val url = mod.url ?: continue
            val name = mod.fileName ?: continue
            runCatching {
                val target = modsDir.resolve(name)
                if (target.exists() && mod.sha256.isNullOrBlank()) {
                    ok++
                    return@runCatching
                }
                val bytes = java.net.URI(url).toURL().openStream().use { it.readBytes() }
                val tmp = modsDir.resolve("$name.part")
                tmp.writeBytes(bytes)
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                ok++
            }
        }
        return ok > 0
    }

    fun refreshCabinet() {
        if (!isLoggedIn) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val me = api.me()
                    meData = me
                    skinTextureHash = me.cabinet?.player?.skinTextureHash ?: skinTextureHash
                    avatarRevision++
                    notifications = api.notifications()
                    statusDraft = me.cabinet?.player?.profileStatus
                        ?: me.cabinet?.profileStatus.orEmpty()
                }
                refreshDiscordPresence()
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
            val id = meData?.cabinet?.badges?.activeBadgeId
                ?: meData?.cabinet?.player?.activeBadgeId
            runCatching { withContext(Dispatchers.IO) { api.setBadge(id, visible) } }
                .onSuccess { meData = it }
                .onFailure { handleError(it) }
        }
    }

    fun setActiveBadge(badgeId: String?) {
        scope.launch {
            val id = badgeId?.takeIf { it.isNotBlank() }
            runCatching {
                withContext(Dispatchers.IO) { api.setBadge(id, visible = id != null) }
            }.onSuccess {
                meData = it
                infoMessage = if (id == null) "Значок снят" else "Значок сохранён"
            }.onFailure { handleError(it) }
        }
    }

    fun setCommentsEnabled(enabled: Boolean) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.setCommentsEnabled(enabled) } }
                .onSuccess { meData = it }
                .onFailure { handleError(it) }
        }
    }

    fun openPublicProfile() {
        val name = userName.trim()
        if (name.isBlank()) return
        val url = siteUrl("/player?player=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}")
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }

    fun openSitePath(path: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(siteUrl(path))) }
    }

    fun refreshAdmin() {
        if (!isAdmin) return
        scope.launch {
            isLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val me = api.adminMe()
                    adminMe = me
                    if (me.permissionDefs.isNotEmpty()) adminPermissionDefs = me.permissionDefs
                    adminStats = runCatching { api.adminStats() }.getOrNull()
                    when (adminSubTab) {
                        0 -> { // Игроки: профили / аккаунты / уведомления
                            adminPlayers = runCatching { api.adminPlayers(adminSearch).players }.getOrDefault(emptyList())
                            adminAccounts = runCatching { api.adminAccounts(accountSearch).accounts }.getOrDefault(emptyList())
                            adminBadges = runCatching { api.adminBadges().badges }.getOrDefault(emptyList())
                        }
                        1 -> { // Банк: карты / казна / товары
                            adminBank = runCatching { api.adminBank().cards }.getOrDefault(emptyList())
                            adminTreasury = runCatching { api.treasury() }.getOrNull()
                            adminProducts = runCatching { api.adminProducts().products }.getOrDefault(emptyList())
                            adminOrders = runCatching { api.adminOrders().orders }.getOrDefault(emptyList())
                        }
                        2 -> adminBadges = runCatching { api.adminBadges().badges }.getOrDefault(emptyList())
                        3 -> { // Карта
                            runCatching { api.mapSettings() }.getOrNull()?.let { adminMapVisibility = it.visibility ?: "public" }
                            runCatching { api.mapMarkers() }.getOrNull()?.let {
                                adminMarkers = it.markers
                                adminMapIcons = it.icons
                                adminMapWorlds = it.worlds
                            }
                        }
                        4 -> adminClans = runCatching { api.adminClans(adminClansFilter).clans }.getOrDefault(emptyList())
                        5 -> adminApps = runCatching { api.adminApplications(adminAppsFilter).applications }.getOrDefault(emptyList())
                        6 -> { // Конкурс
                            runCatching { api.contest(adminContestFilter) }.getOrNull()?.let {
                                adminContest = it.entries
                                if (it.settings != null) adminContestSettings = it.settings
                            }
                            if (adminContestSettings == null) {
                                adminContestSettings = runCatching { api.contestSettings() }.getOrNull()
                            }
                        }
                        7 -> adminWikiPages = runCatching { api.wiki().pages }.getOrDefault(emptyList())
                        8 -> { // Сборки
                            fetchModpacks(force = false)
                            adminModpacks = runCatching { api.adminModpacks().packs }.getOrDefault(emptyList())
                        }
                        9 -> runCatching { api.siteSettings() }.getOrNull()?.let { serverVersionDraft = it.serverVersion ?: serverVersion }
                        10 -> runCatching { api.listAdminsData() }.getOrNull()?.let {
                            adminAdmins = it.admins
                            if (it.permissionDefs.isNotEmpty()) adminPermissionDefs = it.permissionDefs
                        }
                        11 -> {
                            adminMcServers = runCatching { api.mcServers().servers }.getOrDefault(emptyList())
                            loadConsoleOutputInternal()
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

    fun sendBroadcast(toAll: Boolean) {
        if (notifyTitle.isBlank() || notifyMessage.isBlank()) {
            errorMessage = "Заполните заголовок и текст"
            return
        }
        val targets = notifyTargets.split(',', ';', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (!toAll && targets.isEmpty()) {
            errorMessage = "Укажите ники получателей"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.sendNotification(
                        notifyTitle,
                        notifyMessage,
                        all = toAll,
                        players = if (toAll) emptyList() else targets,
                        href = notifyHref,
                    )
                }
            }.onSuccess {
                infoMessage = if (toAll) "Уведомление отправлено всем" else "Уведомление отправлено (${targets.size})"
                notifyTitle = ""
                notifyMessage = ""
                notifyHref = ""
                notifyTargets = ""
            }.onFailure { handleError(it) }
        }
    }

    /** Runs an admin mutation off the UI thread, then refreshes the active tab. */
    private fun adminAction(successMsg: String? = null, refresh: Boolean = true, block: suspend () -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess {
                    if (successMsg != null) infoMessage = successMsg
                    if (refresh) refreshAdmin()
                }
                .onFailure { handleError(it) }
        }
    }

    // --- Players ---
    fun savePlayer(
        id: String,
        banned: Boolean,
        banReason: String,
        warnCount: Int,
        profileStatus: String,
        ranks: List<String>,
    ) = adminAction("Профиль сохранён: $id") {
        api.patchAdminPlayer(
            id,
            banned = banned,
            banReason = banReason.trim().ifBlank { null },
            warnCount = warnCount,
            profileStatus = profileStatus.trim(),
            ranks = ranks,
        )
    }

    fun deletePlayer(id: String, purge: Boolean) =
        adminAction(if (purge) "Данные игрока удалены: $id" else "Профиль удалён: $id") {
            api.deleteAdminPlayer(id, purge)
        }

    fun grantPlayerBadge(playerId: String, badgeId: String) =
        adminAction("Значок выдан") { api.grantPlayerBadge(playerId, badgeId) }

    fun revokePlayerBadge(playerId: String, badgeId: String) =
        adminAction("Значок снят") { api.revokePlayerBadge(playerId, badgeId) }

    // --- Badges ---
    fun createBadge(emoji: String, name: String, description: String) =
        adminAction("Значок создан") { api.createBadge(emoji.trim(), name.trim(), description.trim()) }

    fun updateBadge(id: String, emoji: String, name: String, description: String) =
        adminAction("Значок обновлён") { api.updateBadge(id, emoji.trim(), name.trim(), description.trim()) }

    fun deleteBadge(id: String) = adminAction("Значок удалён") { api.deleteBadge(id) }

    // --- Products / orders ---
    fun createProduct(id: String, name: String, price: Int, description: String, commandsText: String) =
        adminAction("Товар создан") {
            api.createProduct(id.trim(), name.trim(), price, description.trim(), null, parseCommands(commandsText))
        }

    fun updateProduct(id: String, name: String, price: Int, description: String, commandsText: String) =
        adminAction("Товар обновлён") {
            api.updateProduct(id, name.trim(), price, description.trim(), null, parseCommands(commandsText))
        }

    fun deleteProduct(id: String) = adminAction("Товар удалён") { api.deleteProduct(id) }

    fun deleteOrder(id: String) = adminAction("Заказ удалён") { api.deleteOrder(id) }

    private fun parseCommands(text: String): List<String> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    // --- Map ---
    fun saveMapVisibility(visibility: String) = adminAction("Видимость карты: $visibility") {
        val res = api.setMapVisibility(visibility)
        adminMapVisibility = res.visibility ?: visibility
    }

    fun createMapMarker(ownerName: String, name: String, world: String, x: Double, y: Double, z: Double, iconId: String?) =
        adminAction("Метка создана") { api.createMapMarker(ownerName.trim(), name.trim(), world, x, y, z, iconId) }

    fun deleteMapMarker(id: String) = adminAction("Метка удалена") { api.deleteMapMarker(id) }

    // --- Clans ---
    fun setClansFilter(status: String) { adminClansFilter = status; refreshAdmin() }

    // --- Applications ---
    fun setAppsFilter(status: String) { adminAppsFilter = status; refreshAdmin() }

    // --- Contest ---
    fun setContestFilter(status: String) { adminContestFilter = status; refreshAdmin() }

    fun patchContestEntry(id: String, status: String) =
        adminAction("Статус: $status") { api.patchContestEntry(id, status) }

    fun deleteContestEntry(id: String) = adminAction("Работа удалена") { api.deleteContestEntry(id) }

    fun saveContestSettings(enabled: Boolean, title: String, seasonName: String) =
        adminAction("Настройки конкурса сохранены") { api.updateContestSettings(enabled, title.trim(), seasonName.trim()) }

    // --- Wiki ---
    fun createWikiPage(title: String, slug: String, published: Boolean) =
        adminAction("Страница создана") { api.createWikiPage(title.trim(), slug.trim(), published) }

    fun updateWikiPage(id: String, title: String, slug: String, published: Boolean, paragraph: String?) =
        adminAction("Страница сохранена") { api.updateWikiPage(id, title.trim(), slug.trim(), published, paragraph) }

    fun deleteWikiPage(id: String) = adminAction("Страница удалена") { api.deleteWikiPage(id) }

    // --- Modpacks (admin) ---
    fun createModpack(name: String, slug: String, loader: String, mcVersion: String, description: String) =
        adminAction("Сборка создана") { api.createModpack(name.trim(), slug.trim(), loader, mcVersion.trim(), description.trim()) }

    fun updateModpackMeta(id: String, name: String? = null, enabled: Boolean? = null) =
        adminAction("Сборка обновлена") { api.updateModpack(id, name?.trim(), enabled) }

    fun deleteModpack(id: String) = adminAction("Сборка удалена") { api.deleteModpack(id) }

    fun deleteModpackArchive(id: String) = adminAction("ZIP удалён") { api.deleteModpackArchive(id) }

    // --- Site settings ---
    fun saveServerVersion(version: String) = adminAction("Версия сервера сохранена") {
        val res = api.setServerVersion(version.trim())
        res.serverVersion?.let { serverVersion = it; serverVersionDraft = it }
    }

    // --- Access ---
    fun addAdmin(nickname: String, permissions: List<String>) {
        if (nickname.isBlank() || permissions.isEmpty()) {
            errorMessage = "Укажите ник и хотя бы одно право"
            return
        }
        adminAction("Админ добавлен: $nickname") { api.addAdmin(nickname.trim(), permissions) }
    }

    fun updateAdmin(nickname: String, permissions: List<String>) {
        if (permissions.isEmpty()) {
            errorMessage = "Нужно хотя бы одно право"
            return
        }
        adminAction("Права обновлены: $nickname") { api.updateAdmin(nickname, permissions) }
    }

    fun removeAdmin(nickname: String) = adminAction("Админ удалён: $nickname") { api.removeAdmin(nickname) }

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    fun avatarUrl(size: Int = 64): String {
        val hash = skinTextureHash
            ?: meData?.cabinet?.player?.skinTextureHash
            ?: configState.skinTextureUrl.substringAfter("v=", "").takeIf { it.isNotBlank() }
        val skinUrl = configState.skinTextureUrl.trim().takeIf { it.isNotBlank() }
            ?: meData?.cabinet?.player?.skinUrl
        return api.avatarUrl(userName, userUuid, hash, skinUrl, size)
    }

    fun sessionCookie(): String? = api.sessionCookie()

    fun siteUrl(path: String): String {
        val base = configState.apiBaseUrl.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base$p"
    }

    private fun applySession(me: MeResponse) {
        isLoggedIn = true
        isAdmin = me.admin
        userName = me.user?.name.orEmpty()
        userUuid = me.user?.uuid
        meData = me
        skinTextureHash = me.cabinet?.player?.skinTextureHash
            ?: me.cabinet?.skinTextureHash
            ?: skinTextureHash
        val remoteSkin = me.cabinet?.player?.skinUrl.orEmpty().ifBlank { me.cabinet?.skinUrl.orEmpty() }
        if (remoteSkin.isNotBlank() && remoteSkin != configState.skinTextureUrl) {
            configState = configState.copy(skinTextureUrl = remoteSkin)
            runCatching { LauncherConfig.save(configState) }
        }
        avatarRevision++
        statusDraft = me.cabinet?.player?.profileStatus ?: me.cabinet?.profileStatus.orEmpty()
        scope.launch {
            withContext(Dispatchers.IO) {
                skinLibrary.importActiveIfEmpty(userName)
                refreshSkinLibraryState()
                syncActiveSkinToSite()
                refreshDiscordPresence()
            }
            notifications = withContext(Dispatchers.IO) {
                runCatching { api.notifications() }.getOrDefault(emptyList())
            }
        }
        if (me.admin) refreshAdmin()
        refreshDiscordPresence()
    }

    private fun refreshDiscordPresence() {
        discordPresence.update(
            loggedIn = isLoggedIn,
            username = userName,
            playing = isGameRunning,
            packName = selectedModpack?.name ?: selectedModpack?.slug,
            avatarImageUrl = discordAvatarImageUrl(),
        )
    }

    /** Public HTTPS avatar for Discord RPC (max ~256 chars). */
    private fun discordAvatarImageUrl(): String? {
        if (!isLoggedIn || userName.isBlank()) return null
        val fullHash = listOfNotNull(
            skinTextureHash,
            meData?.cabinet?.player?.skinTextureHash,
            meData?.cabinet?.skinTextureHash,
        ).map { it.trim().lowercase() }
            .firstOrNull { it.matches(Regex("^[a-f0-9]{64}$")) }

        val base = configState.apiBaseUrl.trimEnd('/')
        // Prefer static warmed face PNG — unique per skin, bypasses Discord CDN cache of old Steve.
        if (fullHash != null) {
            val staticFace = "$base/img/avatars/${fullHash}_face_128.png"
            if (staticFace.length in 12..256) return staticFace
        }

        val candidates = listOfNotNull(
            fullHash?.let { api.avatarUrl(userName, userUuid, it, skinUrl = null, size = 128) + "&cb=${it.take(12)}" },
            api.avatarUrl(userName, userUuid, null, skinUrl = null, size = 128) + "&cb=$avatarRevision",
        )
        return candidates.firstOrNull { it.length in 12..256 }
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
        gameWatchJob?.cancel()
        runCatching { gameProcess?.destroyForcibly() }
        clearGameProcess()
        runCatching { discordPresence.close() }
        api.close()
        mc.close()
        skins.close()
        updateChecker.close()
    }
}
