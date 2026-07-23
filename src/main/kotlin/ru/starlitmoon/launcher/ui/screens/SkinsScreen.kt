package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.components.LocalSkinFace
import ru.starlitmoon.launcher.ui.components.SkinPreview3D
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkinsScreen(vm: LauncherViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Скины",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = StarlitColors.Text,
        )
        Text(
            "Библиотека скинов и плащей · статичный 2D (скин спереди + плащ) · плащ оффлайн",
            color = StarlitColors.TextMuted,
            fontSize = 13.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xF5161C30), Color(0xF00A0E1C))),
                )
                .border(1.dp, Color(0x33788CDC), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22101828))
                        .border(1.dp, Color(0x28788CDC), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SkinPreview3D(
                        skinPath = vm.activeSkinPath,
                        capePath = vm.activeCapePath,
                        slim = vm.activeSkinSlim,
                        previewSize = 340.dp,
                        revision = vm.avatarRevision,
                    )
                }
                Text(
                    vm.skinLibraryEntries.firstOrNull { it.id == vm.activeSkinId }?.name ?: "Нет активного скина",
                    color = StarlitColors.Gold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                StarlitPrimaryButton(
                    text = "Добавить скин",
                    onClick = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.FILES_ONLY
                            dialogTitle = "Скин PNG"
                            fileFilter = FileNameExtensionFilter("PNG", "png")
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            vm.addSkinToLibrary(chooser.selectedFile.absolutePath)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Библиотека", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (vm.skinLibraryEntries.isEmpty()) {
                    Text("Пока пусто — добавьте PNG-скин слева", color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    vm.skinLibraryEntries.forEach { entry ->
                        val selected = entry.id == vm.activeSkinId
                        val hasCape = entry.capeFileName != null
                        Column(
                            modifier = Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) StarlitColors.GoldMuted else Color(0x22101828))
                                .border(
                                    1.dp,
                                    if (selected) StarlitColors.Gold else Color(0x28788CDC),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { vm.selectLibrarySkin(entry.id) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LocalSkinFace(
                                skinPath = vm.librarySkinPath(entry),
                                fallbackName = entry.name,
                                size = 72.dp,
                                revision = vm.avatarRevision,
                            )
                            Text(
                                entry.name,
                                color = if (selected) StarlitColors.Gold else StarlitColors.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Text("Активный", color = StarlitColors.Gold, fontSize = 11.sp)
                            }
                            StarlitSecondaryButton(
                                text = if (hasCape) "Сменить плащ" else "Добавить плащ",
                                onClick = {
                                    val chooser = JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        dialogTitle = "Плащ PNG 64×32"
                                        fileFilter = FileNameExtensionFilter("PNG", "png")
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        vm.setLibraryCape(entry.id, chooser.selectedFile.absolutePath)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                            )
                            if (hasCape) {
                                StarlitSecondaryButton(
                                    text = "Убрать плащ",
                                    onClick = { vm.setLibraryCape(entry.id, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    compact = true,
                                )
                            }
                            StarlitPrimaryButton(
                                text = "Удалить скин",
                                onClick = { vm.removeLibrarySkin(entry.id) },
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                                danger = true,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
