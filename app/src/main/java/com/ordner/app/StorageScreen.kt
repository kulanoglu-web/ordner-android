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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun StorageScreen(vm: AppViewModel, onBrowse: (File) -> Unit) {
    val ctx = LocalContext.current
    val hasRemovable = vm.roots.any { it.removable }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Laufwerke", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "USB-Sticks, SD-Karten und Festplatten erscheinen hier automatisch, " +
                            "sobald Android sie eingebunden hat.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedButton(onClick = { vm.refreshStorage() }) { Text("Neu einlesen") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { openStorageSettings(ctx) }) {
                            Text("Systemeinstellungen")
                        }
                    }
                }
            }
        }

        items(vm.roots, key = { it.dir.absolutePath }) { root ->
            StorageCard(root, onClick = { onBrowse(root.dir) }) {
                if (root.removable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { openStorageSettings(ctx) }) {
                        Text("Formatieren / Auswerfen …")
                    }
                }
            }
        }

        if (vm.usbDevices.isNotEmpty()) {
            items(vm.usbDevices, key = { it.id + it.name }) { dev ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Usb, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("USB-Speicher: ${dev.name}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (hasRemovable) "Von Android erkannt (${dev.id})"
                                else "Erkannt (${dev.id}), aber nicht eingebunden. Eventuell ist das " +
                                    "Dateisystem nicht unterstützt oder das Laufwerk muss formatiert werden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Hinweis: Android erlaubt normalen Apps weder das Formatieren noch das Aushängen " +
                    "von Laufwerken. Ordner öffnet dafür die Speicher-Einstellungen des Systems. " +
                    "Welche Dateisysteme gelesen werden (FAT32, exFAT, NTFS, ext4), hängt vom Gerät ab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
