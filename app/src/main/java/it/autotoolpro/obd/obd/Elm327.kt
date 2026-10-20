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
    suspend fun readDtcs(pending: Boolean): List<String> {
        val reply = if (pending) "47" else "43"
        val lines = send(if (pending) "07" else "03", 6000)
            .map { it.replace(Regex("^\\d+:"), "").replace(" ", "") }
            // righe di sola lunghezza (es. "014") nelle risposte multi-frame CAN
            .filter { !(it.length == 3 && it.all { c -> c.isDigit() }) }
        if (lines.isEmpty() || !lines[0].startsWith(reply)) return emptyList()
        var data = lines.mapIndexed { n, l -> if (n > 0 && l.startsWith(reply)) l.drop(2) else l }
            .joinToString("").drop(2)
        // shortcut: su CAN il primo byte è il numero di codici; euristica sulla lunghezza, da verificare su auto reali
        if (data.length % 4 == 2) data = data.drop(2)
        return data.chunked(4).filter { it.length == 4 && it != "0000" }.map { decodeDtc(it) }
    }

    /** Valore di un PID mode 01 (byte A, B) o null se non supportato. */
    suspend fun pid(pid: String): Pair<Int, Int>? {
        val line = send("01$pid", 2500).joinToString("").replace(" ", "")
        val at = line.indexOf("41$pid")
        if (at < 0) return null
        val d = line.substring(at + 4)
        val a = d.take(2).toIntOrNull(16) ?: return null
        return a to (d.drop(2).take(2).toIntOrNull(16) ?: 0)
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val ERRORS = setOf("NO DATA", "UNABLE TO CONNECT", "BUS INIT: ERROR", "CAN ERROR", "BUS ERROR", "?", "STOPPED")

        fun decodeDtc(hex4: String): String {
            val n = hex4[0].digitToInt(16)
            return "PCBU"[n shr 2].toString() + (n and 3) + hex4.drop(1)
        }
    }
}
