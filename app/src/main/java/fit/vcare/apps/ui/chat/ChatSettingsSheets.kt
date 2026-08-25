package fit.vcare.apps.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fit.vcare.apps.domain.model.ChatThemeOption
import fit.vcare.apps.domain.model.ChatThemePresets
//ChatSettingsSheets.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBackgroundSheet(
    hasBackground: Boolean,
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemoveBackground: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "پس‌زمینه چت",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("انتخاب از گالری") },
                leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onPickFromGallery()
                }
            )
            if (hasBackground) {
                ListItem(
                    headlineContent = { Text("حذف پس‌زمینه") },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onRemoveBackground()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThemeSheet(
    currentThemeKey: String,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp)) {
            Text("تم چت", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
            ChatThemePresets.all.chunked(4).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowItems.forEach { option: ChatThemeOption ->
                        val swatchColor = if (isDarkTheme) option.darkColor else option.lightColor
                        val isSelected = option.key == currentThemeKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp).clickable {
                                onThemeSelected(option.key)
                                onDismiss()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(option.label, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}