package com.thecarlauncher.car;

/**
 * Ties the ambient strip to the ignition and the headlights, without Android so it can be tested. Stepped about once a
 * second with the ignition flag of the car and the headlight flag (the car gives one flag for parking lights, low beam and
 * high beam alike, so they cannot be told apart yet).
 *
 * The answer is the share of the strip's own level to send, in percent: {@link #NONE} leaves the strip alone, 0 makes it
 * dark, 40 sends 40 % of the level the user set, 100 the level as it is.
 * <ul>
 * <li>Ignition off: after the delay the strip goes dark (when that is wanted); before that it is left as it is.</li>
 * <li>Ignition on, headlights on for a few seconds: the night share; off for a few seconds: the day share. A share of 0
 * in the settings means "do not touch".</li>
 * <li>Ignition or lights not known: the strip keeps what it has.</li>
 * </ul>
 */
public final class AmbientAutoLogic {
    public static final int NONE = -1;
    /** How long the lights have to hold their new state, as for the map's night mode. */
    public static final long HOLD_MS = 4000;

    /** What the user asked for in the settings. */
    public static final class Params {
        public boolean enabled, darkWhenIgnitionOff;
        public int delaySeconds, nightPercent, dayPercent;

        public Params(boolean enabled, boolean darkWhenIgnitionOff, int delaySeconds, int nightPercent, int dayPercent) {
            this.enabled = enabled;
            this.darkWhenIgnitionOff = darkWhenIgnitionOff;
            this.delaySeconds = delaySeconds;
            this.nightPercent = nightPercent;
            this.dayPercent = dayPercent;
        }
    }

    private boolean night;
    private int pending = -1, lastIgn = -1, last = NONE;
    private long since, ignOffAt;

    /** ign and lights: 1 on, 0 off, -1 not known; now in monotonic milliseconds. */
    public int step(long now, int ign, int lights, Params p) {
        if (!p.enabled) {
            reset();
            return NONE;
        }
        if (ign != lastIgn) {
            if (ign == 0) ignOffAt = now;
            lastIgn = ign;
        }
        if (lights >= 0) debounce(now, lights == 1);
        else pending = -1;
        int out;
        if (ign == 0) {
            out = p.darkWhenIgnitionOff && now - ignOffAt >= p.delaySeconds * 1000L ? 0 : NONE;
        } else if (ign == 1) {
            if (lights < 0) return last == 0 ? NONE : last; // the lights are not known: hold what the last look decided
            int share = night ? p.nightPercent : p.dayPercent;
            out = share <= 0 ? NONE : Math.min(100, share);
        } else {
            out = NONE;
        }
        last = out;
        return out;
    }

    private void debounce(long now, boolean want) {
        if (want == night) {
            pending = -1;
            return;
        }
        int w = want ? 1 : 0;
        if (pending != w) {
            pending = w;
            since = now;
        } else if (now - since >= HOLD_MS) {
            night = want;
            pending = -1;
        }
    }

    private void reset() {
        night = false;
        pending = -1;
        lastIgn = -1;
        last = NONE;
    }
}
