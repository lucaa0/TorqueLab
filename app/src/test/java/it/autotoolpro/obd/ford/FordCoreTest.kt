package it.autotoolpro.obd.ford

import it.autotoolpro.obd.ford.FordCore.DispatchDecision
import it.autotoolpro.obd.ford.FordCore.Dispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FordCoreTest {

    @Test fun defaultsWithoutText() {
        val p = FordCore.loadParameters(null)
        assertEquals(1L, p.fields["0x374"])
        assertEquals(0xffffffffL, p.fields["0x384"])
        assertEquals(3L, p.fields["0x388"])
        assertEquals(null, p.fields["0x368"])
        assertFalse(p.hardwareReady)
        assertFalse(p.programmingCompleted)
        assertFalse(p.vehicleBindingVerified)
    }

    @Test fun parsesDwordFieldsAsHex() {
        val p = FordCore.loadParameters("TYPE_ID=2|PATS_RFA=ff|PATS_CYCLE_KEY_TIME=A")
        assertEquals(2L, p.fields["0x374"])
        assertEquals(255L, p.fields["0x384"])
        assertEquals(10L, p.fields["0x388"])
    }

    @Test fun firstDuplicateAndFirstEqualsWin() {
        val p = FordCore.loadParameters("TYPE_ID=5|TYPE_ID=7|NOTE=a=b")
        assertEquals(5L, p.fields["0x374"])
        assertEquals("a=b", p.config["NOTE"])
    }

    @Test fun presenceFlagSetsInitPcmFirst() {
        assertEquals(1L, FordCore.loadParameters("PATS_MODULE_INIT_PCM_FIRST=").fields["0x368"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHexDword() {
        FordCore.loadParameters("TYPE_ID=xyz")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonAscii() {
        FordCore.loadParameters("TYPE_ID=1|NOTE=è")
    }

    @Test fun dispatchParentSuccessShortCircuits() {
        assertEquals(DispatchDecision.Return(1), FordCore.dispatchAfterParent(Dispatcher.BASE_69F6C0, 1))
    }

    @Test fun dispatchKnownAndUnknownStates() {
        assertEquals(DispatchDecision.UnresolvedHandler(0xdc), FordCore.dispatchAfterParent(Dispatcher.BASE_69F6C0, 0, 15))
        assertEquals(DispatchDecision.UnresolvedHandler(0x23c), FordCore.dispatchAfterParent(Dispatcher.EXTENDED_6A6D30, 0, 31))
        assertEquals(DispatchDecision.Return(-1), FordCore.dispatchAfterParent(Dispatcher.BASE_69F6C0, 0, 31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun dispatchNeedsStateAfterFailedParent() {
        FordCore.dispatchAfterParent(Dispatcher.BASE_69F6C0, 0)
    }
}
