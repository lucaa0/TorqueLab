package it.autotoolpro.obd.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.ford.FordCore
import it.autotoolpro.obd.obd.ObdData

private fun needsPermission() = Build.VERSION.SDK_INT >= 31

private fun hasPermission(c: Context) = !needsPermission() ||
    c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
fun ObdScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = vm.obdReport?.asText()
        if (uri != null && text != null) vm.saveReport(uri, text)
    }

    Scaffold(topBar = {
        AppTopBar("Diagnosi auto", onBack = { vm.pop() })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.obdError?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f))) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            when {
                adapter == null -> Hint("Questo dispositivo non ha il Bluetooth.")
                !granted -> {
                    Hint("Per collegare l'adattatore OBD serve il permesso Bluetooth.")
                    Button(onClick = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Consenti Bluetooth") }
                }
                !adapter.isEnabled -> {
                    Hint("Il Bluetooth è spento. Accendilo e torna qui.")
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Apri impostazioni Bluetooth") }
                }
                vm.obdDevice != null -> Connected(vm)
                vm.obdBusy -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                        Text("Connessione in corso… (può richiedere qualche secondo)")
                    }
                }
                else -> DevicePicker(vm, adapter.bondedDevices.toList())
            }
            vm.obdReport?.let { report ->
                Text("Rapporto di identificazione", style = MaterialTheme.typography.titleMedium)
                Text(report.vehicle)
                Hint(if (report.completed) "Raccolta terminata. Le informazioni non disponibili sono indicate nel file."
                    else "Raccolta interrotta: puoi salvare il rapporto parziale. Riconnetti l'adattatore prima di riprovare.")
                Hint("Contiene le risposte dell'adattatore e dell'auto, incluso il VIN se disponibile.")
                OutlinedButton(onClick = { saveReport.launch("rapporto-obd-${System.currentTimeMillis()}.txt") }) {
                    Text("Salva rapporto")
                }
            }
            vm.reportSaveMessage?.let { Text(it) }
            OutlinedButton(onClick = { vm.push(Screen.History) }, enabled = !vm.obdBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Storico letture (${vm.readings.size})")
            }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@SuppressLint("MissingPermission")
@Composable
private fun DevicePicker(vm: AppViewModel, devices: List<BluetoothDevice>) {
    val ctx = LocalContext.current
    Text("1. Inserisci l'adattatore ELM327 nella presa OBD e accendi il quadro.\n2. Abbinalo dalle impostazioni Bluetooth (PIN 1234 o 0000).\n3. Scegli l'adattatore qui sotto.",
        style = MaterialTheme.typography.bodyMedium)
    Text("Adattatori abbinati", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (devices.isEmpty()) Hint("Nessun dispositivo abbinato.")
    devices.forEach { d ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().clickable { vm.connect(d) },
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(d.name ?: "Sconosciuto", fontWeight = FontWeight.Bold)
                Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Abbina un nuovo dispositivo") }
    Hint("Sono supportati gli adattatori ELM327 Bluetooth classico. Quelli solo BLE o Wi-Fi non funzionano ancora.")
}

@Composable
private fun Connected(vm: AppViewModel) {
    LaunchedEffect(vm.obdDevice) { vm.pollLive() }

    Text("● Collegato a ${vm.obdDevice}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
    if (!vm.obdGenericSupported) Hint("La diagnosi OBD generica non è confermata. Puoi comunque raccogliere le risposte dell'adattatore e dell'auto nel rapporto.")

    val keys = vm.liveLabels
    if (vm.supportedPids != null) Hint("Sono mostrati solo i valori che l'auto dichiara di fornire (${keys.size}).")
    keys.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { k -> LiveTile(k, vm.live[k] ?: "—", Modifier.weight(1f)) }
            if (pair.size == 1) Column(Modifier.weight(1f)) {}
        }
    }

    Text("Auto collegata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Hint("Indicala tu: serve per il rapporto e per confrontare le letture nello storico.")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FordCore.vehicles.forEach { vehicle ->
            FilterChip(selected = vm.selectedVehicle == vehicle.id, enabled = !vm.obdBusy,
                onClick = { vm.selectedVehicle = vehicle.id }, label = { Text("${vehicle.name} ${vehicle.year}") })
        }
    }

    Button(onClick = { vm.readCodes() }, enabled = !vm.obdBusy && vm.obdGenericSupported, modifier = Modifier.fillMaxWidth()) {
        Text(if (vm.obdBusy) "Lettura in corso…" else "Leggi codici e stato")
    }

    vm.obdStatus?.let { StatusCard(it) }
    vm.distances?.let { Distances(it) }
    vm.storedCodes?.let { CodeList(vm, "Codici memorizzati", it) }
    vm.pendingCodes?.let { CodeList(vm, "Codici in attesa", it) }
    if (vm.freezeFrameRead) FreezeFrame(vm.freezeFrame)
    vm.historyMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Hint("La cancellazione dei codici non è prevista: serve prima capire e risolvere il guasto.")

    Text("Identifica adattatore e auto", style = MaterialTheme.typography.titleMedium)
    Hint(if (vm.selectedVehicle == null) "Indica prima l'auto collegata, qui sopra."
        else "Crea un rapporto con le informazioni disponibili. Non modifica chiavi o chilometraggio.")
    OutlinedButton(onClick = { vm.selectedVehicle?.let { vm.collectReport(it) } },
        enabled = !vm.obdBusy && vm.selectedVehicle != null, modifier = Modifier.fillMaxWidth()) {
        Text("Crea rapporto di identificazione")
    }

    OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Scollega") }
}

