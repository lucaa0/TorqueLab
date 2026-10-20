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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val openBtSettings = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }

    Scaffold(topBar = { AppTopBar("Diagnosi auto", onBack = { vm.pop() }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.obdError?.let { Notice(it, Lamp.Fault, Icons.Outlined.ErrorOutline) }
            when {
                adapter == null -> BlockingState(Icons.Outlined.BluetoothDisabled, "Bluetooth non disponibile",
                    "Questo telefono non ha il Bluetooth: l'adattatore OBD non si può collegare.")
                !granted -> BlockingState(Icons.Outlined.Bluetooth, "Serve il permesso Bluetooth",
                    "Senza permesso l'app non vede l'adattatore abbinato.", "Consenti Bluetooth") {
                    ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
                !adapter.isEnabled -> BlockingState(Icons.Outlined.BluetoothDisabled, "Il Bluetooth è spento",
                    "Accendilo, poi torna qui.", "Apri impostazioni Bluetooth", openBtSettings)
                vm.obdDevice != null -> Connected(vm)
                vm.obdBusy -> Connecting()
                else -> DevicePicker(vm, adapter.bondedDevices.toList(), openBtSettings)
            }
            // Fuori da Connected: un rapporto parziale resta salvabile anche dopo la disconnessione.
            ReportResult(vm)
        }
    }
}

@Composable
private fun BlockingState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Panel(Modifier.padding(top = 24.dp), padding = 24.dp) {
        LampIcon(icon, Lamp.Warn, size = 56.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Hint(body)
        if (action != null) Button(onClick = onAction, Modifier.fillMaxWidth().height(52.dp)) { Text(action) }
    }
}

@Composable
private fun Connecting() {
    Panel(Modifier.padding(top = 24.dp), padding = 24.dp) {
        CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
        Text("Collegamento in corso", style = MaterialTheme.typography.headlineSmall)
        Hint("L'adattatore cerca il protocollo dell'auto: può richiedere fino a 15 secondi. Tieni il quadro acceso.")
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DevicePicker(vm: AppViewModel, devices: List<BluetoothDevice>, openBtSettings: () -> Unit) {
    Panel {
        Text("Prima di iniziare", style = MaterialTheme.typography.titleMedium)
        Step(1, "Inserisci l'adattatore ELM327 nella presa OBD, sotto il volante, e accendi il quadro.")
        Step(2, "Abbinalo nelle impostazioni Bluetooth del telefono. Il PIN di solito è 1234 o 0000.")
        Step(3, "Scegli l'adattatore qui sotto.")
    }

    SectionTitle("Adattatori abbinati")
    if (devices.isEmpty()) {
        Notice("Nessun dispositivo abbinato. Abbina l'adattatore, poi torna qui.", Lamp.Warn, Icons.Outlined.Info)
    } else {
        NavGroup(devices.map { d ->
            {
                NavRow(Icons.Outlined.Bluetooth, d.name ?: "Dispositivo senza nome", d.address,
                    trailing = { Text("Collega", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }) {
                    vm.connect(d)
                }
            }
        })
    }
    TextButton(onClick = openBtSettings) { Text("Abbina un nuovo adattatore") }
    Hint("Funzionano gli adattatori ELM327 Bluetooth classico. Quelli solo BLE o Wi-Fi non sono ancora supportati.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionTitle("Altro")
    NavGroup(listOf({ HistoryRow(vm) }))
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(28.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelLarge) }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HistoryRow(vm: AppViewModel) =
    NavRow(Icons.Outlined.History, "Storico letture",
        if (vm.readings.isEmpty()) "Nessuna lettura salvata" else "${vm.readings.size} letture salvate",
        enabled = !vm.obdBusy) { vm.push(Screen.History) }

private fun shortName(v: FordCore.Vehicle) = "${v.name.removePrefix("Ford ").substringBefore(' ')} ${v.year}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Connected(vm: AppViewModel) {
    LaunchedEffect(vm.obdDevice) { vm.pollLive() }
    val s = MaterialTheme.colorScheme

    // Collegamento
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = s.surfaceContainer) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Lamp.Ok)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Collegato", style = MaterialTheme.typography.labelMedium, color = s.tertiary)
                Text(vm.obdDevice.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { vm.disconnect() }) {
                Icon(Icons.Outlined.LinkOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scollega")
            }
        }
    }
    if (!vm.obdGenericSupported) Notice(
        "L'auto non ha confermato la diagnosi OBD standard: lettura codici e dati live sono sospesi. " +
            "Puoi comunque creare il rapporto di identificazione.", Lamp.Warn, Icons.Outlined.WarningAmber,
    )

    // Auto
    SectionTitle("Auto collegata")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        FordCore.vehicles.forEachIndexed { i, v ->
            SegmentedButton(
                selected = vm.selectedVehicle == v.id,
                onClick = { vm.selectedVehicle = v.id },
                enabled = !vm.obdBusy,
                shape = SegmentedButtonDefaults.itemShape(i, FordCore.vehicles.size),
            ) { Text(shortName(v)) }
        }
    }
    if (vm.selectedVehicle == null) Hint("Indicala per ritrovare le letture nello storico e per il rapporto.")

    // Azione principale
    Button(
        onClick = { vm.readCodes() },
        enabled = !vm.obdBusy && vm.obdGenericSupported,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(60.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        if (vm.obdBusy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = s.onPrimary)
            Spacer(Modifier.width(12.dp))
            Text("Lettura in corso…", style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(Icons.Outlined.Search, null)
            Spacer(Modifier.width(10.dp))
            Text("Leggi codici e stato", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Risultati
    val hasResults = vm.storedCodes != null || vm.obdStatus != null
    AnimatedVisibility(hasResults, enter = fadeIn() + expandVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Summary(vm)
            vm.storedCodes?.let { CodeList(vm, "Codici memorizzati", it, emptyText = "Nessun codice memorizzato") }
            vm.pendingCodes?.takeIf { it.isNotEmpty() }?.let { CodeList(vm, "Codici in attesa", it, emptyText = "") }
            vm.obdStatus?.let { Readiness(it) }
            vm.distances?.takeIf { it.isNotEmpty() }?.let { Counters(it) }
            if (vm.freezeFrameRead) vm.freezeFrame?.let { FreezeFrame(it) }
            vm.historyMessage?.let { Notice(it, Lamp.Warn, Icons.Outlined.WarningAmber) }
        }
    }

    // Dati live
    if (vm.obdGenericSupported) {
        SectionTitle("Dati in tempo reale") {
            Text(if (vm.obdBusy) "in pausa" else "dal vivo", style = MaterialTheme.typography.labelMedium,
                color = if (vm.obdBusy) s.outline else s.tertiary)
        }
        vm.liveLabels.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { k -> Gauge(k, vm.live[k], Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (vm.supportedPids != null) Hint("Sono mostrati solo i valori che l'auto dichiara di fornire.")
    }

    // Altro
    SectionTitle("Altro")
    NavGroup(listOf(
        { HistoryRow(vm) },
        {
            NavRow(Icons.Outlined.Description, "Rapporto di identificazione",
                if (vm.selectedVehicle == null) "Indica prima l'auto collegata"
                else "Risposte di adattatore e auto in un file di testo",
                enabled = !vm.obdBusy && vm.selectedVehicle != null) {
                vm.selectedVehicle?.let { vm.collectReport(it) }
            }
        },
    ))
}

@Composable
private fun Summary(vm: AppViewModel) {
    val status = vm.obdStatus
    val codes = (vm.storedCodes.orEmpty() + vm.pendingCodes.orEmpty()).distinct().size
    val milOn = status?.milOn == true
    val lamp = when {
        milOn -> Lamp.Fault
        codes > 0 -> Lamp.Warn
        else -> Lamp.Ok
    }
    Panel(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LampIcon(if (lamp == Lamp.Ok) Icons.Outlined.CheckCircle else Icons.Outlined.ReportProblem, lamp, size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        status == null -> "Spia motore non letta"
                        milOn -> "Spia motore accesa"
                        else -> "Spia motore spenta"
                    },
                    style = MaterialTheme.typography.headlineSmall, color = lampColor(lamp),
                )
                Text(
                    when (codes) { 0 -> "Nessun codice guasto"; 1 -> "1 codice guasto"; else -> "$codes codici guasto" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun CodeList(vm: AppViewModel, title: String, codes: List<String>, emptyText: String) {
    SectionTitle(title)
    if (codes.isEmpty()) {
        Notice(emptyText, Lamp.Ok, Icons.Outlined.CheckCircle)
        return
    }
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
private fun Readiness(status: ObdData.Status) {
    val done = status.monitors.count { it.complete }
    SectionTitle("Prontezza") {
        if (status.monitors.isNotEmpty()) Text("$done su ${status.monitors.size}", style = MaterialTheme.typography.labelLarge,
            color = if (done == status.monitors.size) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
    }
    Panel {
        Text(if (status.diesel) "Motore diesel" else "Motore a benzina", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status.monitors.isEmpty()) Hint("L'auto non dichiara monitor di autodiagnosi.")
        status.monitors.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (m.complete) Icons.Outlined.CheckCircle else Icons.Outlined.HourglassEmpty, null,
                    tint = lampColor(if (m.complete) Lamp.Ok else Lamp.Warn), modifier = Modifier.size(20.dp))
                Text(m.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(if (m.complete) "pronto" else "in corso", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (done < status.monitors.size) Hint(
            "I monitor «in corso» ripartono dopo una cancellazione dei codici o lo stacco della batteria. " +
                "Di solito si completano con qualche giorno di guida normale.")
    }
}

@Composable
private fun Counters(items: List<Pair<String, String>>) {
    SectionTitle("Contatori")
    Panel {
        items.forEach { (label, value) -> ValueRow(label, value) }
        Hint("Registrati dalla centralina motore, non dal contachilometri. Pochi km dalla cancellazione " +
            "indicano che i codici sono stati cancellati di recente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FreezeFrame(frame: Pair<String, Map<String, String>>) {
    SectionTitle("Freeze frame")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
            Text("Valori al momento di ${frame.first}", style = MaterialTheme.typography.titleMedium)
        }
        if (frame.second.isEmpty()) Hint("L'auto non fornisce i valori del freeze frame.")
        frame.second.forEach { (label, value) -> ValueRow(label, value) }
    }
}

@Composable
private fun ReportResult(vm: AppViewModel) {
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = vm.obdReport?.asText()
        if (uri != null && text != null) vm.saveReport(uri, text)
    }
    vm.obdReport?.let { report ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LampIcon(Icons.Outlined.Description, if (report.completed) Lamp.Ok else Lamp.Warn, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (report.completed) "Rapporto pronto" else "Rapporto parziale", style = MaterialTheme.typography.titleMedium)
                    Text(report.vehicle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Hint(if (report.completed) "Le informazioni non disponibili sono indicate nel file. Può contenere il VIN."
                else "Raccolta interrotta: puoi salvare la parte raccolta. Ricollega l'adattatore prima di riprovare.")
            FilledTonalButton(onClick = { saveReport.launch("rapporto-obd-${System.currentTimeMillis()}.txt") },
                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salva rapporto")
            }
            vm.reportSaveMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
        }
    }
}
