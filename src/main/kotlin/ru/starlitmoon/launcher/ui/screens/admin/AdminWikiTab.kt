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
import ru.starlitmoon.launcher.api.AdminWikiPageDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminWikiSection(vm: LauncherViewModel) {
    var editing by remember { mutableStateOf<AdminWikiPageDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AdminWikiPageDto?>(null) }

    AdminSectionCard(
        "Вики",
        "Страницы вики. Полный редактор блоков — на сайте; здесь базовое редактирование.",
        trailing = { StarlitPrimaryButton(text = "Создать", onClick = { creating = true }, compact = true, modifier = Modifier.width(110.dp)) },
    ) {
        if (vm.adminWikiPages.isEmpty()) AdminEmpty("Страниц нет")
        vm.adminWikiPages.forEach { page ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(page.title ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "/${page.slug ?: ""} · ${if (page.published) "опубликована" else "черновик"}",
                        color = StarlitColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                StarlitSecondaryButton(text = "Изменить", onClick = { editing = page }, compact = true, modifier = Modifier.width(110.dp))
                StarlitPrimaryButton(text = "Удалить", onClick = { deleteTarget = page }, compact = true, danger = true, modifier = Modifier.width(100.dp))
            }
        }
    }

    if (creating) WikiEditorDialog(vm, null) { creating = false }
    editing?.let { WikiEditorDialog(vm, it) { editing = null } }
    deleteTarget?.let { p ->
        StarlitConfirmDialog(
            title = "Удалить страницу?",
            message = "Страница «${p.title}» и вложенные будут удалены.",
            danger = true,
            onConfirm = { p.id?.let { vm.deleteWikiPage(it) }; deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun WikiEditorDialog(vm: LauncherViewModel, page: AdminWikiPageDto?, onDismiss: () -> Unit) {
    val isNew = page == null
    var title by remember { mutableStateOf(page?.title ?: "") }
    var slug by remember { mutableStateOf(page?.slug ?: "") }
    var published by remember { mutableStateOf(page?.published ?: false) }
    var paragraph by remember {
        mutableStateOf(page?.blocks?.firstOrNull { it.type == "paragraph" }?.data?.text ?: "")
    }

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
                Text(if (isNew) "Новая страница" else "Страница", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                StarlitTextField(title, { title = it }, "Заголовок")
                StarlitTextField(slug, { slug = it }, "Slug (латиница)")
                AdminCheckbox(published, "Опубликована", { published = it })
                if (!isNew) {
                    AdminMultilineField(paragraph, { paragraph = it }, "Текст (один абзац)", minHeight = 120)
                    Text("Прочие блоки редактируются на сайте.", color = StarlitColors.TextDim, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            if (isNew) vm.createWikiPage(title, slug, published)
                            else vm.updateWikiPage(page!!.id!!, title, slug, published, paragraph.takeIf { it.isNotBlank() })
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
