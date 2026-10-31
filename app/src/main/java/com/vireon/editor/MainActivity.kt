package com.vireon.editor

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class VisualKind { VIDEO, IMAGE }

private data class VisualClip(
    val id: Long,
    val uri: String,
    val name: String,
    val kind: VisualKind,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = 0L,
    val imageDurationMs: Long = 3_000L,
    val thumbnail: Bitmap? = null
) {
    val durationMs: Long
        get() = if (kind == VisualKind.VIDEO) {
            (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
        } else imageDurationMs.coerceAtLeast(250L)
}

private data class AudioClip(
    val id: Long,
    val uri: String,
    val name: String,
    val timelineStartMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val volume: Float = 1f,
    val sourceDurationMs: Long = sourceEndMs
) {
    val durationMs: Long
        get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
    val timelineEndMs: Long
        get() = timelineStartMs + durationMs
}

private data class TextOverlay(
    val id: Long,
    val value: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.72f,
    val sizeSp: Float = 26f,
    val rotationDegrees: Float = 0f
)

private data class LocatedClip(val index: Int, val localMs: Long, val timelineStartMs: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VireonTheme { VireonApp() } }
    }
}

@Composable
private fun VireonApp() {
    val context = LocalContext.current
    var initialVideoUris by remember { mutableStateOf<List<String>?>(null) }
    val projectPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) initialVideoUris = uris.map(Uri::toString)
    }

    if (initialVideoUris == null) {
        HomeScreen {
            projectPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
    } else {
        EditorScreen(
            initialVideoUris = initialVideoUris.orEmpty(),
            onBack = { initialVideoUris = null },
            onExport = {
                Toast.makeText(
                    context,
                    "Düzenlemeler hazır. Video dışa aktarma motoru sonraki aşamada bağlanacak.",
                    Toast.LENGTH_LONG
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
            VireonMark(50.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("VIREON", color = VireonText, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("Create without limits.", color = VireonMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("Fikirlerini\nvideoya dönüştür.", color = VireonText, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Düzenle, üret ve paylaş. Kontrol tamamen sende.", color = VireonMuted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VireonSurface),
            border = BorderStroke(1.dp, VireonSurfaceElevated)
        ) {
            Column(Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(VireonSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, contentDescription = null, tint = VireonBlue, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(16.dp))
                Text("Yeni bir proje oluştur", color = VireonText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Birden fazla videoyu aynı projeye ekleyerek başlayabilirsin.", color = VireonMuted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onNewProject,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VireonBlue)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Videoları seç", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VireonSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = VireonMuted, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Projelerim", color = VireonText, fontWeight = FontWeight.SemiBold)
                Text("Proje kaydetme ve yeniden açma sonraki aşamada.", color = VireonMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Ücretsiz  •  Reklamsız  •  Filigransız", modifier = Modifier.fillMaxWidth(), color = VireonMuted, fontSize = 12.sp)
    }
}

@Composable
private fun EditorScreen(
    initialVideoUris: List<String>,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current

    var visualClips by remember { mutableStateOf(emptyList<VisualClip>()) }
    var audioClips by remember { mutableStateOf(emptyList<AudioClip>()) }
    var textOverlays by remember { mutableStateOf(emptyList<TextOverlay>()) }
    var playheadMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedVisualId by remember { mutableStateOf<Long?>(null) }
    var selectedAudioId by remember { mutableStateOf<Long?>(null) }
    var selectedTextId by remember { mutableStateOf<Long?>(null) }
    var nextVisualId by remember { mutableStateOf(1L) }
    var nextAudioId by remember { mutableStateOf(1L) }
    var nextTextId by remember { mutableStateOf(1L) }

    val totalDurationMs = visualClips.sumOf { it.durationMs }

    var showTextDialog by remember { mutableStateOf(false) }
    var editingTextId by remember { mutableStateOf<Long?>(null) }
    var textDraft by remember { mutableStateOf("") }

    val previewPlayer = remember { ExoPlayer.Builder(context).build() }
    val audioPlayers = remember { mutableMapOf<Long, ExoPlayer>() }
    DisposableEffect(Unit) {
        onDispose {
            previewPlayer.release()
            audioPlayers.values.forEach { runCatching { it.release() } }
            audioPlayers.clear()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    val id = nextVisualId++
                    val clip = inspectVisual(context, uri, VisualKind.VIDEO, id)
                    visualClips = visualClips + clip
                    if (selectedVisualId == null) selectedVisualId = clip.id
                }
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    val id = nextVisualId++
                    val clip = inspectVisual(context, uri, VisualKind.IMAGE, id)
                    visualClips = visualClips + clip
                    selectedVisualId = clip.id
                }
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                val info = inspectAudio(context, uri)
                val snappedStart = snapToCut(playheadMs, visualClips, 250L)
                val start = snappedStart.coerceAtMost((totalDurationMs - 250L).coerceAtLeast(0L))
                val usableDuration = minOf(info.second, (totalDurationMs - start).coerceAtLeast(0L))
                if (usableDuration >= 250L) {
                    val clip = AudioClip(
                        id = nextAudioId++,
                        uri = uri.toString(),
                        name = info.first,
                        timelineStartMs = start,
                        sourceStartMs = 0L,
                        sourceEndMs = usableDuration,
                        sourceDurationMs = info.second.coerceAtLeast(usableDuration)
                    )
                    audioClips = audioClips + clip
                    selectedAudioId = clip.id
                    selectedTextId = null
                } else {
                    Toast.makeText(context, "Ses dosyasının süresi okunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(initialVideoUris) {
        if (visualClips.isEmpty()) {
            initialVideoUris.forEach { rawUri ->
                val id = nextVisualId++
                val clip = inspectVisual(context, Uri.parse(rawUri), VisualKind.VIDEO, id)
                visualClips = visualClips + clip
                if (selectedVisualId == null) selectedVisualId = clip.id
            }
        }
    }

    val located = locateVisual(visualClips, playheadMs)
    val activeIndex = located?.index ?: -1
    val activeVisual = visualClips.getOrNull(activeIndex)
    val activeLocalMs = located?.localMs ?: 0L
    val activeTimelineStartMs = located?.timelineStartMs ?: 0L
    val selectedVisual = visualClips.firstOrNull { it.id == selectedVisualId }
    val selectedVisualIndex = visualClips.indexOfFirst { it.id == selectedVisualId }
    val selectedAudio = audioClips.firstOrNull { it.id == selectedAudioId }
    val activeTexts = textOverlays.filter { playheadMs >= it.startMs && playheadMs < it.endMs }
    val activeAudioIds = audioClips.filter { playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs }.map { it.id }.toSet()

    fun setTimelinePosition(requested: Long, snap: Boolean = true) {
        val end = totalDurationMs.coerceAtLeast(0L)
        val candidate = requested.coerceIn(0L, end)
        playheadMs = if (snap) snapToCut(candidate, visualClips, 220L) else candidate
        locateVisual(visualClips, playheadMs)?.let { location ->
            selectedVisualId = visualClips.getOrNull(location.index)?.id
        }
    }

    fun openTextEditor(existing: TextOverlay? = null) {
        editingTextId = existing?.id
        textDraft = existing?.value.orEmpty()
        showTextDialog = true
    }

    fun appendVisual(uri: Uri, kind: VisualKind) {
        scope.launch {
            val id = nextVisualId++
            val clip = inspectVisual(context, uri, kind, id)
            visualClips = visualClips + clip
            selectedVisualId = clip.id
            selectedAudioId = null
            selectedTextId = null
            Toast.makeText(context, if (kind == VisualKind.VIDEO) "Video ana sıraya eklendi." else "Görsel ana sıraya eklendi.", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateAudio(id: Long, transform: (AudioClip) -> AudioClip) {
        audioClips = audioClips.map { if (it.id == id) transform(it) else it }
    }

    fun updateText(id: Long, transform: (TextOverlay) -> TextOverlay) {
        textOverlays = textOverlays.map { if (it.id == id) transform(it) else it }
    }

    LaunchedEffect(activeVisual?.id) {
        val clip = activeVisual
        if (clip != null && clip.kind == VisualKind.VIDEO) {
            previewPlayer.pause()
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
            previewPlayer.prepare()
            previewPlayer.seekTo((clip.sourceStartMs + activeLocalMs).coerceAtLeast(0L))
        } else {
            previewPlayer.pause()
        }
    }

    LaunchedEffect(playheadMs, activeVisual?.id, isPlaying) {
        if (!isPlaying && activeVisual != null && activeVisual.kind == VisualKind.VIDEO) {
            previewPlayer.seekTo((activeVisual.sourceStartMs + activeLocalMs).coerceIn(activeVisual.sourceStartMs, activeVisual.sourceEndMs))
        }
    }

    LaunchedEffect(audioClips) {
        val activeIds = audioClips.map { it.id }.toSet()
        val removedIds = audioPlayers.keys.filter { it !in activeIds }
        removedIds.forEach { id ->
            audioPlayers.remove(id)?.let { runCatching { it.release() } }
        }
    }

    fun syncAudioPlayers(positionMs: Long, shouldPlay: Boolean) {
        audioClips.forEach { clip ->
            val isActive = positionMs >= clip.timelineStartMs && positionMs < clip.timelineEndMs
            val player = if (isActive) audioPlayers.getOrPut(clip.id) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
                    prepare()
                }
            } else audioPlayers[clip.id]
            if (player != null) {
                player.volume = clip.volume.coerceIn(0f, 1f)
                if (isActive) {
                    val expectedSourceMs = clip.sourceStartMs + (positionMs - clip.timelineStartMs)
                    if (abs(player.currentPosition - expectedSourceMs) > 240L) player.seekTo(expectedSourceMs)
                    if (shouldPlay) player.play() else player.pause()
                } else {
                    player.pause()
                }
            }
        }
    }

    LaunchedEffect(playheadMs, isPlaying, audioClips) {
        if (!isPlaying) syncAudioPlayers(playheadMs, false)
    }

    LaunchedEffect(isPlaying, activeVisual?.id, visualClips, audioClips) {
        if (!isPlaying) {
            previewPlayer.pause()
            syncAudioPlayers(playheadMs, false)
            return@LaunchedEffect
        }
        while (isPlaying) {
            val clip = activeVisual
            val clipIndex = activeIndex
            if (clip == null || clipIndex !in visualClips.indices) {
                isPlaying = false
                break
            }
            syncAudioPlayers(playheadMs, true)
            if (clip.kind == VisualKind.VIDEO) {
                if (previewPlayer.currentMediaItem == null) {
                    previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uri)))
                    previewPlayer.prepare()
                    previewPlayer.seekTo(clip.sourceStartMs + activeLocalMs)
                }
                previewPlayer.play()
                delay(45L)
                val sourcePosition = previewPlayer.currentPosition.coerceAtLeast(clip.sourceStartMs)
                val local = (sourcePosition - clip.sourceStartMs).coerceAtLeast(0L)
                if (local >= clip.durationMs - 70L || previewPlayer.playbackState == Player.STATE_ENDED) {
                    val nextIndex = clipIndex + 1
                    if (nextIndex < visualClips.size) {
                        setTimelinePosition(visualClips.take(nextIndex).sumOf { it.durationMs }, false)
                    } else {
                        playheadMs = totalDurationMs
                        isPlaying = false
                    }
                } else {
                    playheadMs = activeTimelineStartMs + local
                }
            } else {
                previewPlayer.pause()
                delay(45L)
                val nextPosition = playheadMs + 45L
                if (nextPosition >= activeTimelineStartMs + clip.durationMs) {
                    val nextIndex = clipIndex + 1
                    if (nextIndex < visualClips.size) {
                        setTimelinePosition(visualClips.take(nextIndex).sumOf { it.durationMs }, false)
                    } else {
                        playheadMs = totalDurationMs
                        isPlaying = false
                    }
                } else {
                    playheadMs = nextPosition
                }
            }
        }
    }

    fun trimVisualStart() {
        val index = visualClips.indexOfFirst { it.id == selectedVisualId }
        if (index < 0) return
        val clip = visualClips[index]
        val location = locateVisual(visualClips, playheadMs)
        val local = if (location?.index == index) location.localMs else 0L
        if (clip.durationMs - local < 500L) {
            Toast.makeText(context, "Klipte en az yarım saniye kalmalı.", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = visualClips.toMutableList()
        updated[index] = if (clip.kind == VisualKind.VIDEO) {
            clip.copy(sourceStartMs = (clip.sourceStartMs + local).coerceAtMost(clip.sourceEndMs - 500L))
        } else {
            clip.copy(imageDurationMs = clip.durationMs - local)
        }
        visualClips = updated
        playheadMs = visualClips.take(index).sumOf { it.durationMs }
    }

    fun trimVisualEnd() {
        val index = visualClips.indexOfFirst { it.id == selectedVisualId }
        if (index < 0) return
        val clip = visualClips[index]
        val location = locateVisual(visualClips, playheadMs)
        val local = if (location?.index == index) location.localMs else clip.durationMs
        if (local < 500L) {
            Toast.makeText(context, "Klipte en az yarım saniye kalmalı.", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = visualClips.toMutableList()
        updated[index] = if (clip.kind == VisualKind.VIDEO) {
            clip.copy(sourceEndMs = (clip.sourceStartMs + local).coerceAtLeast(clip.sourceStartMs + 500L))
        } else {
            clip.copy(imageDurationMs = local.coerceAtLeast(500L))
        }
        visualClips = updated
        playheadMs = (visualClips.take(index).sumOf { it.durationMs } + updated[index].durationMs).coerceAtMost(totalDurationMs)
    }

    fun splitVisual() {
        val index = visualClips.indexOfFirst { it.id == selectedVisualId }
        if (index < 0) return
        val clip = visualClips[index]
        val location = locateVisual(visualClips, playheadMs)
        if (location?.index != index) {
            Toast.makeText(context, "İmleci bölmek istediğin klibin içine taşı.", Toast.LENGTH_SHORT).show()
            return
        }
        val local = location.localMs
        if (local < 500L || local > clip.durationMs - 500L) {
            Toast.makeText(context, "Bölme noktası klibin kenarlarından en az yarım saniye içeride olmalı.", Toast.LENGTH_SHORT).show()
            return
        }
        val left: VisualClip
        val right: VisualClip
        if (clip.kind == VisualKind.VIDEO) {
            val splitSourceMs = clip.sourceStartMs + local
            left = clip.copy(sourceEndMs = splitSourceMs)
            right = clip.copy(id = nextVisualId++, sourceStartMs = splitSourceMs)
        } else {
            left = clip.copy(imageDurationMs = local)
            right = clip.copy(id = nextVisualId++, imageDurationMs = clip.durationMs - local)
        }
        val updated = visualClips.toMutableList()
        updated.removeAt(index)
        updated.add(index, left)
        updated.add(index + 1, right)
        visualClips = updated
        selectedVisualId = right.id
        playheadMs = updated.take(index + 1).sumOf { it.durationMs }
    }

    fun moveVisual(offset: Int) {
        val index = visualClips.indexOfFirst { it.id == selectedVisualId }
        val target = index + offset
        if (index !in visualClips.indices || target !in visualClips.indices) return
        val updated = visualClips.toMutableList()
        val item = updated.removeAt(index)
        updated.add(target, item)
        visualClips = updated
        selectedVisualId = item.id
        playheadMs = updated.take(target).sumOf { it.durationMs }
    }

    fun moveAudio(id: Long, requestedStart: Long) {
        val clip = audioClips.firstOrNull { it.id == id } ?: return
        val maxStart = (totalDurationMs - clip.durationMs).coerceAtLeast(0L)
        val snapped = snapToCut(requestedStart.coerceIn(0L, maxStart), visualClips, 220L)
        updateAudio(id) { it.copy(timelineStartMs = snapped.coerceIn(0L, maxStart)) }
    }

    fun moveText(id: Long, requestedStart: Long) {
        val overlay = textOverlays.firstOrNull { it.id == id } ?: return
        val duration = overlay.endMs - overlay.startMs
        val maxStart = (totalDurationMs - duration).coerceAtLeast(0L)
        val snapped = snapToCut(requestedStart.coerceIn(0L, maxStart), visualClips, 220L)
        updateText(id) { it.copy(startMs = snapped, endMs = snapped + duration) }
    }

    fun resizeAudioStart(id: Long, requestedStart: Long) {
        val clip = audioClips.firstOrNull { it.id == id } ?: return
        val minimumStart = (clip.timelineStartMs - clip.sourceStartMs).coerceAtLeast(0L)
        val maximumStart = (clip.timelineEndMs - 250L).coerceAtLeast(minimumStart)
        val newStart = requestedStart.coerceIn(minimumStart, maximumStart)
        val sourceDelta = newStart - clip.timelineStartMs
        val newSourceStart = (clip.sourceStartMs + sourceDelta).coerceIn(0L, clip.sourceEndMs - 250L)
        updateAudio(id) { it.copy(timelineStartMs = newStart, sourceStartMs = newSourceStart) }
    }

    fun resizeAudioEnd(id: Long, requestedEnd: Long) {
        val clip = audioClips.firstOrNull { it.id == id } ?: return
        val minimumEnd = clip.timelineStartMs + 250L
        val mediaMaximumEnd = clip.timelineStartMs + (clip.sourceDurationMs - clip.sourceStartMs).coerceAtLeast(250L)
        val maximumEnd = minOf(totalDurationMs, mediaMaximumEnd).coerceAtLeast(minimumEnd)
        val newEnd = requestedEnd.coerceIn(minimumEnd, maximumEnd)
        val newSourceEnd = clip.sourceStartMs + (newEnd - clip.timelineStartMs)
        updateAudio(id) { it.copy(sourceEndMs = newSourceEnd) }
    }

    fun resizeTextStart(id: Long, requestedStart: Long) {
        val overlay = textOverlays.firstOrNull { it.id == id } ?: return
        val newStart = requestedStart.coerceIn(0L, (overlay.endMs - 250L).coerceAtLeast(0L))
        updateText(id) { it.copy(startMs = newStart) }
    }

    fun resizeTextEnd(id: Long, requestedEnd: Long) {
        val overlay = textOverlays.firstOrNull { it.id == id } ?: return
        val newEnd = requestedEnd.coerceIn(overlay.startMs + 250L, totalDurationMs)
        updateText(id) { it.copy(endMs = newEnd) }
    }

    val previousCut = cutBoundaries(visualClips).filter { it < playheadMs }.maxOrNull()
    val nextCut = cutBoundaries(visualClips).filter { it > playheadMs }.minOrNull()

    Column(Modifier.fillMaxSize().background(VireonBackground)) {
        EditorTopBar(onBack = onBack, onExport = onExport)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ToolRail(
                compact = isLandscape,
                onAddVideo = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onAddImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                onAddText = { if (totalDurationMs > 0L) openTextEditor() else Toast.makeText(context, "Önce bir video veya görsel ekle.", Toast.LENGTH_SHORT).show() }
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                VideoPreview(
                    player = previewPlayer,
                    activeVisual = activeVisual,
                    activeTexts = activeTexts,
                    isPlaying = isPlaying,
                    onTogglePlayback = { isPlaying = !isPlaying },
                    onTransformText = { id, x, y, size, rotation ->
                        updateText(id) { it.copy(x = x, y = y, sizeSp = size, rotationDegrees = rotation) }
                    },
                    onSelectText = { id ->
                        selectedTextId = id
                        selectedAudioId = null
                        selectedVisualId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                        .weight(if (isLandscape) 0.56f else 0.46f)
                        .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 5.dp)
                )

                TimelinePanel(
                    visualClips = visualClips,
                    audioClips = audioClips,
                    textOverlays = textOverlays,
                    totalDurationMs = totalDurationMs,
                    playheadMs = playheadMs,
                    selectedVisualId = selectedVisualId,
                    selectedAudioId = selectedAudioId,
                    selectedTextId = selectedTextId,
                    onScrub = { setTimelinePosition(it) },
                    onSelectVisual = { id ->
                        selectedVisualId = id
                        selectedAudioId = null
                        selectedTextId = null
                        val index = visualClips.indexOfFirst { it.id == id }
                        if (index >= 0) setTimelinePosition(visualClips.take(index).sumOf { it.durationMs }, false)
                    },
                    onSelectAudio = { id -> selectedAudioId = id; selectedTextId = null; selectedVisualId = null },
                    onSelectText = { id ->
                        selectedTextId = id
                        selectedAudioId = null
                        selectedVisualId = null
                    },
                    onEditText = { id -> openTextEditor(textOverlays.firstOrNull { it.id == id }) },
                    onMoveAudio = ::moveAudio,
                    onMoveText = ::moveText,
                    onResizeAudioStart = ::resizeAudioStart,
                    onResizeAudioEnd = ::resizeAudioEnd,
                    onResizeTextStart = ::resizeTextStart,
                    onResizeTextEnd = ::resizeTextEnd,
                    canPreviousCut = previousCut != null,
                    canNextCut = nextCut != null,
                    onPreviousCut = { previousCut?.let { setTimelinePosition(it, false) } },
                    onNextCut = { nextCut?.let { setTimelinePosition(it, false) } },
                    onSplit = ::splitVisual,
                    onTrimVisualStart = ::trimVisualStart,
                    onTrimVisualEnd = ::trimVisualEnd,
                    onMoveVisualLeft = { moveVisual(-1) },
                    onMoveVisualRight = { moveVisual(1) },
                    onRemoveAudio = { id ->
                        audioClips = audioClips.filterNot { it.id == id }
                        selectedAudioId = null
                    },
                    onTrimAudioStart = { id -> updateAudio(id) { it.copy(sourceStartMs = (it.sourceStartMs + 500L).coerceAtMost(it.sourceEndMs - 250L)) } },
                    onTrimAudioEnd = { id -> updateAudio(id) { it.copy(sourceEndMs = (it.sourceEndMs - 500L).coerceAtLeast(it.sourceStartMs + 250L)) } },
                    onVolumeChange = { id, value -> updateAudio(id) { it.copy(volume = value) } },
                    onImageDurationChange = { id, value -> visualClips = visualClips.map { if (it.id == id && it.kind == VisualKind.IMAGE) it.copy(imageDurationMs = value.toLong()) else it } },
                    modifier = Modifier.fillMaxWidth().weight(if (isLandscape) 0.44f else 0.54f)
                )
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(if (editingTextId == null) "Yazı ekle" else "Yazıyı düzenle") },
            text = {
                Column {
                    OutlinedTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it.take(120) },
                        label = { Text("Yazı") },
                        placeholder = { Text("Videoda görünecek metin") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Konum, boyut ve eğim önizleme ekranında ayarlanır. Süreyi zaman çizelgesindeki yazı klibinden değiştirebilirsin.",
                        color = VireonMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = textDraft.trim()
                        if (clean.isNotEmpty() && totalDurationMs >= 250L) {
                            val existingId = editingTextId
                            if (existingId == null) {
                                val start = playheadMs.coerceIn(0L, (totalDurationMs - 250L).coerceAtLeast(0L))
                                val end = (start + 3_000L).coerceAtMost(totalDurationMs)
                                val overlay = TextOverlay(nextTextId++, clean, start, end)
                                textOverlays = textOverlays + overlay
                                selectedTextId = overlay.id
                                selectedAudioId = null
                                selectedVisualId = null
                            } else {
                                updateText(existingId) { it.copy(value = clean) }
                            }
                            showTextDialog = false
                        }
                    },
                    enabled = textDraft.isNotBlank() && totalDurationMs >= 250L
                ) { Text("Kaydet") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editingTextId != null) {
                        TextButton(onClick = {
                            textOverlays = textOverlays.filterNot { it.id == editingTextId }
                            selectedTextId = null
                            showTextDialog = false
                        }) { Text("Sil") }
                    }
                    TextButton(onClick = { showTextDialog = false }) { Text("Vazgeç") }
                }
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
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = VireonText) }
        Column(Modifier.weight(1f)) {
            Text("Yeni düzenleme", color = VireonText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Vireon 0.3.0", color = VireonMuted, fontSize = 10.sp)
        }
        Button(onClick = onExport, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = VireonBlue)) {
            Text("Dışa aktar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolRail(
    compact: Boolean,
    onAddVideo: () -> Unit,
    onAddImage: () -> Unit,
    onAddAudio: () -> Unit,
    onAddText: () -> Unit
) {
    val entries = listOf(
        Triple("Video +", Icons.Default.Movie, onAddVideo),
        Triple("Görsel +", Icons.Default.Image, onAddImage),
        Triple("Ses +", Icons.Default.MusicNote, onAddAudio),
        Triple("Metin", Icons.Default.TextFields, onAddText)
    )
    Column(
        modifier = Modifier.width(if (compact) 62.dp else 70.dp).fillMaxHeight()
            .background(VireonSurface).verticalScroll(rememberScrollState()).padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        entries.forEach { (label, icon, action) ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).clickable { action() }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = VireonBlue, modifier = Modifier.size(23.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, color = VireonText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VideoPreview(
    player: ExoPlayer,
    activeVisual: VisualClip?,
    activeTexts: List<TextOverlay>,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onTransformText: (Long, Float, Float, Float, Float) -> Unit,
    onSelectText: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        if (activeVisual?.kind == VisualKind.IMAGE && activeVisual.thumbnail != null) {
            ComposeImage(activeVisual.thumbnail.asImageBitmap(), contentDescription = activeVisual.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else if (activeVisual?.kind == VisualKind.VIDEO) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player }
            )
        } else {
            Text("Ana sıraya video veya görsel ekle", color = VireonMuted, modifier = Modifier.align(Alignment.Center))
        }
        activeTexts.forEach { overlay ->
            Text(
                text = overlay.value,
                color = Color.White,
                fontSize = overlay.sizeSp.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = maxWidth * (overlay.x - 0.5f), y = maxHeight * (overlay.y - 0.5f))
                    .graphicsLayer { rotationZ = overlay.rotationDegrees }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .pointerInput(overlay.id) {
                        var currentX = overlay.x
                        var currentY = overlay.y
                        var currentSize = overlay.sizeSp
                        var currentRotation = overlay.rotationDegrees
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val heightPx = size.height.toFloat().coerceAtLeast(1f)
                        detectTransformGestures { _, pan, zoomChange, rotationChange ->
                            currentX = (currentX + pan.x / widthPx).coerceIn(0.05f, 0.95f)
                            currentY = (currentY + pan.y / heightPx).coerceIn(0.05f, 0.95f)
                            currentSize = (currentSize * zoomChange).coerceIn(12f, 96f)
                            currentRotation = (currentRotation + rotationChange).coerceIn(-180f, 180f)
                            onSelectText(overlay.id)
                            onTransformText(overlay.id, currentX, currentY, currentSize, currentRotation)
                        }
                    }
            )
        }
        IconButton(
            onClick = onTogglePlayback,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(30.dp))
        ) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Duraklat" else "Oynat", tint = Color.White)
        }
        if (activeVisual != null) {
            Text(
                activeVisual.name,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun TimelinePanel(
    visualClips: List<VisualClip>,
    audioClips: List<AudioClip>,
    textOverlays: List<TextOverlay>,
    totalDurationMs: Long,
    playheadMs: Long,
    selectedVisualId: Long?,
    selectedAudioId: Long?,
    selectedTextId: Long?,
    onScrub: (Long) -> Unit,
    onSelectVisual: (Long) -> Unit,
    onSelectAudio: (Long) -> Unit,
    onSelectText: (Long) -> Unit,
    onEditText: (Long) -> Unit,
    onMoveAudio: (Long, Long) -> Unit,
    onMoveText: (Long, Long) -> Unit,
    onResizeAudioStart: (Long, Long) -> Unit,
    onResizeAudioEnd: (Long, Long) -> Unit,
    onResizeTextStart: (Long, Long) -> Unit,
    onResizeTextEnd: (Long, Long) -> Unit,
    canPreviousCut: Boolean,
    canNextCut: Boolean,
    onPreviousCut: () -> Unit,
    onNextCut: () -> Unit,
    onSplit: () -> Unit,
    onTrimVisualStart: () -> Unit,
    onTrimVisualEnd: () -> Unit,
    onMoveVisualLeft: () -> Unit,
    onMoveVisualRight: () -> Unit,
    onRemoveAudio: (Long) -> Unit,
    onTrimAudioStart: (Long) -> Unit,
    onTrimAudioEnd: (Long) -> Unit,
    onVolumeChange: (Long, Float) -> Unit,
    onImageDurationChange: (Long, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVisual = visualClips.firstOrNull { it.id == selectedVisualId }
    val selectedVisualIndex = visualClips.indexOfFirst { it.id == selectedVisualId }
    val selectedAudio = audioClips.firstOrNull { it.id == selectedAudioId }
    val selectedText = textOverlays.firstOrNull { it.id == selectedTextId }
    val location = locateVisual(visualClips, playheadMs)
    val selectedLocalMs = if (location != null && visualClips.getOrNull(location.index)?.id == selectedVisualId) location.localMs else 0L
    val canCut = selectedVisual != null && selectedLocalMs >= 500L && selectedLocalMs <= selectedVisual.durationMs - 500L
    var zoom by remember { mutableStateOf(1f) }
    val pxPerMs = timelineScale(totalDurationMs) * zoom
    val timelineWidth = (totalDurationMs.coerceAtLeast(1L) * pxPerMs).coerceAtLeast(320f * zoom).dp
    val density = LocalDensity.current
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier.background(VireonSurface).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zaman çizelgesi", color = VireonText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TimelineAction("−", zoom > 0.5f) { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) }
            TimelineAction("1×", zoom != 1f) { zoom = 1f }
            TimelineAction("+", zoom < 3f) { zoom = (zoom + 0.25f).coerceAtMost(3f) }
        }
        Text("${formatTimelineTime(playheadMs)} / ${formatTimelineTime(totalDurationMs)}", color = VireonMuted, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TimelineAction("◀ Kesit", canPreviousCut, onPreviousCut)
            TimelineAction("Kesit ▶", canNextCut, onNextCut)
            TimelineAction("Böl", canCut, onSplit)
            TimelineAction("Başı kırp", canCut, onTrimVisualStart)
            TimelineAction("Sonu kırp", canCut, onTrimVisualEnd)
            TimelineAction("←", selectedVisualIndex > 0, onMoveVisualLeft)
            TimelineAction("→", selectedVisualIndex >= 0 && selectedVisualIndex < visualClips.lastIndex, onMoveVisualRight)
        }
        if (selectedVisual?.kind == VisualKind.IMAGE) {
            Text("Görsel süresi: ${(selectedVisual.durationMs / 1000f).let { "%.1f".format(it) }} sn", color = VireonText, fontSize = 11.sp)
            Slider(
                value = selectedVisual.durationMs.toFloat().coerceIn(1_000f, 30_000f),
                onValueChange = { onImageDurationChange(selectedVisual.id, it.coerceIn(1_000f, 30_000f)) },
                valueRange = 1_000f..30_000f
            )
        }
        if (selectedAudio != null) {
            Spacer(Modifier.height(3.dp))
            Text("Ses: ${selectedAudio.name}", color = VireonText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TimelineAction("Başından 0,5 sn kes", selectedAudio.durationMs > 750L, { onTrimAudioStart(selectedAudio.id) })
                TimelineAction("Sonundan 0,5 sn kes", selectedAudio.durationMs > 750L, { onTrimAudioEnd(selectedAudio.id) })
                TimelineAction("Sesi sil", true, { onRemoveAudio(selectedAudio.id) })
            }
            Text("Ses seviyesi: ${(selectedAudio.volume * 100).roundToInt()}%", color = VireonMuted, fontSize = 10.sp)
            Slider(value = selectedAudio.volume, onValueChange = { onVolumeChange(selectedAudio.id, it) }, valueRange = 0f..1f)
        }
        if (selectedText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seçili yazı: ${selectedText.value}", color = VireonText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TimelineAction("Düzenle", true) { onEditText(selectedText.id) }
            }
            Text("${formatTimelineTime(selectedText.startMs)} – ${formatTimelineTime(selectedText.endMs)}  •  Süreyi yazı klibinin kenarlarından ayarla", color = VireonMuted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(4.dp))
        Text("ANA SIRA  •  Video ve görseller ardışık", color = VireonMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        if (visualClips.isEmpty()) {
            Text("Video veya görsel ekleyerek başla.", color = VireonMuted, fontSize = 11.sp)
        } else {
            Column(Modifier.horizontalScroll(horizontalScroll)) {
                Box(
                    modifier = Modifier.width(timelineWidth).height(20.dp)
                        .pointerInput(totalDurationMs, visualClips) {
                            detectTapGestures { point -> onScrub(timeFromX(point.x, size.width.toFloat(), totalDurationMs, visualClips)) }
                        }
                        .pointerInput(totalDurationMs, visualClips) {
                            detectDragGestures(
                                onDragStart = { point -> onScrub(timeFromX(point.x, size.width.toFloat(), totalDurationMs, visualClips)) },
                                onDrag = { change, _ ->
                                    onScrub(timeFromX(change.position.x, size.width.toFloat(), totalDurationMs, visualClips))
                                    change.consume()
                                }
                            )
                        }
                ) {
                    Text("0:00", color = VireonMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterStart))
                    Text(formatTimelineTime(totalDurationMs), color = VireonMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Box(Modifier.width(timelineWidth).height(66.dp)) {
                    Row(Modifier.fillMaxSize()) {
                        visualClips.forEachIndexed { index, clip ->
                            val clipWidth = (clip.durationMs * pxPerMs).coerceAtLeast(22f).dp
                            Box(
                                Modifier.width(clipWidth).fillMaxHeight().padding(end = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(if (clip.id == selectedVisualId) 2.dp else 1.dp, if (clip.id == selectedVisualId) VireonBlue else VireonSurfaceElevated, RoundedCornerShape(6.dp))
                                    .background(VireonSurfaceElevated)
                                    .clickable { onSelectVisual(clip.id) }
                            ) {
                                if (clip.thumbnail != null) {
                                    ComposeImage(clip.thumbnail.asImageBitmap(), contentDescription = clip.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Text(
                                    if (clip.kind == VisualKind.VIDEO) "VID ${index + 1}" else "IMG ${index + 1}",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(bottomEnd = 5.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    formatTimelineTime(clip.durationMs),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(topStart = 5.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    TimelineGuides(visualClips, totalDurationMs, playheadMs)
                }

                Spacer(Modifier.height(7.dp))
                Text("SES KATMANI  •  Bağımsız konumlandır", color = VireonMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                AudioTimelineLane(
                    clips = audioClips,
                    selectedId = selectedAudioId,
                    totalWidth = timelineWidth,
                    totalDurationMs = totalDurationMs,
                    playheadMs = playheadMs,
                    pxPerMs = pxPerMs,
                    density = density.density,
                    onSelect = onSelectAudio,
                    onMove = onMoveAudio,
                    onResizeStart = onResizeAudioStart,
                    onResizeEnd = onResizeAudioEnd
                )
                Spacer(Modifier.height(7.dp))
                Text("YAZI KATMANI  •  Bağımsız konumlandır", color = VireonMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                TextTimelineLane(
                    clips = textOverlays,
                    selectedId = selectedTextId,
                    totalWidth = timelineWidth,
                    totalDurationMs = totalDurationMs,
                    playheadMs = playheadMs,
                    pxPerMs = pxPerMs,
                    density = density.density,
                    onSelect = onSelectText,
                    onMove = onMoveText,
                    onResizeStart = onResizeTextStart,
                    onResizeEnd = onResizeTextEnd
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("İpucu: İmleci kesit çizgisine yaklaştırınca otomatik hizalanır. Kesit düğmeleri önceki/sonraki sınıra atlar.", color = VireonMuted, fontSize = 9.sp)
    }
}

@Composable
private fun AudioTimelineLane(
    clips: List<AudioClip>,
    selectedId: Long?,
    totalWidth: Dp,
    totalDurationMs: Long,
    playheadMs: Long,
    pxPerMs: Float,
    density: Float,
    onSelect: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onResizeStart: (Long, Long) -> Unit,
    onResizeEnd: (Long, Long) -> Unit
) {
    Box(Modifier.width(totalWidth).height(54.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2B2042))) {
        clips.forEach { clip ->
            var moveOffsetPx by remember(clip.id) { mutableStateOf(0f) }
            var leftResizePx by remember(clip.id) { mutableStateOf(0f) }
            var rightResizePx by remember(clip.id) { mutableStateOf(0f) }
            val x = (clip.timelineStartMs * pxPerMs).dp + (leftResizePx / density).dp
            val baseWidth = (clip.durationMs * pxPerMs).coerceAtLeast(42f)
            val visibleWidth = (baseWidth + (rightResizePx - leftResizePx) / density).coerceAtLeast(36f).dp
            Box(
                Modifier.offset(x = x).width(visibleWidth).fillMaxHeight().padding(vertical = 2.dp, horizontal = 1.dp)
                    .graphicsLayer { translationX = moveOffsetPx }
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (clip.id == selectedId) 2.dp else 1.dp, if (clip.id == selectedId) VireonBlue else VireonPurple, RoundedCornerShape(6.dp))
                    .background(VireonPurple.copy(alpha = 0.82f))
                    .clickable { onSelect(clip.id) }
                    .pointerInput(clip.id, clip.timelineStartMs, clip.sourceStartMs, clip.sourceEndMs, pxPerMs, density) {
                        var dragPx = 0f
                        detectDragGestures(
                            onDragStart = { dragPx = 0f; onSelect(clip.id) },
                            onDragEnd = {
                                val deltaMs = (dragPx / density / pxPerMs).roundToLong()
                                onMove(clip.id, clip.timelineStartMs + deltaMs)
                                moveOffsetPx = 0f
                            },
                            onDragCancel = { moveOffsetPx = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragPx += amount.x
                                moveOffsetPx = dragPx
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(clip.name, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.align(Alignment.CenterStart).width(12.dp).fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.16f))
                        .pointerInput(clip.id, clip.timelineStartMs, clip.sourceStartMs, clip.sourceEndMs, pxPerMs, density) {
                            detectDragGestures(
                                onDragStart = { leftResizePx = 0f; onSelect(clip.id) },
                                onDragEnd = {
                                    val deltaMs = (leftResizePx / density / pxPerMs).roundToLong()
                                    onResizeStart(clip.id, clip.timelineStartMs + deltaMs)
                                    leftResizePx = 0f
                                },
                                onDragCancel = { leftResizePx = 0f },
                                onDrag = { change, amount -> change.consume(); leftResizePx += amount.x }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight(0.55f).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).width(12.dp).fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.16f))
                        .pointerInput(clip.id, clip.timelineStartMs, clip.sourceStartMs, clip.sourceEndMs, pxPerMs, density) {
                            detectDragGestures(
                                onDragStart = { rightResizePx = 0f; onSelect(clip.id) },
                                onDragEnd = {
                                    val deltaMs = (rightResizePx / density / pxPerMs).roundToLong()
                                    onResizeEnd(clip.id, clip.timelineEndMs + deltaMs)
                                    rightResizePx = 0f
                                },
                                onDragCancel = { rightResizePx = 0f },
                                onDrag = { change, amount -> change.consume(); rightResizePx += amount.x }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight(0.55f).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
            }
        }
        TimelinePlayhead(playheadMs, totalDurationMs, totalWidth)
    }
}

@Composable
private fun TextTimelineLane(
    clips: List<TextOverlay>,
    selectedId: Long?,
    totalWidth: Dp,
    totalDurationMs: Long,
    playheadMs: Long,
    pxPerMs: Float,
    density: Float,
    onSelect: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onResizeStart: (Long, Long) -> Unit,
    onResizeEnd: (Long, Long) -> Unit
) {
    Box(Modifier.width(totalWidth).height(52.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1C302A))) {
        clips.forEach { clip ->
            var moveOffsetPx by remember(clip.id) { mutableStateOf(0f) }
            var leftResizePx by remember(clip.id) { mutableStateOf(0f) }
            var rightResizePx by remember(clip.id) { mutableStateOf(0f) }
            val x = (clip.startMs * pxPerMs).dp + (leftResizePx / density).dp
            val durationMs = clip.endMs - clip.startMs
            val baseWidth = (durationMs * pxPerMs).coerceAtLeast(42f)
            val visibleWidth = (baseWidth + (rightResizePx - leftResizePx) / density).coerceAtLeast(36f).dp
            Box(
                Modifier.offset(x = x).width(visibleWidth).fillMaxHeight().padding(vertical = 2.dp, horizontal = 1.dp)
                    .graphicsLayer { translationX = moveOffsetPx }
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (clip.id == selectedId) 2.dp else 1.dp, if (clip.id == selectedId) VireonBlue else VireonGreen, RoundedCornerShape(6.dp))
                    .background(VireonGreen.copy(alpha = 0.82f))
                    .clickable { onSelect(clip.id) }
                    .pointerInput(clip.id, clip.startMs, clip.endMs, pxPerMs, density) {
                        var dragPx = 0f
                        detectDragGestures(
                            onDragStart = { dragPx = 0f; onSelect(clip.id) },
                            onDragEnd = {
                                val deltaMs = (dragPx / density / pxPerMs).roundToLong()
                                onMove(clip.id, clip.startMs + deltaMs)
                                moveOffsetPx = 0f
                            },
                            onDragCancel = { moveOffsetPx = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragPx += amount.x
                                moveOffsetPx = dragPx
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    clip.value,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                )
                Box(
                    Modifier.align(Alignment.CenterStart).width(12.dp).fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.16f))
                        .pointerInput(clip.id, clip.startMs, clip.endMs, pxPerMs, density) {
                            detectDragGestures(
                                onDragStart = { leftResizePx = 0f; onSelect(clip.id) },
                                onDragEnd = {
                                    val deltaMs = (leftResizePx / density / pxPerMs).roundToLong()
                                    onResizeStart(clip.id, clip.startMs + deltaMs)
                                    leftResizePx = 0f
                                },
                                onDragCancel = { leftResizePx = 0f },
                                onDrag = { change, amount -> change.consume(); leftResizePx += amount.x }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight(0.55f).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).width(12.dp).fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.16f))
                        .pointerInput(clip.id, clip.startMs, clip.endMs, pxPerMs, density) {
                            detectDragGestures(
                                onDragStart = { rightResizePx = 0f; onSelect(clip.id) },
                                onDragEnd = {
                                    val deltaMs = (rightResizePx / density / pxPerMs).roundToLong()
                                    onResizeEnd(clip.id, clip.endMs + deltaMs)
                                    rightResizePx = 0f
                                },
                                onDragCancel = { rightResizePx = 0f },
                                onDrag = { change, amount -> change.consume(); rightResizePx += amount.x }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight(0.55f).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
            }
        }
        TimelinePlayhead(playheadMs, totalDurationMs, totalWidth)
    }
}

@Composable
private fun TimelinePlayhead(playheadMs: Long, totalDurationMs: Long, totalWidth: Dp) {
    val x = if (totalDurationMs > 0L) (totalWidth.value * playheadMs.toFloat() / totalDurationMs).coerceIn(0f, totalWidth.value) else 0f
    Canvas(Modifier.fillMaxSize()) {
        val px = x.dp.toPx()
        drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
    }
}

@Composable
private fun TimelineGuides(clips: List<VisualClip>, totalDurationMs: Long, playheadMs: Long) {
    Canvas(Modifier.fillMaxSize()) {
        var elapsed = 0L
        clips.dropLast(1).forEach { clip ->
            elapsed += clip.durationMs
            val x = if (totalDurationMs > 0L) elapsed.toFloat() / totalDurationMs * size.width else 0f
            drawLine(Color(0xFFFFD166), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawCircle(Color(0xFFFFD166), 3.dp.toPx(), Offset(x, 5.dp.toPx()))
        }
        val cursorX = if (totalDurationMs > 0L) (playheadMs.toFloat() / totalDurationMs * size.width).coerceIn(0f, size.width) else 0f
        drawLine(Color.White, Offset(cursorX, 0f), Offset(cursorX, size.height), 2.dp.toPx())
    }
}

@Composable
private fun TimelineAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(31.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

private fun timelineScale(totalDurationMs: Long): Float {
    if (totalDurationMs <= 0L) return 0.036f
    return (maxOf(320f, totalDurationMs / 1_000f * 36f) / totalDurationMs.toFloat())
}

private fun timeFromX(x: Float, widthPx: Float, totalDurationMs: Long, clips: List<VisualClip>): Long {
    if (widthPx <= 0f || totalDurationMs <= 0L) return 0L
    val raw = (x.coerceIn(0f, widthPx) / widthPx * totalDurationMs).toLong()
    return snapToCut(raw, clips, 220L)
}

private fun locateVisual(clips: List<VisualClip>, positionMs: Long): LocatedClip? {
    if (clips.isEmpty()) return null
    var elapsed = 0L
    clips.forEachIndexed { index, clip ->
        val end = elapsed + clip.durationMs
        if (positionMs < end || index == clips.lastIndex) {
            return LocatedClip(index, (positionMs - elapsed).coerceIn(0L, clip.durationMs), elapsed)
        }
        elapsed = end
    }
    val last = clips.lastIndex
    return LocatedClip(last, clips[last].durationMs, clips.take(last).sumOf { it.durationMs })
}

private fun cutBoundaries(clips: List<VisualClip>): List<Long> {
    var elapsed = 0L
    val boundaries = mutableListOf<Long>()
    clips.dropLast(1).forEach { clip ->
        elapsed += clip.durationMs
        boundaries.add(elapsed)
    }
    return boundaries
}

private fun snapToCut(positionMs: Long, clips: List<VisualClip>, thresholdMs: Long): Long {
    val nearest = cutBoundaries(clips).minByOrNull { abs(it - positionMs) } ?: return positionMs
    return if (abs(nearest - positionMs) <= thresholdMs) nearest else positionMs
}

private fun formatTimelineTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private suspend fun inspectVisual(context: Context, uri: Uri, kind: VisualKind, id: Long): VisualClip = withContext(Dispatchers.IO) {
    val name = queryDisplayName(context, uri)
    if (kind == VisualKind.IMAGE) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        VisualClip(id, uri.toString(), name, kind, imageDurationMs = 3_000L, thumbnail = bitmap?.let(::scaledBitmap))
    } else {
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        var frame: Bitmap? = null
        try {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            // Invalid or unsupported video: retain the entry so the user can see the import failure.
        } finally {
            runCatching { retriever.release() }
        }
        VisualClip(
            id = id,
            uri = uri.toString(),
            name = name,
            kind = kind,
            sourceStartMs = 0L,
            sourceEndMs = duration.coerceAtLeast(500L),
            thumbnail = frame?.let(::scaledBitmap)
        )
    }
}

private suspend fun inspectAudio(context: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    var duration = 0L
    try {
        retriever.setDataSource(context, uri)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        duration = 0L
    } finally {
        runCatching { retriever.release() }
    }
    queryDisplayName(context, uri) to duration
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: "Medya"
        } else uri.lastPathSegment?.substringAfterLast('/') ?: "Medya"
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Medya"
    } finally {
        cursor?.close()
    }
}

private fun scaledBitmap(source: Bitmap): Bitmap {
    val factor = minOf(640f / source.width, 640f / source.height, 1f)
    if (factor >= 1f) return source
    val scaled = Bitmap.createScaledBitmap(source, (source.width * factor).roundToInt().coerceAtLeast(1), (source.height * factor).roundToInt().coerceAtLeast(1), true)
    if (scaled !== source) source.recycle()
    return scaled
}

@Composable
private fun VireonMark(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(VireonBlue), contentAlignment = Alignment.Center) {
        Text("V", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}
