package it.autotoolpro.obd.obd

/** Decodifica OBD-II standard (SAE J1979), senza dipendenze Android: testabile su JVM. */
object ObdData {

    class LivePid(val pid: String, val label: String, val decode: (List<Int>) -> String?)

    private fun ab(d: List<Int>) = if (d.size >= 2) d[0] * 256 + d[1] else null
    private fun pct(d: List<Int>) = d.firstOrNull()?.let { "${it * 100 / 255} %" }
    private fun temp(d: List<Int>) = d.firstOrNull()?.let { "${it - 40} °C" }
    private fun trim(d: List<Int>) = d.firstOrNull()?.let { "%+.1f %%".format((it - 128) * 100 / 128.0) }

    /** Valori mostrati in tempo reale e nel freeze frame, solo se l'auto li dichiara supportati. */
    val livePids = listOf(
        LivePid("0C", "Giri motore") { d -> ab(d)?.let { "${it / 4} giri/min" } },
        LivePid("0D", "Velocità") { d -> d.firstOrNull()?.let { "$it km/h" } },
        LivePid("05", "Temp. liquido", ::temp),
        LivePid("04", "Carico motore", ::pct),
        LivePid("11", "Farfalla", ::pct),
        LivePid("0F", "Temp. aria aspirata", ::temp),
        LivePid("0B", "Pressione collettore") { d -> d.firstOrNull()?.let { "$it kPa" } },
        LivePid("10", "Flusso aria (MAF)") { d -> ab(d)?.let { "%.1f g/s".format(it / 100.0) } },
        LivePid("23", "Pressione rail") { d -> ab(d)?.let { "${it / 10} bar" } },
        LivePid("06", "Correzione breve B1", ::trim),
        LivePid("07", "Correzione lunga B1", ::trim),
        LivePid("0E", "Anticipo") { d -> d.firstOrNull()?.let { "%.1f °".format(it / 2.0 - 64) } },
        LivePid("5C", "Temp. olio", ::temp),
        LivePid("46", "Temp. esterna", ::temp),
        LivePid("2F", "Livello carburante", ::pct),
        LivePid("42", "Tensione") { d -> ab(d)?.let { "%.1f V".format(it / 1000.0) } },
    )

    /** Fallback quando l'elenco dei PID supportati non è disponibile (comportamento precedente). */
    val defaultLive = setOf("0C", "0D", "05", "04", "42")

    /**
     * Byte di dati dopo [header] (es. "410C"), saltando [skip] byte (numero frame nel mode 02).
     * Le risposte di più centraline arrivano su righe separate: vale la prima che corrisponde.
     */
    fun dataBytes(raw: List<String>, header: String, skip: Int = 0): List<Int>? =
        raw.asSequence().mapNotNull { line ->
            val clean = line.replace(" ", "").uppercase()
            val at = clean.indexOf(header)
            if (at < 0) return@mapNotNull null
            clean.substring(at + header.length).chunked(2)
                .takeWhile { it.length == 2 }
                .map { it.toIntOrNull(16) ?: return@mapNotNull null }
                .drop(skip)
        }.firstOrNull()

    /**
     * PID supportati da una bitmap (risposte a 0100/0120/0140, o 020000 nel mode 02).
     * Unione su tutte le righe: ogni centralina dichiara i propri.
     */
    fun supportedPids(raw: List<String>, header: String, base: Int, skip: Int = 0): Set<String> =
        raw.flatMap { line ->
            val bytes = dataBytes(listOf(line), header, skip)?.take(4) ?: return@flatMap emptyList()
            bytes.flatMapIndexed { i, byte ->
                (0 until 8).filter { bit -> byte and (0x80 shr bit) != 0 }
                    .map { bit -> "%02X".format(base + i * 8 + bit + 1) }
            }
        }.toSet()

    class Monitor(val name: String, val complete: Boolean)

    class Status(val milOn: Boolean, val dtcCount: Int, val diesel: Boolean, val monitors: List<Monitor>)

    private val continuous = listOf("Mancate accensioni", "Sistema carburante", "Componenti")
    private val spark = listOf(
        "Catalizzatore", "Catalizzatore riscaldato", "Sistema evaporativo", "Aria secondaria",
        null, "Sonda lambda", "Riscaldatore sonda lambda", "EGR / VVT",
    )
    private val compression = listOf(
        "Catalizzatore NMHC", "NOx / SCR", null, "Pressione sovralimentazione",
        null, "Sensore gas di scarico", "Filtro antiparticolato", "EGR / VVT",
    )

    /** PID 01 01: spia motore, numero codici e monitor di prontezza disponibili. */
    fun status(d: List<Int>): Status? {
        if (d.size < 4) return null
        val (a, b, c, e) = d
        val diesel = b and 0x08 != 0
        val monitors = buildList {
            continuous.forEachIndexed { i, name ->
                if (b and (1 shl i) != 0) add(Monitor(name, b and (0x10 shl i) == 0))
            }
            (if (diesel) compression else spark).forEachIndexed { i, name ->
                if (name != null && c and (1 shl i) != 0) add(Monitor(name, e and (1 shl i) == 0))
            }
        }
        return Status(a and 0x80 != 0, a and 0x7F, diesel, monitors)
    }

    /** Valore a due byte (km, minuti) o null. */
    fun word(d: List<Int>?): Int? = d?.let(::ab)
}
