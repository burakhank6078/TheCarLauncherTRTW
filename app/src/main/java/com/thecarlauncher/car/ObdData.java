package com.thecarlauncher.car;

import java.util.Arrays;

/**
 * Trip values that come from the car's OBD / Canbus data, for the three trip pages (since start,
 * since refuelling, long-term). Nothing fills it yet: the reader that does will call {@link #set}.
 * A value that is not known stays NaN and the page shows "---" for it, like the factory unit does
 * before it has data. Units are fixed: kilometres, milliseconds, km/h, l/100 km.
 */
public final class ObdData {
    /** Page index for {@link #set} / {@link #get}. */
    public static final int START = 0, REFUEL = 1, LONG = 2;
    /** Field index for {@link #set} / {@link #get}. */
    public static final int DIST = 0, TIME = 1, SPEED = 2, CONS = 3, RANGE = 4;

    private static final ObdData INSTANCE = new ObdData();
    private final double[][] v = new double[3][5];

    private ObdData() {
        for (double[] row : v) Arrays.fill(row, Double.NaN);
    }

    public static ObdData get() { return INSTANCE; }

    public synchronized void set(int page, int field, double value) { v[page][field] = value; }

    /** The value, or NaN when it is not known. */
    public synchronized double get(int page, int field) { return v[page][field]; }
}
