package com.vireon.editor

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var selectedTool by rememberSaveable { mutableStateOf("Düzenle") }

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
                compact = isLandscape
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                VideoPreview(
                    uri = videoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isLandscape) 0.62f else 0.56f)
                        .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)
                )
                TimelinePanel(
                    audioLabel = audioLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (isLandscape) 0.38f else 0.44f)
                )
            }
        }
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
    compact: Boolean
) {
    val tools = listOf(
        Triple("Düzenle", Icons.Default.ContentCut, { onToolSelected("Düzenle") }),
        Triple("Ses", Icons.Default.MusicNote, onAddAudio),
        Triple("Metin", Icons.Default.TextFields, { onToolSelected("Metin") }),
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
private fun VideoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}

@Composable
private fun TimelinePanel(audioLabel: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(VireonSurface)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zaman çizelgesi", color = VireonText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("30 FPS", color = VireonMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00:00", "00:05", "00:10", "00:15", "00:20").forEach {
                Text(it, color = VireonMuted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        TimelineTrack(
            icon = Icons.Default.Movie,
            label = "Video klibi",
            color = VireonBlueSoft,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(5.dp))
        TimelineTrack(
            icon = Icons.Default.MusicNote,
            label = audioLabel ?: "Müzik ekle",
            color = VireonPurple,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(5.dp))
        TimelineTrack(
            icon = Icons.Default.TextFields,
            label = "Metin katmanı",
            color = VireonGreen,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Zaman çizelgesi araçları geliştirme aşamasında.",
            color = VireonMuted,
            fontSize = 9.sp
        )
    }
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
