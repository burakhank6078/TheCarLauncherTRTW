package com.thecarlauncher.car;

import android.content.Context;

import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The history of trips, cut from the GPS speed by {@link TripCore} and kept in the preferences (the last 400). A trip
 * also gets the car's own average consumption when the car's "since start" trip agrees with it on the distance.
 * Fed once a second from the launcher's clock.
 */
public final class TripLog {
    private static final String KEY = "trip_log", ACTIVE = "trip_active";
    private static final int MAX = 400;
    private static final long SAVE_EVERY_MS = 30_000;
    private static TripLog sInstance;

    private final Context app;
    private final TripCore core = new TripCore();
    private final List<TripCore.Trip> trips = new ArrayList<>(); // oldest first
    private long savedAt;

    public static synchronized TripLog get(Context c) {
        if (sInstance == null) sInstance = new TripLog(c.getApplicationContext());
        return sInstance;
    }

    private TripLog(Context c) {
        app = c;
        trips.addAll(decode(Prefs.str(c, KEY, "")));
        // A trip that was running when the app stopped is closed where it last moved.
        TripCore.Trip left = TripCore.fromSaved(Prefs.str(c, ACTIVE, ""));
        if (left != null) add(left);
        else Prefs.put(c, ACTIVE, "");
    }

    /** One speed sample (km/h, 0 without a fix); call about once a second. */
    public synchronized void tick(long mono, long wall, float kmh) {
        TripCore.Trip t = core.step(mono, wall, kmh);
        if (t != null) {
            add(withConsumption(t));
        } else if (core.active() && mono - savedAt >= SAVE_EVERY_MS) {
            savedAt = mono;
            Prefs.put(app, ACTIVE, core.save());
        }
    }

    /** True while a trip is running. */
    public synchronized boolean driving() { return core.active(); }

    /** The distance of the trip in progress, km. */
    public synchronized double currentKm() { return core.active() ? core.km() : 0; }

    /** All trips, newest first. */
    public synchronized List<TripCore.Trip> trips() {
        List<TripCore.Trip> out = new ArrayList<>(trips);
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized void clear() {
        trips.clear();
        Prefs.put(app, KEY, "");
    }

    private void add(TripCore.Trip t) {
        trips.add(t);
        while (trips.size() > MAX) trips.remove(0);
        Prefs.put(app, KEY, encode(trips));
        Prefs.put(app, ACTIVE, "");
    }

    /** The car's average since start, when its trip matches ours (same distance within 30 %). */
    private static TripCore.Trip withConsumption(TripCore.Trip t) {
        double cons = ObdData.get().get(ObdData.START, ObdData.CONS);
        double dist = ObdData.get().get(ObdData.START, ObdData.DIST);
        if (Double.isNaN(cons) || Double.isNaN(dist) || cons <= 0 || dist <= 0 || Math.abs(dist - t.km) > 0.3 * t.km) return t;
        return new TripCore.Trip(t.start, t.end, t.km, t.movingMs, t.maxKmh, (float) cons);
    }

    // ---- Storage and summary (no Android needed) -----------------------------------------------------------------

    /** One trip per line: start, end, km x100, moving seconds, max km/h x10, consumption x10 (-1 unknown). */
    static String encode(List<TripCore.Trip> list) {
        StringBuilder sb = new StringBuilder();
        for (TripCore.Trip t : list) {
            sb.append(t.start).append(',').append(t.end).append(',').append(Math.round(t.km * 100)).append(',')
                    .append(t.movingMs / 1000).append(',').append(Math.round(t.maxKmh * 10)).append(',')
                    .append(t.consumption < 0 ? -1 : Math.round(t.consumption * 10)).append('\n');
        }
        return sb.toString();
    }

    static List<TripCore.Trip> decode(String s) {
        List<TripCore.Trip> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split(",");
            if (p.length < 6) continue;
            try {
                long st = Long.parseLong(p[0]), en = Long.parseLong(p[1]);
                double km = Long.parseLong(p[2]) / 100.0;
                long mv = Long.parseLong(p[3]) * 1000;
                float mx = Long.parseLong(p[4]) / 10f;
                long c = Long.parseLong(p[5]);
                if (en < st || km <= 0) continue;
                out.add(new TripCore.Trip(st, en, km, mv, mx, c < 0 ? -1f : c / 10f));
            } catch (NumberFormatException ignored) {
                // a broken line is skipped
            }
        }
        return out;
    }

    /** The trips of one calendar month added up. */
    public static final class Month {
        public final int year, month; // month 1..12
        public int count;
        public double km;
        public long durationMs;
        /** Weighted average consumption in l/100 km over the trips that have one, else -1. */
        public float consumption = -1f;
        /** Fuel cost in the price's currency, or -1 when the consumption or the price is not known. */
        public double cost = -1;
        private double consKm, consLitres;

        Month(int year, int month) {
            this.year = year;
            this.month = month;
        }

        public String label() { return String.format(Locale.US, "%02d.%d", month, year); }
    }

    /** Months, newest first. pricePerLitre &lt;= 0 means unknown. */
    public static List<Month> months(List<TripCore.Trip> list, double pricePerLitre) {
        List<Month> out = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        for (TripCore.Trip t : list) {
            cal.setTimeInMillis(t.start);
            int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH) + 1;
            Month cur = null;
            for (Month x : out) if (x.year == y && x.month == m) cur = x;
            if (cur == null) {
                cur = new Month(y, m);
                out.add(cur);
            }
            cur.count++;
            cur.km += t.km;
            cur.durationMs += t.durationMs();
            if (t.consumption >= 0) {
                cur.consKm += t.km;
                cur.consLitres += t.km * t.consumption / 100.0;
            }
        }
        for (Month x : out) {
            if (x.consKm > 0) {
                x.consumption = (float) (x.consLitres / x.consKm * 100.0);
                if (pricePerLitre > 0) x.cost = x.consLitres * pricePerLitre;
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Month>() {
            @Override
            public int compare(Month a, Month b) {
                return a.year != b.year ? b.year - a.year : b.month - a.month;
            }
        });
        return out;
    }
}
