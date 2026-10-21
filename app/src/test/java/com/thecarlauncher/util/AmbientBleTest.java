package com.thecarlauncher.util;

import static org.junit.Assert.assertArrayEquals;

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
    public void lowestLevel() {
        byte[] m = AmbientBle.pack((byte) 0x8D, (byte) 0, (byte) 0x48, (byte) 1, (byte) 1);
        // sum = 0x8D + 4 + 0 + 0x48 + 1 + 1 = 0xDB, XOR 0xFF = 0x24
        assertArrayEquals(new byte[]{0x2E, (byte) 0x8D, 0x04, 0x00, 0x48, 0x01, 0x01, 0x24}, m);
    }
}
