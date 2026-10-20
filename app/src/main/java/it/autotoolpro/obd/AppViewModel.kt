package it.autotoolpro.obd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.autotoolpro.obd.data.DbObject
import it.autotoolpro.obd.data.OpenResult
import it.autotoolpro.obd.data.TechDatabase
import it.autotoolpro.obd.obd.Elm327
import it.autotoolpro.obd.obd.ObdData
import it.autotoolpro.obd.obd.ObdReport
import it.autotoolpro.obd.ford.FordCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Schermate dell'app (navigazione a pila, senza librerie esterne). */
sealed interface Screen {
    data object Home : Screen
    data object Info : Screen
    data object Vehicles : Screen
    data object Ford : Screen
    data class Vehicle(val id: String) : Screen
    data class Dtcs(val query: String = "") : Screen
    data class Dtc(val code: String) : Screen
    data object Obd : Screen
    data object Database : Screen
    data class Table(val obj: DbObject) : Screen
    data class Search(val query: String) : Screen
    data class Row(val source: String, val columns: List<String>, val values: List<String?>) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val db = TechDatabase(app)

    var openResult by mutableStateOf<OpenResult?>(null)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Home)

    // ---- Diagnosi OBD (ELM327 Bluetooth)
    private val elm = Elm327()
    var obdBusy by mutableStateOf(false); private set
    var obdDevice by mutableStateOf<String?>(null); private set
    var obdGenericSupported by mutableStateOf(false); private set
    var obdError by mutableStateOf<String?>(null); private set
    var storedCodes by mutableStateOf<List<String>?>(null); private set
    var pendingCodes by mutableStateOf<List<String>?>(null); private set
    var obdReport by mutableStateOf<ObdReport?>(null); private set
    var reportSaveMessage by mutableStateOf<String?>(null); private set
    val live = mutableStateMapOf<String, String>()
    /** PID mode 01 dichiarati dall'auto; null finché non letti (si usa [ObdData.defaultLive]). */
    var supportedPids by mutableStateOf<Set<String>?>(null); private set
    var obdStatus by mutableStateOf<ObdData.Status?>(null); private set
    var distances by mutableStateOf<List<Pair<String, String>>?>(null); private set
    var freezeFrame by mutableStateOf<Pair<String, Map<String, String>>?>(null); private set
    var freezeFrameRead by mutableStateOf(false); private set

    /** Etichette dei valori live da mostrare, nell'ordine di [ObdData.livePids]. */
    val liveLabels: List<String>
        get() {
            val pids = supportedPids ?: ObdData.defaultLive
            return ObdData.livePids.filter { it.pid in pids || it.pid == "42" }.map { it.label }
        }

    init {
        viewModelScope.launch { openResult = db.open() }
    }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) = launchObd {
        obdDevice = null
        obdGenericSupported = false
        obdReport = null; reportSaveMessage = null
        clearReadings()
        obdGenericSupported = elm.connect(device)
        obdDevice = device.name ?: device.address
        if (obdGenericSupported) supportedPids = elm.supportedPids().takeIf { it.isNotEmpty() }
    }

    fun disconnect() {
        elm.close()
        obdGenericSupported = false
        obdDevice = null
        clearReadings()
    }

    private fun clearReadings() {
        storedCodes = null; pendingCodes = null; live.clear()
        supportedPids = null; obdStatus = null; distances = null
        freezeFrame = null; freezeFrameRead = false
    }

    /** Controllo completo in sola lettura: stato spia e monitor, contatori, codici, freeze frame. */
    fun readCodes() = launchObd {
        obdStatus = elm.mode01("01")?.let(ObdData::status)
        distances = buildList {
            ObdData.word(elm.mode01("21"))?.let { add("Km percorsi con spia accesa" to "$it km") }
            ObdData.word(elm.mode01("31"))?.let { add("Km dalla cancellazione dei codici" to "$it km") }
            elm.mode01("30")?.firstOrNull()?.let { add("Avviamenti a caldo dalla cancellazione" to "$it") }
            ObdData.word(elm.mode01("4E"))?.let { add("Minuti motore dalla cancellazione" to "$it min") }
        }
        storedCodes = elm.readDtcs(pending = false)
        pendingCodes = elm.readDtcs(pending = true)
        freezeFrame = elm.freezeFrame()
        freezeFrameRead = true
    }

    fun collectReport(vehicleId: String) = launchObd {
        val vehicle = FordCore.vehicles.single { it.id == vehicleId }
        val device = obdDevice ?: error("Collegare prima l'adattatore")
        obdReport = null; reportSaveMessage = null
        obdReport = elm.collectReport("${vehicle.name} ${vehicle.year} ${vehicle.engine}", device, Instant.now().toString())
        if (!elm.isConnected) disconnect()
    }

    fun saveReport(uri: Uri, text: String) {
        reportSaveMessage = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("Impossibile aprire il file scelto")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                reportSaveMessage = "Rapporto salvato"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportSaveMessage = "Salvataggio non riuscito: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Dati in tempo reale: gira finché la schermata che lo lancia resta attiva. */
    suspend fun pollLive() {
        while (obdDevice != null) {
            if (!obdBusy && obdGenericSupported) try {
                val pids = supportedPids ?: ObdData.defaultLive
                for (p in ObdData.livePids) {
                    if (obdBusy) break
                    val value = if (p.pid in pids) elm.mode01(p.pid)?.let(p.decode) else null
                    // Tensione: PID 42 se supportato, altrimenti misurata dall'adattatore.
                    val shown = value ?: if (p.pid == "42") elm.adapterVoltage()?.let { "%.1f V".format(it) } else null
                    if (shown != null) live[p.label] = shown else live.remove(p.label)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                obdError = e.message; disconnect()
            }
            delay(500)
        }
    }

    private fun launchObd(block: suspend () -> Unit) {
        if (obdBusy) return
        obdBusy = true; obdError = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                obdError = e.message ?: e.javaClass.simpleName
                if (!elm.isConnected) disconnect()
                if (obdDevice == null) elm.close()
            } finally {
                obdBusy = false
            }
        }
    }

    override fun onCleared() = elm.close()
}
