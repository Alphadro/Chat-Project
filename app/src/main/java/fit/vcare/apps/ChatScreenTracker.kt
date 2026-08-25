package fit.vcare.apps
//ChatScreenTracker
/** ردیابی سراسری اینکه الان کاربر داخل کدوم چت هست — برای جلوگیری از نمایش نوتیف تکراری */
object ChatScreenTracker {
    @Volatile private var openConversationId: String? = null

    fun onChatOpened(conversationId: String) { openConversationId = conversationId }
    fun onChatClosed(conversationId: String) {
        if (openConversationId == conversationId) openConversationId = null
    }
    fun isConversationOpen(conversationId: String): Boolean = openConversationId == conversationId
}