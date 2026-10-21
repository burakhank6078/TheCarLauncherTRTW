package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Frame 30 as the factory service page reads it: state bits in byte 1, days and distance (x100) in pairs. */
public class ServiceDataTest {
    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    @Test
    public void inspectionToGoAndOilOverdue() {
        ServiceData d = ServiceData.get();
        // byte 1 = 0x58: inspection days 01 (to go), inspection distance 01, oil days 10 (overdue), oil distance 00 (none)
        assertTrue(d.decodeFrame(hex("00580078002A00050000")));
        assertEquals(1, d.state(ServiceData.INSPECTION_DAYS));
        assertEquals(120, d.value(ServiceData.INSPECTION_DAYS));
        assertEquals(1, d.state(ServiceData.INSPECTION_DIST));
        assertEquals(4200, d.value(ServiceData.INSPECTION_DIST));
        assertEquals(2, d.state(ServiceData.OIL_DAYS));
        assertEquals(5, d.value(ServiceData.OIL_DAYS));
        assertEquals(0, d.state(ServiceData.OIL_DIST));
        assertFalse(d.miles());
        assertTrue(d.known());
    }

    @Test
    public void milesWhenFirstByteIsSet() {
        ServiceData d = ServiceData.get();
        assertTrue(d.decodeFrame(hex("01000000000000000000")));
        assertTrue(d.miles());
        assertEquals(0, d.state(ServiceData.OIL_DIST));
    }

    @Test
    public void shortFrameIsRefused() {
        assertFalse(ServiceData.get().decodeFrame(hex("0058")));
        assertFalse(ServiceData.get().decodeFrame(null));
    }
}
