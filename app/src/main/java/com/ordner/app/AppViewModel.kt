package com.ordner.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val transfer = TransferManager(app, viewModelScope)

    var roots by mutableStateOf<List<StorageRoot>>(emptyList())
        private set
    var usbDevices by mutableStateOf<List<UsbDeviceInfo>>(emptyList())
        private set

    /** null = Übersicht der Speicher */
    var currentDir by mutableStateOf<File?>(null)
        private set
    var entries by mutableStateOf<List<File>>(emptyList())
        private set

    var selectMode by mutableStateOf(false)
        private set
    var selection by mutableStateOf<Set<File>>(emptySet())
        private set

    var clipboard by mutableStateOf<List<File>>(emptyList())
        private set
    private var clipboardCut = false

    var sendQueue by mutableStateOf<List<File>>(emptyList())
        private set

    var message by mutableStateOf<String?>(null)

    init {
        transfer.startReceiving()
    }

    // ------------------------------------------------------------ Speicher

    fun refreshStorage() {
        val ctx = getApplication<Application>()
        roots = Storage.roots(ctx)
        usbDevices = Storage.usbMassStorage(ctx)
        val d = currentDir
        if (d != null) {
            if (d.exists()) load(d) else {
                currentDir = null
                entries = emptyList()
                clearSelection()
            }
        }
    }

    /** Android braucht nach dem Einstecken einen Moment, bis das Laufwerk eingebunden ist. */
    fun refreshStorageDelayed() {
        viewModelScope.launch {
            delay(1000)
            refreshStorage()
        }
    }

    // ---------------------------------------------------------- Navigation

    fun open(dir: File) {
        currentDir = dir
        clearSelection()
        load(dir)
    }

    private fun load(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = FileOps.list(dir)
            if (currentDir == dir) entries = list
        }
    }

    fun up() {
        val d = currentDir ?: return
        clearSelection()
        val parent = d.parentFile
        if (roots.any { it.dir == d } || parent == null) {
            currentDir = null
            entries = emptyList()
        } else {
            open(parent)
        }
    }

    // ------------------------------------------------------------ Auswahl

    fun longPress(f: File) {
        selectMode = true
        selection = selection + f
    }

    fun toggle(f: File) {
        selection = if (f in selection) selection - f else selection + f
    }

    fun toggleSelectMode() {
        selectMode = !selectMode
        if (!selectMode) selection = emptySet()
    }

    fun clearSelection() {
        selectMode = false
        selection = emptySet()
    }

    // --------------------------------------------------------- Dateiaktionen

    fun copySelection(cut: Boolean) {
        clipboard = selection.toList()
        clipboardCut = cut
        message = "${clipboard.size} Element(e) " + if (cut) "ausgeschnitten" else "kopiert"
        clearSelection()
    }

    fun paste() {
        val target = currentDir ?: return
        val items = clipboard
        val cut = clipboardCut
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                items.forEach { if (cut) FileOps.move(it, target) else FileOps.copy(it, target) }
            }
            withContext(Dispatchers.Main) {
                result
                    .onSuccess {
                        message = "Eingefügt"
                        if (cut) clipboard = emptyList()
                    }
                    .onFailure { message = "Fehler: ${it.message}" }
                load(target)
            }
        }
    }

    fun deleteSelection() {
        val items = selection.toList()
        val dir = currentDir
        clearSelection()
        viewModelScope.launch(Dispatchers.IO) {
            val failed = items.count { !it.deleteRecursively() }
            withContext(Dispatchers.Main) {
                message = if (failed == 0) "${items.size} Element(e) gelöscht" else "$failed Element(e) konnten nicht gelöscht werden"
                if (dir != null) load(dir)
            }
        }
    }

    fun rename(file: File, newName: String) {
        val name = FileOps.safeName(newName)
        val target = File(file.parentFile, name)
        clearSelection()
        message = when {
            target.exists() -> "Name existiert bereits"
            file.renameTo(target) -> "Umbenannt"
            else -> "Umbenennen fehlgeschlagen"
        }
        currentDir?.let { load(it) }
    }

    fun newFolder(name: String) {
        val dir = currentDir ?: return
        val target = File(dir, FileOps.safeName(name))
        message = when {
            target.exists() -> "Name existiert bereits"
            target.mkdirs() -> "Ordner erstellt"
            else -> "Ordner konnte nicht erstellt werden"
        }
        load(dir)
    }

    // ------------------------------------------------------------ Transfer

    fun queueSelection() {
        sendQueue = selection.toList()
        clearSelection()
    }

    fun clearQueue() {
        sendQueue = emptyList()
    }

    fun sendTo(host: String, port: Int = TransferManager.PORT) {
        if (sendQueue.isEmpty()) {
            message = "Nichts zum Senden ausgewählt"
            return
        }
        transfer.send(host.trim(), port, sendQueue)
    }

    override fun onCleared() {
        transfer.shutdown()
    }
}
