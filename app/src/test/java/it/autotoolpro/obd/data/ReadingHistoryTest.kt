package it.autotoolpro.obd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadingHistoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun reading(t: Long, vehicle: String = "Fiesta", stored: List<String> = emptyList(), pending: List<String> = emptyList()) =
        Reading(t, vehicle, "OBDII", true, stored, pending, listOf("Catalizzatore"),
            listOf("Km dalla cancellazione dei codici" to "120 km"), "P0133")

    @Test fun roundTrip() {
        val r = reading(1000, stored = listOf("P0133", "P0300"), pending = listOf("P0171"))
        assertEquals(r, ReadingHistory.decode(ReadingHistory.encode(r)))
    }

    @Test fun roundTripEmptyAndUnknown() {
        val r = Reading(5, "Puma", "dev", null, emptyList(), emptyList(), emptyList(), emptyList(), null)
        assertEquals(r, ReadingHistory.decode(ReadingHistory.encode(r)))
    }

    @Test fun separatorsInTextDoNotBreakTheLine() {
        val r = reading(1).copy(device = "ad\tatt;\nore", counters = listOf("a=b" to "1 km"))
        val back = ReadingHistory.decode(ReadingHistory.encode(r))!!
        assertEquals("ad att; ore", back.device)
        assertEquals(listOf("a-b" to "1 km"), back.counters)
    }

    @Test fun badLinesAreSkipped() {
        assertNull(ReadingHistory.decode(""))
        assertNull(ReadingHistory.decode("2\tfuturo"))
        assertNull(ReadingHistory.decode("1\tx\t\t\t\t\t\t\t\t"))
    }

    @Test fun fileKeepsNewestFirstAndCaps() {
        val h = ReadingHistory(tmp.root.resolve("storico.txt"))
        assertEquals(emptyList<Reading>(), h.load())
        repeat(ReadingHistory.MAX + 5) { h.add(reading(it.toLong())) }
        val all = h.load()
        assertEquals(ReadingHistory.MAX, all.size)
        assertEquals((ReadingHistory.MAX + 4).toLong(), all.first().timestamp)
        h.clear()
        assertEquals(emptyList<Reading>(), h.load())
    }

    @Test fun diffAgainstPreviousOfSameVehicle() {
        val history = listOf(
            reading(1, stored = listOf("P0133", "P0300")),
            reading(2, vehicle = "Puma", stored = listOf("P0420")),
        )
        val now = reading(3, stored = listOf("P0300"), pending = listOf("P0171"))
        assertEquals(CodeDiff(listOf("P0171"), listOf("P0133")), ReadingHistory.diff(history + now, now))
        assertNull(ReadingHistory.diff(history, reading(0)))
    }
}
