package fit.vcare.apps.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fit.vcare.apps.ChatScreenTracker
import fit.vcare.apps.MainActivity
import fit.vcare.apps.R
import fit.vcare.apps.data.mapper.unwrapDocument
import fit.vcare.apps.login_system.getUid
import fit.vcare.apps.tools.FirestoreApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class VCareFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            registerFcmToken(applicationContext, token)
        }
    }



    private fun showChatNotification(
        conversationId: String,
        partnerUid: String,
        partnerName: String,
        preview: String
    ) {
        val channelId = "chat_messages"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "پیام‌های چت", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان پیام‌های جدید چت با پارتنر"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deeplink_conversationId", conversationId)
            putExtra("deeplink_partnerUid", partnerUid)
            putExtra("deeplink_partnerName", partnerName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // آیکون کوچیک مناسب اضافه کن
            .setContentTitle(partnerName)
            .setContentText(preview)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(conversationId.hashCode(), notification)
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val conversationId = data["conversationId"] ?: return
        val partnerUid = data["senderUid"] ?: ""
        val partnerName = data["senderName"] ?: "پیام جدید"
        val preview = data["preview"] ?: "پیام جدید دریافت شد"

        if (ChatScreenTracker.isConversationOpen(conversationId)) return
        if (ChatNotificationPrefs.isMuted(applicationContext, conversationId)) return   // ← جدید

        showChatNotification(conversationId, partnerUid, partnerName, preview)
    }
}

suspend fun registerFcmToken(context: Context, token: String) {
    val uid = getUid(context) ?: return
    val path = "users/$uid"
    val existingRaw = FirestoreApiClient.read(context, path)
    val doc = if (existingRaw != null && !existingRaw.has("error")) {
        existingRaw.unwrapDocument().let { if (it.length() > 0) it else JSONObject() }
    } else JSONObject()

    doc.put("fcmToken", token)
    FirestoreApiClient.write(context, path, doc)
}

/** ذخیره محلی (فقط همین دستگاه) اینکه کدوم چت‌ها بی‌صدا شدن — نوتیف FCM قبل از نمایش چک می‌کنه */
object ChatNotificationPrefs {

    private const val PREFS_NAME = "chat_notification_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMuted(context: Context, conversationId: String): Boolean =
        prefs(context).getBoolean("muted_$conversationId", false)

    fun setMuted(context: Context, conversationId: String, muted: Boolean) {
        prefs(context).edit().putBoolean("muted_$conversationId", muted).apply()
    }
}