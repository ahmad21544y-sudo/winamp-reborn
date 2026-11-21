package com.winamp.reborn

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

private val Bg = Color(0xFF080B0D)
private val Panel = Color(0xFF151B1F)
private val Green = Color(0xFFB7FF36)
private val Cyan = Color(0xFF25D9E8)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val hasPerm = mutableStateOf(false)

    private fun audioPerm() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun granted() =
        ContextCompat.checkSelfPermission(this, audioPerm()) == PackageManager.PERMISSION_GRANTED

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasPerm.value = granted()
        }

    private fun askPerm() {
        val list = mutableListOf(audioPerm())
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(list.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPerm.value = granted()
        if (!hasPerm.value) askPerm()

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            runCatching { mediaController = controllerFuture?.get() }
        }, ContextCompat.getMainExecutor(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Bg, surface = Panel)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    WinampScreen(
                        getController = { mediaController },
                        hasPerm = hasPerm.value,
                        onRequestPerm = { askPerm() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPerm.value = granted()
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onDestroy()
    }
}

private fun playQueue(c: MediaController, list: List<Track>, index: Int) {
    val same = c.mediaItemCount == list.size &&
        list.indices.all { c.getMediaItemAt(it).mediaId == list[it].id }
    if (!same) c.setMediaItems(list.map { it.toItem() })
    c.prepare()
    c.seekTo(index, 0L)
    c.play()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WinampScreen(
    getController: () -> MediaController?,
    hasPerm: Boolean,
    onRequestPerm: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { PlaylistStore(ctx) }
    val filters = remember { FilterPrefs(ctx) }

    var controller by remember { mutableStateOf(getController()) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val tick = revision
    var showEq by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }

    var allTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<Map<String, List<String>>>(store.load()) }
    var source by remember { mutableStateOf<String?>(null) }
    var minSec by remember { mutableIntStateOf(filters.minSec) }
    var sort by remember { mutableIntStateOf(filters.sort) }
    var hideSystem by remember { mutableStateOf(filters.hideSystem) }

    var showFilter by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<Track?>(null) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    fun savePlaylists(m: Map<String, List<String>>) {
        playlists = m
        store.save(m)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            controller = getController()
            revision++
            delay(350)
        }
    }

    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            loading = true
            allTracks = withContext(Dispatchers.IO) { loadLibrary(ctx) }
            loading = false
        }
    }

    val visible = remember(allTracks, source, playlists, minSec, sort, hideSystem) {
        val s = source
        val byId = allTracks.associateBy { it.id }
        val base: List<Track> =
            if (s == null) allTracks
            else (playlists[s] ?: emptyList()).mapNotNull { byId[it] }
        val filtered = base.filter {
            it.durationMs >= minSec * 1000L && !(hideSystem && it.isSystem)
        }
        when (sort) {
            1 -> filtered.sortedByDescending { it.added }
            2 -> filtered.sortedBy { it.artist.lowercase() }
            3 -> filtered.sortedByDescending { it.durationMs }
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    val c = controller
    val current = c?.currentMediaItem
    val title = current?.mediaMetadata?.title?.toString() ?: "WINAMP REBORN"
    val artistNow = current?.mediaMetadata?.artist?.toString() ?: ""
    val duration = (c?.duration ?: 0L).coerceAtLeast(0L)
    val position = (c?.currentPosition ?: 0L).coerceAtLeast(0L)
    val currentId = current?.mediaId

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11191D), Bg, Color(0xFF030405))))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("WINAMP", color = Green, fontSize = 27.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                Text("REBORN  •  ANDROID PLAYER", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            Text("3.0", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }

        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(Color(0xFF263238), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("W", color = Green, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (artistNow.isNotBlank()) artistNow else if ((c?.mediaItemCount ?: 0) == 0) "اختر أغنية للبدء" else "${(c?.currentMediaItemIndex ?: 0) + 1} / ${c?.mediaItemCount ?: 0}",
                        color = Green, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(if (c?.isPlaying == true) "PLAY" else "READY", color = Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (duration > 0) c?.seekTo((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green, inactiveTrackColor = Color(0xFF39464B))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(formatTime(duration), color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shuffle = !shuffle; c?.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Green else Color.Gray) }
                IconButton(onClick = { c?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                FilledIconButton(
                    onClick = { if (c?.isPlaying == true) c.pause() else c?.play() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Green, contentColor = Color.Black),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(if (c?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { c?.seekToNext() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { repeat = !repeat; c?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Green else Color.Gray) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showFilter = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)) {
                Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(6.dp)); Text("فلاتر")
            }
            OutlinedButton(onClick = { pendingTrack = null; showNew = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("قائمة")
            }
            OutlinedButton(onClick = { showEq = !showEq }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                Icon(Icons.Default.Equalizer, null); Spacer(Modifier.width(4.dp)); Text("EQ")
            }
        }

        if (showEq) EqualizerPanel()

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceChip("الكل", source == null, onClick = { source = null }, onLongClick = null)
            playlists.keys.forEach { name ->
                SourceChip(name, source == name, onClick = { source = name }, onLongClick = { deleteTarget = name })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (source == null) "LIBRARY" else "PLAYLIST", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("${visible.size} TRACKS", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Column(Modifier.weight(1f).fillMaxWidth().background(Color(0xCC0D1215), RoundedCornerShape(8.dp))) {
            when {
                !hasPerm -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("نحتاج إذن الوصول إلى الموسيقى", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRequestPerm, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("منح الإذن") }
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("لا توجد أغانٍ", color = Color.Gray, fontSize = 13.sp)
                        Text("جرّب تخفيف الفلاتر", color = Color.DarkGray, fontSize = 10.sp)
                    }
                }
                else -> LazyColumn {
                    itemsIndexed(visible) { index, t ->
                        val active = t.id == currentId
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (active) Color(0xFF26352B) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { c?.let { playQueue(it, visible, index) } },
                                    onLongClick = { actionTrack = t }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(String.format(Locale.US, "%02d", index + 1), color = if (active) Green else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = if (active) Green else Color(0xFFD4DADD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (t.artist.isNotBlank()) Text(t.artist, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(formatTime(t.durationMs), color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        HorizontalDivider(color = Color(0xFF20292D), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    // Long-press actions on a track
    actionTrack?.let { t ->
        AlertDialog(
            onDismissRequest = { actionTrack = null },
            title = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { pendingTrack = t; actionTrack = null }) { Text("إضافة إلى قائمة تشغيل") }
                    val s = source
                    if (s != null) {
                        TextButton(onClick = {
                            val m = playlists.toMutableMap()
                            m[s] = (m[s] ?: emptyList()).filter { it != t.id }
                            savePlaylists(m)
                            actionTrack = null
                        }) { Text("إزالة من هذه القائمة") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionTrack = null }) { Text("إغلاق") } }
        )
    }

    // Choose a playlist for the pending track
    pendingTrack?.let { t ->
        if (!showNew) {
            AlertDialog(
                onDismissRequest = { pendingTrack = null },
                title = { Text("اختر قائمة") },
                text = {
                    Column {
                        if (playlists.isEmpty()) Text("لا توجد قوائم بعد", color = Color.Gray)
                        playlists.keys.forEach { name ->
                            TextButton(onClick = {
                                val m = playlists.toMutableMap()
                                val cur = m[name] ?: emptyList()
                                if (t.id !in cur) m[name] = cur + t.id
                                savePlaylists(m)
                                pendingTrack = null
                            }) { Text(name) }
                        }
                        TextButton(onClick = { showNew = true }) { Text("+ قائمة جديدة") }
                    }
                },
                confirmButton = { TextButton(onClick = { pendingTrack = null }) { Text("إلغاء") } }
            )
        }
    }

    // New playlist
    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false; pendingTrack = null },
            title = { Text("قائمة جديدة") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("الاسم") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        val m = playlists.toMutableMap()
                        val cur = m[n] ?: emptyList()
                        val t = pendingTrack
                        m[n] = if (t != null && t.id !in cur) cur + t.id else cur
                        savePlaylists(m)
                    }
                    showNew = false
                    pendingTrack = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showNew = false; pendingTrack = null }) { Text("إلغاء") } }
        )
    }

    // Delete playlist
    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف القائمة؟") },
            text = { Text(name) },
            confirmButton = {
                TextButton(onClick = {
                    val m = playlists.toMutableMap()
                    m.remove(name)
                    savePlaylists(m)
                    if (source == name) source = null
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    // Filters
    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("الفلاتر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("أقل مدة للأغنية", color = Cyan, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "الكل", 30 to "30ث", 60 to "1د", 120 to "2د").forEach { (v, label) ->
                            SourceChip(label, minSec == v, onClick = { minSec = v; filters.minSec = v }, onLongClick = null)
                        }
                    }
                    Text("الترتيب", color = Cyan, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "الاسم", 1 to "الأحدث", 2 to "الفنان", 3 to "المدة").forEach { (v, label) ->
                            SourceChip(label, sort == v, onClick = { sort = v; filters.sort = v }, onLongClick = null)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("إخفاء النغمات والتنبيهات", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = hideSystem, onCheckedChange = { hideSystem = it; filters.hideSystem = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilter = false }) { Text("تم") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        Modifier
            .background(if (selected) Green else Color(0xFF20292D), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Color.Black else Color.LightGray, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun EqualizerPanel() {
    var enabled by remember { mutableStateOf(false) }
    val bands = remember { mutableStateListOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF11191D), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("5-BAND EQUALIZER", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Text("المعادل شكلي حالياً، تأثيرات الصوت لم تُفعّل بعد", color = Color.Gray, fontSize = 9.sp)
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
