package fit.vcare.apps.ui.partner


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import fit.vcare.apps.domain.model.PartnerPresence
import fit.vcare.apps.viewmodel.PartnerProfileViewModel

//PartnerProfileScreen.kt
private const val PROFILE_PRESENCE_STALE_MS = 20_000L
private val profileTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
private val PROFILE_AVATAR_SIZE = 140.dp

private fun profileStatusText(presence: PartnerPresence?): String {
    if (presence == null) return ""
    val isFresh = presence.lastActiveAt > 0 &&
            (System.currentTimeMillis() - presence.lastActiveAt) < PROFILE_PRESENCE_STALE_MS
    return when {
        presence.isOnline && isFresh -> "آنلاین"
        presence.lastActiveAt > 0 -> formatProfileLastSeen(presence.lastActiveAt)
        else -> ""
    }
}

private fun formatProfileLastSeen(lastActiveAt: Long): String {
    val nowCal = Calendar.getInstance()
    val seenCal = Calendar.getInstance().apply { timeInMillis = lastActiveAt }
    val isSameDay = nowCal.get(Calendar.YEAR) == seenCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == seenCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == seenCal.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == seenCal.get(Calendar.DAY_OF_YEAR)

    val timeStr = profileTimeFormatter.format(Date(lastActiveAt))
    val diff = System.currentTimeMillis() - lastActiveAt

    return when {
        isSameDay -> "آخرین بازدید امروز ساعت $timeStr"
        isYesterday -> "آخرین بازدید دیروز ساعت $timeStr"
        diff < 7L * 24 * 60 * 60 * 1000L -> {
            val dayFormatter = SimpleDateFormat("EEEE", Locale("fa"))
            "آخرین بازدید ${dayFormatter.format(Date(lastActiveAt))} ساعت $timeStr"
        }
        diff < 30L * 24 * 60 * 60 * 1000L -> "آخرین بازدید در یک ماه اخیر"
        else -> "آخرین بازدید خیلی وقت پیش"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerProfileScreen(
    navController: NavController,
    partnerUid: String,
    partnerName: String,
    viewModel: PartnerProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(partnerUid) {
        viewModel.load(partnerUid)
        onDispose { viewModel.stopObserving() }
    }

    val displayName = uiState.userInfo?.displayName ?: partnerName
    val statusText = profileStatusText(uiState.presence)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val photoUrl = uiState.userInfo?.photoUrl

            Box(
                modifier = Modifier
                    .size(PROFILE_AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "عکس پروفایل",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase(),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(text = displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            if (statusText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = if (uiState.presence?.isOnline == true)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Divider()

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        // ── بخش اطلاعات (فعلاً فقط ایمیل — بعداً می‌شه بیشتر اضافه کرد) ──
        val email = uiState.userInfo?.email
        if (!email.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(email, fontSize = 15.sp) },
                supportingContent = { Text("ایمیل", fontSize = 12.sp) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            Divider()
        }
    }
}