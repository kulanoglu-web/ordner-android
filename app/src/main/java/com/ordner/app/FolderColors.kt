package com.ordner.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

data class FolderColor(val name: String, val color: Color)

/** Farbmarkierungen für Ordner, gespeichert nach Pfad. */
object FolderColors {
    val palette = listOf(
        FolderColor("Rot", Color(0xFFE53935)),
        FolderColor("Orange", Color(0xFFFB8C00)),
        FolderColor("Gelb", Color(0xFFFDD835)),
        FolderColor("Grün", Color(0xFF43A047)),
        FolderColor("Blau", Color(0xFF1E88E5)),
        FolderColor("Lila", Color(0xFF8E24AA)),
        FolderColor("Pink", Color(0xFFD81B60)),
        FolderColor("Grau", Color(0xFF757575)),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("folder_colors", Context.MODE_PRIVATE)

    fun load(ctx: Context): Map<String, Int> =
        prefs(ctx).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    /** index = null entfernt die Farbe. */
    fun set(ctx: Context, folders: Collection<File>, index: Int?) {
        val e = prefs(ctx).edit()
        folders.forEach {
            if (index == null) e.remove(it.absolutePath) else e.putInt(it.absolutePath, index)
        }
        e.apply()
    }

    /** Farbe beim Umbenennen/Verschieben mitnehmen. */
    fun move(ctx: Context, from: File, to: File) {
        val p = prefs(ctx)
        val idx = p.getInt(from.absolutePath, -1)
        if (idx >= 0) p.edit().remove(from.absolutePath).putInt(to.absolutePath, idx).apply()
    }
}
