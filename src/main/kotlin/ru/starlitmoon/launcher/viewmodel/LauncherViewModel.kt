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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import javax.swing.SwingUtilities
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
import ru.starlitmoon.launcher.api.ModpackDto
import ru.starlitmoon.launcher.api.NotificationDto
import ru.starlitmoon.launcher.api.ServerStatus
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.api.StarlitApiException
import ru.starlitmoon.launcher.minecraft.MinecraftLauncher
import ru.starlitmoon.launcher.minecraft.ModpackSync
import ru.starlitmoon.launcher.minecraft.OfflineSkinBridge
import ru.starlitmoon.launcher.minecraft.SkinManager
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

enum class LauncherTab { Home, Builds, Cabinet, Settings, Admin }

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
    var currentTab by mutableStateOf(LauncherTab.Home)
    var adminSubTab by mutableStateOf(0)
    var modpacks by mutableStateOf<List<ModpackDto>>(emptyList())
    var isLoadingModpacks by mutableStateOf(false)
    var selectedModpack by mutableStateOf<ModpackDto?>(null)
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
        }
    }

    fun saveSettings(newConfig: LauncherConfig) {
        LauncherConfig.save(newConfig)
        configState = newConfig
        mc.close()
        mc = MinecraftLauncher(configState)
        skins.close()
        skins = SkinManager(configState.skinsDir)
        infoMessage = "Настройки сохранены"
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
                infoMessage = "Скин «${it.name}» добавлен в библиотеку"
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
                infoMessage = "Скин выбран"
            }.onFailure { handleError(it) }
            skinBusy = false
        }
    }

    fun setLibraryCape(skinId: String, capePath: String?) {
        scope.launch {
            skinBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    skinLibrary.setCape(skinId, capePath?.takeIf { it.isNotBlank() }?.let { Path.of(it) })
                    if (skinLibrary.activeId() == skinId) {
                        applyLibrarySkin(skinId, upload = false)
                    } else {
                        refreshSkinLibraryState()
                    }
                }
            }.onSuccess {
                infoMessage = if (capePath.isNullOrBlank()) "Плащ снят" else "Плащ установлен"
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
            url = uploaded.skinUrl.orEmpty()
            hash = uploaded.skinTextureHash
            skinCommand = if (url.isNotBlank()) "/skin set $url" else ""
        }
        configState = configState.copy(
            skinPath = path.toString(),
            skinTextureUrl = url,
        )
        LauncherConfig.save(configState)
        skinTextureHash = hash
        refreshSkinLibraryState()
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
        gameWatchJob = scope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            val livedMs = System.currentTimeMillis() - startedAt
            val stoppedByUser = gameStopRequested
            val logTail = runCatching {
                configState.dataDir.resolve("last-launch.log").toFile().readText().takeLast(900)
            }.getOrNull()
            val looksLikeCrash = !stoppedByUser && (code != 0 || livedMs < 8_000)
            SwingUtilities.invokeLater {
                clearGameProcess()
                when {
                    stoppedByUser -> {
                        // already set info in stopGame(); keep a quiet success toast
                        if (infoMessage.isNullOrBlank()) infoMessage = "Игра остановлена"
                    }
                    looksLikeCrash && !logTail.isNullOrBlank() -> {
                        val hint = logTail.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .lastOrNull()
                            ?: "См. лог запуска"
                        errorMessage = "Игра закрылась. $hint"
                    }
                    !configState.keepLauncherOpen -> requestExit = true
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
                    adminMe = api.adminMe()
                    when (adminSubTab) {
                        // Игроки / Банк / Значки / Карта / Кланы / Заявки / … / Консоль
                        0, 1 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
                            adminPlayers = runCatching { api.adminPlayers(adminSearch).players }.getOrDefault(emptyList())
                            adminAccounts = runCatching { api.adminAccounts(accountSearch).accounts }.getOrDefault(emptyList())
                            adminBank = runCatching { api.adminBank().cards }.getOrDefault(emptyList())
                            adminTreasury = runCatching { api.treasury() }.getOrNull()
                            adminProducts = runCatching { api.adminProducts().products }.getOrDefault(emptyList())
                            adminOrders = runCatching { api.adminOrders().orders }.getOrDefault(emptyList())
                        }
                        2 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
                            adminBadges = runCatching { api.adminBadges().badges }.getOrDefault(emptyList())
                        }
                        3, 6, 7, 8, 9, 10 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
                        }
                        4 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
                            adminClans = runCatching { api.adminClans("pending").clans }.getOrDefault(emptyList())
                        }
                        5 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
                            adminApps = runCatching { api.adminApplications("pending").applications }.getOrDefault(emptyList())
                        }
                        11 -> {
                            adminStats = runCatching { api.adminStats() }.getOrNull()
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
            }
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
        gameWatchJob?.cancel()
        runCatching { gameProcess?.destroyForcibly() }
        clearGameProcess()
        api.close()
        mc.close()
        skins.close()
        updateChecker.close()
    }
}
