package it.autotoolpro.obd.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.CodeDiff
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

    Scaffold(topBar = {
        AppTopBar("Storico letture", onBack = { vm.pop() }) {
            if (all.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Cancella storico")
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vm.historyMessage?.let { Notice(it, Lamp.Fault, Icons.Outlined.ReportProblem) }
            if (all.isEmpty()) {
                Panel(Modifier.padding(top = 24.dp), padding = 24.dp) {
                    LampIcon(Icons.Outlined.History, Lamp.Off, size = 56.dp)
                    Text("Ancora nessuna lettura", style = MaterialTheme.typography.headlineSmall)
                    Hint("Ogni volta che premi «Leggi codici e stato» il risultato viene salvato qui, " +
                        "solo su questo telefono. Così puoi vedere se un codice torna dopo una riparazione.")
                }
                return@Column
            }
            if (vehicles.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tutte") })
                vehicles.forEach { v -> FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(v) }) }
            }
            shown.forEach { r -> ReadingCard(vm, r, ReadingHistory.diff(all, r)) }
            Hint("Sono conservate le ultime ${ReadingHistory.MAX} letture.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text("Cancellare lo storico?") },
        text = { Text("Vengono eliminate tutte le ${all.size} letture salvate su questo telefono. Non si può annullare.") },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; vm.clearHistory() }) {
                Text("Cancella", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingCard(vm: AppViewModel, r: Reading, diff: CodeDiff?) {
    val s = MaterialTheme.colorScheme
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.timestamp))
    val codes = (r.stored + r.pending).distinct()
    val lamp = when {
        r.milOn == true -> Lamp.Fault
        codes.isNotEmpty() -> Lamp.Warn
        else -> Lamp.Ok
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LampIcon(if (lamp == Lamp.Ok) Icons.Outlined.CheckCircle else Icons.Outlined.ReportProblem, lamp, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(date, style = MaterialTheme.typography.titleMedium)
                Text(r.vehicle, style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant)
            }
            Text(
                when (codes.size) { 0 -> "nessun codice"; 1 -> "1 codice"; else -> "${codes.size} codici" },
                style = MaterialTheme.typography.labelLarge, color = lampColor(lamp),
            )
        }
        if (codes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            codes.forEach { code ->
                AssistChip(
                    onClick = { vm.push(Screen.Dtc(code)) },
                    label = {
                        Text(code + if (code in r.pending && code !in r.stored) " · in attesa" else "",
                            fontWeight = FontWeight.SemiBold)
                    },
                    colors = AssistChipDefaults.assistChipColors(labelColor = s.onSurface),
                )
            }
        }
        diff?.let { d ->
            if (d.appeared.isEmpty() && d.disappeared.isEmpty()) {
                Hint("Uguale alla lettura precedente")
            } else {
                if (d.appeared.isNotEmpty()) DiffLine(Icons.Outlined.ArrowUpward, "Nuovi: ${d.appeared.joinToString()}", Lamp.Fault)
                if (d.disappeared.isNotEmpty()) DiffLine(Icons.Outlined.ArrowDownward, "Spariti: ${d.disappeared.joinToString()}", Lamp.Ok)
            }
        }
        if (r.incompleteMonitors.isNotEmpty() || r.counters.isNotEmpty() || r.freezeFrameCode != null) {
            HorizontalDivider(color = s.outlineVariant)
            if (r.incompleteMonitors.isNotEmpty())
                Hint("Monitor in corso: ${r.incompleteMonitors.joinToString()}")
            r.counters.forEach { (k, v) -> ValueRow(k, v) }
            r.freezeFrameCode?.let { ValueRow("Freeze frame", it) }
        }
        Text("Adattatore: ${r.device}", style = MaterialTheme.typography.labelSmall, color = s.onSurfaceVariant)
    }
}

@Composable
private fun DiffLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, lamp: Lamp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = lampColor(lamp), modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = lampColor(lamp))
    }
}
