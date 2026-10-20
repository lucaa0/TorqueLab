package it.autotoolpro.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.Reading
import it.autotoolpro.obd.data.ReadingHistory
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: AppViewModel) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val all = vm.readings
    val vehicles = all.map { it.vehicle }.distinct()
    val shown = all.filter { filter == null || it.vehicle == filter }

    Scaffold(topBar = { AppTopBar("Storico letture", onBack = { vm.pop() }) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ogni «Leggi codici e stato» viene salvato qui, solo su questo telefono. " +
                "Ultime ${ReadingHistory.MAX} letture.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (all.isEmpty()) Text("Nessuna lettura salvata.")
            if (vehicles.size > 1) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tutte") })
                vehicles.forEach { v -> FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(v) }) }
            }
            shown.forEach { r -> ReadingCard(vm, r, ReadingHistory.diff(all, r)) }
            vm.historyMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (all.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancella storico")
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Cancellare lo storico?") },
        text = { Text("Vengono eliminate tutte le ${all.size} letture salvate su questo telefono. Non si può annullare.") },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Cancella") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
    )
}

@Composable
private fun ReadingCard(vm: AppViewModel, r: Reading, diff: it.autotoolpro.obd.data.CodeDiff?) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.timestamp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, fontWeight = FontWeight.Bold)
            Text("${r.vehicle} · ${r.device}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when (r.milOn) { true -> "Spia motore accesa"; false -> "Spia motore spenta"; null -> "Spia motore: non letta" },
                color = if (r.milOn == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            CodeLine(vm, "Memorizzati", r.stored)
            CodeLine(vm, "In attesa", r.pending)
            diff?.let {
                when {
                    it.appeared.isEmpty() && it.disappeared.isEmpty() -> Text("Uguale alla lettura precedente",
                        style = MaterialTheme.typography.bodySmall)
                    else -> {
                        if (it.appeared.isNotEmpty()) Text("Nuovi rispetto alla precedente: ${it.appeared.joinToString()}",
                            color = MaterialTheme.colorScheme.error)
                        if (it.disappeared.isNotEmpty()) Text("Non più presenti: ${it.disappeared.joinToString()}",
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            if (r.incompleteMonitors.isNotEmpty()) Text("Monitor non completati: ${r.incompleteMonitors.joinToString()}",
                style = MaterialTheme.typography.bodySmall)
            r.counters.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
            r.freezeFrameCode?.let { Text("Freeze frame: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CodeLine(vm: AppViewModel, label: String, codes: List<String>) {
    if (codes.isEmpty()) {
        Text("$label: nessuno")
        return
    }
    Text("$label:")
    codes.forEach { code ->
        TextButton(onClick = { vm.push(Screen.Dtc(code)) }) { Text(code) }
    }
}
