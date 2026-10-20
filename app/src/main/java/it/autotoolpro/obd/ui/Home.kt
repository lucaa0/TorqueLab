package it.autotoolpro.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.autotoolpro.obd.AppViewModel
import it.autotoolpro.obd.Screen

@Composable
fun HomeScreen(vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.22f), scheme.background)))
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column {
                Text("AUTO TOOL PRO", style = MaterialTheme.typography.labelLarge, color = scheme.primary, letterSpacing = 3.sp)
                Text("1000", fontSize = 44.sp, fontWeight = FontWeight.Black, color = scheme.onBackground)
                Text("Diagnosi, veicoli e codici guasto. Tutto offline.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.push(Screen.Info) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Info, contentDescription = "Informazioni", tint = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile("🔌", "Diagnosi auto", "Collega l'adattatore ELM327 e leggi i codici guasto e i dati live", highlight = true) { vm.push(Screen.Obd) }
            BigTile("🚗", "Veicoli", "Cerca marca e modello: chiavi, strumenti e programmatori compatibili") { vm.push(Screen.Vehicles) }
            BigTile("🚘", "Ford Fiesta e Puma", "Profili e stato delle funzioni dedicate") { vm.push(Screen.Ford) }
            BigTile("⚠️", "Codici guasto", "Cerca un codice (es. P0420) e vedi cause e controlli") { vm.push(Screen.Dtcs()) }
            BigTile("🗄️", "Database completo", "Sfoglia tutte le tabelle") { vm.push(Screen.Database) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BigTile(emoji: String, title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlight) scheme.primary else scheme.surfaceContainer,
        contentColor = if (highlight) scheme.onPrimary else scheme.onSurface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (highlight) Color.Black.copy(alpha = 0.15f) else scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 26.sp) }
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlight) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
