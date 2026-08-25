package fit.vcare.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import fit.vcare.apps.tools.SessionExpiryNotifier
import fit.vcare.apps.ui.theme.ChatProjectTheme
import kotlinx.coroutines.launch
//MainActivity.kt
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* نتیجه لازم نیست هندل بشه */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        lifecycleScope.launch {
            SessionExpiryNotifier.sessionExpired.collect {
                // navController داخل MainScreen هست، پس یه state سراسری یا event بذار که MainScreen ببینتش
                // ساده‌ترین راه: یه Toast + ریستارت اکتیویتی که می‌ره سمت لاگین چون isLoggedIn=false شده
                Toast.makeText(this@MainActivity, "نشست شما منقضی شده، لطفاً دوباره وارد شوید", Toast.LENGTH_LONG).show()
                recreate() // یا navController.navigate("login") اگه navController در دسترس باشه
            }
        }
        setContent {
            ChatProjectTheme {
                MainScreen(initialDeepLink = extractDeepLink(intent))
            }
        }
    }

    private fun extractDeepLink(intent: Intent?): ChatDeepLink? {
        val conversationId = intent?.getStringExtra("deeplink_conversationId") ?: return null
        val partnerUid = intent.getStringExtra("deeplink_partnerUid") ?: return null
        val partnerName = intent.getStringExtra("deeplink_partnerName") ?: return null
        return ChatDeepLink(conversationId, partnerUid, partnerName)
    }
}

data class ChatDeepLink(val conversationId: String, val partnerUid: String, val partnerName: String)
