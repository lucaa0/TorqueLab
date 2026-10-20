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
        obdGenericSupported = elm.connect(device)
        obdDevice = device.name ?: device.address
        storedCodes = null; pendingCodes = null; live.clear()
    }

    fun disconnect() {
        elm.close()
        obdGenericSupported = false
        obdDevice = null; storedCodes = null; pendingCodes = null; live.clear()
    }

    fun readCodes() = launchObd {
        storedCodes = elm.readDtcs(pending = false)
        pendingCodes = elm.readDtcs(pending = true)
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
                elm.pid("0C")?.let { (a, b) -> live["Giri motore"] = "${(a * 256 + b) / 4} giri/min" }
                elm.pid("0D")?.let { (a, _) -> live["Velocità"] = "$a km/h" }
                elm.pid("05")?.let { (a, _) -> live["Temp. liquido"] = "${a - 40} °C" }
                elm.pid("04")?.let { (a, _) -> live["Carico motore"] = "${a * 100 / 255} %" }
                val volts = elm.pid("42")?.let { (a, b) -> (a * 256 + b) / 1000.0 } ?: elm.adapterVoltage()
                volts?.let { live["Tensione"] = "%.1f V".format(it) }
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
