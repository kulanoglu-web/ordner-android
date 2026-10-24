package com.ordner.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FilesScreen(vm: AppViewModel, onSend: () -> Unit) {
    val ctx = LocalContext.current
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dir = vm.currentDir

    BackHandler(enabled = vm.selectMode || dir != null) {
        if (vm.selectMode) vm.clearSelection() else vm.up()
    }

    Column(Modifier.fillMaxSize()) {
        FilesToolbar(
            vm = vm,
            onNewFolder = { showNewFolder = true },
            onRename = { renameTarget = vm.selection.firstOrNull() },
            onDelete = { confirmDelete = true },
            onSend = {
                vm.queueSelection()
                onSend()
            },
        )
        if (dir == null) {
            StorageOverview(vm)
        } else {
            FileList(vm)
        }
    }

    if (showNewFolder) {
        TextPromptDialog(
            title = "Neuer Ordner",
            initial = "",
            onConfirm = { name ->
                vm.newFolder(name)
                showNewFolder = false
            },
            onDismiss = { showNewFolder = false },
        )
    }
    renameTarget?.let { target ->
        TextPromptDialog(
            title = "Umbenennen",
            initial = target.name,
            onConfirm = { name ->
                vm.rename(target, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Löschen?") },
            text = { Text("${vm.selection.size} Element(e) werden endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelection()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun FilesToolbar(
    vm: AppViewModel,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
) {
    val ctx = LocalContext.current
    val dir = vm.currentDir
    val hasSel = vm.selection.isNotEmpty()

    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vm.selectMode) {
                IconButton(onClick = { vm.clearSelection() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Auswahl beenden")
                }
                Text(
                    "${vm.selection.size} ausgewählt",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            } else {
                IconButton(onClick = { vm.up() }, enabled = dir != null) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
                Text(
                    dir?.absolutePath ?: "Speicher",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (vm.selectMode) {
                    IconButton(onClick = { vm.copySelection(false) }, enabled = hasSel) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
                    }
                    IconButton(onClick = { vm.copySelection(true) }, enabled = hasSel) {
                        Icon(Icons.Filled.ContentCut, contentDescription = "Ausschneiden")
                    }
                    IconButton(onClick = onRename, enabled = vm.selection.size == 1) {
                        Icon(Icons.Filled.Edit, contentDescription = "Umbenennen")
                    }
                    IconButton(onClick = onDelete, enabled = hasSel) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                    IconButton(onClick = onSend, enabled = hasSel) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Per WLAN/LAN senden")
                    }
                    IconButton(
                        onClick = { BluetoothShare.send(ctx, vm.selection.toList()) },
                        enabled = hasSel,
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = "Per Bluetooth senden")
                    }
                } else {
                    if (dir != null) {
                        IconButton(onClick = { vm.paste() }, enabled = vm.clipboard.isNotEmpty()) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Einfügen")
                        }
                        IconButton(onClick = onNewFolder) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "Neuer Ordner")
                        }
                        IconButton(onClick = { vm.toggleSelectMode() }) {
                            Icon(Icons.Filled.Checklist, contentDescription = "Auswählen")
                        }
                    }
                    IconButton(onClick = { vm.refreshStorage() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageOverview(vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(vm.roots, key = { it.dir.absolutePath }) { root ->
            StorageCard(root, onClick = { vm.open(root.dir) })
        }
        if (vm.roots.isEmpty()) {
            item { Text("Kein Speicher gefunden.") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(vm: AppViewModel) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (vm.entries.isEmpty()) {
            item {
                Text(
                    "Dieser Ordner ist leer",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(vm.entries, key = { it.absolutePath }) { f ->
            FileRow(
                file = f,
                selected = f in vm.selection,
                selectMode = vm.selectMode,
                onClick = {
                    when {
                        vm.selectMode -> vm.toggle(f)
                        f.isDirectory -> vm.open(f)
                        else -> openFile(ctx, f)
                    }
                },
                onLongClick = { vm.longPress(f) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val sub = remember(file) {
        val date = formatDate(file.lastModified())
        if (file.isDirectory) date else "${formatSize(file.length())} · $date"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .focusHighlight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            iconFor(file),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun iconFor(f: File): ImageVector {
    if (f.isDirectory) return Icons.Filled.Folder
    return when (f.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> Icons.Filled.Image
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Filled.Movie
        "mp3", "flac", "wav", "ogg", "m4a" -> Icons.Filled.MusicNote
        "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.Archive
        "pdf" -> Icons.Filled.PictureAsPdf
        "apk" -> Icons.Filled.Android
        else -> Icons.Filled.Description
    }
}

@Composable
fun StorageCard(
    root: StorageRoot,
    onClick: (() -> Unit)?,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (root.removable) Icons.Filled.Usb else Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(root.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        root.dir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val used = root.totalBytes - root.freeBytes
            LinearProgressIndicator(
                progress = { if (root.totalBytes > 0) used.toFloat() / root.totalBytes else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatSize(root.freeBytes)} frei von ${formatSize(root.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            extra()
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
