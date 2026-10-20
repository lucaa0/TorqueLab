package it.autotoolpro.obd.obd

import it.autotoolpro.obd.obd.Elm327.Companion.decodeDtc
import it.autotoolpro.obd.obd.Elm327.Companion.parseDtcs
import it.autotoolpro.obd.obd.Elm327.Companion.parsePid
import it.autotoolpro.obd.obd.Elm327.Companion.parseVoltage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Risposte sintetiche nel formato ELM327 con ATS0/ATS1: non catturate da auto reali.
class Elm327ParsingTest {

    @Test fun decodeDtcLetters() {
        assertEquals("P0133", decodeDtc("0133"))
        assertEquals("C0123", decodeDtc("4123"))
        assertEquals("B1234", decodeDtc("9234"))
        assertEquals("U0123", decodeDtc("C123"))
    }

    @Test fun dtcsLegacyProtocolPaddedFrame() {
        assertEquals(listOf("P0133"), parseDtcs(listOf("43 01 33 00 00 00 00"), pending = false))
    }

    @Test fun dtcsCanSingleFrameWithCount() {
        assertEquals(listOf("P0133"), parseDtcs(listOf("43 01 01 33"), pending = false))
    }

    @Test fun dtcsCanNoCodes() {
        assertEquals(emptyList<String>(), parseDtcs(listOf("43 00"), pending = false))
    }

    @Test fun dtcsCanMultiFrame() {
        val raw = listOf("008", "0: 43 03 01 33 02 21", "1: 03 00 00 00 00 00")
        assertEquals(listOf("P0133", "P0221", "P0300"), parseDtcs(raw, pending = false))
    }

    @Test fun dtcsCanMultiFrameHexLengthLine() {
        val raw = listOf("00A", "0: 43 04 01 33 02 21", "1: 03 00 C1 23 00 00")
        assertEquals(listOf("P0133", "P0221", "P0300", "U0123"), parseDtcs(raw, pending = false))
    }

    @Test fun dtcsPendingNeedsMode47() {
        assertEquals(listOf("P0133"), parseDtcs(listOf("47 01 01 33"), pending = true))
        assertEquals(emptyList<String>(), parseDtcs(listOf("43 01 01 33"), pending = true))
    }

    @Test fun dtcsEmptyReply() {
        assertEquals(emptyList<String>(), parseDtcs(emptyList(), pending = false))
    }

    @Test fun pidTwoBytes() {
        assertEquals(0x1A to 0xF8, parsePid(listOf("41 0C 1A F8"), "0C"))
    }

    @Test fun pidWithCanHeader() {
        assertEquals(0x1A to 0xF8, parsePid(listOf("7E8 04 41 0C 1A F8"), "0C"))
    }

    @Test fun pidSingleByteDefaultsB() {
        assertEquals(0x7B to 0, parsePid(listOf("41 05 7B"), "05"))
    }

    @Test fun pidNegativeOrMissing() {
        assertNull(parsePid(listOf("7F 01 12"), "0C"))
        assertNull(parsePid(emptyList(), "0C"))
    }

    @Test fun voltage() {
        assertEquals(12.4, parseVoltage(listOf("12.4V"))!!, 0.0)
        assertEquals(9.0, parseVoltage(listOf("9V"))!!, 0.0)
        assertNull(parseVoltage(listOf("?")))
        assertNull(parseVoltage(emptyList()))
    }
}
