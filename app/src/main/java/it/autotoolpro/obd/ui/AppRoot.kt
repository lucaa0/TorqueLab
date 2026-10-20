package it.autotoolpro.obd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.R
import it.autotoolpro.obd.Screen
import it.autotoolpro.obd.data.OpenResult

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    when (val result = vm.openResult) {
        null -> Scaffold { p -> CenteredProgress(Modifier.padding(p)) }
        OpenResult.Missing -> Scaffold { p ->
            MessageBox(stringResource(R.string.db_missing_title), stringResource(R.string.db_missing_body), Modifier.padding(p))
        }
        is OpenResult.Error -> Scaffold { p ->
            MessageBox(stringResource(R.string.db_error_title), result.message, Modifier.padding(p))
        }
        OpenResult.Ok -> Navigator(vm)
    }
}

@Composable
private fun Navigator(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }
    when (val screen = vm.stack.last()) {
        Screen.Home -> HomeScreen(vm)
        Screen.Vehicles -> VehiclesScreen(vm)
        Screen.Ford -> FordScreen(vm)
        is Screen.Vehicle -> VehicleScreen(vm, screen.id)
        is Screen.Dtcs -> DtcsScreen(vm, screen.query)
        is Screen.Dtc -> DtcScreen(vm, screen.code)
        Screen.Obd -> ObdScreen(vm)
        Screen.Database -> DatabaseScreen(vm)
        Screen.Info -> InfoScreen(vm)
        is Screen.Table -> TableScreen(vm, screen.obj)
        is Screen.Search -> SearchScreen(vm, screen.query)
        is Screen.Row -> RowScreen(vm, screen)
    }
}
