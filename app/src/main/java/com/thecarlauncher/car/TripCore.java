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
 *
 * While a trip runs the change of speed from one sample to the next is watched: braking harder than
 * {@link #HARD_BRAKE} m/s&sup2; or pulling away harder than {@link #HARD_ACCEL} m/s&sup2; counts as one event each time
 * (an event ends when the change falls back under 1 m/s&sup2;); {@link #score} turns the events into a number.
 */
public final class TripCore {
    public static final float START_KMH = 5f, MOVING_KMH = 2f;
    public static final long STOP_END_MS = 5 * 60_000L;
    public static final double MIN_KM = 0.5;
    /** Braking and acceleration (m/s&sup2;) that count as hard: about 0.3 g and 0.25 g. */
    public static final double HARD_BRAKE = 3.0, HARD_ACCEL = 2.5;
    /** A step between 0 and more than this is taken for a lost or regained GPS fix, not for driving. */
    private static final float LOST_FIX_KMH = 20f;
    /** A longer gap between two samples (no GPS, the unit asleep) adds no distance. */
    private static final long MAX_STEP_MS = 10_000L;

    /** One finished trip. */
    public static final class Trip {
        public final long start, end, movingMs;
        public final double km;
        public final float maxKmh;
        /** Average consumption in l/100 km when the car told it, else -1. */
        public final float consumption;
        /** Hard brakings and hard accelerations of the trip; -1 when the trip was recorded before they were counted. */
        public final int brakes, accels;

        public Trip(long start, long end, double km, long movingMs, float maxKmh, float consumption) {
            this(start, end, km, movingMs, maxKmh, consumption, -1, -1);
        }

        public Trip(long start, long end, double km, long movingMs, float maxKmh, float consumption, int brakes, int accels) {
            this.start = start;
            this.end = end;
            this.km = km;
            this.movingMs = movingMs;
            this.maxKmh = maxKmh;
            this.consumption = consumption;
            this.brakes = brakes;
            this.accels = accels;
        }

        public long durationMs() { return Math.max(0, end - start); }

        /** Average speed while moving, km/h. */
        public double avgKmh() { return movingMs <= 0 ? 0 : km / (movingMs / 3_600_000.0); }

        /** Driving score 0-100, or -1 when the events were not counted for this trip. */
        public int score() { return brakes < 0 ? -1 : TripCore.score(brakes, accels, km); }
    }

    /**
     * 100 minus 8 for every "event point" per 10 km, a hard braking counting two and a hard acceleration one (at least
     * 5 km are used, so a short trip with one braking is not judged harshly): 40 km with 3 brakings and 2 accelerations
     * is 84.
     */
    public static int score(int brakes, int accels, double km) {
        double per10 = (brakes * 2 + accels) / Math.max(km, 5.0) * 10.0;
        return (int) Math.max(0, Math.min(100, Math.round(100 - 8 * per10)));
    }

    private boolean active;
    private long startWall, lastMoveWall, last, lastMove, movingMs;
    private double km;
    private float max, lastKmh;
    private int brakes, accels;
    private boolean inBrake, inAccel;

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
                lastKmh = kmh;
                brakes = accels = 0;
                inBrake = inAccel = false;
            }
            return null;
        }
        long dt = mono - last;
        last = mono;
        watchSpeed(dt, kmh);
        lastKmh = kmh;
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

    /** Counts a hard braking or acceleration between two samples about a second apart (anything else is a gap). */
    private void watchSpeed(long dt, float kmh) {
        if (dt < 500 || dt > 2500) {
            inBrake = inAccel = false;
            return;
        }
        // No fix is fed as 0 km/h: a jump to or from 0 at speed is the GPS dropping out, not a braking or a pull-away.
        if ((kmh == 0f && lastKmh > LOST_FIX_KMH) || (lastKmh == 0f && kmh > LOST_FIX_KMH)) return;
        double a = (kmh - lastKmh) / 3.6 / (dt / 1000.0);
        if (a <= -HARD_BRAKE && !inBrake) {
            brakes++;
            inBrake = true;
        } else if (a > -1.0) {
            inBrake = false;
        }
        if (a >= HARD_ACCEL && !inAccel) {
            accels++;
            inAccel = true;
        } else if (a < 1.0) {
            inAccel = false;
        }
    }

    /** Ends the trip in progress at the moment it last moved (the app is closing); null when there is none or it was too short. */
    public Trip finish() {
        if (!active) return null;
        active = false;
        if (km < MIN_KM) return null;
        return new Trip(startWall, lastMoveWall, km, movingMs, max, -1f, brakes, accels);
    }

    public boolean active() { return active; }

    public double km() { return km; }

    /** Hard brakings and accelerations of the trip in progress. */
    public int brakes() { return brakes; }

    public int accels() { return accels; }

    /** State to keep while a trip is running, so a restart can still close it: start, lastMove (wall clock), km, movingMs, max, brakes, accels. */
    public String save() {
        return active ? startWall + "," + lastMoveWall + "," + String.format(java.util.Locale.US, "%.3f", km) + "," + movingMs + ","
                + String.format(java.util.Locale.US, "%.1f", max) + "," + brakes + "," + accels : "";
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
            int b = p.length >= 7 ? Integer.parseInt(p[5]) : -1, a = p.length >= 7 ? Integer.parseInt(p[6]) : -1;
            if (k < MIN_KM || lm <= st) return null;
            return new Trip(st, lm, k, mv, mx, -1f, b, a);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
