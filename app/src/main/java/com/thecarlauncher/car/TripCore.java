package com.thecarlauncher.car;

/**
 * Cuts the GPS speed into trips, without Android so it can be tested: a trip starts when the car reaches
 * {@link #START_KMH} and ends when it has stood still (under {@link #MOVING_KMH}) for {@link #STOP_END_MS}; the trip
 * ends at the moment it last moved. Distance is the speed summed over time. Very short trips (a car shuffled in a
 * car park) are dropped.
 *
 * Every sample carries two times: a monotonic one (SystemClock.elapsedRealtime) that all the timing rules use, because
 * this head unit's wall clock jumps (it was seen going back a day at engine start), and the wall clock, which only
 * gives the trip its start and end stamps.
 */
public final class TripCore {
    public static final float START_KMH = 5f, MOVING_KMH = 2f;
    public static final long STOP_END_MS = 5 * 60_000L;
    public static final double MIN_KM = 0.5;
    /** A longer gap between two samples (no GPS, the unit asleep) adds no distance. */
    private static final long MAX_STEP_MS = 10_000L;

    /** One finished trip. */
    public static final class Trip {
        public final long start, end, movingMs;
        public final double km;
        public final float maxKmh;
        /** Average consumption in l/100 km when the car told it, else -1. */
        public final float consumption;

        public Trip(long start, long end, double km, long movingMs, float maxKmh, float consumption) {
            this.start = start;
            this.end = end;
            this.km = km;
            this.movingMs = movingMs;
            this.maxKmh = maxKmh;
            this.consumption = consumption;
        }

        public long durationMs() { return Math.max(0, end - start); }

        /** Average speed while moving, km/h. */
        public double avgKmh() { return movingMs <= 0 ? 0 : km / (movingMs / 3_600_000.0); }
    }

    private boolean active;
    private long startWall, lastMoveWall, last, lastMove, movingMs;
    private double km;
    private float max;

    /**
     * One speed sample (km/h; 0 when there is no fix). mono is the monotonic time in ms, wall the wall clock in ms.
     * Returns the trip this sample finished, or null.
     */
    public Trip step(long mono, long wall, float kmh) {
        if (!active) {
            if (kmh >= START_KMH) {
                active = true;
                startWall = wall;
                lastMoveWall = wall;
                last = mono;
                lastMove = mono;
                km = 0;
                movingMs = 0;
                max = kmh;
            }
            return null;
        }
        long dt = mono - last;
        last = mono;
        if (kmh >= MOVING_KMH) {
            if (dt > 0 && dt <= MAX_STEP_MS) {
                km += kmh * (dt / 3_600_000.0);
                movingMs += dt;
            }
            lastMove = mono;
            lastMoveWall = wall;
            if (kmh > max) max = kmh;
            return null;
        }
        if (mono - lastMove >= STOP_END_MS) return finish();
        return null;
    }

    /** Ends the trip in progress at the moment it last moved (the app is closing); null when there is none or it was too short. */
    public Trip finish() {
        if (!active) return null;
        active = false;
        if (km < MIN_KM) return null;
        return new Trip(startWall, lastMoveWall, km, movingMs, max, -1f);
    }

    public boolean active() { return active; }

    public double km() { return km; }

    /** State to keep while a trip is running, so a restart can still close it: start, lastMove (wall clock), km, movingMs, max. */
    public String save() {
        return active ? startWall + "," + lastMoveWall + "," + String.format(java.util.Locale.US, "%.3f", km) + "," + movingMs + ","
                + String.format(java.util.Locale.US, "%.1f", max) : "";
    }

    /** The trip a saved state describes (ended where it last moved), or null when it is empty, broken or too short. */
    public static Trip fromSaved(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(",");
        if (p.length < 5) return null;
        try {
            long st = Long.parseLong(p[0]), lm = Long.parseLong(p[1]), mv = Long.parseLong(p[3]);
            double k = Double.parseDouble(p[2]);
            float mx = Float.parseFloat(p[4]);
            if (k < MIN_KM || lm <= st) return null;
            return new Trip(st, lm, k, mv, mx, -1f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
