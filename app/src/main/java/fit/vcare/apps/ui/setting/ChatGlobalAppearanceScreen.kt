package fit.vcare.apps.ui.setting

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import fit.vcare.apps.data.local.ChatAppearancePrefs
import fit.vcare.apps.domain.model.ChatThemePresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGlobalAppearanceScreen(navController: NavController) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    var backgroundUri by remember { mutableStateOf(ChatAppearancePrefs.getGlobalBackgroundUri(context)) }
    var themeKey by remember { mutableStateOf(ChatAppearancePrefs.getGlobalThemeKey(context)) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            backgroundUri = uri.toString()
            ChatAppearancePrefs.setGlobalBackgroundUri(context, uri.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("پس‌زمینه و تم پیش‌فرض چت‌ها") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
                }
            }
        )

        Text(
            "این تنظیم به‌صورت پیش‌فرض روی همه‌ی چت‌های شما اعمال می‌شود، مگر اینکه برای یک چت خاص جداگانه تنظیمش کرده باشید.",
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(Modifier.padding(16.dp)) {
            Text("پس‌زمینه پیش‌فرض", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (!backgroundUri.isNullOrBlank()) {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
            }
            Row {
                Button(onClick = {
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("انتخاب از گالری") }

                if (!backgroundUri.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        backgroundUri = null
                        ChatAppearancePrefs.setGlobalBackgroundUri(context, null)
                    }) { Text("حذف") }
                }
            }
        }

        Divider()

        Column(Modifier.padding(16.dp)) {
            Text("تم رنگی پیش‌فرض", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ChatThemePresets.all.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { option ->
                        val swatch = if (isDarkTheme) option.darkColor else option.lightColor
                        val selected = option.key == themeKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp).clickable {
                                themeKey = option.key
                                ChatAppearancePrefs.setGlobalThemeKey(context, option.key)
                            }
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(swatch)
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(option.label, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}