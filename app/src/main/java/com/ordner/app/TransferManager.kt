package com.ordner.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Gerät-zu-Gerät-Transfer über WLAN/LAN.
 *
 * Ablauf: Der Sender verbindet sich per TCP (Port 8765), schickt eine Dateiliste, der
 * Empfänger muss per Dialog bestätigen, dann werden die Dateien als Rohdaten übertragen.
 * Gefunden werden Geräte per mDNS/NSD (Diensttyp _ordner._tcp.); alternativ per IP.
 */
class TransferManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        const val PORT = 8765
        private const val MAGIC = "ORDNER1"
        private const val SERVICE_TYPE = "_ordner._tcp."
        private const val BUF = 64 * 1024
    }

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _offer = MutableStateFlow<IncomingOffer?>(null)
    val offer: StateFlow<IncomingOffer?> = _offer.asStateFlow()

    private val _progress = MutableStateFlow<TransferState?>(null)
    val progress: StateFlow<TransferState?> = _progress.asStateFlow()

    private val _receiving = MutableStateFlow(false)
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()

    val deviceName: String = (
        Settings.Global.getString(context.contentResolver, "device_name")
            ?: Build.MODEL
        ).ifBlank { "Android" }.take(40)

    val inbox: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Ordner",
        ).apply { mkdirs() }

    fun dismissProgress() {
        _progress.value = null
    }

    // ---------------------------------------------------------------- Empfangen

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val offerMutex = Mutex()

    fun startReceiving() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(PORT)
                serverSocket = ss
                _receiving.value = true
                registerService()
                while (isActive) {
                    val s = ss.accept()
                    launch { handle(s) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _progress.value = TransferState(
                        "Empfang", 0, 0, true,
                        "Empfang konnte nicht starten: ${e.message}",
                    )
                }
            } finally {
                _receiving.value = false
            }
        }
    }

    fun stopReceiving() {
        unregisterService()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignorieren
        }
        serverJob?.cancel()
        _receiving.value = false
    }

    private suspend fun handle(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                socket.use { s ->
                    s.soTimeout = 120_000
                    val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                    val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))

                    if (input.readUTF() != MAGIC) return@use
                    val sender = input.readUTF()
                    val count = input.readInt()
                    if (count !in 1..10_000) return@use

                    val names = ArrayList<String>()
                    val sizes = LongArray(count)
                    for (i in 0 until count) {
                        names.add(FileOps.safeName(input.readUTF()))
                        sizes[i] = input.readLong()
                    }
                    if (sizes.any { it < 0 }) return@use
                    val total = sizes.sum()

                    val accepted = offerMutex.withLock {
                        val decision = CompletableDeferred<Boolean>()
                        _offer.value = IncomingOffer(sender, names, total, decision)
                        val r = withTimeoutOrNull(60_000) { decision.await() } ?: false
                        _offer.value = null
                        r
                    }
                    output.writeBoolean(accepted)
                    output.flush()
                    if (!accepted) return@use

                    val dir = inbox
                    val buf = ByteArray(BUF)
                    var done = 0L
                    val title = "Empfange von $sender"
                    _progress.value = TransferState(title, 0, total)
                    for (i in 0 until count) {
                        val target = FileOps.unique(dir, names[i])
                        FileOutputStream(target).use { out ->
                            var left = sizes[i]
                            while (left > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw EOFException("Verbindung abgebrochen")
                                out.write(buf, 0, n)
                                left -= n
                                done += n
                                _progress.value = TransferState(title, done, total)
                            }
                        }
                    }
                    output.writeBoolean(true)
                    output.flush()
                    _progress.value = TransferState(
                        "Empfangen von $sender (${names.size} Datei(en)) → Download/Ordner",
                        total, total, true,
                    )
                }
            } catch (e: Exception) {
                _progress.value = TransferState(
                    "Empfang", 0, 0, true, e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    // ------------------------------------------------------------------ Senden

    fun send(host: String, port: Int, files: List<File>) {
        scope.launch(Dispatchers.IO) {
            try {
                val flat = files.flatMap {
                    if (it.isDirectory) it.walkTopDown().filter { f -> f.isFile }.toList() else listOf(it)
                }
                if (flat.isEmpty()) {
                    _progress.value = TransferState("Senden", 0, 0, true, "Keine Dateien ausgewählt")
                    return@launch
                }
                val sizes = flat.map { it.length() }
                val total = sizes.sum()
                _progress.value = TransferState("Verbinde mit $host …", 0, total)

                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 5_000)
                    s.soTimeout = 90_000
                    val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    val inp = DataInputStream(BufferedInputStream(s.getInputStream()))

                    out.writeUTF(MAGIC)
                    out.writeUTF(deviceName)
                    out.writeInt(flat.size)
                    flat.forEachIndexed { i, f ->
                        out.writeUTF(f.name)
                        out.writeLong(sizes[i])
                    }
                    out.flush()

                    _progress.value = TransferState("Warte auf Bestätigung am Zielgerät …", 0, total)
                    if (!inp.readBoolean()) {
                        _progress.value = TransferState(
                            "Senden", 0, total, true, "Das Zielgerät hat abgelehnt",
                        )
                        return@use
                    }

                    val buf = ByteArray(BUF)
                    var done = 0L
                    flat.forEachIndexed { i, f ->
                        var left = sizes[i]
                        FileInputStream(f).use { ins ->
                            while (left > 0) {
                                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw EOFException("Datei ${f.name} wurde während des Sendens verändert")
                                out.write(buf, 0, n)
                                left -= n
                                done += n
                                _progress.value = TransferState("Sende an $host …", done, total)
                            }
                        }
                    }
                    out.flush()
                    inp.readBoolean()
                    _progress.value = TransferState(
                        "Gesendet (${flat.size} Datei(en))", total, total, true,
                    )
                }
            } catch (e: Exception) {
                _progress.value = TransferState(
                    "Senden", 0, 0, true, e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    // ------------------------------------------------- Geräteerkennung (mDNS)

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var ownName: String? = null
    private val lock = Any()
    private val found = LinkedHashMap<String, Peer>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private fun registerService() {
        if (regListener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = "Ordner-$deviceName"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                ownName = serviceInfo.serviceName
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        regListener = l
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            regListener = null
        }
    }

    private fun unregisterService() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
    }

    fun startDiscovery() {
        if (discListener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName != ownName) enqueueResolve(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { found.remove(serviceInfo.serviceName) }
                publishPeers()
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discListener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discListener = l
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            discListener = null
        }
    }

    fun stopDiscovery() {
        discListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discListener = null
        synchronized(lock) {
            found.clear()
            resolveQueue.clear()
            resolving = false
        }
        publishPeers()
    }

    private fun publishPeers() {
        _peers.value = synchronized(lock) { found.values.sortedBy { it.name.lowercase() } }
    }

    // NsdManager löst nur einen Dienst gleichzeitig auf – daher eine Warteschlange.
    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(lock) {
            resolveQueue.add(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    private fun resolveNext() {
        val next = synchronized(lock) {
            val n = resolveQueue.removeFirstOrNull()
            if (n == null) resolving = false
            n
        } ?: return
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolveNext()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        val name = serviceInfo.serviceName.removePrefix("Ordner-")
                        synchronized(lock) {
                            found[serviceInfo.serviceName] = Peer(name, host, serviceInfo.port)
                        }
                        publishPeers()
                    }
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            resolveNext()
        }
    }

    fun shutdown() {
        stopReceiving()
        stopDiscovery()
    }
}
