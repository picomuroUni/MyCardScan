    package com.Picone.MyCardScan.nearby

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.Picone.MyCardScan.repository.SettingsRepository
import com.Picone.MyCardScan.repository.CollectionRepository
import com.Picone.MyCardScan.repository.WantedRepository
import com.Picone.MyCardScan.repository.normalizeKey
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File

    //Messaggi scambiati tra due telefoni. Ognuno manda profile e riceve match
 //  - "profile": nome, numero di carte, Wanted list e (solo se condivisa) la collezione
 //  - "match":   risposta con quante carte della collezione di chi risponde sono nella Wanted list di chi ha inviato

@Serializable
data class Msg(
    val type: String,
    val name: String? = null,
    val collectionCount: Int? = null,
    val wanted: List<String>? = null,
    val collection: List<String>? = null,
    val matchCount: Int? = null
)

@SuppressLint("StaticFieldLeak")
object NearbyManager {
    private const val TAG = "NearbyManager"
    private const val SERVICE_ID = "com.Picone.MyCardScan.collectors"
    private const val MAX_NAME = 20
    private const val MAX_ITEMS = 2000
    private const val MAX_BYTES = 31 * 1024        // il limite dei messaggi "bytes" è 32 KB

    private const val PHOTO_MARKER: Byte = 1       // i messaggi JSON iniziano con '{', mai con 0x01
    private const val MAX_PHOTOS = 300
    private const val THUMB_WIDTH = 240
    private const val MAX_PHOTO_BYTES = 26 * 1024  // con l'intestazione resta sotto MAX_BYTES

    // true mentre è aperta la collezione di un collezionista: le connessioni non vanno chiuse.
    @Volatile var keepConnections = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val photoJobs = mutableMapOf<String, Job>()

    private val _photoReceived = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    // (idCollezionista, chiave carta) ogni volta che arriva una foto
    val photoReceived: SharedFlow<Pair<String, String>> = _photoReceived.asSharedFlow()

    fun photoDir(ctx: Context, collectorId: String) =
        File(
            ctx.applicationContext.cacheDir,
            "nearby/" + collectorId.replace(Regex("[^A-Za-z0-9]"), "_")
        )

    // il nome file viene da un altro telefono: solo lettere e cifre, niente "../"
    fun photoName(key: String) = key.replace(Regex("[^A-Z0-9]"), "_") + ".jpg"
    private val strategy = Strategy.P2P_CLUSTER
    private val json = Json { ignoreUnknownKeys = true }

    // Un collezionista trovato vicino. I campi null non sono ancora arrivati.
    data class Collector(
        val id: String,
        val name: String,
        val collectionCount: Int? = null,
        val matchCount: Int? = null,           // sue carte presenti nella MIA Wanted list
        val collection: List<String>? = null   // solo se condivide la collezione
    )

    private val _collectors = MutableStateFlow<Map<String, Collector>>(emptyMap())
    val collectors: StateFlow<Map<String, Collector>> = _collectors

    @SuppressLint("StaticFieldLeak")
    private lateinit var appCtx: Context
    private val client by lazy { Nearby.getConnectionsClient(appCtx) }

    private var advertising = false
    private var discovering = false
    private val requested = mutableSetOf<String>()    // endpoint a cui ho già chiesto la connessione

    private fun myName() = SettingsRepository.getDisplayName(appCtx)



    // Mi rendo visibile agli altri. Si può chiamare più volte: se è già attivo non fa nulla.
    fun advertise(ctx: Context) {
        appCtx = ctx.applicationContext
        if (advertising) return
        advertising = true
        client.startAdvertising(
            myName(), SERVICE_ID, lifecycle,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener { Log.d(TAG, "advertising OK come '${myName()}'") }
            .addOnFailureListener {
                advertising = (it as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING
                Log.e(TAG, "startAdvertising fallito: ${it.describe()}")
            }
    }

    // Cerco gli altri. Si può chiamare più volte: se la ricerca è già attiva non fa nulla.
    fun discover(ctx: Context) {
        appCtx = ctx.applicationContext
        if (discovering) return
        discovering = true
        client.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                    _collectors.update {
                        if (id in it) it else it + (id to Collector(id, clean(info.endpointName)))
                    }
                    if (requested.add(id)) {
                        client.requestConnection(myName(), id, lifecycle)
                            .addOnFailureListener {
                                requested.remove(id)
                                Log.w(TAG, "requestConnection fallito: ${it.message}")
                            }
                    }
                }

                override fun onEndpointLost(id: String) {
                    requested.remove(id)
                    _collectors.update { it - id }
                }
            },
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener { Log.d(TAG, "discovery OK") }
            .addOnFailureListener {
                discovering = (it as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING
                Log.e(TAG, "startDiscovery fallito: ${it.describe()}")
            }
    }

    fun stopScanning() {
        if (!::appCtx.isInitialized) return
        client.stopDiscovery()
        discovering = false
    }

    // interrompe la ricerca, chiude le connessioni e svuota la lista. L'annuncio resta attivo.
    fun reset() {
        if (!::appCtx.isInitialized) return
        stopScanning()
        client.stopAllEndpoints()
        photoJobs.values.forEach { it.cancel() }
        photoJobs.clear()
        File(appCtx.cacheDir, "nearby").deleteRecursively()
        requested.clear()
        _collectors.value = emptyMap()
    }

    // Ferma tutto, annuncio compreso.
    fun stopAll() {
        if (!::appCtx.isInitialized) return
        client.stopAdvertising()
        advertising = false
        reset()
    }

    // Connessioni

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            upsert(id) { it.copy(name = clean(info.endpointName)) }
            client.acceptConnection(id, payloads)
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                sendProfile(id)
            } else {
                requested.remove(id)
                Log.w(TAG, "Connessione non riuscita: ${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(id: String) {
            photoJobs.remove(id)?.cancel()
            requested.remove(id)
            _collectors.update { it - id }
        }
    }

