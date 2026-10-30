package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void doorFramesAreTimedSoAnOldStateCanBeDistrusted() {
        VehicleState v = VehicleState.get();
        v.decodeFrame(null, 26, hex("7F8100000004000800000000"));
        assertTrue(v.doorsAgeMs() < 2000);
    }

    @Test
    public void engineRunningFromRpmWithAChargingVoltageOrFromTheVoltageAlone() {
        VehicleState v = VehicleState.get();
        v.setAidl(19, "800");
        v.setAidl(4, "14.1");
        assertTrue(v.engineRunning());
        v.setAidl(4, "12.1"); // the service kept the old rpm, the battery is not charging
        assertFalse(v.engineRunning());
        // The idling Golf sent no rpm at all while the battery sat at 13.1-13.2 V (28 Oct 2026 recording).
        v.setAidl(19, "0");
        v.setAidl(4, "13.2");
        assertTrue(v.engineRunning());
        v.setAidl(4, "12.5"); // after the engine stopped
        assertFalse(v.engineRunning());
    }

    @Test
    public void ignitionFromFrames26And17() {
        VehicleState v = VehicleState.get();
        v.decodeFrame(null, 26, hex("7F8100000004FFFD00000000"));
        assertEquals(1, v.ignition());
        v.decodeFrame(null, 26, hex("7F0000000004FFFD00000000")); // 17:38:30 on 28 Oct: the engine was switched off
        assertEquals(0, v.ignition());
        v.decodeFrame(null, 17, hex("190000000000FFFC0000"));
        assertEquals(1, v.ignition());
        v.decodeFrame(null, 17, hex("180000000000FFFD0000"));
        assertEquals(0, v.ignition());
    }

    @Test
    public void trunkAndVoltageFromTheRecordedFrames() {
        VehicleState v = VehicleState.get();
        v.setAidl(4, "0.0");
        // driver door open and the trunk opened too (frames 26 and 18 of the A06 step)
        v.decodeFrame(null, 26, hex("7F8900000004FFFA00000000"));
        assertEquals(VehicleState.DOOR_DRIVER | VehicleState.DOOR_TRUNK, v.doors());
        v.decodeFrame(null, 18, hex("000088630980202B0D02"));
        assertEquals(VehicleState.DOOR_DRIVER | VehicleState.DOOR_TRUNK, v.doors());
        assertEquals(13.2f, v.voltage(), 0.001f); // bytes 8 and 9: 0D 02
        v.decodeFrame(null, 18, hex("000080630980202B0C08"));
        assertEquals(VehicleState.DOOR_DRIVER, v.doors()); // trunk shut again
        assertEquals(12.8f, v.voltage(), 0.001f);
        v.decodeFrame(null, 18, hex("000000630980202B0000")); // nonsense voltage bytes are left alone
        assertEquals(12.8f, v.voltage(), 0.001f);
    }
}
