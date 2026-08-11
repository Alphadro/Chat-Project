package fit.vcare.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import fit.vcare.apps.mainPage_Nav.nav.Routes
import kotlinx.coroutines.delay

@Composable
fun Splash(
    navController: NavController
) {

    LaunchedEffect(Unit) {
        delay(2000)

        navController.navigate(Routes.FITFLOW) {
            popUpTo(Routes.SPLASH) {
                inclusive = true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Hi Welcome to Here"
        )

        Text(
            text = "Chati :)"
        )

        CircularProgressIndicator()
    }
}