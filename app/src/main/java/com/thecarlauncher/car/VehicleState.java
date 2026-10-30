package com.thecarlauncher.car;

import android.content.Context;

/**
 * Live state of the car for the Vehicle status page: doors, driver seat belt, lights and battery voltage.
 * Everything is read-only and comes from what the unit already receives:
 * <ul>
 * <li>Golf 7 frame 26 (byte 1 high nibble = doors, byte 2 low bits = indicators) and frame 18 (byte 2 doors,
 * byte 6 bit 0x20 = driver belt not fastened, bytes 8-9 battery voltage), found with the test recordings;</li>
 * <li>the car info service: #4 battery voltage, #5 lights, #12 driver belt (1 = fastened), #19 rpm.</li>
 * </ul>
 * The car sends the frames when something changes, so a value is only shown while the unit is
 * talking to the box (the heartbeat frame 255 or a service answer arrived in the last few seconds).
 */
public final class VehicleState {
    /**
     * Door bits as in frames 26 and 18 (the rear right one is by order of the test: not checked on its own). The trunk is
     * the 0x08 bit of the byte under the doors' nibble (frame 26 byte 1, frame 18 byte 2), seen once, 28 Oct 2026.
     */
    public static final int DOOR_DRIVER = 8, DOOR_PASSENGER = 4, DOOR_REAR_LEFT = 2, DOOR_REAR_RIGHT = 1, DOOR_TRUNK = 16;
    /** The doors in the order screens list them: driver, passenger, rear left, rear right, trunk. */
    public static final int[] DOOR_ORDER = {DOOR_DRIVER, DOOR_PASSENGER, DOOR_REAR_LEFT, DOOR_REAR_RIGHT, DOOR_TRUNK};

    private static final long ALIVE_MS = 10000;
    private static final float CHARGING_V = 13.0f;
    private static final VehicleState INSTANCE = new VehicleState();

    private int doors = -1, belt = -1, lights = -1, rpm = -1;
    private float voltage = Float.NaN;
    private long beatAt, serviceAt, doorsAt;

    private VehicleState() {}

    public static VehicleState get() { return INSTANCE; }

    /** Feeds one 0x501 frame; unknown frames are ignored. */
    public synchronized void decodeFrame(Context c, int frame, byte[] b) {
        if (b == null) return;
        long now = System.currentTimeMillis();
        if (frame == 255) {
            beatAt = now;
        } else if (frame == 26 && b.length >= 3) {
            doors = ((b[1] >> 4) & 0xF) | ((b[1] & 0x08) != 0 ? DOOR_TRUNK : 0);
            doorsAt = now;
            beatAt = now;
            ign = b[1] & 1; // the ignition flag: 0x81 -> 0x00 the moment the engine was switched off (28 Oct 2026)
        } else if (frame == 17 && b.length >= 1) {
            ign = b[0] & 1; // the same flag in frame 17 (0x19 -> 0x18 at that moment)
            beatAt = now;
        } else if (frame == 18 && b.length >= 7) {
            doors = ((b[2] >> 4) & 0xF) | ((b[2] & 0x08) != 0 ? DOOR_TRUNK : 0);
            doorsAt = now;
            belt = (b[6] & 0x20) != 0 ? 0 : 1;
            beatAt = now;
            if (b.length >= 10) {
                // Bytes 8 and 9 are the battery voltage as volts and tenths (0D 02 = 13.2 V), the same as the service's #4.
                int volts = b[8] & 0xFF, tenths = b[9] & 0xFF;
                if (volts >= 6 && volts <= 20 && tenths <= 9) voltage = volts + tenths / 10f;
            }
        }
    }

    /** Feeds one answer of the car info service (the text form the poller already has). */
    public synchronized void setAidl(int code, String text) {
        serviceAt = System.currentTimeMillis();
        if (code != 4 && code != 5 && code != 12 && code != 19) return; // the rest are arrays, not worth a failed parse
        float v;
        try {
            v = Float.parseFloat(text);
        } catch (RuntimeException e) {
            return;
        }
        switch (code) {
            case 4: voltage = v > 0 ? v : Float.NaN; break;
            case 5: lights = v != 0 ? 1 : 0; break;
            case 12: belt = v != 0 ? 1 : 0; break;
            default: rpm = Math.round(v); break;
        }
    }

    /** True while the unit is receiving from the car. */
    public synchronized boolean alive() {
        long now = System.currentTimeMillis();
        return now - beatAt < ALIVE_MS || now - serviceAt < ALIVE_MS;
    }

    private int ign = -1;

    /**
     * 1 = ignition on, 0 = off, -1 = not known yet. It is the last thing the car said (the car sends it when it changes and
     * about twice a second while it is on), so "off" stays off while the box is silent.
     */
    public synchronized int ignition() { return ign; }

    /** Open doors as a bit mask of the DOOR_ constants, or -1 when not known. */
    public synchronized int doors() { return alive() ? doors : -1; }

    /** 1 = driver belt fastened, 0 = not fastened, -1 = not known. */
    public synchronized int belt() { return alive() ? belt : -1; }

    /** 1 = lights on, 0 = off, -1 = not known. */
    public synchronized int lights() { return serviceFresh() ? lights : -1; }

    /** Engine speed from the car info service, or -1 when the service has gone quiet. */
    public synchronized int rpm() { return serviceFresh() ? rpm : -1; }

    /**
     * True when the engine is running. The service may keep reporting the last rpm after the engine stops,
     * so a charging voltage is required as well (when a voltage is known). On the Golf the idling engine showed no rpm
     * at all in the 28 Oct 2026 recording while the battery sat at 13.1-13.2 V, so a charging voltage alone counts too.
     */
    public synchronized boolean engineRunning() {
        if (!serviceFresh()) return false;
        if (!Float.isNaN(voltage) && voltage >= CHARGING_V) return true;
        return rpm > 400 && (Float.isNaN(voltage) || voltage >= 12.9f);
    }

    /** Battery voltage, NaN when not known. */
    public synchronized float voltage() { return serviceFresh() ? voltage : Float.NaN; }

    /** True while the car info service keeps answering (its values are only as old as the last poll). */
    private boolean serviceFresh() { return System.currentTimeMillis() - serviceAt < ALIVE_MS; }

    /** How long ago a door frame (18 or 26) last arrived, or Long.MAX_VALUE if none did. */
    public synchronized long doorsAgeMs() { return doorsAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - doorsAt; }

    /**
     * Earlier versions showed the chassis number on the status page and kept a copy on the unit (a file, and before that a
     * preference). It is not shown any more, so the leftovers are removed.
     */
    public static void forgetChassisNumber(Context c) {
        Context app = c.getApplicationContext();
        new java.io.File(app.getNoBackupFilesDir(), "car_vin.txt").delete();
        android.content.SharedPreferences sp = com.thecarlauncher.util.Prefs.of(app);
        if (sp.contains("car_vin")) sp.edit().remove("car_vin").apply();
    }
}
