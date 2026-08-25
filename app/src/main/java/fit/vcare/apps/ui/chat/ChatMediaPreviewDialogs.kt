package fit.vcare.apps.ui.chat


import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

//ChatMediaPreviewDialogs.kt
@Composable
fun PendingVideoPreviewDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var videoCaption by remember(videoUri) { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VideoPreviewPlayer(uri = videoUri)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = videoCaption,
                    onValueChange = { videoCaption = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                    placeholder = { Text("افزودن توضیح...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onSend(videoCaption) }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun PendingFilePreviewDialog(
    fileName: String,
    fileSize: Long,
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var fileCaption by remember(fileName) { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(fileName, maxLines = 1)
                    Text(formatFileSize(fileSize), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fileCaption,
                    onValueChange = { fileCaption = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("افزودن توضیح...") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "لغو")
                }
                IconButton(onClick = { onSend(fileCaption) }) {
                    Icon(Icons.Filled.Send, contentDescription = "ارسال")
                }
            }
        }
    }
}

@Composable
fun PendingAudioPreviewDialog(
    durationMs: Long,
    captionKey: String,
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var audioCaption by remember(captionKey) { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("پیام صوتی — ${formatDuration(durationMs)}")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = audioCaption,
                    onValueChange = { audioCaption = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("افزودن توضیح...") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "لغو")
                }
                IconButton(onClick = { onSend(audioCaption) }) {
                    Icon(Icons.Filled.Send, contentDescription = "ارسال")
                }
            }
        }
    }
}
@Composable
fun PendingImagePreviewDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    var imageCaption by remember(imageUri) { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                coil.compose.AsyncImage(
                    model = imageUri,
                    contentDescription = "پیش‌نمایش عکس",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = imageCaption,
                    onValueChange = { imageCaption = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                    placeholder = { Text("افزودن توضیح...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onSend(imageCaption) }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}