package fit.vcare.apps.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fit.vcare.apps.navigation.PartnerChatRoutes
import fit.vcare.apps.viewmodel.ChatListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
//ChatListScreen.kt
private val listTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatListTime(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    return listTimeFormatter.format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController,
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startObserving()
                Lifecycle.Event.ON_STOP -> viewModel.stopObserving()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.startObserving()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopObserving()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Chats") },
            actions = {
                IconButton(onClick = { navController.navigate(PartnerChatRoutes.GLOBAL_APPEARANCE) }) {
                    Icon(Icons.Filled.Wallpaper, contentDescription = "پس‌زمینه و تم پیش‌فرض")
                }
            }
        )
        Button(
            onClick = { navController.navigate("add_partner") },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Add Partner")
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "خطا")
            }
            uiState.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("هنوز پارتنری اضافه نکرده‌اید")
            }
            else -> LazyColumn {
                items(uiState.items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    "chat/${item.conversationId}/${item.partnerUid}/${android.net.Uri.encode(item.partnerName)}"
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.partnerPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.partnerPhotoUrl,
                                    contentDescription = "عکس پروفایل",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = item.partnerName.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(item.partnerName, fontWeight = FontWeight.Bold)
                            Text(
                                text = item.lastMessage ?: "شروع مکالمه",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val timeText = formatListTime(item.lastMessageAt)
                            if (timeText.isNotBlank()) {
                                Text(
                                    text = timeText,
                                    fontSize = 12.sp,
                                    color = if (item.unreadCount > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.unreadCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Divider()
                }
            }
        }
    }
}