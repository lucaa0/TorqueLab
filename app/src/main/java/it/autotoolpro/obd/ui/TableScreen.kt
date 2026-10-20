package it.autotoolpro.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.R
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.DbObject
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

@Composable
fun TableScreen(vm: AppViewModel, obj: DbObject) {
    var filterText by rememberSaveable(obj.name) { mutableStateOf("") }
    var applied by rememberSaveable(obj.name) { mutableStateOf("") }
    var columns by remember(obj.name) { mutableStateOf<List<String>>(emptyList()) }
    val rows = remember(obj.name) { mutableStateListOf<List<String?>>() }
    var total by remember(obj.name) { mutableStateOf(-1L) }
    var loading by remember(obj.name) { mutableStateOf(false) }
    var error by remember(obj.name) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        loading = true
        error = null
        try {
            val page = vm.db.page(obj.name, applied, if (reset) 0 else rows.size, PAGE_SIZE)
            if (reset) rows.clear()
            columns = page.columns
            rows.addAll(page.rows)
            total = page.total
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    LaunchedEffect(obj.name, applied) { load(reset = true) }

    Scaffold(topBar = { AppTopBar(obj.name, onBack = vm::pop) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text(stringResource(R.string.filter_hint)) },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { applied = filterText }) { Text(stringResource(R.string.apply)) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applied = filterText }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (total >= 0) {
                Text(
                    stringResource(R.string.shown_of_total, rows.size, total.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            error?.let { ErrorText(it) }
            when {
                rows.isEmpty() && loading -> CenteredProgress()
                rows.isEmpty() && total == 0L -> MessageBox("", stringResource(R.string.no_rows))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(rows) { _, values ->
                        RowCard(columns, values) {
                            vm.push(Screen.Row(obj.name, columns, values))
                        }
                    }
                    if (rows.size < total) {
                        item {
                            OutlinedButton(
                                onClick = { scope.launch { load(reset = false) } },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.load_more)) }
                        }
                    }
                }
            }
        }
    }
}
