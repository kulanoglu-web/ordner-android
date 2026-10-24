package com.ordner.app

import kotlinx.coroutines.CompletableDeferred
import java.io.File

data class StorageRoot(
    val label: String,
    val dir: File,
    val removable: Boolean,
    val freeBytes: Long,
    val totalBytes: Long,
)

data class UsbDeviceInfo(val name: String, val id: String)

data class Peer(val name: String, val host: String, val port: Int)

data class IncomingOffer(
    val senderName: String,
    val fileNames: List<String>,
    val totalBytes: Long,
    val decision: CompletableDeferred<Boolean>,
)

data class TransferState(
    val title: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val finished: Boolean = false,
    val error: String? = null,
)
