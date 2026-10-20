package it.autotoolpro.obd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: AppViewModel) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AutoToolPro", style = MaterialTheme.typography.headlineLarge, color = s.onBackground)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(if (vm.obdDevice != null) Lamp.Ok else Lamp.Off)
                    Text(vm.obdDevice?.let { "Collegato a $it" } ?: "Adattatore non collegato",
                        style = MaterialTheme.typography.bodyMedium, color = s.onSurfaceVariant)
                }
            }
            IconButton(onClick = { vm.push(Screen.Info) }) {
                Icon(Icons.Outlined.Info, contentDescription = "Informazioni", tint = s.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(4.dp))
        DiagnosisHero(vm)

        SectionTitle("Strumenti")
        NavGroup(listOf(
            { NavRow(Icons.Outlined.History, "Storico letture",
                if (vm.readings.isEmpty()) "Le letture dei codici vengono salvate qui" else "${vm.readings.size} letture salvate") {
                vm.push(Screen.History) } },
            { NavRow(Icons.Outlined.ErrorOutline, "Codici guasto", "Cerca un codice, es. P0420: cause e controlli") {
                vm.push(Screen.Dtcs()) } },
            { NavRow(Icons.Outlined.Search, "Veicoli", "Marca e modello: chiavi, strumenti, programmatori") {
                vm.push(Screen.Vehicles) } },
            { NavRow(Icons.Outlined.Key, "Ford Fiesta e Puma", "Profili e prototipo PATS e odometro") {
                vm.push(Screen.Ford) } },
            { NavRow(Icons.Outlined.Storage, "Database completo", "Tutte le tabelle, in sola lettura") {
                vm.push(Screen.Database) } },
        ))

        Text("Funziona offline. Legge soltanto: non modifica l'auto.",
            style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 24.dp))
    }
}

/** Azione principale: grande, ambra, con lo stato dell'ultima lettura. */
@Composable
private fun DiagnosisHero(vm: AppViewModel) {
    val s = MaterialTheme.colorScheme
    val last = vm.readings.firstOrNull()
    Surface(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.push(Screen.Obd) },
        shape = MaterialTheme.shapes.extraLarge,
        color = s.primary,
        contentColor = s.onPrimary,
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
            Column {
                Text("Diagnosi auto", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (vm.obdDevice != null) "Adattatore collegato: dati live e lettura codici"
                    else "Collega l'adattatore OBD e leggi codici, spia motore e dati live",
                    style = MaterialTheme.typography.bodyMedium, color = s.onPrimary.copy(alpha = 0.78f),
                )
            }
            if (last != null) {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last.timestamp))
                val codes = (last.stored + last.pending).distinct().size
                Surface(shape = MaterialTheme.shapes.medium, color = s.onPrimary.copy(alpha = 0.10f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.DirectionsCar, null, modifier = Modifier.size(18.dp))
                        Text(
                            "Ultima lettura $date · ${last.vehicle} · " +
                                if (codes == 0) "nessun codice" else if (codes == 1) "1 codice" else "$codes codici",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
