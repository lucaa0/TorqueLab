package it.autotoolpro.obd.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import android.os.SystemClock

/** Adattatore ELM327 Bluetooth classico (SPP). Solo lettura: nessun comando di scrittura/cancellazione. */
@SuppressLint("MissingPermission")
class Elm327 {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val lock = Mutex()
    val isConnected: Boolean get() = socket != null

    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        close()
        val s = device.createRfcommSocketToServiceRecord(SPP)
        socket = s
        s.connect()
        input = s.inputStream; output = s.outputStream
        for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) send(cmd, 3000)
        // Il primo comando OBD avvia la ricerca del protocollo: può richiedere diversi secondi
        val r = lock.withLock { exchange("0100", 12000, keepErrors = true) }
        hasObdSupportResponse(r)
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    /** Risposta ripulita, una riga per elemento. Vuota se NO DATA. */
    suspend fun send(cmd: String, timeoutMs: Long = 4000): List<String> = lock.withLock {
        exchange(cmd, timeoutMs)
    }

    suspend fun collectReport(vehicle: String, device: String, capturedAt: String): ObdReport = lock.withLock {
        var completed = false
        try {
            ObdReport.collect(vehicle, device, capturedAt) { exchange(it, 10000, keepErrors = true) }
                .also { completed = it.completed }
        } finally {
            // An interrupted capture may leave headers enabled; reconnect before other reads.
            if (!completed) close()
        }
    }

    private suspend fun exchange(cmd: String, timeoutMs: Long, keepErrors: Boolean = false): List<String> {
        return withContext(Dispatchers.IO) {
          try {
            val i = input ?: error("Non connesso")
            val o = output ?: error("Non connesso")
            while (i.available() > 0) i.read()
            o.write((cmd + "\r").toByteArray()); o.flush()
            val reply = ElmReply()
            val end = SystemClock.elapsedRealtime() + timeoutMs
            while (!reply.complete && SystemClock.elapsedRealtime() < end) {
                if (i.available() > 0) {
                    reply.accept(i.read())
                } else delay(15)
            }
            val lines = reply.lines(cmd)
            val bad = lines.firstOrNull { it in ERRORS }
            if (keepErrors) return@withContext lines
            if (bad != null && bad != "NO DATA") error("Adattatore: $bad")
            if (bad != null) emptyList() else lines
          } catch (e: Exception) {
            close()
            throw e
          }
        }
    }

    /** Codici di guasto: mode 03 (memorizzati) o 07 (in attesa). */
    suspend fun readDtcs(pending: Boolean): List<String> =
        parseDtcs(send(if (pending) "07" else "03", 6000), pending)

    /** Valore di un PID mode 01 (byte A, B) o null se non supportato. */
    suspend fun pid(pid: String): Pair<Int, Int>? = parsePid(send("01$pid", 2500), pid)

    /** Tensione misurata dall'adattatore (ATRV): funziona anche se l'auto non supporta il PID 42. */
    // keepErrors: un "?" da un clone senza ATRV non deve chiudere il collegamento.
    suspend fun adapterVoltage(): Double? = parseVoltage(query("ATRV", 2000))

    /**
     * Lettura tollerante: NO DATA, "?" o errori di bus diventano null invece di chiudere
     * il collegamento. Le eccezioni di I/O chiudono comunque, come in [send].
     */
    private suspend fun query(cmd: String, timeoutMs: Long = 2500): List<String> =
        lock.withLock { exchange(cmd, timeoutMs, keepErrors = true) }

    /** Byte di dati di un PID mode 01, o null se l'auto non risponde. */
    suspend fun mode01(pid: String): List<Int>? = ObdData.dataBytes(query("01$pid"), "41$pid")

    /** PID mode 01 dichiarati dall'auto (0100, poi 0120/0140 se annunciati). */
    suspend fun supportedPids(): Set<String> {
        val all = mutableSetOf<String>()
        for (base in listOf(0x00, 0x20, 0x40)) {
            val pid = "%02X".format(base)
            if (base > 0 && pid !in all) break
            all += ObdData.supportedPids(query("01$pid", 4000), "41$pid", base)
        }
        return all
    }

    /** Freeze frame 00: codice che l'ha generato e valori registrati, o null se assente. */
    suspend fun freezeFrame(): Pair<String, Map<String, String>>? {
        val code = ObdData.dataBytes(query("020200"), "4202", skip = 1)?.take(2)
            ?.takeIf { it.size == 2 && (it[0] or it[1]) != 0 }
            ?.let { decodeDtc("%02X%02X".format(it[0], it[1])) } ?: return null
        val supported = ObdData.supportedPids(query("020000"), "4200", 0, skip = 1)
        val values = linkedMapOf<String, String>()
        for (p in ObdData.livePids) {
            if (p.pid !in supported) continue
            ObdData.dataBytes(query("02${p.pid}00"), "42${p.pid}", skip = 1)
                ?.let(p.decode)?.let { values[p.label] = it }
        }
        return code to values
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val ERRORS = setOf("NO DATA", "UNABLE TO CONNECT", "BUS INIT: ERROR", "CAN ERROR", "BUS ERROR", "?", "STOPPED")

        fun parseDtcs(raw: List<String>, pending: Boolean): List<String> {
            val reply = if (pending) "47" else "43"
            val lines = raw
                .map { it.replace(Regex("^\\d+:"), "").replace(" ", "") }
                // righe di sola lunghezza (es. "014", "00A": esadecimale) nelle risposte multi-frame CAN
                .filter { !(it.length == 3 && it.all { c -> c.isDigit() || c in 'A'..'F' }) }
            if (lines.isEmpty() || !lines[0].startsWith(reply)) return emptyList()
            var data = lines.mapIndexed { n, l -> if (n > 0 && l.startsWith(reply)) l.drop(2) else l }
                .joinToString("").drop(2)
            // shortcut: su CAN il primo byte è il numero di codici; euristica sulla lunghezza, da verificare su auto reali
            if (data.length % 4 == 2) data = data.drop(2)
            return data.chunked(4).filter { it.length == 4 && it != "0000" }.map { decodeDtc(it) }
        }

        fun parsePid(raw: List<String>, pid: String): Pair<Int, Int>? {
            val d = ObdData.dataBytes(raw, "41$pid")?.takeIf { it.isNotEmpty() } ?: return null
            return d[0] to (d.getOrNull(1) ?: 0)
        }

        /** "12.4V" → 12.4; null se la risposta non è una tensione. */
        fun parseVoltage(raw: List<String>): Double? =
            raw.firstNotNullOfOrNull { Regex("^(\\d{1,2}(?:\\.\\d)?)V$").find(it.trim())?.groupValues?.get(1)?.toDouble() }

        fun decodeDtc(hex4: String): String {
            val n = hex4[0].digitToInt(16)
            return "PCBU"[n shr 2].toString() + (n and 3) + hex4.drop(1)
        }
    }
}
