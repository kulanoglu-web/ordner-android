package com.ordner.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val snackbar = remember { SnackbarHostState() }
    val offer by vm.transfer.offer.collectAsStateWithLifecycle()

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val tabs = listOf(
        NavTab("Dateien", Icons.Filled.Folder),
        NavTab("Übertragen", Icons.Filled.SwapHoriz),
        NavTab("Speicher", Icons.Filled.Usb),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) {
                NavigationRail {
                    Spacer(Modifier.weight(1f))
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    0 -> FilesScreen(vm, onSend = { tab = 1 })
                    1 -> TransferScreen(vm, onGoFiles = { tab = 0 })
                    else -> StorageScreen(vm, onBrowse = { dir ->
                        vm.open(dir)
                        tab = 0
                    })
                }
            }
        }
    }

    offer?.let { o ->
        AlertDialog(
            onDismissRequest = { o.decision.complete(false) },
            title = { Text("Eingehende Dateien") },
            text = {
                val list = o.fileNames.take(5).joinToString("\n") { "• $it" } +
                    if (o.fileNames.size > 5) "\n…" else ""
                Text("${o.senderName} möchte ${o.fileNames.size} Datei(en) (${formatSize(o.totalBytes)}) senden:\n\n$list")
            },
            confirmButton = {
                Button(onClick = { o.decision.complete(true) }) { Text("Annehmen") }
            },
            dismissButton = {
                TextButton(onClick = { o.decision.complete(false) }) { Text("Ablehnen") }
            },
        )
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Zugriff auf alle Dateien nötig",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ordner ist ein Dateimanager und braucht Zugriff auf den gesamten Speicher – " +
                "auch auf SD-Karten, USB-Sticks und Festplatten.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.focusRequester(focus)) {
            Text("Zugriff erlauben")
        }
        if (isTv(ctx)) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Hinweis für Android TV: Auf manchen Geräten fehlt dieser Einstellungsdialog. " +
                    "Dann per ADB freigeben:\nadb shell appops set ${ctx.packageName} MANAGE_EXTERNAL_STORAGE allow",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
