package fit.vcare.apps.ui.partner


import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fit.vcare.apps.viewmodel.PartnerViewModel

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@Composable
fun AddPartnerScreen(
    navController: NavController,
    viewModel: PartnerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.invite == null) viewModel.generateInvite()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add Partner", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> {
                Text(uiState.error?.message ?: "خطا", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.generateInvite() }) { Text("تلاش دوباره") }
            }
            uiState.inviteLink != null -> {
                val bitmap = remember(uiState.inviteLink) { generateQrBitmap(uiState.inviteLink!!) }
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Invite QR")
                Spacer(Modifier.height(16.dp))
                Text("این QR را برای پارتنر خود نمایش دهید یا لینک را share کنید")
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, uiState.inviteLink)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Invite"))
                }) {
                    Text("Share Invite")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { navController.navigate("scan_partner") }) {
            Text("Scan a Partner's QR instead")
        }
    }
}