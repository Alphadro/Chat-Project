package fit.vcare.apps.navigation


import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import fit.vcare.apps.ui.chat.ChatListScreen
import fit.vcare.apps.ui.chat.ChatScreen
import fit.vcare.apps.ui.partner.AddPartnerScreen
import fit.vcare.apps.ui.partner.PartnerInviteAcceptScreen
import fit.vcare.apps.ui.partner.PartnerProfileScreen
import fit.vcare.apps.ui.partner.ScanPartnerScreen
import fit.vcare.apps.ui.setting.ChatGlobalAppearanceScreen

object PartnerChatRoutes {
    const val ADD_PARTNER = "add_partner"
    const val SCAN_PARTNER = "scan_partner"
    const val PARTNER_REQUEST = "partner_request/{token}"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{conversationId}/{partnerUid}/{partnerName}"
    const val GLOBAL_APPEARANCE = "chat_appearance_global"
    const val PARTNER_PROFILE = "partner_profile/{partnerUid}/{partnerName}" // ← جدید
}

/**
 * فقط این خط را داخل NavHost موجود خودتان صدا بزنید:
 *   partnerChatGraph(navController)
 */
fun NavGraphBuilder.partnerChatGraph(navController: NavHostController) {

    composable(PartnerChatRoutes.ADD_PARTNER) {
        AddPartnerScreen(navController)
    }

    composable(PartnerChatRoutes.SCAN_PARTNER) {
        ScanPartnerScreen(navController)
    }

    composable(
        route = PartnerChatRoutes.PARTNER_REQUEST,
        arguments = listOf(navArgument("token") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://YOUR_DOMAIN/partner/invite/{token}" },
            navDeepLink { uriPattern = "vcareapp://partner/invite/{token}" }
        )
    ) { backStackEntry ->
        val token = backStackEntry.arguments?.getString("token") ?: ""
        PartnerInviteAcceptScreen(navController, token)
    }

    composable(PartnerChatRoutes.CHAT_LIST) {
        ChatListScreen(navController)
    }

    composable(
        route = PartnerChatRoutes.CHAT,
        arguments = listOf(
            navArgument("conversationId") { type = NavType.StringType },
            navArgument("partnerUid") { type = NavType.StringType },
            navArgument("partnerName") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
        val partnerUid = backStackEntry.arguments?.getString("partnerUid") ?: ""
        val partnerName = backStackEntry.arguments?.getString("partnerName") ?: ""
        ChatScreen(navController, conversationId, partnerUid, partnerName)
    }
    composable(PartnerChatRoutes.GLOBAL_APPEARANCE) {
        ChatGlobalAppearanceScreen(navController)
    }
    composable(
        route = PartnerChatRoutes.PARTNER_PROFILE,
        arguments = listOf(
            navArgument("partnerUid") { type = NavType.StringType },
            navArgument("partnerName") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val partnerUid = backStackEntry.arguments?.getString("partnerUid") ?: ""
        val partnerName = backStackEntry.arguments?.getString("partnerName") ?: ""
        PartnerProfileScreen(navController, partnerUid, partnerName)
    }
}