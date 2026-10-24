package com.ordner.app

import android.webkit.MimeTypeMap
import java.io.File

object FileOps {

    fun list(dir: File): List<File> =
        (dir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    /** Entfernt Pfadanteile, damit empfangene Namen nie aus dem Zielordner ausbrechen. */
    fun safeName(name: String): String {
        val n = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return if (n.isEmpty() || n == "." || n == "..") "datei" else n
    }

    fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$suffix")
            i++
        }
        return f
    }

    fun copy(src: File, dstDir: File): File {
        if (src.isDirectory) {
            val s = src.canonicalPath
            val d = dstDir.canonicalPath
            require(!(d == s || d.startsWith(s + File.separator))) {
                "Ordner kann nicht in sich selbst kopiert werden"
            }
            val target = unique(dstDir, src.name)
            target.mkdirs()
            src.listFiles()?.forEach { copy(it, target) }
            return target
        }
        val target = unique(dstDir, src.name)
        src.copyTo(target)
        return target
    }

    fun move(src: File, dstDir: File): File {
        val target = unique(dstDir, src.name)
        if (src.renameTo(target)) return target
        val copied = copy(src, dstDir)
        src.deleteRecursively()
        return copied
    }

    fun mime(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
}
