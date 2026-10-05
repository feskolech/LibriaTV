package ru.feskolech.libriatv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.domain.Release

@Composable
fun PosterCard(
    release: Release,
    badge: String?,
    timeBadge: String? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
) {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(142.dp).scale(if (focused) 1.1f else 1f)) {
        Box(
            Modifier.width(142.dp).height(213.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF303030))
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() else longPressed = false }
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    when {
                        key.keyCode == KeyEvent.KEYCODE_MENU && key.action == KeyEvent.ACTION_DOWN &&
                            key.repeatCount == 0 -> {
                            onLongClick(); true
                        }
                        (key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == KeyEvent.KEYCODE_ENTER) &&
                            key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 -> {
                            longPressed = false; false
                        }
                        (key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == KeyEvent.KEYCODE_ENTER) &&
                            key.action == KeyEvent.ACTION_DOWN && key.repeatCount > 0 -> {
                            if (!longPressed) { longPressed = true; onLongClick() }
                            true
                        }
                        (key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == KeyEvent.KEYCODE_ENTER) &&
                            key.action == KeyEvent.ACTION_UP && longPressed -> {
                            longPressed = false; true
                        }
                        else -> false
                    }
                }
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = release.posterUrl,
                contentDescription = release.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (focused) Box(Modifier.fillMaxSize().border(3.dp, Color.White, RoundedCornerShape(9.dp)))
            if (badge != null) {
                Text(badge, modifier = Modifier.align(Alignment.BottomStart)
                    .padding(7.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(0xE6B32121)).padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (isFavorite) Text("♥", modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = Color(0xFFFF5555), fontSize = 20.sp)
        }
        Text(release.title, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color.White,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (timeBadge != null) Text(timeBadge, color = Color(0xFFBDBDBD), fontSize = 11.sp)
    }
}
