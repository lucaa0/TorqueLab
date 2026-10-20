package it.autotoolpro.obd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Risposte sintetiche costruite dalla specifica SAE J1979, non catturate da auto reali.
class ObdDataTest {

    @Test fun dataBytesPlainAndWithHeader() {
        assertEquals(listOf(0x1A, 0xF8), ObdData.dataBytes(listOf("410C1AF8"), "410C"))
        assertEquals(listOf(0x1A, 0xF8), ObdData.dataBytes(listOf("7E8 04 41 0C 1A F8"), "410C"))
        assertNull(ObdData.dataBytes(listOf("NO DATA"), "410C"))
    }

    @Test fun dataBytesSkipsFreezeFrameNumber() {
        assertEquals(listOf(0x01, 0x33), ObdData.dataBytes(listOf("42 02 00 01 33"), "4202", skip = 1))
    }

    @Test fun dataBytesFirstMatchingEcu() {
        val raw = listOf("7E9 03 41 05 50", "7E8 03 41 05 7B")
        assertEquals(listOf(0x50), ObdData.dataBytes(raw, "4105"))
    }

    @Test fun supportedPidsFromBitmap() {
        // BE 1F A8 13: esempio classico della documentazione OBD.
        val pids = ObdData.supportedPids(listOf("41 00 BE 1F A8 13"), "4100", 0x00)
        assertEquals(
            setOf("01", "03", "04", "05", "06", "07", "0C", "0D", "0E", "0F", "10", "11", "13", "15", "1C", "1F", "20"),
            pids,
        )
    }

    @Test fun supportedPidsUnionAcrossEcus() {
        val raw = listOf("7E8 06 41 00 80 00 00 00", "7E9 06 41 00 00 00 00 01")
        assertEquals(setOf("01", "20"), ObdData.supportedPids(raw, "4100", 0x00))
    }

    @Test fun supportedPidsSecondRangeAndModeTwo() {
        assertEquals(setOf("21", "40"), ObdData.supportedPids(listOf("41 20 80 00 00 01"), "4120", 0x20))
        assertEquals(setOf("02", "0C"), ObdData.supportedPids(listOf("42 00 00 40 10 00 00"), "4200", 0x00, skip = 1))
    }

    @Test fun statusSparkIgnition() {
        // A=0x82: spia accesa, 2 codici. B=0x07 continui disponibili e completati.
        // C=0x65: catalizzatore, evaporativo, sonda, riscaldatore. D=0x04: evaporativo non completato.
        val s = ObdData.status(listOf(0x82, 0x07, 0x65, 0x04))!!
        assertTrue(s.milOn)
        assertEquals(2, s.dtcCount)
        assertFalse(s.diesel)
        assertEquals(
            listOf("Mancate accensioni", "Sistema carburante", "Componenti", "Catalizzatore",
                "Sistema evaporativo", "Sonda lambda", "Riscaldatore sonda lambda"),
            s.monitors.map { it.name },
        )
        assertEquals(listOf("Sistema evaporativo"), s.monitors.filter { !it.complete }.map { it.name })
    }

    @Test fun statusDieselUsesCompressionNames() {
        // B=0x0F diesel + continui; B bit 4 → mancate accensioni non completato. C=0xC0 DPF, EGR.
        val s = ObdData.status(listOf(0x00, 0x1F, 0xC0, 0x40))!!
        assertFalse(s.milOn)
        assertTrue(s.diesel)
        assertEquals(listOf(false, true, true, false, true), s.monitors.map { it.complete })
        assertEquals("Filtro antiparticolato", s.monitors[3].name)
        assertEquals("EGR / VVT", s.monitors[4].name)
    }

    @Test fun statusTooShort() {
        assertNull(ObdData.status(listOf(0x00, 0x00)))
    }

    @Test fun liveDecoders() {
        fun decode(pid: String, vararg d: Int) = ObdData.livePids.single { it.pid == pid }.decode(d.toList())
        assertEquals("1726 giri/min", decode("0C", 0x1A, 0xF8))
        assertEquals("83 °C", decode("05", 0x7B))
        assertEquals("100 %", decode("11", 0xFF))
        assertEquals("98 kPa", decode("0B", 98))
        assertNull(decode("0C", 0x1A))
        assertNull(decode("05"))
    }

    @Test fun word() {
        assertEquals(0x0123, ObdData.word(listOf(0x01, 0x23)))
        assertNull(ObdData.word(listOf(0x01)))
        assertNull(ObdData.word(null))
    }
}
