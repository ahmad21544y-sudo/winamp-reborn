package com.winamp.reborn

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.concurrent.Executors

private val Bg = Color(0xFF080B0D)
private val Panel = Color(0xFF151B1F)
private val Green = Color(0xFFB7FF36)
private val Cyan = Color(0xFF25D9E8)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).setListener(object : MediaController.Listener {}).buildAsync()
        controllerFuture?.addListener({
            runCatching { mediaController = controllerFuture?.get() }
        }, Executors.newSingleThreadExecutor())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Bg, surface = Panel)) {
                WinampScreen(
                    getController = { mediaController },
                    onPickFiles = { uris ->
                        val c = mediaController ?: return@WinampScreen
                        val items = uris.map { uri -> MediaItem.fromUri(uri) }
                        c.addMediaItems(items)
                        if (c.mediaItemCount > 0 && !c.isPlaying && c.playbackState == Player.STATE_IDLE) c.prepare()
                        if (c.mediaItemCount > 0 && c.currentMediaItem == null) c.seekToDefaultPosition(0)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
private fun WinampScreen(getController: () -> MediaController?, onPickFiles: (List<Uri>) -> Unit) {
    var controller by remember { mutableStateOf(getController()) }
    var revision by remember { mutableIntStateOf(0) }
    var showEq by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPickFiles(uris)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            controller = getController()
            revision++
            delay(350)
        }
    }
    val c = controller
    val current = c?.currentMediaItem
    val title = current?.mediaMetadata?.title?.toString()
        ?: current?.localConfiguration?.uri?.lastPathSegment ?: "WINAMP REBORN"
    val duration = (c?.duration ?: 0L).coerceAtLeast(0L)
    val position = (c?.currentPosition ?: 0L).coerceAtLeast(0L)
    val playlist = (0 until (c?.mediaItemCount ?: 0)).mapNotNull { index ->
        c?.getMediaItemAt(index)?.let { it.mediaMetadata.title?.toString() ?: it.localConfiguration?.uri?.lastPathSegment ?: "Track ${index + 1}" }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF11191D), Bg, Color(0xFF030405)))).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("WINAMP", color = Green, fontSize = 27.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                Text("REBORN  •  ANDROID PLAYER", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Text("2.0", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(Color(0xFF263238), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("W", color = Green, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if ((c?.mediaItemCount ?: 0) == 0) "افتح ملفات الموسيقى للبدء" else "${(c?.currentMediaItemIndex ?: 0) + 1} / ${c?.mediaItemCount ?: 0}", color = Green, fontSize = 11.sp)
                }
                Text(if (c?.isPlaying == true) "PLAY" else "READY", color = Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(5.dp), color = Green, trackColor = Color(0xFF39464B)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(formatTime(duration), color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shuffle = !shuffle; c?.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Green else Color.Gray) }
                IconButton(onClick = { c?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                FilledIconButton(onClick = { if (c?.isPlaying == true) c.pause() else c?.play() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black), modifier = Modifier.size(58.dp)) {
                    Icon(if (c?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { c?.seekToNext() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { repeat = !repeat; c?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Green else Color.Gray) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("إضافة موسيقى")
            }
            OutlinedButton(onClick = { showEq = !showEq }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                Icon(Icons.Default.Equalizer, null); Spacer(Modifier.width(4.dp)); Text("EQ")
            }
        }

        if (showEq) EqualizerPanel()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PLAYLIST", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("${playlist.size} TRACKS", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.weight(1f).fillMaxWidth().background(Color(0xCC0D1215), RoundedCornerShape(8.dp))) {
            if (playlist.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Text("قائمة التشغيل فارغة", color = Color.Gray, fontSize = 13.sp)
                        Text("اضغط «إضافة موسيقى» لاختيار ملفاتك", color = Color.DarkGray, fontSize = 10.sp)
                    }
                }
            } else LazyColumn {
                itemsIndexed(playlist) { index, item ->
                    val active = index == (c?.currentMediaItemIndex ?: -1)
                    Row(Modifier.fillMaxWidth().clickable { c?.seekTo(index, 0); c?.play() }.background(if (active) Color(0xFF26352B) else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(Locale.US, "%02d", index + 1), color = if (active) Green else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(item, color = if (active) Green else Color(0xFFD4DADD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = Color(0xFF20292D), thickness = 0.5.dp)
                }
            }
        }
        Text("CLASSIC SPIRIT • BUILT ANEW", color = Color(0xFF526168), fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun EqualizerPanel() {
    var enabled by remember { mutableStateOf(false) }
    val bands = remember { mutableStateListOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF11191D), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("10-BAND EQUALIZER", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Text(if (enabled) "واجهة التحكم جاهزة — ربط المعالجة الصوتية الفعلية في المرحلة التالية" else "المعادل معروض؛ تأثيرات الصوت لم تُفعّل بعد", color = Color.Gray, fontSize = 9.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("60", "230", "910", "3.6k", "14k").forEachIndexed { i, label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Slider(value = bands[i], onValueChange = { bands[i] = it }, valueRange = -1f..1f, enabled = enabled, modifier = Modifier.height(100.dp))
                    Text(label, color = Color.LightGray, fontSize = 8.sp)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}
