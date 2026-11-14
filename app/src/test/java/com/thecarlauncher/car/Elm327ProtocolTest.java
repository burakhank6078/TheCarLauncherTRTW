package com.thecarlauncher.car;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class Elm327ProtocolTest {
    @Test
    public void onlyTheAdaptersOwnCommandsAndModeZeroOneRequestsMayBeSent() {
        for (String ok : new String[]{"ATZ", "atz", "ATE0", "ATSP0", "ATRV", "0100", "010C", "015c", "0146"}) assertTrue(ok, Elm327Protocol.allowed(ok));
        // nothing else: not a write, not another mode, not a long request, not an unknown AT command
        for (String no : new String[]{"", null, "0902", "03", "04", "0105 ", "ATBD", "ATPP 0C SV 1", "010", "01ZZ", "2E0101", "ATWS", "AT"}) {
            if ("0105 ".equals(no)) continue; // trailing space is trimmed: allowed
            assertFalse(String.valueOf(no), Elm327Protocol.allowed(no));
        }
        assertTrue(Elm327Protocol.allowed("0105 "));
    }

    @Test
    public void aRequestIsTwoDigitHex() {
        assertEquals("010C", Elm327Protocol.request(Elm327Protocol.RPM));
        assertEquals("0105", Elm327Protocol.request(Elm327Protocol.COOLANT));
        assertEquals("0100", Elm327Protocol.request(0));
    }

    @Test
    public void theBytesOfAReplyAreFoundThroughSpacesEchoesAndSearchingLines() {
        assertArrayEquals(new int[]{0x1A, 0xF8}, Elm327Protocol.payload("41 0C 1A F8 \r\r>", 0x0C));
        assertArrayEquals(new int[]{0x7B}, Elm327Protocol.payload("010541 05 7B", 0x05));          // echo in front
        assertArrayEquals(new int[]{0xBE, 0x3F, 0xA8, 0x13}, Elm327Protocol.payload("SEARCHING...\r4100BE3FA813\r\r>", 0x00));
        assertNull(Elm327Protocol.payload("41 0D 32", 0x0C));   // a reply for another value
        assertNull(Elm327Protocol.payload("NO DATA", 0x0C));
        assertNull(Elm327Protocol.payload(null, 0x0C));
    }

    @Test
    public void theValuesAreWorkedOutLikeTheStandardSays() {
        assertEquals(83.0, Elm327Protocol.value(Elm327Protocol.COOLANT, new int[]{0x7B}), 0.001);   // 123 - 40
        assertEquals(-3.0, Elm327Protocol.value(Elm327Protocol.AMBIENT, new int[]{37}), 0.001);
        assertEquals(98.0, Elm327Protocol.value(Elm327Protocol.OIL, new int[]{138}), 0.001);
        assertEquals(1742.0, Elm327Protocol.value(Elm327Protocol.RPM, new int[]{0x1B, 0x38}), 0.001); // (27*256+56)/4
        assertEquals(50.0, Elm327Protocol.value(Elm327Protocol.SPEED, new int[]{50}), 0.001);
        assertEquals(50.0, Elm327Protocol.value(Elm327Protocol.FUEL, new int[]{128}), 0.5);
        assertEquals(14.1, Elm327Protocol.value(Elm327Protocol.MODULE_VOLTAGE, new int[]{0x37, 0x0C}), 0.01);
        assertTrue(Double.isNaN(Elm327Protocol.value(Elm327Protocol.RPM, new int[]{0x1B})));       // one byte is not enough
        assertTrue(Double.isNaN(Elm327Protocol.value(0x99, new int[]{1})));
        assertTrue(Double.isNaN(Elm327Protocol.value(Elm327Protocol.SPEED, null)));
    }

    @Test
    public void theSupportedMaskGivesTheNumbersFromBasePlusOne() {
        // BE 3F A8 13: 01 03 04 05 06 07 | 0C 0D 0E 0F 10 11 | 13 15 | 1C 1F 20 ...
        List<Integer> l = Elm327Protocol.supported(0x00, new int[]{0xBE, 0x3F, 0xA8, 0x13});
        assertTrue(l.contains(0x05));
        assertTrue(l.contains(0x0C));
        assertTrue(l.contains(0x0D));
        assertTrue(l.contains(0x0F));
        assertTrue(l.contains(0x11));
        assertTrue(l.contains(0x20)); // the next block exists
        assertFalse(l.contains(0x02));
        assertEquals(Arrays.asList(0x21), Elm327Protocol.supported(0x20, new int[]{0x80, 0, 0, 0}));
        assertTrue(Elm327Protocol.supported(0x00, null).isEmpty());
    }

    @Test
    public void failuresAreRecognisedAndTheBatteryVoltageIsRead() {
        assertTrue(Elm327Protocol.isFailure("NO DATA"));
        assertTrue(Elm327Protocol.isFailure("UNABLE TO CONNECT"));
        assertTrue(Elm327Protocol.isFailure("?"));
        assertTrue(Elm327Protocol.isFailure(""));
        assertFalse(Elm327Protocol.isFailure("41 0C 1A F8"));
        assertEquals(12.6, Elm327Protocol.voltage("12.6V\r"), 0.001);
        assertEquals(13.1, Elm327Protocol.voltage("ATRV\r13.1V"), 0.001);
        assertTrue(Double.isNaN(Elm327Protocol.voltage("NO DATA")));
        assertNotNull(Elm327Protocol.clean(null));
    }
}
