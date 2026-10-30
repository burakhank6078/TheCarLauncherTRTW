package com.thecarlauncher.car;

/**
 * The rules behind the car events (ignition on and off, a weak battery at rest, a charging fault), without Android so they
 * can be tested. Stepped about once a second with the ignition flag of the car, the battery voltage, the speed and whether
 * the engine runs; every event is returned once.
 *
 * Why these numbers: the Golf's own charging control moves the voltage between about 12.2 and 15 V while driving, so a
 * dip alone says nothing and a fault has to last; at rest a healthy battery sits at 12.3-12.7 V and cranking pulls it
 * under 11 V for a second or two, so a weak battery has to stay under 12.0 V for several seconds with the engine off.
 */
public final class CarEventsLogic {
    public enum Event { NONE, IGNITION_ON, IGNITION_OFF, WEAK_BATTERY, CHARGING_FAULT, REST_SAMPLE }

    static final float WEAK_V = 12.0f, FAULT_V = 12.4f, RECOVER_V = 12.8f, CRANK_V = 10.5f;
    static final long WEAK_MS = 5000, FAULT_MS = 90_000, REST_AFTER_MS = 6000;
    static final float FAULT_KMH = 20f, STILL_KMH = 2f;

    private int lastIgn = -1;
    private long ignOnAt, lowSince, faultSince;
    private boolean weakWarned, faultWarned, restTaken;
    private float voltage = Float.NaN;

    /** The voltage that belongs to the last WEAK_BATTERY or REST_SAMPLE event. */
    public float voltage() { return voltage; }

    /** One look at the car. ign is 1, 0 or -1 (not known); volts NaN when not known; now in monotonic milliseconds. */
    public Event step(long now, int ign, float volts, float kmh, boolean engineRunning) {
        if (ign < 0) return Event.NONE;
        if (lastIgn < 0) { // the first thing known: no event, just the starting point
            lastIgn = ign;
            if (ign == 1) startCycle(now);
            return Event.NONE;
        }
        if (ign != lastIgn) {
            lastIgn = ign;
            if (ign == 1) {
                startCycle(now);
                return Event.IGNITION_ON;
            }
            return Event.IGNITION_OFF;
        }
        if (ign != 1 || Float.isNaN(volts)) return Event.NONE;

        boolean still = kmh < STILL_KMH && !engineRunning;
        // A weak battery: ignition on, engine off, under 12.0 V for several seconds in a row.
        if (still && volts < WEAK_V && volts >= CRANK_V) {
            if (lowSince == 0) lowSince = now;
            if (!weakWarned && now - lowSince >= WEAK_MS) {
                weakWarned = true;
                restTaken = true;
                voltage = volts;
                return Event.WEAK_BATTERY;
            }
        } else {
            lowSince = 0;
        }
        // A charging fault: moving, and under 12.4 V for a minute and a half.
        if (kmh >= FAULT_KMH && volts < FAULT_V) {
            if (faultSince == 0) faultSince = now;
            if (!faultWarned && now - faultSince >= FAULT_MS) {
                faultWarned = true;
                voltage = volts;
                return Event.CHARGING_FAULT;
            }
        } else if (volts >= RECOVER_V || kmh < FAULT_KMH) {
            faultSince = 0;
        }
        // The resting voltage once per ignition cycle, for the history, when the engine has not started.
        if (!restTaken && still && volts >= CRANK_V && now - ignOnAt >= REST_AFTER_MS) {
            restTaken = true;
            voltage = volts;
            return Event.REST_SAMPLE;
        }
        return Event.NONE;
    }

    private void startCycle(long now) {
        ignOnAt = now;
        lowSince = 0;
        faultSince = 0;
        weakWarned = faultWarned = restTaken = false;
    }

    /** The clock screen shows once the ignition has been off for a few seconds, until it is touched or the ignition comes on. */
    public static boolean standbyWanted(boolean enabled, int ign, long offForMs, boolean dismissed) {
        return enabled && ign == 0 && offForMs >= 8000 && !dismissed;
    }
}
