package com.mahinio.myma

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.Locale

private val Violet = Color(0xFF7657F6)
private val DarkBg = Color(0xFF0D0E13)
private val DarkCard = Color(0xFF191B24)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyMaApp() }
    }
}

@Composable
private fun MyMaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("myma", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

    val colors = if (darkMode) darkColorScheme(
        primary = Violet,
        secondary = Color(0xFFB7A9FF),
        background = DarkBg,
        surface = DarkCard,
        onBackground = Color(0xFFF4F3FA),
        onSurface = Color(0xFFF4F3FA)
    ) else lightColorScheme(
        primary = Violet,
        secondary = Color(0xFF6650C8),
        background = Color(0xFFF7F7FB),
        surface = Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen(
                darkMode = darkMode,
                onThemeChange = {
                    darkMode = it
                    prefs.edit().putBoolean("dark_mode", it).apply()
                }
            )
        }
    }
}

private data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val downloaded: Long,
    val total: Long
)

@Composable
private fun MainScreen(darkMode: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
    var isExtracting by remember { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    fun refreshDownloads() {
        downloads = getMyDownloads(context)
    }

    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { outputUri ->
        val inputUri = pendingVideoUri
        if (outputUri != null && inputUri != null) {
            scope.launch {
                isExtracting = true
                message = "Extracting audio…"
                val result = withContext(Dispatchers.IO) {
                    runCatching { extractAudioToM4a(context, inputUri, outputUri) }
                }
                isExtracting = false
                message = if (result.isSuccess) "Audio saved successfully." else
                    "Could not extract audio: ${result.exceptionOrNull()?.localizedMessage ?: "Unsupported file"}"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        pendingVideoUri = null
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingVideoUri = uri
            val name = displayName(context, uri)
            // CreateDocument asks the user where to save the extracted audio.
            outputPicker.launch("$name.m4a")
        }
    }

    // Read the clipboard when the app comes to the foreground. Android does not allow
    // background clipboard polling; this keeps clipboard access user-visible and contextual.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                if (!text.isNullOrBlank() && looksLikeUrl(text) && url.isBlank()) url = text.trim()
                refreshDownloads()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshDownloads()
            delay(1200)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Download, null) },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                url = url,
                onUrlChange = { url = it },
                message = message,
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else ""
                    if (!text.isNullOrBlank()) url = text.trim()
                },
                onDownload = {
                    val result = enqueueAuthorizedDownload(context, url)
                    message = result
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    if (result.startsWith("Download added")) url = ""
                },
                onExtract = { pickVideo.launch(arrayOf("video/*")) },
                isExtracting = isExtracting
            )
            1 -> DownloadsScreen(Modifier.padding(padding), downloads, onRefresh = ::refreshDownloads)
            else -> SettingsScreen(
                modifier = Modifier.padding(padding),
                darkMode = darkMode,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    url: String,
    onUrlChange: (String) -> Unit,
    message: String?,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
    onExtract: () -> Unit,
    isExtracting: Boolean
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Violet),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("My-Ma", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("A Mahinio product", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Bolt, contentDescription = "Fast", tint = Violet, modifier = Modifier.size(25.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Your media,\nyour way.", fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
            Text("Save media you have permission to download.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 14.sp)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Violet)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste media link", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onPaste) { Text("PASTE") }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/video.mp4") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(15.dp),
                    trailingIcon = {
                        if (url.isNotBlank()) IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    enabled = url.isNotBlank()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Download media", fontWeight = FontWeight.SemiBold)
                }
                if (!message.isNullOrBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 12.sp)
                }
                Text(
                    "Direct media links only • Downloads are saved to your Downloads folder",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f)
                )
            }
        }

        Text("TOOLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), letterSpacing = 1.5.sp)
        Card(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !isExtracting, onClick = onExtract),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Violet.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Violet, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Video to audio", fontWeight = FontWeight.SemiBold)
                    Text("Extract audio from a local video • M4A", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
                if (isExtracting) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("MADE WITH ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), letterSpacing = 1.sp)
            Text("♥ ", fontSize = 10.sp, color = Violet)
            Text(" BY MAHINIO", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun DownloadsScreen(modifier: Modifier, downloads: List<DownloadItem>, onRefresh: () -> Unit) {
    Column(modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Downloads", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("${downloads.size} item(s)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
        Spacer(Modifier.height(18.dp))
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Violet, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing here yet", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Your downloads will appear here.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(downloads, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Violet)
                                Spacer(Modifier.width(10.dp))
                                Text(item.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(statusLabel(item.status), fontSize = 11.sp, color = Violet)
                            }
                            val progress = if (item.total > 0) item.downloaded.toFloat() / item.total else 0f
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Violet)
                            Text(
                                if (item.total > 0) "${formatBytes(item.downloaded)} / ${formatBytes(item.total)}" else statusLabel(item.status),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, darkMode: Boolean, onThemeChange: (Boolean) -> Unit) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null, tint = Violet)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dark theme", fontWeight = FontWeight.SemiBold)
                    Text("Switch between dark and light appearance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                Switch(checked = darkMode, onCheckedChange = onThemeChange)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About My-Ma", fontWeight = FontWeight.SemiBold)
                Text("Version 1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                Text("A lightweight media utility by Mahinio.", fontSize = 13.sp)
                Text("Use only with media you own or are authorized to download. YouTube content is not extracted by this app.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
        }
    }
}

private fun enqueueAuthorizedDownload(context: Context, rawUrl: String): String {
    val parsed = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull()
        ?: return "Please enter a valid URL."
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return "Only HTTP and HTTPS links are supported."

    val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
    if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" ||
        host == "m.youtube.com" || host == "music.youtube.com") {
        return "YouTube links cannot be downloaded here. Open the link in YouTube."
    }

    val pathName = parsed.lastPathSegment?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?.take(90)
        ?.takeIf { it.contains('.') && it.length > 4 }
        ?: "myma_${System.currentTimeMillis()}.media"

    return try {
        val request = DownloadManager.Request(parsed)
            .setTitle(pathName)
            .setDescription("Downloading with My-Ma")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "My-Ma/$pathName")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        val prefs = context.getSharedPreferences("myma", Context.MODE_PRIVATE)
        val ids = prefs.getStringSet("download_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id.toString())
        prefs.edit().putStringSet("download_ids", ids).apply()
        "Download added. Check the Downloads tab."
    } catch (e: Exception) {
        "Could not start download: ${e.localizedMessage ?: "unknown error"}"
    }
}

private fun getMyDownloads(context: Context): List<DownloadItem> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val prefs = context.getSharedPreferences("myma", Context.MODE_PRIVATE)
    val ids = prefs.getStringSet("download_ids", emptySet()).orEmpty()
    val result = mutableListOf<DownloadItem>()
    ids.forEach { rawId ->
        val id = rawId.toLongOrNull() ?: return@forEach
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                result += DownloadItem(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "Media file",
                    status = cursor.getInt(statusCol),
                    downloaded = cursor.getLong(bytesCol),
                    total = cursor.getLong(totalCol)
                )
            }
        }
    }
    return result.sortedByDescending { it.id }
}

