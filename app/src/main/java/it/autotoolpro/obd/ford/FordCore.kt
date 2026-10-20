package it.autotoolpro.obd.ford

/** Port del sottoinsieme verificato offline. Non seleziona procedure ECU. */
object FordCore {
    data class Vehicle(val id: String, val name: String, val year: Int, val engine: String)
    val vehicles = listOf(
        Vehicle("fiesta_2010_16_diesel", "Ford Fiesta", 2010, "1.6 diesel"),
        Vehicle("puma_1998_14_petrol", "Ford Puma coupé Mk1", 1998, "1.4 benzina"),
    )

    data class Parameters(val fields: Map<String, Long?>, val config: Map<String, String>) {
        val hardwareReady: Boolean get() = false
        val programmingCompleted: Boolean get() = false
        val vehicleBindingVerified: Boolean get() = false
    }

    private val defaults = mapOf<String, Long?>(
        "0x374" to 1L, "0x378" to 0L, "0x37c" to 0L,
        "0x384" to 0xffffffffL, "0x388" to 3L, "0x368" to null,
    )
    private val fields = mapOf(
        "NAME_ID" to "0x8", "LABEL_ID" to "0x68", "TYPE_ID" to "0x374",
        "PATS_RESET_TYPE" to "0x378", "PATS_DISTRIBUTED_TYPE" to "0x37c",
        "PATS_RFA" to "0x384", "PATS_CYCLE_KEY_TIME" to "0x388",
    )

    /** Clean ASCII, first '=' and first duplicate retained; no native locale claims. */
    fun loadParameters(
        text: String?,
        existingConfig: Map<String, String> = emptyMap(),
        previousFields: Map<String, Long?> = emptyMap(),
    ): Parameters {
        val values = (defaults + previousFields).toMutableMap()
        if (text == null) return Parameters(values.toMap(), existingConfig.toMap())
        val config = if (existingConfig.isNotEmpty()) existingConfig.toMap() else {
            require(text.all { it.code in 1..127 }) { "Configurazione fuori dal sottoinsieme ASCII" }
            val parsed = linkedMapOf<String, String>()
            text.split('|').forEach { token ->
                val separator = token.indexOf('=')
                if (separator >= 0) {
                    val key = token.substring(0, separator)
                    val value = token.substring(separator + 1)
                    require(Regex("[A-Z][A-Z0-9_]*").matches(key) && value.all { it.code >= 32 })
                    if (key !in parsed) parsed[key] = value
                }
            }
            parsed.toMap()
        }
        fields.forEach { (key, offset) ->
            config[key]?.let { value ->
                require(Regex("[0-9a-fA-F]{1,8}").matches(value)) { "Parametro DWORD non valido: $key" }
                values[offset] = value.toLong(16)
            }
        }
        if ("PATS_MODULE_INIT_PCM_FIRST" in config) values["0x368"] = 1L
        // Other keys remain available to callers, with no inferred effect.
        return Parameters(values.toMap(), config.toMap())
    }

    enum class Dispatcher { BASE_69F6C0, EXTENDED_6A6D30 }
    sealed interface DispatchDecision {
        data class Return(val value: Int) : DispatchDecision
        data class UnresolvedHandler(val slot: Int) : DispatchDecision
    }
    private val baseSlots = mapOf(
        15 to 0xdc, 16 to 0xf8, 18 to 0xfc, 17 to 0x100, 19 to 0x104,
        20 to 0x108, 21 to 0x10c, 22 to 0x110, 24 to 0x138, 23 to 0x13c,
        25 to 0x140, 26 to 0x144, 27 to 0x148, 28 to 0xe4, 29 to 0x154, 30 to 0x14c,
    )
    private val extendedSlots = mapOf(
        31 to 0x23c, 32 to 0x240, 33 to 0x244, 34 to 0x248, 35 to 0x24c, 36 to 0x250,
        37 to 0x254, 38 to 0x260, 39 to 0x264, 40 to 0x268, 42 to 0x25c, 41 to 0x258,
    )

    /** Immediate parent must already have returned; state is its POST-call state. */
    fun dispatchAfterParent(
        dispatcher: Dispatcher, parentResult: Int, stateAfterParent: Int? = null,
    ): DispatchDecision {
        if (parentResult == 1) return DispatchDecision.Return(1)
        requireNotNull(stateAfterParent) { "Serve lo stato dopo il metodo padre" }
        val slots = if (dispatcher == Dispatcher.BASE_69F6C0) baseSlots else extendedSlots
        return slots[stateAfterParent]?.let { DispatchDecision.UnresolvedHandler(it) }
            ?: DispatchDecision.Return(-1)
    }
}
