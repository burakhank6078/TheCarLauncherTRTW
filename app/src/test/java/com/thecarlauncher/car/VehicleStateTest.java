package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Frames 26, 18 and 31 as recorded in the 22 Oct 2026 test (doors, driver belt, chassis number). */
public class VehicleStateTest {
    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    @Test
    public void doorsFromFrame26() {
        VehicleState v = VehicleState.get();
        v.decodeFrame(null, 26, hex("7F8100000004000800000000"));
        assertEquals(VehicleState.DOOR_DRIVER, v.doors());
        v.decodeFrame(null, 26, hex("7F4100000004000800000000"));
        assertEquals(VehicleState.DOOR_PASSENGER, v.doors());
        v.decodeFrame(null, 26, hex("7F2100000004000800000000"));
        assertEquals(VehicleState.DOOR_REAR_LEFT, v.doors());
        v.decodeFrame(null, 26, hex("7F1100000004000800000000"));
        assertEquals(VehicleState.DOOR_REAR_RIGHT, v.doors());
        // Hazard lights (byte 2 = 04) with every door shut.
        v.decodeFrame(null, 26, hex("7F0104000004000800000000"));
        assertEquals(0, v.doors());
    }

    @Test
    public void beltAndDoorsFromFrame18() {
        VehicleState v = VehicleState.get();
        v.decodeFrame(null, 18, hex("00008063098020140C00")); // driver door open, belt not fastened
        assertEquals(VehicleState.DOOR_DRIVER, v.doors());
        assertEquals(0, v.belt());
        v.decodeFrame(null, 18, hex("00000063098000140C00")); // belt fastened (bit 0x20 clear)
        assertEquals(0, v.doors());
        assertEquals(1, v.belt());
    }

    @Test
    public void serviceAnswersAndVoltage() {
        VehicleState v = VehicleState.get();
        v.setAidl(4, "13.9");
        v.setAidl(19, "789.0");
        assertEquals(13.9f, v.voltage(), 0.001f);
        assertEquals(789, v.rpm());
        v.setAidl(4, "0.0");
        assertTrue(Float.isNaN(v.voltage()));
    }

    @Test
    public void chassisNumberAsAscii() {
        VehicleState v = VehicleState.get();
        byte[] f = new byte[20];
        byte[] vin = "WVWZZZ00ZEW000000".getBytes();
        System.arraycopy(vin, 0, f, 0, 17);
        v.decodeFrame(null, 31, f);
        assertEquals("WVWZZZ00ZEW000000", v.vin(null));
        // Something that is not a chassis number leaves it alone.
        v.decodeFrame(null, 31, new byte[20]);
        assertEquals("WVWZZZ00ZEW000000", v.vin(null));
    }
}
