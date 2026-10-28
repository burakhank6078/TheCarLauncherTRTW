package com.thecarlauncher.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The strip's message framing: 2E, type, length, data..., checksum = (type + length + data) XOR 0xFF. */
public class AmbientBleTest {
    @Test
    public void sessionStart() {
        // 0x81 + 1 + 1 = 0x83, XOR 0xFF = 0x7C
        assertArrayEquals(new byte[]{0x2E, (byte) 0x81, 0x01, 0x01, 0x7C}, AmbientBle.pack((byte) 0x81, (byte) 1));
    }

    @Test
    public void colourMessage() {
        byte[] m = AmbientBle.pack((byte) 0x8D, (byte) 1, (byte) 8, (byte) 255, (byte) 0, (byte) 0, (byte) 255, (byte) 0, (byte) 0);
        assertArrayEquals(new byte[]{0x2E, (byte) 0x8D, 0x08, 0x01, 0x08, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00,
                (byte) (((0x8D + 8 + 1 + 8 + 255 + 255) & 0xFF) ^ 0xFF)}, m);
    }

    @Test
    public void pastelColoursAreStretchedToFullSaturationForTheStrip() {
        // "Blue (MIB)" 2D8CEB: darkest channel to 0, brightest to 255
        assertEquals(0x0080FF, AmbientBle.ledColor(0xFF2D8CEB, true));
        // "Lavender" B39DFF
        assertEquals(0x3900FF, AmbientBle.ledColor(0xFFB39DFF, true));
        // already saturated colours stay as they are
        assertEquals(0xFF0000, AmbientBle.ledColor(0xFFFF0000, true));
        assertEquals(0x00FF00, AmbientBle.ledColor(0xFF00FF00, true));
    }

    @Test
    public void whitesAndGreysAreLeftAlone() {
        assertEquals(0xFFE2B8, AmbientBle.ledColor(0xFFFFE2B8, true)); // warm white
        assertEquals(0xD8ECFF, AmbientBle.ledColor(0xFFD8ECFF, true)); // cool white
        assertEquals(0xF0F0F0, AmbientBle.ledColor(0xFFF0F0F0, true)); // white
        assertEquals(0xB8BEC8, AmbientBle.ledColor(0xFFB8BEC8, true)); // silver (almost grey)
        assertEquals(0x000000, AmbientBle.ledColor(0xFF000000, true));
    }

    @Test
    public void theVividSwitchSendsTheColourAsChosen() {
        assertEquals(0x2D8CEB, AmbientBle.ledColor(0xFF2D8CEB, false));
    }

    @Test
    public void oneColourAndOneLevelGoAsTheWholeStripForm() {
        java.util.List<byte[]> m = AmbientBle.messages(0xFFFF0000, 0xFFFF0000, 40, 40, true);
        assertEquals(2, m.size());
        assertArrayEquals(AmbientBle.pack((byte) 0x8D, (byte) 1, (byte) 8, (byte) 255, (byte) 0, (byte) 0, (byte) 255, (byte) 0, (byte) 0), m.get(0));
        assertArrayEquals(AmbientBle.pack((byte) 0x8D, (byte) 0, (byte) 0x48, (byte) 40, (byte) 40), m.get(1));
    }

    @Test
    public void twoColoursAndTwoLevelsGoAsTheTwoZoneForm() {
        // as the LAMP&FRGN app sends them: colours type 00, brightness 40 (lamp on, no uniform bit)
        java.util.List<byte[]> m = AmbientBle.messages(0xFFFF0000, 0xFF0000FF, 100, 20, true);
        assertArrayEquals(AmbientBle.pack((byte) 0x8D, (byte) 1, (byte) 0, (byte) 255, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 255), m.get(0));
        assertArrayEquals(AmbientBle.pack((byte) 0x8D, (byte) 0, (byte) 0x40, (byte) 100, (byte) 20), m.get(1));
    }

    @Test
    public void lowestLevel() {
        byte[] m = AmbientBle.pack((byte) 0x8D, (byte) 0, (byte) 0x48, (byte) 1, (byte) 1);
        // sum = 0x8D + 4 + 0 + 0x48 + 1 + 1 = 0xDB, XOR 0xFF = 0x24
        assertArrayEquals(new byte[]{0x2E, (byte) 0x8D, 0x04, 0x00, 0x48, 0x01, 0x01, 0x24}, m);
    }
}
