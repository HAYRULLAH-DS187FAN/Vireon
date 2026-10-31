package com.vireon.editor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vireon.editor.ui.theme.VireonBackground
import com.vireon.editor.ui.theme.VireonBlue
import com.vireon.editor.ui.theme.VireonBlueSoft
import com.vireon.editor.ui.theme.VireonGreen
import com.vireon.editor.ui.theme.VireonMuted
import com.vireon.editor.ui.theme.VireonPurple
import com.vireon.editor.ui.theme.VireonSurface
import com.vireon.editor.ui.theme.VireonSurfaceElevated
import com.vireon.editor.ui.theme.VireonText
import com.vireon.editor.ui.theme.VireonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TimelineClip(
    val id: Long,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

private data class TimelineText(
    val id: Long,
    val value: String,
    val startMs: Long,
    val endMs: Long
)

private data class TimelineThumbnail(
    val timeMs: Long,
    val bitmap: Bitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VireonTheme {
                VireonApp()
            }
        }
    }
}

@Composable
private fun VireonApp() {
    var selectedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAudioLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideo = uri.toString()
            selectedAudioLabel = null
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedAudioLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "Ses dosyası"
        }
    }

    val openVideoPicker = {
        videoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    if (selectedVideo == null) {
        HomeScreen(onNewProject = openVideoPicker)
    } else {
        EditorScreen(
            videoUri = Uri.parse(selectedVideo),
            audioLabel = selectedAudioLabel,
            onBack = {
                selectedVideo = null
                selectedAudioLabel = null
            },
            onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
            onExport = {
                Toast.makeText(
                    context,
                    "Dışa aktarma motoru sonraki geliştirme aşamasında eklenecek.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun HomeScreen(onNewProject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VireonBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VireonMark(size = 50.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "VIREON",
                    color = VireonText,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text("Create without limits.", color = VireonMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(34.dp))
        Text(
            text = "Fikirlerini\nvideoya dönüştür.",
            color = VireonText,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Düzenle, üret ve paylaş. Kontrol tamamen sende.",
            color = VireonMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VireonSurface),
            border = BorderStroke(1.dp, VireonSurfaceElevated)
        ) {
            Column(Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(VireonSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = VireonBlue, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Yeni bir proje oluştur", color = VireonText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cihazındaki bir videoyu seçerek düzenleme alanını aç.",
                    color = VireonMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onNewProject,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VireonBlue)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Video seç", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Projelerim", color = VireonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("0 proje", color = VireonMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VireonSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = VireonMuted, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("Henüz proje yok", color = VireonText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("İlk videonu seçerek başlayabilirsin.", color = VireonMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Ücretsiz  •  Reklamsız  •  Filigransız",
            modifier = Modifier.fillMaxWidth(),
            color = VireonMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EditorScreen(
    videoUri: Uri,
    audioLabel: String?,
    onBack: () -> Unit,
    onAddAudio: () -> Unit,
    onExport: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    var selectedTool by rememberSaveable { mutableStateOf("Düzenle") }
    var clips by remember(videoUri) { mutableStateOf(emptyList<TimelineClip>()) }
    var selectedIndex by remember(videoUri) { mutableStateOf(0) }
    var currentPositionMs by remember(videoUri) { mutableStateOf(0L) }
    var sourceDurationMs by remember(videoUri) { mutableStateOf(0L) }
    var nextClipId by remember(videoUri) { mutableStateOf(2L) }
    var timelineThumbnails by remember(videoUri) { mutableStateOf(emptyList<TimelineThumbnail>()) }
    var textOverlays by remember(videoUri) { mutableStateOf(emptyList<TimelineText>()) }
    var nextTextId by remember(videoUri) { mutableStateOf(1L) }
    var showTextDialog by rememberSaveable(videoUri) { mutableStateOf(false) }
    var textDraft by rememberSaveable(videoUri) { mutableStateOf("") }

    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Read the source duration and keep the playhead synchronized with the active clip.
    LaunchedEffect(player) {
        while (sourceDurationMs <= 0L) {
            val duration = player.duration
            if (duration > 0L) {
                sourceDurationMs = duration
                clips = listOf(TimelineClip(id = 1L, startMs = 0L, endMs = duration))
                selectedIndex = 0
                break
            }
            delay(100L)
        }

        while (true) {
            val activeIndex = player.currentMediaItemIndex
            if (activeIndex in clips.indices) {
                selectedIndex = activeIndex
                currentPositionMs = player.currentPosition.coerceIn(0L, clips[activeIndex].durationMs)
            }
            delay(80L)
        }
    }

    // Extract lightweight stills off the UI thread for the clip thumbnails.
    LaunchedEffect(videoUri, sourceDurationMs) {
        if (sourceDurationMs <= 0L) return@LaunchedEffect
        timelineThumbnails = emptyList()
        val extracted = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val count = (sourceDurationMs / 1200L).toInt().coerceIn(8, 30)
                buildList {
                    for (index in 0 until count) {
                        val timeMs = (((index + 0.5) / count) * sourceDurationMs)
                            .toLong().coerceIn(0L, (sourceDurationMs - 1L).coerceAtLeast(0L))
                        val frame = retriever.getFrameAtTime(
                            timeMs * 1_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (frame != null) {
                            val thumbnail = Bitmap.createScaledBitmap(frame, 180, 100, true)
                            if (thumbnail !== frame) frame.recycle()
                            add(TimelineThumbnail(timeMs, thumbnail))
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                runCatching { retriever.release() }
            }
        }
        timelineThumbnails = extracted
    }

    fun applyClips(updatedClips: List<TimelineClip>, targetIndex: Int) {
        if (updatedClips.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(updatedClips.indices)
        clips = updatedClips
        selectedIndex = safeIndex
        currentPositionMs = 0L
        player.pause()
        val mediaItems = updatedClips.map { clip ->
            MediaItem.Builder()
                .setUri(videoUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
    }

    fun trimClipStart() {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val newStart = clip.startMs + currentPositionMs
        if (newStart >= clip.endMs - 500L) {
            Toast.makeText(context, "Klipte en az yarım saniye kalmalı.", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = clips.toMutableList()
        updated[selectedIndex] = clip.copy(startMs = newStart)
        applyClips(updated, selectedIndex)
    }

    fun trimClipEnd() {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val newEnd = clip.startMs + currentPositionMs
        if (newEnd <= clip.startMs + 500L) {
            Toast.makeText(context, "Klipte en az yarım saniye kalmalı.", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = clips.toMutableList()
        updated[selectedIndex] = clip.copy(endMs = newEnd.coerceAtMost(clip.endMs))
        applyClips(updated, selectedIndex)
    }

    fun splitClip() {
        val clip = clips.getOrNull(selectedIndex) ?: return
        val splitAt = clip.startMs + currentPositionMs
        if (splitAt < clip.startMs + 500L || splitAt > clip.endMs - 500L) {
            Toast.makeText(context, "İmleci klibin içinde, kenarlardan en az yarım saniye uzağa taşı.", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = clips.toMutableList()
        updated.removeAt(selectedIndex)
        updated.add(selectedIndex, clip.copy(endMs = splitAt))
        updated.add(selectedIndex + 1, TimelineClip(nextClipId, splitAt, clip.endMs))
        nextClipId += 1L
        applyClips(updated, selectedIndex + 1)
    }

    fun moveSelectedClip(offset: Int) {
        val targetIndex = selectedIndex + offset
        if (targetIndex !in clips.indices) return
        val updated = clips.toMutableList()
        val movingClip = updated.removeAt(selectedIndex)
        updated.add(targetIndex, movingClip)
        applyClips(updated, targetIndex)
    }

    val timelinePositionMs = clips.take(selectedIndex).sumOf { it.durationMs } + currentPositionMs
    val totalTimelineDurationMs = clips.sumOf { it.durationMs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VireonBackground)
    ) {
        EditorTopBar(onBack = onBack, onExport = onExport)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ToolRail(
                selectedTool = selectedTool,
                onToolSelected = { selectedTool = it },
                onAddAudio = onAddAudio,
                onAddText = {
                    selectedTool = "Metin"
                    if (clips.isEmpty()) {
                        Toast.makeText(context, "Önce videonun yüklenmesini bekle.", Toast.LENGTH_SHORT).show()
                    } else {
                        textDraft = ""
                        showTextDialog = true
                    }
                },
                compact = isLandscape
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                VideoPreview(
                    player = player,
                    textOverlays = textOverlays,
                    timelinePositionMs = timelinePositionMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isLandscape) 0.62f else 0.56f)
                        .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
                )
                TimelinePanel(
                    audioLabel = audioLabel,
                    clips = clips,
                    selectedIndex = selectedIndex,
                    currentPositionMs = currentPositionMs,
                    thumbnails = timelineThumbnails,
                    textOverlays = textOverlays,
                    onScrub = { index, positionMs ->
                        if (index in clips.indices) {
                            player.pause()
                            selectedIndex = index
                            currentPositionMs = positionMs.coerceIn(0L, clips[index].durationMs)
                            player.seekTo(index, currentPositionMs)
                        }
                    },
                    onSplit = ::splitClip,
                    onTrimStart = ::trimClipStart,
                    onTrimEnd = ::trimClipEnd,
                    onMoveLeft = { moveSelectedClip(-1) },
                    onMoveRight = { moveSelectedClip(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isLandscape) 0.38f else 0.44f)
                )
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Videoya yazı ekle") },
            text = {
                Column {
                    Text(
                        "Yazı, zaman çizelgesindeki imlecin bulunduğu noktadan itibaren 3 saniye görünür.",
                        color = VireonMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it.take(120) },
                        label = { Text("Yazı") },
                        placeholder = { Text("Buraya yaz...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanText = textDraft.trim()
                        if (cleanText.isNotEmpty() && totalTimelineDurationMs > 0L) {
                            val start = timelinePositionMs.coerceIn(
                                0L,
                                (totalTimelineDurationMs - 500L).coerceAtLeast(0L)
                            )
                            val end = (start + 3_000L).coerceAtMost(totalTimelineDurationMs)
                            textOverlays = textOverlays + TimelineText(nextTextId, cleanText, start, end)
                            nextTextId += 1L
                            showTextDialog = false
                            textDraft = ""
                            Toast.makeText(context, "Yazı zaman çizelgesine eklendi.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = textDraft.isNotBlank() && totalTimelineDurationMs > 0L
                ) { Text("Ekle") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTextDialog = false }) { Text("Vazgeç") }
            }
        )
    }
}
@Composable
private fun EditorTopBar(onBack: () -> Unit, onExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = VireonText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Yeni düzenleme", color = VireonText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Vireon 0.1.0", color = VireonMuted, fontSize = 10.sp)
        }
        Button(
            onClick = onExport,
            shape = RoundedCornerShape(12.dp),
            contentPadding = ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = VireonBlue)
        ) {
            Text("Dışa aktar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolRail(
    selectedTool: String,
    onToolSelected: (String) -> Unit,
    onAddAudio: () -> Unit,
    onAddText: () -> Unit,
    compact: Boolean
) {
    val tools = listOf(
        Triple("Düzenle", Icons.Default.ContentCut, { onToolSelected("Düzenle") }),
        Triple("Ses", Icons.Default.MusicNote, onAddAudio),
        Triple("Metin", Icons.Default.TextFields, onAddText),
        Triple("Efekt", Icons.Default.AutoAwesome, { onToolSelected("Efekt") }),
        Triple("Hız", Icons.Default.Speed, { onToolSelected("Hız") }),
        Triple("Renk", Icons.Default.Tune, { onToolSelected("Renk") }),
        Triple("Katman", Icons.Default.Layers, { onToolSelected("Katman") })
    )

    Column(
        modifier = Modifier
            .width(if (compact) 64.dp else 72.dp)
            .fillMaxHeight()
            .background(VireonSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tools.forEach { (label, icon, action) ->
            val selected = selectedTool == label
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) VireonSurfaceElevated else Color.Transparent)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = action, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) VireonBlue else VireonMuted,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Text(
                    text = label,
                    color = if (selected) VireonText else VireonMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPreview(
    player: ExoPlayer,
    textOverlays: List<TimelineText>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier
) {
    val activeText = textOverlays.lastOrNull {
        timelinePositionMs >= it.startMs && timelinePositionMs < it.endMs
    }
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { view -> view.player = player }
        )
        if (activeText != null) {
            Text(
                text = activeText.value,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}
@Composable
private fun TimelinePanel(
    audioLabel: String?,
    clips: List<TimelineClip>,
    selectedIndex: Int,
    currentPositionMs: Long,
    thumbnails: List<TimelineThumbnail>,
    textOverlays: List<TimelineText>,
    onScrub: (Int, Long) -> Unit,
    onSplit: () -> Unit,
    onTrimStart: () -> Unit,
    onTrimEnd: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedClip = clips.getOrNull(selectedIndex)
    val canSplit = selectedClip != null &&
        currentPositionMs >= 500L && currentPositionMs <= selectedClip.durationMs - 500L
    val canTrimStart = selectedClip != null &&
        currentPositionMs >= 500L && currentPositionMs < selectedClip.durationMs - 500L
    val canTrimEnd = selectedClip != null &&
        currentPositionMs >= 500L && currentPositionMs < selectedClip.durationMs - 500L
    val clipWidthsDp = clips.map { (it.durationMs / 1000f * 34f).coerceIn(112f, 360f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clipWidthsPx = clipWidthsDp.map { with(density) { it.dp.toPx() } }
    val timelineWidthDp = clipWidthsDp.sum().coerceAtLeast(112f)
    val totalWidthPx = clipWidthsPx.sum()

    fun scrubAt(rawX: Float) {
        if (clips.isEmpty() || totalWidthPx <= 0f) return
        var remainingX = rawX.coerceIn(0f, totalWidthPx)
        for (index in clips.indices) {
            val widthPx = clipWidthsPx[index].coerceAtLeast(1f)
            if (remainingX <= widthPx || index == clips.lastIndex) {
                val fraction = (remainingX / widthPx).coerceIn(0f, 1f)
                onScrub(index, (clips[index].durationMs * fraction).toLong())
                return
            }
            remainingX -= widthPx
        }
    }

    var playheadX = 0f
    if (selectedClip != null && selectedIndex in clipWidthsPx.indices) {
        playheadX = clipWidthsPx.take(selectedIndex).sum() +
            clipWidthsPx[selectedIndex] *
            (currentPositionMs.toFloat() / selectedClip.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .background(VireonSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zaman çizelgesi", color = VireonText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (clips.isEmpty()) "Video hazırlanıyor…" else "Klip ${selectedIndex + 1}/${clips.size}",
                color = VireonMuted,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            if (selectedClip == null) "Video süresi okunuyor"
            else "İmleç ${formatTimelineTime(currentPositionMs)}  •  Klip ${formatTimelineTime(selectedClip.durationMs)}",
            color = VireonMuted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Karelere dokun veya sürükle; imleç oraya gider.",
            color = VireonMuted,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineAction("Böl", canSplit, onSplit)
            TimelineAction("Başı kırp", canTrimStart, onTrimStart)
            TimelineAction("Sonu kırp", canTrimEnd, onTrimEnd)
            TimelineAction("←", selectedIndex > 0, onMoveLeft)
            TimelineAction("→", selectedIndex >= 0 && selectedIndex < clips.lastIndex, onMoveRight)
        }
        Spacer(Modifier.height(7.dp))
        if (clips.isEmpty()) {
            Text("Video hazırlanıyor…", color = VireonMuted, fontSize = 10.sp)
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .width(timelineWidthDp.dp)
                        .height(88.dp)
                        .pointerInput(clips, clipWidthsPx) {
                            detectTapGestures { scrubAt(it.x) }
                        }
                        .pointerInput(clips, clipWidthsPx) {
                            detectDragGestures(
                                onDragStart = { scrubAt(it.x) },
                                onDrag = { change, _ ->
                                    scrubAt(change.position.x)
                                    change.consume()
                                }
                            )
                        }
                ) {
                    Row(Modifier.fillMaxSize()) {
                        clips.forEachIndexed { index, clip ->
                            val candidates = thumbnails.filter {
                                it.timeMs >= clip.startMs && it.timeMs <= clip.endMs
                            }
                            val frames = if (candidates.isNotEmpty()) {
                                if (candidates.size <= 5) candidates else List(5) { frameIndex ->
                                    candidates[frameIndex * (candidates.lastIndex) / 4]
                                }
                            } else {
                                thumbnails.minByOrNull { kotlin.math.abs(it.timeMs - (clip.startMs + clip.endMs) / 2L) }
                                    ?.let { listOf(it) } ?: emptyList()
                            }
                            Box(
                                modifier = Modifier
                                    .width(clipWidthsDp[index].dp)
                                    .fillMaxHeight()
                                    .padding(end = 3.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .border(
                                        width = if (index == selectedIndex) 2.dp else 1.dp,
                                        color = if (index == selectedIndex) VireonBlue else VireonSurfaceElevated,
                                        shape = RoundedCornerShape(7.dp)
                                    )
                                    .background(VireonSurfaceElevated)
                            ) {
                                if (frames.isNotEmpty()) {
                                    Row(Modifier.fillMaxSize()) {
                                        frames.forEach { frame ->
                                            Image(
                                                bitmap = frame.bitmap.asImageBitmap(),
                                                contentDescription = "Video karesi",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Klip ${index + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(bottomEnd = 5.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                                Text(
                                    formatTimelineTime(clip.durationMs),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topStart = 5.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val x = playheadX.coerceIn(1f, (size.width - 1f).coerceAtLeast(1f))
                        drawLine(
                            color = Color.White,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, 5.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        TimelineTrack(
            icon = Icons.Default.MusicNote,
            label = audioLabel ?: "Müzik ekle",
            color = VireonPurple,
            modifier = Modifier.height(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        if (textOverlays.isEmpty()) {
            TimelineTrack(
                icon = Icons.Default.TextFields,
                label = "Metin eklemek için soldan Metin'e dokun",
                color = VireonGreen,
                modifier = Modifier.height(22.dp)
            )
        } else {
            textOverlays.forEach { overlay ->
                TimelineTrack(
                    icon = Icons.Default.TextFields,
                    label = overlay.value,
                    color = VireonGreen,
                    modifier = Modifier.height(22.dp)
                )
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
@Composable
private fun TimelineAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTimelineTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun TimelineTrack(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = VireonMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(color)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VireonMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(VireonBlue),
        contentAlignment = Alignment.Center
    ) {
        Text("V", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}
