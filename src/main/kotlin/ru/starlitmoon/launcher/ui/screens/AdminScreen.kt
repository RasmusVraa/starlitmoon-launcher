package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminScreen(vm: LauncherViewModel, adminUrl: String, @Suppress("UNUSED_PARAMETER") sessionCookie: String?) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }

    LaunchedEffect(Unit) { vm.refreshAdminAccess() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!vm.isAdmin) {
            SectionTitle("Админ-панель", "Нет доступа")
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Ваш аккаунт не имеет прав администратора сайта.",
                        color = StarlitColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    StarlitSecondaryButton(
                        text = "Открыть личный кабинет",
                        onClick = { vm.currentTab = ru.starlitmoon.launcher.viewmodel.LauncherTab.Cabinet },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            return@Column
        }

        SectionTitle(
            title = "Админ-панель",
            subtitle = "Полный интерфейс — на сайте (та же сессия в браузере после входа)",
        )

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Вы вошли как ${vm.userName}",
                    color = StarlitColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
                vm.adminMe?.permissions?.takeIf { it.isNotEmpty() }?.let { perms ->
                    StatRow("Права", perms.joinToString(", "))
                }
                vm.adminMe?.let { admin ->
                    StatRow("Консоль MC", if (admin.permissions.contains("console")) "да" else "нет")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StarlitPrimaryButton(
                        text = "Открыть админку на сайте",
                        onClick = {
                            runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(adminUrl))
                            }.onFailure {
                                vm.errorMessage = "Не удалось открыть браузер"
                            }
                        },
                    )
                    StarlitSecondaryButton(text = "Обновить", onClick = vm::refreshAdminAccess)
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сводка", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                Text(
                    "Игроки, банк, заявки, консоль и остальные разделы доступны в веб-админке — " +
                        "она идентична starlit-moon.ru/admin.",
                    color = StarlitColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
