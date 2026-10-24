package com.ordner.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.io.File

/**
 * Bluetooth-Versand über die System-Freigabe (OBEX/OPP). Ob das Gerät das unterstützt,
 * hängt vom Hersteller ab – viele Smart-TVs können Bluetooth-Dateien nur empfangen,
 * nicht senden.
 */
object BluetoothShare {

    fun isSupported(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    fun send(ctx: Context, files: List<File>) {
        val uris = ArrayList<Uri>(files.filter { it.isFile }.map { fileUri(ctx, it) })
        if (uris.isEmpty()) {
            Toast.makeText(ctx, "Bitte Dateien (keine Ordner) auswählen", Toast.LENGTH_SHORT).show()
            return
        }
        val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(Intent(share).setPackage("com.android.bluetooth"))
        } catch (e: ActivityNotFoundException) {
            try {
                ctx.startActivity(Intent.createChooser(share, "Senden über …"))
            } catch (e2: Exception) {
                Toast.makeText(ctx, "Keine Freigabe-App gefunden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Bluetooth-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }
}
