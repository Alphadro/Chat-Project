package fit.vcare.apps.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
//ChatTopBar.kt
enum class ChatMenuAction {
    ClearHistory, DeleteChat, Search, ChangeBackground, ChangeTheme,
    Block, Unblock, Report, Unmatch, ToggleMute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onCloseSearch: () -> Unit,
    partnerName: String,
    partnerPhotoUrl: String?,
    statusText: String,
    isPartnerTyping: Boolean,
    onNavigateBack: () -> Unit,
    onOpenPartnerProfile: () -> Unit,
    showOverflowMenu: Boolean,
    onOverflowMenuToggle: (Boolean) -> Unit,
    isBlockedByMe: Boolean,
    isMuted: Boolean,
    onMenuAction: (ChatMenuAction) -> Unit
) {
    if (isSearchActive) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجو در پیام‌ها...") },
                    singleLine = true
                )
            },
            actions = {
                if (matchCount > 0) {
                    Text(
                        text = "${currentMatchIndex + 1}/$matchCount",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = onPrevMatch) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "قبلی")
                    }
                    IconButton(onClick = onNextMatch) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "بعدی")
                    }
                }
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = "بستن جستجو")
                }
            }
        )
        return
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenPartnerProfile() }
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!partnerPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = partnerPhotoUrl,
                            contentDescription = "عکس پروفایل",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = partnerName.take(1).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(partnerName)
                    if (statusText.isNotBlank()) {
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = if (isPartnerTyping)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { onOverflowMenuToggle(true) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "منو")
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { onOverflowMenuToggle(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("پاک کردن تاریخچه") },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.ClearHistory) }
                )
                DropdownMenuItem(
                    text = { Text("حذف چت") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.DeleteChat) }
                )
                Divider()
                DropdownMenuItem(
                    text = { Text("جستجو در پیام‌ها") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.Search) }
                )
                DropdownMenuItem(
                    text = { Text("تغییر پس‌زمینه") },
                    leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.ChangeBackground) }
                )
                DropdownMenuItem(
                    text = { Text("تم چت") },
                    leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.ChangeTheme) }
                )
                if (!isBlockedByMe) {
                    DropdownMenuItem(
                        text = { Text("مسدود کردن پارتنر", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.Block) }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("لغو مسدودیت") },
                        leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                        onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.Unblock) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("گزارش پارتنر") },
                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.Report) }
                )
                DropdownMenuItem(
                    text = { Text("پایان رابطه (Unmatch)") },
                    leadingIcon = { Icon(Icons.Filled.HeartBroken, contentDescription = null) },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.Unmatch) }
                )
                DropdownMenuItem(
                    text = { Text(if (isMuted) "فعال کردن اعلان‌ها" else "بی‌صدا کردن اعلان‌ها") },
                    leadingIcon = {
                        Icon(
                            if (isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                            contentDescription = null
                        )
                    },
                    onClick = { onOverflowMenuToggle(false); onMenuAction(ChatMenuAction.ToggleMute) }
                )
            }
        }
    )
}