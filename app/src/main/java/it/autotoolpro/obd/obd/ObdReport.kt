package it.autotoolpro.obd.obd

import kotlin.coroutines.cancellation.CancellationException

internal fun hasObdSupportResponse(lines: List<String>): Boolean =
    lines.any { Regex("4100[0-9A-F]{8}").matches(it.replace(" ", "").uppercase()) }

/** Prompt-delimited ELM response. Incomplete replies must never become data. */
internal class ElmReply {
    private val text = StringBuilder()
    var complete = false
        private set

    fun accept(byte: Int) {
        check(byte >= 0) { "Connessione terminata durante la risposta" }
        check(!complete) { "Risposta già conclusa" }
        if (byte == '>'.code) complete = true else {
            check(text.length < 16384) { "Risposta dell'adattatore troppo lunga" }
            text.append(byte.toChar())
        }
    }

    fun lines(command: String): List<String> {
        check(complete) { "Tempo scaduto: risposta incompleta, riconnettere l'adattatore" }
        return text.split('\r', '\n').map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != command.uppercase() && !it.startsWith("SEARCHING") }
    }
}

data class ObdReportEntry(val command: String, val response: List<String>, val error: String? = null)

data class ObdReport(
    val vehicle: String, val device: String, val capturedAt: String,
    val entries: List<ObdReportEntry>, val completed: Boolean,
) {
    fun asText(): String = buildString {
        appendLine("AutoToolPro - rapporto identificazione OBD v1")
        appendLine("Data: ${quoted(capturedAt)}")
        appendLine("Veicolo selezionato dall'utente: ${quoted(vehicle)}")
        appendLine("Dispositivo Bluetooth: ${quoted(device)}")
        appendLine("Raccolta: ${if (completed) "terminata" else "interrotta / rapporto parziale"}")
        appendLine("Risposte originali dell'adattatore; NO DATA, ? e risposte negative restano tali.")
        appendLine("Identificazione OBD generica: non include tutti i moduli Ford e non verifica PATS/odometro.")
        entries.forEach { entry ->
            appendLine("\nComando: ${entry.command}")
            if (entry.response.isEmpty()) appendLine("Nessuna risposta utile")
            entry.response.forEach { appendLine("Risposta: ${quoted(it)}") }
            entry.error?.let { appendLine("Errore: ${quoted(it)}") }
        }
    }

    private fun quoted(text: String) = "\"" + text.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\""

    companion object {
        // Fixed identification queries; header settings affect adapter formatting only.
        val commands = listOf("ATI", "AT@1", "ATDP", "ATDPN", "ATRV", "ATH1", "0100", "0900", "0902", "0904", "090A", "ATH0")

        internal suspend fun collect(
            vehicle: String, device: String, capturedAt: String,
            exchange: suspend (String) -> List<String>,
        ): ObdReport {
            val entries = mutableListOf<ObdReportEntry>()
            for (command in commands) {
                try {
                    val response = exchange(command).toList()
                    val error = if (command in setOf("ATH1", "ATH0") && "OK" !in response)
                        "Formato risposta non confermato dall'adattatore" else null
                    entries += ObdReportEntry(command, response, error)
                    if (error != null) return ObdReport(vehicle, device, capturedAt, entries.toList(), false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    entries += ObdReportEntry(command, emptyList(), e.message ?: e.javaClass.simpleName)
                    return ObdReport(vehicle, device, capturedAt, entries.toList(), false)
                }
            }
            return ObdReport(vehicle, device, capturedAt, entries.toList(), true)
        }
    }
}
