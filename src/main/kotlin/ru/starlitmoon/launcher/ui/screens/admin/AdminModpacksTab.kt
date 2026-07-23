package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.starlitmoon.launcher.api.ModpackDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

private val LOADERS = listOf("vanilla", "fabric", "neoforge")

@Composable
fun AdminModpacksSection(vm: LauncherViewModel) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ModpackDto?>(null) }
    var deletePack by remember { mutableStateOf<ModpackDto?>(null) }
    var deleteArchivePack by remember { mutableStateOf<ModpackDto?>(null) }

    val packs = if (vm.adminModpacks.isNotEmpty()) vm.adminModpacks else vm.modpacks

    AdminSectionCard(
        "Сборки лаунчера",
        "Официальные сборки. Загрузка ZIP включает у игроков «Требуется обновление».",
        trailing = { StarlitPrimaryButton(text = "Создать", onClick = { creating = true }, compact = true, modifier = Modifier.width(110.dp)) },
    ) {
        if (packs.isEmpty()) AdminEmpty("Сборок нет")
        packs.forEach { pack ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pack.name ?: pack.slug ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                pack.loader,
                                pack.mcVersion?.let { "MC $it" },
                                if (pack.hasArchive) "ZIP есть" else "без ZIP",
                                if (!pack.enabled) "выключена" else null,
                            ).joinToString(" · "),
                            color = StarlitColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    AdminCheckbox(pack.enabled, "вкл", { on -> pack.id?.let { vm.updateModpackMeta(it, enabled = on) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StarlitPrimaryButton(
                        text = if (pack.hasArchive) "Обновить ZIP" else "Загрузить ZIP",
                        onClick = {
                            val chooser = javax.swing.JFileChooser().apply {
                                fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                                dialogTitle = "ZIP сборки"
                                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip")
                            }
                            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                vm.uploadModpackUpdate(pack, chooser.selectedFile.absolutePath)
                            }
                        },
                        compact = true,
                        enabled = vm.launchProgress == null && !pack.id.isNullOrBlank(),
                        loading = vm.launchProgress != null && vm.selectedModpack?.id == pack.id,
                        modifier = Modifier.width(150.dp),
                    )
                    StarlitSecondaryButton(text = "Изменить", onClick = { editing = pack }, compact = true, modifier = Modifier.width(110.dp))
                    if (pack.hasArchive) {
                        StarlitSecondaryButton(text = "Убрать ZIP", onClick = { deleteArchivePack = pack }, compact = true, modifier = Modifier.width(120.dp))
                    }
                    StarlitPrimaryButton(text = "Удалить", onClick = { deletePack = pack }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                }
            }
        }
        if (vm.launchProgress != null) Text(vm.launchProgress!!, color = StarlitColors.Gold, fontSize = 12.sp)
    }

    if (creating) ModpackEditorDialog(vm, null) { creating = false }
    editing?.let { ModpackEditorDialog(vm, it) { editing = null } }
    deletePack?.let { p ->
        StarlitConfirmDialog(
            title = "Удалить сборку?",
            message = "Сборка «${p.name}» будет удалена вместе с архивом.",
            danger = true,
            onConfirm = { p.id?.let { vm.deleteModpack(it) }; deletePack = null },
            onDismiss = { deletePack = null },
        )
    }
    deleteArchivePack?.let { p ->
        StarlitConfirmDialog(
            title = "Удалить ZIP?",
            message = "Архив сборки «${p.name}» будет удалён.",
            danger = true,
            onConfirm = { p.id?.let { vm.deleteModpackArchive(it) }; deleteArchivePack = null },
            onDismiss = { deleteArchivePack = null },
        )
    }
}

@Composable
private fun ModpackEditorDialog(vm: LauncherViewModel, pack: ModpackDto?, onDismiss: () -> Unit) {
    val isNew = pack == null
    var name by remember { mutableStateOf(pack?.name ?: "") }
    var slug by remember { mutableStateOf(pack?.slug ?: "") }
    var loader by remember { mutableStateOf(pack?.loader ?: "vanilla") }
    var mcVersion by remember { mutableStateOf(pack?.mcVersion ?: "") }
    var description by remember { mutableStateOf(pack?.description ?: "") }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(StarlitColors.OverlayScrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 440.dp, max = 560.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF161C2B), Color(0xFF0E121C))))
                    .border(1.dp, StarlitColors.BorderStrong, RoundedCornerShape(18.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (isNew) "Новая сборка" else "Сборка", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                StarlitTextField(name, { name = it }, "Название")
                if (isNew) {
                    StarlitTextField(slug, { slug = it }, "Slug (опц.)")
                    Text("Loader", color = StarlitColors.TextMuted, fontSize = 12.sp)
                    AdminFilterChips(LOADERS.map { it to it }, loader) { loader = it }
                    StarlitTextField(mcVersion, { mcVersion = it }, "Версия Minecraft")
                    StarlitTextField(description, { description = it }, "Описание")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            if (isNew) vm.createModpack(name, slug, loader, mcVersion, description)
                            else vm.updateModpackMeta(pack!!.id!!, name = name)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    StarlitSecondaryButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}
