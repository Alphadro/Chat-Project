package fit.vcare.apps.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
//ChatDialogs.kt
@Composable
fun MicPermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسترسی میکروفون لازم است") },
        text = { Text("برای ارسال پیام صوتی، لطفاً دسترسی میکروفون را از تنظیمات اپ فعال کنید.") },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("باز کردن تنظیمات") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بعداً") }
        }
    )
}

@Composable
fun ClearHistoryConfirmDialog(
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isClearing) onDismiss() },
        title = { Text("پاک کردن تاریخچه") },
        text = { Text("آیا مطمئنید؟ تمام پیام‌های این چت پاک می‌شوند اما خود چت باقی می‌ماند.") },
        confirmButton = {
            TextButton(enabled = !isClearing, onClick = onConfirm) {
                Text("پاک کردن", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !isClearing, onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun DeleteChatConfirmDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("حذف چت") },
        text = { Text("آیا مطمئنید؟ این چت به همراه تمام پیام‌ها برای شما حذف می‌شود.") },
        confirmButton = {
            TextButton(enabled = !isDeleting, onClick = onConfirm) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !isDeleting, onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun BlockPartnerConfirmDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مسدود کردن پارتنر") },
        text = { Text("بعد از مسدود کردن، $partnerName دیگر نمی‌تواند برای شما پیام بفرستد یا دوباره متصل شود. مطمئنید؟") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("مسدود کن", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun UnmatchConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پایان رابطه") },
        text = { Text("این رابطه به‌طور کامل پایان می‌یابد و برای هر دو طرف غیرفعال می‌شود. مطمئنید؟") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("پایان بده", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ReportPartnerDialog(
    reportReason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش پارتنر") },
        text = {
            OutlinedTextField(
                value = reportReason,
                onValueChange = onReasonChange,
                placeholder = { Text("دلیل گزارش را بنویسید...") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = reportReason.isNotBlank(), onClick = onConfirm) { Text("ارسال گزارش") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun ChatLoadingOverlay(message: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message)
        }
    }
}