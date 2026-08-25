package fit.vcare.apps.ui.partner


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fit.vcare.apps.viewmodel.PartnerViewModel
//PartnerInviteAcceptScreen.kt
@Composable
fun PartnerInviteAcceptScreen(
    navController: NavController,
    token: String,
    viewModel: PartnerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(token) {
        viewModel.loadInvitePreview(token)
    }

    LaunchedEffect(uiState.acceptedConversationId) {
        val convId = uiState.acceptedConversationId
        val partnerUid = uiState.acceptedPartnerUid
        val partnerName = uiState.acceptedPartnerName
        if (convId != null && partnerUid != null && partnerName != null) {
            navController.navigate("chat/$convId/$partnerUid/${android.net.Uri.encode(partnerName)}") {
                popUpTo("chat_list") { inclusive = false }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> {
                Text(uiState.error?.message ?: "خطا", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { navController.popBackStack() }) { Text("بازگشت") }
            }
            uiState.previewUser != null -> {
                Text("Connect with this partner?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(uiState.previewUser!!.displayName)
                Spacer(Modifier.height(24.dp))
                Row {
                    Button(onClick = { viewModel.acceptInvite(token) }) { Text("Connect") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { navController.popBackStack() }) { Text("Cancel") }
                }
            }
        }
    }
}