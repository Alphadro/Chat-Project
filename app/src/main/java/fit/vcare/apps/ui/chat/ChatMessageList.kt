package fit.vcare.apps.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fit.vcare.apps.domain.model.Message
import fit.vcare.apps.viewmodel.ChatUiState
import kotlinx.coroutines.CoroutineScope
//ChatMessageList.kt
@Composable
fun ChatMessageList(
    modifier: Modifier = Modifier,
    uiState: ChatUiState,
    reversedEntries: List<ChatListEntry>,
    listState: LazyListState,
    highlightedMessageId: String?,
    myBubbleColor: Color,
    isDarkTheme: Boolean,
    coroutineScope: CoroutineScope,
    onReply: (Message) -> Unit,
    onLongPress: (Message) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onToggleAudio: (Message) -> Unit,
    onDownloadFile: (String, String) -> Unit,
    onSaveImage: (String) -> Unit,
    partnerName: String,
    replyThresholdPx: Float,
    onRespondToProposal: (Message, Boolean) -> Unit
) {
    Box(modifier = modifier) {
        val bgUri = uiState.backgroundUri
        if (!bgUri.isNullOrBlank()) {
            AsyncImage(
                model = bgUri,
                contentDescription = "پس‌زمینه چت",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDarkTheme) 0.35f else 0.15f))
            )
        }

        when {
            uiState.isLoading && uiState.messages.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            uiState.messages.isEmpty() ->
                Text("هنوز پیامی ارسال نشده", Modifier.align(Alignment.Center))

            else -> LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                items(
                    items = reversedEntries,
                    key = { entry ->
                        when (entry) {
                            is ChatListEntry.MessageEntry -> entry.message.messageId
                            is ChatListEntry.DateHeaderEntry -> "date_${entry.dateKey}"
                        }
                    }
                ) { entry ->
                    when (entry) {
                        is ChatListEntry.DateHeaderEntry -> {
                            DateHeaderItem(entry.label)
                        }
                        is ChatListEntry.MessageEntry -> {
                            val message = entry.message
                            MessageBubbleRow(
                                message = message,
                                isMine = message.senderId == uiState.currentUid,
                                isPending = message.status == fit.vcare.apps.domain.model.MessageStatus.PENDING,
                                isHighlighted = message.messageId == highlightedMessageId,
                                myBubbleColor = myBubbleColor,
                                currentUid = uiState.currentUid,
                                partnerName = partnerName,
                                partnerLastReadAt = uiState.partnerLastReadAt,
                                replyThresholdPx = replyThresholdPx,
                                reversedEntries = reversedEntries,
                                coroutineScope = coroutineScope,
                                listState = listState,
                                onReply = onReply,
                                onLongPress = onLongPress,
                                onImageClick = onImageClick,
                                onVideoClick = onVideoClick,
                                onToggleAudio = onToggleAudio,
                                onDownloadFile = onDownloadFile,
                                onSaveImage = onSaveImage,
                                onRespondToProposal = onRespondToProposal
                            )
                        }
                    }
                }
                item {
                    if (uiState.isLoadingOlderMessages) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeaderItem(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
