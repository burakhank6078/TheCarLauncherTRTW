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
 * milliseconds, km/h, l/100 km. The last values are kept, so the pages are filled at once after a
 * restart (the car sends a frame about once a minute).
 */
public final class ObdData {
    /** Page index for {@link #set} / {@link #get}. */
    public static final int START = 0, REFUEL = 1, LONG = 2;
    /** Field index for {@link #set} / {@link #get}. */
    public static final int DIST = 0, TIME = 1, SPEED = 2, CONS = 3, RANGE = 4;

    private static final String KEY = "obd_trip_values";
    private static final ObdData INSTANCE = new ObdData();
    private final double[][] v = new double[3][5];
    private boolean loaded;

    private ObdData() {
        for (double[] row : v) Arrays.fill(row, Double.NaN);
    }

    public static ObdData get() { return INSTANCE; }

    public synchronized void set(int page, int field, double value) { v[page][field] = value; }

    /** The value, or NaN when it is not known. */
    public synchronized double get(int page, int field) { return v[page][field]; }

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
        }
        return true;
    }

    private static int word(byte[] b, int i) { return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF); }

    /** Fills the values from the last run; only the first call does anything. */
    public synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        String s = Prefs.str(c, KEY, "");
        String[] p = s.split(",");
        if (p.length != 15) return;
        try {
            double[][] t = new double[3][5];
            for (int i = 0; i < 15; i++) t[i / 5][i % 5] = Double.parseDouble(p[i]);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 5; j++) {
                    if (Double.isNaN(v[i][j])) v[i][j] = t[i][j]; // never over a value that just arrived
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /** Remembers the values for the next start. */
    public synchronized void save(Context c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 5; j++) {
                if (sb.length() > 0) sb.append(',');
                sb.append(String.format(Locale.US, "%s", v[i][j]));
            }
        }
        Prefs.put(c, KEY, sb.toString());
    }
}
