package fit.vcare.apps.tools


object FirestorePaths {
    private const val MISSIONS_ROOT = "rps/missions"
    private fun userRoot(uid: String) = "users/$uid/$MISSIONS_ROOT"

    fun achievementsCollection() = "$MISSIONS_ROOT/achievements"
    fun achievementDoc(achievementId: String) = "${achievementsCollection()}/$achievementId"
    fun shareTemplatesCollection() = "$MISSIONS_ROOT/share_templates"
    fun shareTemplateDoc(docId: String) = "${shareTemplatesCollection()}/$docId"

    fun missionProfile(uid: String) = userRoot(uid)
    fun userAchievementsCollection(uid: String) = "${userRoot(uid)}/achievements"
    fun userAchievementDoc(uid: String, achievementId: String) = "${userAchievementsCollection(uid)}/$achievementId"
    fun streakHistoryCollection(uid: String) = "${userRoot(uid)}/streak_history"
}