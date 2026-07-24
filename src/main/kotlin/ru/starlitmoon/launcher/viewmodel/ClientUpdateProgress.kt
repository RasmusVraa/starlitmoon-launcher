package ru.starlitmoon.launcher.viewmodel

/**
 * Full-page client update / prep UI state (see ClientUpdateScreen).
 */
data class ClientUpdateProgress(
    val eyebrow: String = "ОБНОВЛЕНИЕ КЛИЕНТА",
    val title: String = "ПОДГОТОВКА",
    val status: String = "Подготовка",
    val stageIndex: Int = 1,
    val stageCount: Int = 3,
    val detail: String = "",
    val overall: Float = 0f,
    val stageProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val speedBps: Long? = null,
    val remainingLabel: String = "Расчёт...",
    val filesDone: Int = 0,
    val filesTotal: Int? = null,
    val currentFile: String = "ожидание…",
    val activeThreads: Int = 0,
) {
    val stageCaption: String
        get() = "Этап $stageIndex из $stageCount"

    val overallPercent: Int
        get() = (overall.coerceIn(0f, 1f) * 100).toInt()

    val stagePercent: Int
        get() = (stageProgress.coerceIn(0f, 1f) * 100).toInt()
}

enum class ClientUpdatePhase {
    Prep,
    Files,
    Client,
}

object ClientUpdateLabels {
    fun titleFor(phase: ClientUpdatePhase): String = when (phase) {
        ClientUpdatePhase.Prep -> "ПОДГОТОВКА"
        ClientUpdatePhase.Files -> "ПРОВЕРКА ФАЙЛОВ"
        ClientUpdatePhase.Client -> "ПОДГОТОВКА КЛИЕНТА"
    }

    fun statusFor(phase: ClientUpdatePhase, message: String): String = when {
        message.contains("Скачивание", ignoreCase = true) -> "Скачивание"
        message.contains("Распаковка", ignoreCase = true) -> "Распаковка"
        message.contains("Проверка", ignoreCase = true) -> "Проверка"
        message.contains("Java", ignoreCase = true) -> "Java"
        message.contains("Ресурс", ignoreCase = true) -> "Ресурсы"
        message.contains("Библиотек", ignoreCase = true) -> "Библиотеки"
        message.contains("Запуск", ignoreCase = true) -> "Запуск"
        phase == ClientUpdatePhase.Prep -> "Подготовка"
        phase == ClientUpdatePhase.Files -> "Файлы сборки"
        else -> "Клиент"
    }

    fun detailFor(phase: ClientUpdatePhase, message: String): String = when (phase) {
        ClientUpdatePhase.Prep -> message.ifBlank { "Готовим запуск…" }
        ClientUpdatePhase.Files -> message.ifBlank { "Собираем список файлов и рассчитываем объём обновления" }
        ClientUpdatePhase.Client -> message.ifBlank { "Скачиваем и проверяем файлы клиента" }
    }

    fun formatBytes(n: Long): String {
        if (n >= 1024L * 1024L * 1024L) return "%.1f ГБ".format(n / (1024.0 * 1024.0 * 1024.0))
        if (n >= 1024L * 1024L) return "%.1f МБ".format(n / (1024.0 * 1024.0))
        if (n >= 1024L) return "%.0f КБ".format(n / 1024.0)
        return "$n Б"
    }

    fun formatSpeed(bps: Long): String {
        if (bps <= 0L) return "—"
        return "${formatBytes(bps)}/с"
    }

    fun formatEta(remainingBytes: Long, bps: Long): String {
        if (bps <= 0L || remainingBytes <= 0L) return "Расчёт..."
        val sec = (remainingBytes / bps).toInt().coerceAtLeast(1)
        return when {
            sec < 60 -> "~$sec с"
            sec < 3600 -> "~${sec / 60} мин"
            else -> "~${sec / 3600} ч ${(sec % 3600) / 60} мин"
        }
    }
}