    //  Messaggi

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            if (bytes.isNotEmpty() && bytes[0] == PHOTO_MARKER) { onPhoto(id, bytes); return }
            val text = bytes.toString(Charsets.UTF_8)
            // mai fidarsi dei dati altrui: se non si legge, si ignora
            val msg = runCatching { json.decodeFromString<Msg>(text) }.getOrNull() ?: return
            when (msg.type) {
                "profile" -> onProfile(id, msg)
                "match" -> upsert(id) { it.copy(matchCount = msg.matchCount?.coerceIn(0, MAX_ITEMS)) }
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    private fun sendProfile(id: String) {
        val mine = CollectionRepository.getAll(appCtx).map { normalizeKey(it.cardId) }
        val wanted = WantedRepository.getAll(appCtx).map { it.key }
        val share = SettingsRepository.getShareCollection(appCtx)

        val full = Msg(
            type = "profile",
            name = myName(),
            collectionCount = mine.size,
            wanted = wanted,
            collection = if (share) mine else null
        )
        if (!send(id, full) && share) {
            Log.w(TAG, "Collezione troppo grande per un messaggio: invio solo i conteggi")
            send(id, full.copy(collection = null))
        }
        if (share) sendPhotos(id)

    }

    private fun onProfile(id: String, msg: Msg) {
        val theirWanted = msg.wanted.orEmpty().take(MAX_ITEMS).map { normalizeKey(it) }.toSet()
        val count = CollectionRepository.getAll(appCtx)
            .count { normalizeKey(it.cardId) in theirWanted }
        send(id, Msg(type = "match", matchCount = count))

        upsert(id) {
            it.copy(
                name = msg.name?.let { n -> clean(n) }?.takeIf { n -> n.isNotBlank() } ?: it.name,
                collectionCount = msg.collectionCount?.coerceIn(0, 100_000),
                collection = msg.collection?.take(MAX_ITEMS)?.map { k -> normalizeKey(k) }
            )
        }
    }

    //Restituisce false se il messaggio supera il limite e non è stato inviato.
    private fun send(id: String, msg: Msg): Boolean {
        val bytes = json.encodeToString(msg).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return false
        client.sendPayload(id, Payload.fromBytes(bytes))
        return true
    }

    private fun sendPhotos(id: String) {
        photoJobs.remove(id)?.cancel()
        photoJobs[id] = scope.launch {
            val seen = HashSet<String>()
            for (c in CollectionRepository.getAll(appCtx)) {
                if (!isActive || seen.size >= MAX_PHOTOS) break
                val key = normalizeKey(c.cardId)
                if (!seen.add(key)) continue
                val jpeg = thumbnail(c.imagePath) ?: continue
                val k = key.toByteArray(Charsets.UTF_8)
                val frame = ByteArray(2 + k.size + jpeg.size)
                frame[0] = PHOTO_MARKER
                frame[1] = k.size.toByte()
                k.copyInto(frame, 2)
                jpeg.copyInto(frame, 2 + k.size)
                client.sendPayload(id, Payload.fromBytes(frame))
            }
        }
    }

    private fun thumbnail(path: String): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
        val src = BitmapFactory.decodeFile(
            path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val h = (src.height * THUMB_WIDTH.toFloat() / src.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createScaledBitmap(src, THUMB_WIDTH, h, true)
        if (bmp !== src) src.recycle()

        val out = ByteArrayOutputStream()
        var q = 80
        while (q >= 30) {
            out.reset()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= MAX_PHOTO_BYTES) break
            q -= 10
        }
        bmp.recycle()
        return out.toByteArray().takeIf { it.size <= MAX_PHOTO_BYTES }
    }

    private fun onPhoto(id: String, b: ByteArray) {
        if (id !in _collectors.value || b.size < 3) return
        val keyLen = b[1].toInt() and 0xFF
        if (b.size <= 2 + keyLen) return
        val key = normalizeKey(String(b, 2, keyLen, Charsets.UTF_8))
        val dir = photoDir(appCtx, id).apply { mkdirs() }
        if ((dir.list()?.size ?: 0) >= MAX_PHOTOS) return
        File(dir, photoName(key)).writeBytes(b.copyOfRange(2 + keyLen, b.size))
        _photoReceived.tryEmit(id to key)
    }



    private fun upsert(id: String, change: (Collector) -> Collector) {
        _collectors.update { m -> m + (id to change(m[id] ?: Collector(id, "Collezionista"))) }
    }

    // I nomi arrivano da altri telefoni: niente caratteri di controllo e lunghezza limitata.
    private fun clean(name: String): String =
        name.filter { !it.isISOControl() }.trim().take(MAX_NAME)


    private fun Exception.describe(): String {
        val code = (this as? ApiException)?.statusCode ?: return message.orEmpty()
        return "$code ${ConnectionsStatusCodes.getStatusCodeString(code)}"
    }
}