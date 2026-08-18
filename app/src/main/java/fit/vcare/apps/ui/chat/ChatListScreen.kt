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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fit.vcare.apps.navigation.PartnerChatRoutes
import fit.vcare.apps.viewmodel.ChatListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    LaunchedEffect(Unit) { viewModel.load() }

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
                                maxLines = 1
                            )
                        }

                        val timeText = formatListTime(item.lastMessageAt)
                        if (timeText.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = timeText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}