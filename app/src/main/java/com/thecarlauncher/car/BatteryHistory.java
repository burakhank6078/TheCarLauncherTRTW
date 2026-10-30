package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The resting battery voltage, one reading per ignition cycle (taken a few seconds after the ignition comes on, before the
 * engine starts), the last {@link #MAX} of them in the preferences. A battery that is going weak shows as a falling run
 * of readings long before it fails to start the car.
 */
public final class BatteryHistory {
    static final int MAX = 30;
    private static final String KEY = "battery_rest";

    private BatteryHistory() {}

    /** One reading: when (wall clock) and the voltage. */
    public static final class Reading {
        public final long at;
        public final float volts;

        Reading(long at, float volts) {
            this.at = at;
            this.volts = volts;
        }
    }

    public static synchronized void add(Context c, float volts) {
        Prefs.put(c, KEY, append(Prefs.str(c, KEY, ""), System.currentTimeMillis(), volts));
    }

    /** The readings, oldest first. */
    public static synchronized List<Reading> all(Context c) {
        return decode(Prefs.str(c, KEY, ""));
    }

    /** "time:volts" pairs separated by semicolons; the oldest drop out past {@link #MAX}. */
    static String append(String old, long at, float volts) {
        List<Reading> l = decode(old);
        l.add(new Reading(at, volts));
        while (l.size() > MAX) l.remove(0);
        StringBuilder sb = new StringBuilder();
        for (Reading r : l) sb.append(sb.length() > 0 ? ";" : "").append(r.at).append(':').append(String.format(Locale.US, "%.1f", r.volts));
        return sb.toString();
    }

    static List<Reading> decode(String s) {
        List<Reading> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            int i = part.indexOf(':');
            if (i <= 0) continue;
            try {
                float v = Float.parseFloat(part.substring(i + 1));
                if (v > 5 && v < 20) out.add(new Reading(Long.parseLong(part.substring(0, i)), v));
            } catch (NumberFormatException ignored) {
                // a broken entry is skipped
            }
        }
        return out;
    }

    /** The mean of the last n readings, NaN when there are none. */
    public static float average(List<Reading> l, int n) {
        if (l.isEmpty()) return Float.NaN;
        int from = Math.max(0, l.size() - n);
        float sum = 0;
        for (int i = from; i < l.size(); i++) sum += l.get(i).volts;
        return sum / (l.size() - from);
    }
}
