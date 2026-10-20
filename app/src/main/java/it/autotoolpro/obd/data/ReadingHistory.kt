package it.autotoolpro.obd.data

import java.io.File

/** Una lettura «codici e stato», salvata solo sul telefono. */
data class Reading(
    val timestamp: Long,
    val vehicle: String,
    val device: String,
    val milOn: Boolean?,
    val stored: List<String>,
    val pending: List<String>,
    val incompleteMonitors: List<String>,
    val counters: List<Pair<String, String>>,
    val freezeFrameCode: String?,
)

/** Codici comparsi e scomparsi rispetto alla lettura precedente della stessa auto. */
data class CodeDiff(val appeared: List<String>, val disappeared: List<String>)

/**
 * Storico in un file di testo privato dell'app, una lettura per riga.
 * Formato a campi separati da tab: niente librerie JSON, leggibile e testabile su JVM.
 */
class ReadingHistory(private val file: File) {

    fun load(): List<Reading> =
        if (!file.exists()) emptyList()
        else file.readLines(Charsets.UTF_8).mapNotNull(::decode).sortedByDescending { it.timestamp }

    /** Aggiunge in testa e conserva le ultime [MAX] letture. */
    fun add(reading: Reading): List<Reading> {
        val all = (listOf(reading) + load()).take(MAX)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(all.joinToString("\n", transform = ::encode), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true); tmp.delete()
        }
        return all
    }

    fun clear() {
        file.delete()
    }

    companion object {
        const val MAX = 200
        private const val VERSION = "1"

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        private fun list(items: List<String>) = items.joinToString(";") { clean(it).replace(";", ",") }
        private fun unlist(s: String) = if (s.isEmpty()) emptyList() else s.split(';')

        fun encode(r: Reading): String = listOf(
            VERSION,
            r.timestamp.toString(),
            clean(r.vehicle),
            clean(r.device),
            when (r.milOn) { true -> "1"; false -> "0"; null -> "" },
            list(r.stored),
            list(r.pending),
            list(r.incompleteMonitors),
            list(r.counters.map { (k, v) -> "${k.replace("=", "-")}=$v" }),
            r.freezeFrameCode ?: "",
        ).joinToString("\t")

        /** null per righe illeggibili o di un formato futuro: non bloccano il resto dello storico. */
        fun decode(line: String): Reading? {
            val f = line.split('\t')
            if (f.size != 10 || f[0] != VERSION) return null
            return Reading(
                timestamp = f[1].toLongOrNull() ?: return null,
                vehicle = f[2],
                device = f[3],
                milOn = when (f[4]) { "1" -> true; "0" -> false; else -> null },
                stored = unlist(f[5]),
                pending = unlist(f[6]),
                incompleteMonitors = unlist(f[7]),
                counters = unlist(f[8]).map { it.substringBefore('=') to it.substringAfter('=', "") },
                freezeFrameCode = f[9].ifEmpty { null },
            )
        }

        /** Confronto con la lettura precedente della stessa auto; null se è la prima. */
        fun diff(history: List<Reading>, reading: Reading): CodeDiff? {
            val previous = history
                .filter { it.vehicle == reading.vehicle && it.timestamp < reading.timestamp }
                .maxByOrNull { it.timestamp } ?: return null
            val before = (previous.stored + previous.pending).toSet()
            val now = (reading.stored + reading.pending).toSet()
            return CodeDiff((now - before).sorted(), (before - now).sorted())
        }
    }
}
