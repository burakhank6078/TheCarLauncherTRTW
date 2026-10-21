package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The Golf 7 trip frames, with payloads taken from a real recording of the unit and checked against
 * the factory screens (long-term: 774 km, 21:57 h, 35 km/h; since start: 0:17 h).
 */
public class ObdDataTest {
    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    @Test
    public void longTermFrame() {
        ObdData d = ObdData.get();
        assertTrue(d.decodeFrame(20, hex("004700FA1E40053B2300")));
        assertEquals(7.1, d.get(ObdData.LONG, ObdData.CONS), 1e-9);
        assertEquals(250, d.get(ObdData.LONG, ObdData.RANGE), 1e-9);
        assertEquals(774.4, d.get(ObdData.LONG, ObdData.DIST), 1e-9);
        assertEquals(0x053B * 60000.0, d.get(ObdData.LONG, ObdData.TIME), 1e-6);
        assertEquals(35, d.get(ObdData.LONG, ObdData.SPEED), 1e-9);
    }

    @Test
    public void sinceStartFrameWithNoConsumption() {
        ObdData d = ObdData.get();
        assertTrue(d.decodeFrame(19, hex("FFFF00FA000000280000")));
        assertTrue(Double.isNaN(d.get(ObdData.START, ObdData.CONS))); // 0xFFFF means "no value"
        assertEquals(250, d.get(ObdData.START, ObdData.RANGE), 1e-9);
        assertEquals(0, d.get(ObdData.START, ObdData.DIST), 1e-9);
        assertEquals(40 * 60000.0, d.get(ObdData.START, ObdData.TIME), 1e-6);
    }

    @Test
    public void sinceRefuellingFrame() {
        ObdData d = ObdData.get();
        assertTrue(d.decodeFrame(21, hex("004D00FA031B00B01B00")));
        assertEquals(7.7, d.get(ObdData.REFUEL, ObdData.CONS), 1e-9);
        assertEquals(79.5, d.get(ObdData.REFUEL, ObdData.DIST), 1e-9);
        assertEquals(27, d.get(ObdData.REFUEL, ObdData.SPEED), 1e-9);
    }

    @Test
    public void unknownSpeedAndOtherFramesAreRefused() {
        ObdData d = ObdData.get();
        assertTrue(d.decodeFrame(20, hex("004700FA1E400516FF00")));
        assertTrue(Double.isNaN(d.get(ObdData.LONG, ObdData.SPEED)));
        assertFalse(d.decodeFrame(25, hex("000000000000000003AE"))); // not a trip frame
        assertFalse(d.decodeFrame(20, hex("0047"))); // too short
        assertFalse(d.decodeFrame(20, null));
    }
}
