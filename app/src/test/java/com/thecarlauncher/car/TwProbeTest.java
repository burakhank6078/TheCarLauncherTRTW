package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** What the recording may hold: car frames as hex, phone traffic only as a shape. */
public class TwProbeTest {
    @Test
    public void textLosesNamesAndNumbers() {
        assertEquals("\"Aaaaa Aaaaaa\"", TwProbe.shape("Ahmet Yilmaz"));
        assertEquals("\"+## ### ### ## ##\"", TwProbe.shape("+90 532 123 45 67"));
        // Turkish letters show up as *, so the recording still tells whether they arrived intact.
        assertEquals("\"A**a\"", TwProbe.shape("Aşıa"));
    }

    @Test
    public void phoneBytesKeepTheirEncodingVisible() {
        String s = TwProbe.describe(0x510, 24, new byte[]{0x41, 0x68, 0x00, 0x39, (byte) 0xC3, (byte) 0xA7});
        assertEquals("bytes[6] Aa~#{C3}{A7}", s);
        assertFalse(s.contains("Ah"));
    }

    @Test
    public void carFramesStayHexAndTheChassisNumberIsCut() {
        assertEquals("0055003E", TwProbe.describe(0x501, 30, new byte[]{0x00, 0x55, 0x00, 0x3E}));
        byte[] vin = new byte[20];
        for (int i = 0; i < 17; i++) vin[i] = (byte) 'W';
        StringBuilder expected = new StringBuilder("575757");
        for (int i = 3; i < 17; i++) expected.append("XX");
        expected.append("000000");
        assertEquals(expected.toString(), TwProbe.describe(0x501, 31, vin));
    }

    @Test
    public void idsSharedWithTheBluetoothAppAreMaskedToo() {
        assertEquals("bytes[2] Aa", TwProbe.describe(0x201, 1, new byte[]{0x41, 0x68}));
    }

    @Test
    public void onlyFrame31Of0x501LosesItsMiddle() {
        byte[] b = new byte[20];
        java.util.Arrays.fill(b, (byte) 0x57);
        // Another id with arg1 31 keeps all its bytes (up to 24 are shown).
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < 20; i++) all.append("57");
        assertEquals(all.toString(), TwProbe.describe(0x20B, 31, b));
    }

    @Test
    public void bluetoothModuleCommandNumbersAreMasked() {
        // The phone module's messages come with "what" = command number (11 call, 24 phone book data, 47 ring...).
        assertEquals("bytes[3] Aa#", TwProbe.describe(24, 0, new byte[]{0x41, 0x68, 0x35}));
        assertEquals("bytes[1] A", TwProbe.describe(0xFF, 0, new byte[]{0x41}));
        // From 0x100 up the car's own ids stay as hex.
        assertEquals("41", TwProbe.describe(0x10C, 0, new byte[]{0x41}));
    }

    @Test
    public void radioIdsOfUnknownMeaningAreMasked() {
        // 265 and 0x9E00 are opened by the radio app but not understood: they are written as a shape.
        assertEquals("bytes[2] Aa", TwProbe.describe(265, 0, new byte[]{0x41, 0x68}));
        assertEquals("bytes[2] Aa", TwProbe.describe(0x9E00, 0, new byte[]{0x41, 0x68}));
    }

    @Test
    public void radioMessagesStayReadable() {
        // 0x401 / 0x404 are the tuner's ids: bytes stay hex, nothing is masked.
        assertEquals("27F6", TwProbe.describe(0x401, 2, new byte[]{0x27, (byte) 0xF6}));
        assertEquals("41", TwProbe.describe(0x404, 0, new byte[]{0x41}));
    }

    @Test
    public void longTextIsCut() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('x');
        String s = TwProbe.shape(sb.toString());
        assertEquals(64 + 1 + 2, s.length()); // quotes and the ellipsis
    }
}
