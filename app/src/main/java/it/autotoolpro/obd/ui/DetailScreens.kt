package it.autotoolpro.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.BuildConfig
import it.autotoolpro.obd.R
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.DbInfo
import it.autotoolpro.obd.data.SearchHit

/** Dettaglio di una riga: tutte le colonne, valori selezionabili, provenienza indicata. */
@Composable
fun RowScreen(vm: AppViewModel, screen: Screen.Row) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.row_detail, screen.source), onBack = vm::pop) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    stringResource(R.string.read_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            items(screen.columns.indices.toList()) { i ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            screen.columns[i],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        SourceTag()
                    }
                    Spacer(Modifier.height(4.dp))
                    val v = screen.values[i]
                    SelectionContainer {
                        Text(
                            v ?: stringResource(R.string.empty_value),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, query: String) {
    var result by remember(query) { mutableStateOf<Result<List<SearchHit>?>?>(null) }
    LaunchedEffect(query) {
        result = runCatching { vm.db.search(query) }
    }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.search_results, query), onBack = vm::pop) }) { padding ->
        val r = result
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                r == null -> CenteredProgress()
                r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
                r.getOrNull() == null -> MessageBox("", stringResource(R.string.no_fts))
                r.getOrThrow()!!.isEmpty() -> MessageBox("", stringResource(R.string.no_results))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    r.getOrThrow()!!.forEach { hit ->
                        item(key = "h-" + hit.table) {
                            Text(
                                hit.table + " · " + hit.rows.size,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(hit.rows) { values ->
                            RowCard(hit.columns, values) {
                                vm.push(Screen.Row(hit.table, hit.columns, values))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoScreen(vm: AppViewModel) {
    val info by produceState<Result<DbInfo>?>(null) { value = runCatching { vm.db.info() } }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.info), onBack = vm::pop) }) { padding ->
        val r = info
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                r == null -> CenteredProgress()
                r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
                else -> {
                    val i = r.getOrThrow()
                    val yes = stringResource(R.string.yes)
                    val no = stringResource(R.string.no)
                    val lines = listOf(
                        stringResource(R.string.info_app) to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        stringResource(R.string.info_sqlite) to i.sqliteVersion,
                        stringResource(R.string.info_fts5) to if (i.fts5) yes else no,
                        stringResource(R.string.info_json) to if (i.json) yes else no,
                        stringResource(R.string.info_objects) to "${i.tables} / ${i.views} / ${i.fts}",
                        stringResource(R.string.info_user_version) to i.userVersion.toString(),
                        stringResource(R.string.info_size) to "%.1f MB".format(i.sizeBytes / 1_048_576.0),
                        stringResource(R.string.info_sha256) to i.sha256,
                    )
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(lines) { (label, value) ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                SelectionContainer {
                                    Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
