package com.ordner.app

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Environment
import android.os.StatFs
import java.io.File

object Storage {

    /** Alle eingebundenen Speicher: intern, SD-Karte, USB-Stick/-Festplatte. */
    fun roots(context: Context): List<StorageRoot> {
        val marker = "/Android/data"
        val result = mutableListOf<StorageRoot>()
        context.getExternalFilesDirs(null).forEachIndexed { index, appDir ->
            if (appDir == null) return@forEachIndexed
            val path = appDir.absolutePath
            if (!path.contains(marker)) return@forEachIndexed
            val root = File(path.substringBefore(marker))
            if (!root.exists() || !root.canRead()) return@forEachIndexed
            val removable = try {
                Environment.isExternalStorageRemovable(appDir)
            } catch (e: Exception) {
                index > 0
            }
            val stat = try {
                StatFs(root.absolutePath)
            } catch (e: Exception) {
                null
            }
            result += StorageRoot(
                label = if (index == 0) "Interner Speicher" else "Externer Speicher (${root.name})",
                dir = root,
                removable = removable && index > 0,
                freeBytes = stat?.availableBytes ?: 0L,
                totalBytes = stat?.totalBytes ?: 0L,
            )
        }
        return result.distinctBy { it.dir.absolutePath }
    }

    /** USB-Massenspeicher, die Android sieht (auch wenn sie evtl. nicht eingebunden sind). */
    fun usbMassStorage(context: Context): List<UsbDeviceInfo> {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return usb.deviceList.values
            .filter { d ->
                (0 until d.interfaceCount).any {
                    d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                }
            }
            .map { d ->
                UsbDeviceInfo(
                    name = d.productName ?: d.deviceName,
                    id = "%04x:%04x".format(d.vendorId, d.productId),
                )
            }
    }
}
