package fit.vcare.apps.ui.partner


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import fit.vcare.apps.viewmodel.PartnerViewModel

@Composable
fun ScanPartnerScreen(
    navController: NavController,
    viewModel: PartnerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var errorText by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (scanned == null) {
            errorText = "اسکن لغو شد"
            return@rememberLauncherForActivityResult
        }
        val token = viewModel.extractTokenFromScannedText(scanned)
        if (token == null) {
            errorText = "کد QR معتبر نیست"
        } else {
            navController.navigate("partner_request/$token")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Scan Partner QR", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        errorText?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Button(onClick = {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("QR کد پارتنر خود را اسکن کنید")
                setBeepEnabled(true)
                setOrientationLocked(true)
            }
            scanLauncher.launch(options)
        }) {
            Text("Start Scanning")
        }
    }
}