package it.autotoolpro.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.ford.FordCore
import kotlinx.coroutines.delay

private enum class DemoOperation { Odometer, Pats }

@Composable
fun FordScreen(vm: AppViewModel) {
    var selected by rememberSaveable { mutableStateOf(FordCore.vehicles.first().id) }
    // Not saveable: a rotation mid-run drops the simulation instead of restarting it.
    var operation by remember { mutableStateOf<DemoOperation?>(null) }
    var failure by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf("Scegli una funzione per iniziare.") }
    var odometer by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    val idle = operation == null
    // Demo never calls Elm327 or the real Ford dispatcher.
    LaunchedEffect(operation, selected) {
        val op = operation ?: return@LaunchedEffect
        val steps = if (op == DemoOperation.Pats) listOf(
            "Connessione simulata…", "Verifica prerequisiti simulati…", "Elaborazione dimostrativa…"
        ) else listOf("Connessione simulata…", "Lettura dimostrativa…")
        for (step in steps) {
            progress = step
            delay(900)
        }
        result = if (failure) {
            "Errore simulato: centralina non disponibile. Puoi riprovare."
        } else if (op == DemoOperation.Odometer) {
            odometer = if (selected == FordCore.vehicles.first().id) "123.456" else "98.765"
            "Lettura simulata completata. Valore fittizio, non letto dall'auto."
        } else {
            "Simulazione PATS completata: 2 chiavi dimostrative. Nessuna chiave reale programmata."
        }
        progress = ""
        operation = null
    }
    Scaffold(topBar = { AppTopBar("Ford Fiesta e Puma", onBack = { vm.pop() }) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PROTOTIPO · SIMULAZIONE", style = MaterialTheme.typography.titleMedium)
            Text("PATS e odometro funzionano qui con dati fittizi, senza adattatore. Compatibilità reale da verificare.")
            Text("Profili veicolo", style = MaterialTheme.typography.headlineSmall)
            FordCore.vehicles.forEach { vehicle ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selected == vehicle.id,
                            enabled = idle,
                            onClick = {
                                selected = vehicle.id
                                odometer = ""
                                result = "Veicolo selezionato. Scegli una funzione."
                            },
                            label = { Text(if (selected == vehicle.id) "Selezionato" else "Seleziona") },
                        )
                        Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                        Text("${vehicle.year} · ${vehicle.engine}")
                        Text("Dati del veicolo indicati dal proprietario. Centraline e compatibilità delle procedure da verificare.")
                    }
                }
            }
            Text("Funzioni disponibili", style = MaterialTheme.typography.titleMedium)
            Text("Diagnosi OBD generica: lettura codici guasto e dati motore, se supportati dall'auto e dall'adattatore.")
            OutlinedButton(enabled = idle, onClick = { vm.push(Screen.Obd) }) { Text("Apri diagnosi OBD") }
            Text("Funzioni Ford simulate", style = MaterialTheme.typography.titleMedium)
            Text("Veicolo demo: ${FordCore.vehicles.first { it.id == selected }.name}")
            FilterChip(
                selected = failure,
                enabled = idle,
                onClick = { failure = !failure },
                label = { Text(if (failure) "Scenario: errore" else "Scenario: successo") },
            )
            Text(if (odometer.isEmpty()) "Odometro: nessuna lettura" else "Odometro: $odometer km · DATO FITTIZIO")
            Button(enabled = idle, onClick = {
                odometer = ""
                operation = DemoOperation.Odometer
            }) { Text("Simula lettura odometro") }
            Button(enabled = idle, onClick = {
                operation = DemoOperation.Pats
            }) { Text("Avvia simulazione PATS") }
            if (idle) {
                Text(result)
            } else {
                CircularProgressIndicator()
                Text(progress.ifEmpty { "Avvio…" })
                OutlinedButton(onClick = {
                    operation = null
                    progress = ""
                    result = "Simulazione annullata. Puoi riprovare."
                }) { Text("Annulla simulazione") }
            }
        }
    }
}
