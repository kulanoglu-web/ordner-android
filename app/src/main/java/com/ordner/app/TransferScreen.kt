package com.ordner.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TransferScreen(vm: AppViewModel, onGoFiles: () -> Unit) {
    val ctx = LocalContext.current
    val t = vm.transfer
    val peers by t.peers.collectAsStateWithLifecycle()
    val progress by t.progress.collectAsStateWithLifecycle()
    val receiving by t.receiving.collectAsStateWithLifecycle()
    val searching by t.searching.collectAsStateWithLifecycle()
    var manualIp by rememberSaveable { mutableStateOf("") }

    val queue = vm.sendQueue
    val busy = progress?.let { !it.finished } == true
    val canSend = queue.isNotEmpty() && !busy
    val ips = remember(receiving) { localIps() }

    // Keine Dauersuche: Suche nur auf Knopfdruck, beim Verlassen des Reiters wird sie beendet.
    DisposableEffect(Unit) {
        onDispose { t.stopDiscovery() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard("Zum Senden bereit") {
                if (queue.isEmpty()) {
                    Text("Nichts ausgewählt. Im Reiter „Dateien“ Dateien markieren und auf Senden tippen.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onGoFiles) { Text("Zu den Dateien") }
                } else {
                    val preview = queue.take(3).joinToString { it.name } + if (queue.size > 3) " …" else ""
                    Text("${queue.size} Element(e): $preview")
                    TextButton(onClick = { vm.clearQueue() }) { Text("Leeren") }
                }
            }
        }

        item {
            SectionCard("Geräte im Netzwerk (WLAN / LAN)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { t.startDiscovery() }, enabled = !searching) {
                        Text("Geräte suchen")
                    }
                    if (searching) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Suche läuft …")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (peers.isEmpty()) {
                    if (!searching) {
                        Text(
                            "Keine Geräte gefunden. Auf dem anderen Gerät muss Ordner offen und der Empfang aktiv sein.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    peers.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Devices, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                Text(
                                    p.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(enabled = canSend, onClick = { vm.sendTo(p.host, p.port) }) {
                                Text("Senden")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Oder per IP-Adresse:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("z. B. 192.168.1.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSend && manualIp.isNotBlank(),
                        onClick = { vm.sendTo(manualIp) },
                    ) { Text("Senden") }
                }
            }
        }

        item {
            SectionCard("Dieses Gerät empfängt") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = receiving,
                        onCheckedChange = { if (it) t.startReceiving() else t.stopReceiving() },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(if (receiving) "Empfang aktiv" else "Empfang aus")
                }
                Spacer(Modifier.height(8.dp))
                Text("Name: ${t.deviceName}")
                if (ips.isNotEmpty()) {
                    Text("IP: ${ips.joinToString(" · ")}   Port: ${TransferManager.PORT}")
                }
                Text(
                    "Eingehende Dateien müssen bestätigt werden und landen in Download/Ordner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    vm.open(t.inbox)
                    onGoFiles()
                }) { Text("Empfangsordner öffnen") }
            }
        }

        progress?.let { p ->
            item {
                SectionCard(p.title) {
                    if (p.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { (p.doneBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("${formatSize(p.doneBytes)} / ${formatSize(p.totalBytes)}")
                    }
                    p.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (p.finished) {
                        TextButton(onClick = { t.dismissProgress() }) { Text("Schließen") }
                    }
                }
            }
        }

        item {
            SectionCard("Bluetooth") {
                if (!BluetoothShare.isSupported(ctx)) {
                    Text("Dieses Gerät hat kein Bluetooth.")
                } else {
                    Text(
                        "Bluetooth nutzt die Freigabe von Android. Das Zielgerät muss vorher " +
                            "gekoppelt sein und Dateien empfangen können.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = queue.isNotEmpty(),
                            onClick = { BluetoothShare.send(ctx, queue) },
                        ) { Text("Per Bluetooth senden") }
                        OutlinedButton(onClick = { BluetoothShare.openSettings(ctx) }) {
                            Text("Koppeln")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
