package fit.vcare.apps.mainPage_Nav.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import fit.vcare.apps.Splash
import fit.vcare.apps.mainPage_Nav.pages.*
import fit.vcare.apps.navigation.partnerChatGraph
//AppNavigation.kt
object Routes {
    const val SPLASH = "splash"
    const val FITFLOW = "fitflow"
    const val BALANCE = "balance"
    const val RISE = "rise"
    const val MINE = "mine"
    const val INSIGHT = "insight"
    const val LOGIN = "login"
}

@Composable
fun AppNavigation(navController: NavHostController, innerPadding: PaddingValues) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = Modifier.padding(innerPadding)
    ) {
        composable(Routes.SPLASH) {
            Splash(navController)
        }
        composable(Routes.FITFLOW) {
            FitFlowScreen(
                navController)
        }
        composable(Routes.BALANCE) {
            BalanceScreen(navController)
        }
        composable(Routes.RISE) {
            RiseScreen()
        }
        composable(Routes.MINE) {
            MineScreen()
        }
        composable(Routes.INSIGHT) {
            InsightScreen(navController)
        }

        partnerChatGraph(navController)
    }
}