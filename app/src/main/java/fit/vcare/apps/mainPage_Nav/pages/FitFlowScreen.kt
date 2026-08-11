package fit.vcare.apps.mainPage_Nav.pages
import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController


@Composable
fun FitFlowScreen(navController: NavHostController) {
    val context = LocalContext.current
    val activity = context as? Activity

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Button(
            onClick = {
                navController.navigate("chat_list")
            }
        ) {
            Text("کلیک کن")
        }
    }
}