private fun statusLabel(status: Int): String = when (status) {
    DownloadManager.STATUS_PENDING -> "Queued"
    DownloadManager.STATUS_RUNNING -> "Downloading"
    DownloadManager.STATUS_PAUSED -> "Paused"
    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
    DownloadManager.STATUS_FAILED -> "Failed"
    else -> "Unknown"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024)
}

private fun looksLikeUrl(text: String): Boolean =
    text.startsWith("https://", true) || text.startsWith("http://", true)

private fun displayName(context: Context, uri: Uri): String {
    var name = "video"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
    }
    return name.substringBeforeLast('.').ifBlank { "audio" }
}

/**
 * Copies the audio track from a user-selected local video into an M4A container.
 * This is audio extraction/remuxing, not MP3 encoding or transcoding.
 */
private fun extractAudioToM4a(context: Context, input: Uri, output: Uri) {
    val temp = File(context.cacheDir, "myma_audio_${System.currentTimeMillis()}.m4a")
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    try {
        extractor.setDataSource(context, input, null)
        var audioTrack = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrack = i
                break
            }
        }
        require(audioTrack >= 0) { "This video does not contain an audio track." }
        extractor.selectTrack(audioTrack)
        muxer = android.media.MediaMuxer(temp.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outputTrack = muxer.addTrack(extractor.getTrackFormat(audioTrack))
        muxer.start()

        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outputTrack, buffer, info)
            extractor.advance()
        }
        muxer.stop()
        muxer.release()
        muxer = null

        context.contentResolver.openOutputStream(output, "w")?.use { out ->
            temp.inputStream().use { inputStream -> inputStream.copyTo(out) }
        } ?: error("Could not open the selected destination.")
    } finally {
        runCatching { muxer?.release() }
        extractor.release()
        temp.delete()
    }
}
