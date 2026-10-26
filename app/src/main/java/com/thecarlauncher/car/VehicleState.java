package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.util.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Live state of the car for the Vehicle status page: doors, driver seat belt, lights, battery voltage
 * and the chassis number. Everything is read-only and comes from what the unit already receives:
 * <ul>
 * <li>Golf 7 frame 26 (byte 1 high nibble = doors, byte 2 low bits = indicators) and frame 18 (byte 2 doors,
 * byte 6 bit 0x20 = driver belt not fastened), found with the 22 Oct 2026 test recording;</li>
 * <li>frame 31 = the chassis number as ASCII;</li>
 * <li>the car info service: #4 battery voltage, #5 lights, #12 driver belt (1 = fastened), #19 rpm.</li>
 * </ul>
 * The car sends the frames when something changes, so a value is only shown while the unit is
 * talking to the box (the heartbeat frame 255 or a service answer arrived in the last few seconds).
 */
public final class VehicleState {
    /** Door bits as in frames 26 and 18 (the rear right one is by order of the test: not checked on its own). */
    public static final int DOOR_DRIVER = 8, DOOR_PASSENGER = 4, DOOR_REAR_LEFT = 2, DOOR_REAR_RIGHT = 1;
    /** The doors in the order screens list them: driver, passenger, rear left, rear right. */
    public static final int[] DOOR_ORDER = {DOOR_DRIVER, DOOR_PASSENGER, DOOR_REAR_LEFT, DOOR_REAR_RIGHT};

    private static final long ALIVE_MS = 10000;
    private static final String PREF_VIN = "car_vin"; // only read to move an old value
    private static final String VIN_FILE = "car_vin.txt";
    private static final String VIN_PATTERN = "[A-HJ-NPR-Z0-9]{17}";
    private static final VehicleState INSTANCE = new VehicleState();

    private int doors = -1, belt = -1, lights = -1, rpm = -1;
    private float voltage = Float.NaN;
    private long beatAt, serviceAt, doorsAt;
    private String vin;

    private VehicleState() {}

    public static VehicleState get() { return INSTANCE; }

    /** Feeds one 0x501 frame; unknown frames are ignored. */
    public synchronized void decodeFrame(Context c, int frame, byte[] b) {
        if (b == null) return;
        long now = System.currentTimeMillis();
        if (frame == 255) {
            beatAt = now;
        } else if (frame == 26 && b.length >= 3) {
            doors = (b[1] >> 4) & 0xF;
            doorsAt = now;
            beatAt = now;
        } else if (frame == 18 && b.length >= 7) {
            doors = (b[2] >> 4) & 0xF;
            doorsAt = now;
            belt = (b[6] & 0x20) != 0 ? 0 : 1;
            beatAt = now;
        } else if (frame == 31 && b.length >= 17) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 17; i++) sb.append((char) (b[i] & 0xFF));
            String v = sb.toString();
            if (v.matches(VIN_PATTERN) && !v.equals(vin)) {
                vin = v;
                if (c != null) saveVin(c, v);
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
     * so a charging voltage is required as well (when a voltage is known).
     */
    public synchronized boolean engineRunning() {
        if (!serviceFresh() || rpm <= 400) return false;
        return Float.isNaN(voltage) || voltage >= 12.9f;
    }

    /** Battery voltage, NaN when not known. */
    public synchronized float voltage() { return serviceFresh() ? voltage : Float.NaN; }

    /** True while the car info service keeps answering (its values are only as old as the last poll). */
    private boolean serviceFresh() { return System.currentTimeMillis() - serviceAt < ALIVE_MS; }

    /** How long ago a door frame (18 or 26) last arrived, or Long.MAX_VALUE if none did. */
    public synchronized long doorsAgeMs() { return doorsAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - doorsAt; }

    /** The 17-character chassis number, from the car or the last time it was read; null when never seen. */
    public synchronized String vin(Context c) {
        if (vin == null && c != null) {
            String saved = loadVin(c);
            if (saved != null) vin = saved;
        }
        return vin;
    }

    // The chassis number lives in a file the backup leaves out, not in the preferences (those are backed up).
    private static File vinFile(Context c) { return new File(c.getApplicationContext().getNoBackupFilesDir(), VIN_FILE); }

    private static void saveVin(Context c, String v) {
        try (FileWriter w = new FileWriter(vinFile(c))) {
            w.write(v);
        } catch (IOException ignored) {
            // shown from memory this time; the next frame 31 tries again
        }
    }

    private static String loadVin(Context c) {
        String old = Prefs.str(c, PREF_VIN, null);
        if (old != null) { // moves the value an earlier version kept in the preferences
            Prefs.of(c).edit().remove(PREF_VIN).apply();
            if (old.matches(VIN_PATTERN)) saveVin(c, old);
        }
        try (BufferedReader r = new BufferedReader(new FileReader(vinFile(c)))) {
            String s = r.readLine();
            return s != null && s.matches(VIN_PATTERN) ? s : null;
        } catch (IOException e) {
            return null;
        }
    }
}