@Composable
private fun LiveTile(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CodeList(vm: AppViewModel, title: String, codes: List<String>) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (codes.isEmpty()) Text("Nessun codice ✓", color = MaterialTheme.colorScheme.tertiary)
    codes.forEach { code ->
        var info by remember(code) { mutableStateOf<List<String?>?>(null) }
        LaunchedEffect(code) {
            info = runCatching {
                vm.db.select("SELECT descrizione, gravita FROM diagnostica WHERE codice = ?1 AND eliminato = 0", listOf(code)).rows.firstOrNull()
            }.getOrNull()
        }
        DtcCard(code, info?.getOrNull(0), info?.getOrNull(1)) { vm.push(Screen.Dtc(code)) }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun StatusCard(status: ObdData.Status) {
    Text("Stato e prontezza", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    InfoCard {
        Text(
            if (status.milOn) "Spia motore ACCESA" else "Spia motore spenta",
            fontWeight = FontWeight.Bold,
            color = if (status.milOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
        Text("Codici che accendono la spia: ${status.dtcCount}")
        Text(if (status.diesel) "Motore: diesel (accensione per compressione)" else "Motore: benzina (accensione comandata)",
            style = MaterialTheme.typography.bodySmall)
        if (status.monitors.isEmpty()) Hint("Nessun monitor dichiarato dall'auto.")
        status.monitors.forEach { m ->
            Text("${if (m.complete) "✓" else "…"} ${m.name}: ${if (m.complete) "completato" else "non completato"}",
                color = if (m.complete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pending = status.monitors.count { !it.complete }
        if (pending > 0) Hint("$pending monitor non completati: succede dopo una cancellazione dei codici o lo stacco della batteria. " +
            "Di solito si completano con qualche giorno di guida normale.")
    }
}

@Composable
private fun Distances(items: List<Pair<String, String>>) {
    if (items.isEmpty()) return
    Text("Contatori", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    InfoCard {
        items.forEach { (label, value) -> Text("$label: $value") }
        Hint("Valori registrati dalla centralina motore, non dal contachilometri. Pochi km dalla cancellazione indicano che i codici sono stati cancellati di recente.")
    }
}

@Composable
private fun FreezeFrame(frame: Pair<String, Map<String, String>>?) {
    Text("Freeze frame", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (frame == null) {
        Hint("Nessun freeze frame memorizzato.")
        return
    }
    InfoCard {
        Text("Valori registrati quando è comparso ${frame.first}", fontWeight = FontWeight.Bold)
        if (frame.second.isEmpty()) Hint("L'auto non fornisce i valori del freeze frame.")
        frame.second.forEach { (label, value) -> Text("$label: $value") }
    }
}