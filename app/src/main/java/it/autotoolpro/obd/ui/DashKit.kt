package it.autotoolpro.obd.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pannello del cruscotto: un solo livello di contenitore, mai pannelli dentro pannelli. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Titolo di sezione: più spazio sopra che sotto. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color)

/** Stato di una spia: come le spie del quadro, il colore dice tutto anche senza leggere. */
enum class Lamp { Ok, Warn, Fault, Off }

@Composable
fun lampColor(lamp: Lamp): Color {
    val s = MaterialTheme.colorScheme
    val target = when (lamp) {
        Lamp.Ok -> s.tertiary
        Lamp.Warn -> s.primary
        Lamp.Fault -> s.error
        Lamp.Off -> s.outline
    }
    val c by animateColorAsState(target, tween(400), label = "lamp")
    return c
}

/** Spia circolare con icona, con alone tenue nel colore della spia. */
@Composable
fun LampIcon(icon: ImageVector, lamp: Lamp, size: Dp = 44.dp, contentDescription: String? = null) {
    val c = lampColor(lamp)
    Box(
        Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c, modifier = Modifier.size(size * 0.5f))
    }
}

/** Puntino di stato accanto a un testo. */
@Composable
fun StatusDot(lamp: Lamp, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(lampColor(lamp)))
}

/** Riga di elenco toccabile, alta almeno 64dp: si centra anche con l'auto in moto al minimo. */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (enabled) s.primary else s.outline, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) s.onSurface else s.outline)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke() ?: Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = s.outline)
    }
}

/** Gruppo di righe in un unico pannello, separate da linee sottili. */
@Composable
fun NavGroup(rows: List<@Composable () -> Unit>) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                row()
            }
        }
    }
}

/** Quadrante di un valore live: numero grande, unità piccola accanto. */
@Composable
fun Gauge(label: String, value: String?, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val number = value?.substringBefore(' ')
    val unit = value?.substringAfter(' ', "")
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = s.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = s.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(number ?: "—", style = GaugeStyle, color = if (number == null) s.outline else s.onSurface, maxLines = 1)
                if (!unit.isNullOrEmpty()) Text(" $unit", style = MaterialTheme.typography.labelLarge, color = s.primary,
                    modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
            }
        }
    }
}

/** Coppia etichetta–valore allineata, per contatori e freeze frame. */
@Composable
fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Condensed, fontFeatureSettings = TabularNums),
            fontWeight = FontWeight.Bold)
    }
}

/** Avviso in linea, colorato secondo la gravità. */
@Composable
fun Notice(text: String, lamp: Lamp, icon: ImageVector, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val (bg, fg) = when (lamp) {
        Lamp.Fault -> s.errorContainer to s.onErrorContainer
        Lamp.Warn -> s.primaryContainer to s.onPrimaryContainer
        Lamp.Ok -> s.tertiaryContainer to s.onTertiaryContainer
        Lamp.Off -> s.surfaceContainerHigh to s.onSurfaceVariant
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = bg, contentColor = fg) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
