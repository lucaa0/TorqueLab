package it.autotoolpro.obd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.R
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.DbObject
import it.autotoolpro.obd.data.ObjectKind

@Composable
fun DatabaseScreen(vm: AppViewModel) {
    val objects by produceState<Result<List<DbObject>>?>(null) {
        value = runCatching { vm.db.listObjects() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val submit = { if (query.isNotBlank()) vm.push(Screen.Search(query.trim())) }

    Scaffold(
        topBar = {
            AppTopBar(title = "Database", onBack = { vm.pop() }, actions = {
                IconButton(onClick = { vm.push(Screen.Info) }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info))
                }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = submit) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.sections),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val r = objects
            when {
                r == null -> CenteredProgress()
                r.isFailure -> ErrorText(r.exceptionOrNull()?.message ?: "?")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(r.getOrThrow(), key = { it.name }) { obj ->
                        ObjectCard(obj) { vm.push(Screen.Table(obj)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectCard(obj: DbObject, onClick: () -> Unit) {
    val kind = when (obj.kind) {
        ObjectKind.TABLE -> stringResource(R.string.kind_table)
        ObjectKind.VIEW -> stringResource(R.string.kind_view)
        ObjectKind.FTS -> stringResource(R.string.kind_fts)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(obj.name, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (obj.rows >= 0) {
                Text(
                    stringResource(R.string.rows_count, obj.rows.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
