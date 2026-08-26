package fit.vcare.apps.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
//EmojiPickerPanel.kt
@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
            ScrollableTabRow(
                selectedTabIndex = selectedCategory,
                edgePadding = 8.dp
            ) {
                EmojiData.categories.forEachIndexed { index, category ->
                    Tab(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        text = { Text(category.icon, fontSize = 20.sp) }
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(EmojiData.categories[selectedCategory].emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }

@Composable
fun EmojiOrKeyboardSpacer(
    showEmojiPicker: Boolean,
    onEmojiSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val imeHeightPx = WindowInsets.ime.getBottom(density)
    var lastKeyboardHeightPx by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(imeHeightPx) {
        if (imeHeightPx > 0) lastKeyboardHeightPx = imeHeightPx
    }

    if (showEmojiPicker) {
        val minPanelPx = with(density) { 280.dp.roundToPx() }
        val panelHeightPx = maxOf(lastKeyboardHeightPx, minPanelPx)
        val panelHeightDp = with(density) { panelHeightPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeightDp)
        ) {
            EmojiPickerPanel(onEmojiSelected = onEmojiSelected)
        }
    } else {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        )
    }
}