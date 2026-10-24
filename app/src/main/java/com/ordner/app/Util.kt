package com.ordner.app

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun isTv(ctx: Context): Boolean {
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    do {
        v /= 1024
        i++
    } while (v >= 1024 && i < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", v, units[i])
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun localIps(): List<String> = try {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { ni ->
            ni.inetAddresses.toList()
                .filter { it is Inet4Address && !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        }
} catch (e: Exception) {
    emptyList()
}

fun fileUri(ctx: Context, file: File) =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

fun openFile(ctx: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(fileUri(ctx, file), FileOps.mime(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Android erlaubt normalen Apps weder Formatieren noch Aushängen von Laufwerken.
 * Wir leiten deshalb zu den Speicher-Einstellungen des Systems weiter.
 */
fun openStorageSettings(ctx: Context) {
    val actions = listOf(
        Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        Settings.ACTION_MEMORY_CARD_SETTINGS,
        Settings.ACTION_SETTINGS,
    )
    for (a in actions) {
        try {
            ctx.startActivity(Intent(a))
            return
        } catch (e: Exception) {
            // nächste Variante probieren
        }
    }
    Toast.makeText(ctx, "Einstellungen konnten nicht geöffnet werden", Toast.LENGTH_SHORT).show()
}
