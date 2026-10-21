package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.util.Prefs;

import java.util.Arrays;
import java.util.Locale;

/**
 * Trip values that come from the car's Canbus data, for the three trip pages (since start,
 * since refuelling, long-term). {@link TwProbe} fills it from the Golf 7 frames 19 (since start),
 * 20 (long-term) and 21 (since refuelling). A value that is not known stays NaN and the page shows
 * "---" for it, like the factory unit does before it has data. Units are fixed: kilometres,
 * milliseconds, km/h, l/100 km.
 *
 * Frames 19 and 20 were matched with the factory screens (0:17 h and 774 km, 21:57 h); frame 21 as
 * "since refuelling" is inferred, nothing on the factory unit confirmed it yet.
 *
 * The last values are kept so the pages are filled at once after a restart (the car sends a frame
 * about once a minute). Each page remembers when its frame arrived and gives no value once that is
 * older than {@link #STALE_MS}, so old numbers are never shown as current.
 */
public final class ObdData {
    /** Page index for {@link #set} / {@link #get}. */
    public static final int START = 0, REFUEL = 1, LONG = 2;
    /** Field index for {@link #set} / {@link #get}. */
    public static final int DIST = 0, TIME = 1, SPEED = 2, CONS = 3, RANGE = 4;

    private static final long STALE_MS = 15 * 60_000L;
    private static final String KEY = "obd_trip_values";
    private static final ObdData INSTANCE = new ObdData();
    private final double[][] v = new double[3][5];
    private final long[] at = new long[3]; // when each page's frame last arrived (0 = never)
    private boolean loaded;

    private ObdData() {
        for (double[] row : v) Arrays.fill(row, Double.NaN);
    }

    public static ObdData get() { return INSTANCE; }

    public synchronized void set(int page, int field, double value) {
        v[page][field] = value;
        at[page] = System.currentTimeMillis();
    }

    /** The value, or NaN when it is not known or older than the limit. */
    public synchronized double get(int page, int field) {
        long age = System.currentTimeMillis() - at[page];
        if (age < 0 || age > STALE_MS) return Double.NaN; // a clock that went back makes the age unknown
        return v[page][field];
    }

    /**
     * Decodes one trip frame (10 bytes): consumption (2 bytes, 0.1 l/100 km), range (2, km), distance
     * (2, 0.1 km), time (2, minutes), average speed (1, km/h). An all-ones field means "no value".
     * Returns false when the frame is not a trip frame or too short.
     */
    public boolean decodeFrame(int frame, byte[] b) {
        if (b == null || b.length < 9) return false;
        int page = frame == 19 ? START : frame == 21 ? REFUEL : frame == 20 ? LONG : -1;
        if (page < 0) return false;
        int cons = word(b, 0), range = word(b, 2), dist = word(b, 4), min = word(b, 6), spd = b[8] & 0xFF;
        synchronized (this) {
            v[page][CONS] = cons == 0xFFFF ? Double.NaN : cons / 10.0;
            v[page][RANGE] = range == 0xFFFF ? Double.NaN : range;
            v[page][DIST] = dist == 0xFFFF ? Double.NaN : dist / 10.0;
            v[page][TIME] = min == 0xFFFF ? Double.NaN : min * 60000.0;
            v[page][SPEED] = spd == 0xFF ? Double.NaN : spd;
            at[page] = System.currentTimeMillis();
        }
        return true;
    }

    private static int word(byte[] b, int i) { return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); }

    /** Fills the values from the last run; only the first call does anything. */
    public synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        String[] p = Prefs.str(c, KEY, "").split(",");
        if (p.length != 18) return; // 15 values + 3 times; anything else is an older format
        try {
            for (int i = 0; i < 3; i++) {
                long t = Long.parseLong(p[15 + i]);
                if (t <= at[i] || t > System.currentTimeMillis()) continue; // newer frame already here, or time unknown
                at[i] = t;
                for (int j = 0; j < 5; j++) v[i][j] = Double.parseDouble(p[i * 5 + j]);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /** Remembers the values for the next start. */
    public synchronized void save(Context c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 5; j++) sb.append(String.format(Locale.US, "%s", v[i][j])).append(',');
        }
        sb.append(at[0]).append(',').append(at[1]).append(',').append(at[2]);
        Prefs.put(c, KEY, sb.toString());
    }
}
