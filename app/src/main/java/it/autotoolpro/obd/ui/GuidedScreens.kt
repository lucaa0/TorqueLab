package it.autotoolpro.obd.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.Page

private const val NA = "Da verificare"

private fun like(q: String) = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

private fun nice(col: String) = col.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun SearchList(
    vm: AppViewModel,
    title: String,
    hint: String,
    initial: String,
    sql: String,
    row: @Composable (List<String?>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initial) }
    var result by remember(query, sql) { mutableStateOf<Result<Page>?>(null) }
    LaunchedEffect(query, sql) {
        result = runCatching { vm.db.select(sql, listOf(like(query))) }
    }
    Scaffold(topBar = { AppTopBar(title, onBack = { vm.pop() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(hint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val r = result
            when {
                r == null -> CenteredProgress()
                r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
                r.getOrThrow().rows.isEmpty() -> MessageBox("Nessun risultato", "Prova con meno lettere.")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { items(r.getOrThrow().rows) { row(it) } }
            }
        }
    }
}

@Composable
private fun ItemCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
}

// ------------------------------------------------------------------ veicoli

@Composable
fun VehiclesScreen(vm: AppViewModel) = SearchList(
    vm, "Veicoli", "Marca, modello o motore (es. fiat panda)", "",
    "SELECT id, marca, modello, anno_da, anno_a, motorizzazione FROM veicoli " +
        "WHERE eliminato = 0 AND (marca || ' ' || modello || ' ' || IFNULL(motorizzazione,'')) LIKE ?1 ESCAPE '\\' " +
        "ORDER BY marca, modello, anno_da LIMIT 200",
) { r ->
    ItemCard({ vm.push(Screen.Vehicle(r[0].orEmpty())) }) {
        Text("${r[1]} ${r[2]}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val years = if (r[3] == r[4] || r[4].isNullOrBlank()) r[3] else "${r[3]}–${r[4]}"
        Text("$years · ${r[5].orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val HIDDEN = setOf("id", "id_esterno", "stato_record", "campi_da_verificare", "fonti", "ultima_verifica", "strumenti_compatibili", "eliminato")

@Composable
fun VehicleScreen(vm: AppViewModel, id: String) {
    var data by remember(id) { mutableStateOf<Result<Pair<Page, Page>>?>(null) }
    LaunchedEffect(id) {
        data = runCatching {
            vm.db.select("SELECT * FROM veicoli WHERE id = ?1", listOf(id)) to
                vm.db.select(
                    "SELECT strumento_id, funzione, stato FROM compat_strumenti WHERE veicolo_id = ?1 ORDER BY stato DESC, strumento_id",
                    listOf(id),
                )
        }
    }
    Scaffold(topBar = { AppTopBar("Scheda veicolo", onBack = { vm.pop() }) }) { padding ->
        val r = data
        when {
            r == null -> CenteredProgress(Modifier.padding(padding))
            r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
            else -> {
                val (veh, compat) = r.getOrThrow()
                val v = veh.rows.firstOrNull()
                if (v == null) { MessageBox("Veicolo non trovato", id, Modifier.padding(padding)); return@Scaffold }
                val c = veh.columns
                fun get(name: String) = v[c.indexOf(name)]
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${get("marca")} ${get("modello")}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(get("motorizzazione").orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Section("Dati tecnici") {
                        c.forEachIndexed { i, name ->
                            if (name !in HIDDEN && name !in setOf("marca", "modello", "motorizzazione", "note")) {
                                Field(nice(name), v[i])
                            }
                        }
                    }
                    Section("Strumenti e programmatori compatibili") {
                        if (compat.rows.isEmpty()) Text("Nessuna compatibilità registrata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        compat.rows.forEach { (tool, fn, st) ->
                            val ok = st == "verificata"
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(tool.orEmpty(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${fn.orEmpty()} · " + if (ok) "verificata" else "da confermare",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    get("note")?.takeIf { it.isNotBlank() }?.let { Section("Note") { Text(it) } }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    val missing = value.isNullOrBlank() || value == NA
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(
            if (missing) "da verificare" else value!!,
            style = MaterialTheme.typography.bodyMedium,
            color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f),
        )
    }
}

// ------------------------------------------------------------------ codici guasto

fun severityColor(g: String?) = when (g) {
    // Stessi toni delle spie del tema (Theme.kt).
    "alta" -> Color(0xFFFF6259)
    "media" -> Color(0xFFFFB524)
    "bassa" -> Color(0xFF63D69E)
    else -> Color(0xFFA9B1BB)
}

@Composable
fun DtcsScreen(vm: AppViewModel, initial: String) = SearchList(
    vm, "Codici guasto", "Codice o parola (es. P0420, catalizzatore)", initial,
    "SELECT codice, descrizione, gravita FROM diagnostica WHERE eliminato = 0 AND " +
        "(codice || ' ' || IFNULL(descrizione,'') || ' ' || IFNULL(sistema,'')) LIKE ?1 ESCAPE '\\' ORDER BY codice LIMIT 200",
) { r -> DtcCard(r[0].orEmpty(), r[1], r[2]) { vm.push(Screen.Dtc(r[0].orEmpty())) } }

@Composable
fun DtcCard(code: String, description: String?, severity: String?, onClick: () -> Unit) {
    ItemCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(code, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TabularNums),
                color = severityColor(severity), modifier = Modifier.weight(1f))
            if (severity != null) Surface(shape = MaterialTheme.shapes.small, color = severityColor(severity).copy(alpha = 0.14f),
                contentColor = severityColor(severity)) {
                Text("gravità $severity", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Text(description ?: "Non presente nel database", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DtcScreen(vm: AppViewModel, code: String) {
    var data by remember(code) { mutableStateOf<Result<Page>?>(null) }
    LaunchedEffect(code) {
        data = runCatching { vm.db.select("SELECT * FROM diagnostica WHERE codice = ?1", listOf(code)) }
    }
    Scaffold(topBar = { AppTopBar(code, onBack = { vm.pop() }) }) { padding ->
        val r = data
        when {
            r == null -> CenteredProgress(Modifier.padding(padding))
            r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
            r.getOrThrow().rows.isEmpty() -> MessageBox(code, "Questo codice non è nel database.", Modifier.padding(padding))
            else -> {
                val p = r.getOrThrow(); val v = p.rows[0]
                fun get(n: String) = v[p.columns.indexOf(n)]?.takeIf { it.isNotBlank() }
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(get("descrizione").orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(get("sistema"), get("gravita")?.let { "gravità $it" }, get("tipo")).joinToString(" · "),
                        color = severityColor(get("gravita")),
                    )
                    listOf(
                        "Possibili cause" to "possibili_cause",
                        "Controlli consigliati" to "controlli_consigliati",
                        "Procedura di verifica" to "procedura_verifica",
                        "Note" to "note",
                    ).forEach { (t, col) -> get(col)?.let { Section(t) { Text(it) } } }
                    Text(
                        "Indicazioni orientative: confrontare sempre con i dati del costruttore.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
