package com.thecarlauncher.car;

import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * The values an ELM327 adapter on the OBD-II port has given lately, in one place next to {@link VehicleState} (which holds
 * what the Canbus box says). A value that has not been refreshed for a while counts as not known, so a dropped
 * connection never leaves an old temperature on the screen. VehicleState falls back to the voltage and the rpm kept here
 * when the Canbus box is silent, so the pages that read VehicleState do not need to know where a value came from.
 */
public final class Obd2Data {
    /** How long a value stays valid without a new reading. */
    static final long FRESH_MS = 15_000;

    private static final Obd2Data INSTANCE = new Obd2Data();

    private final Map<Integer, Double> values = new HashMap<>();
    private final Map<Integer, Long> at = new HashMap<>();
    private double adapterVolts = Double.NaN;
    private long adapterVoltsAt;
    private String status = "";

    private Obd2Data() {}

    public static Obd2Data get() { return INSTANCE; }

    public synchronized void set(int pid, double value) { set(pid, value, SystemClock.elapsedRealtime()); }

    synchronized void set(int pid, double value, long now) {
        if (Double.isNaN(value)) return;
        values.put(pid, value);
        at.put(pid, now);
    }

    /** The value of a Mode 01 request (see {@link Elm327Protocol}), NaN when it has not been given lately. */
    public synchronized double get(int pid) { return get(pid, SystemClock.elapsedRealtime()); }

    synchronized double get(int pid, long now) {
        Long t = at.get(pid);
        Double v = values.get(pid);
        return t == null || v == null || now - t > FRESH_MS ? Double.NaN : v;
    }

    /** The adapter's own measurement of the battery (the OBD port's pin 16), in volts, NaN when not lately. */
    public synchronized void setAdapterVolts(double volts) {
        adapterVolts = volts;
        adapterVoltsAt = SystemClock.elapsedRealtime();
    }

    /** The battery voltage from the adapter or, failing that, the control module's; NaN when neither is fresh. */
    public synchronized double voltage() { return voltage(SystemClock.elapsedRealtime()); }

    synchronized double voltage(long now) {
        if (!Double.isNaN(adapterVolts) && now - adapterVoltsAt <= FRESH_MS) return adapterVolts;
        return get(MODULE_VOLTAGE, now);
    }

    private static final int MODULE_VOLTAGE = Elm327Protocol.MODULE_VOLTAGE;

    /** The rpm, NaN when not lately. */
    public synchronized double rpm() { return get(Elm327Protocol.RPM); }

    /** The outside temperature in degrees C, NaN when the car does not give it or it is not fresh. */
    public synchronized double outsideC() { return get(Elm327Protocol.AMBIENT); }

    /** What the connection is doing, in English, for the settings page. */
    public synchronized void setStatus(String s) { status = s == null ? "" : s; }

    public synchronized String status() { return status; }

    /** True while at least one value is fresh. */
    public synchronized boolean alive() {
        long now = SystemClock.elapsedRealtime();
        for (Long t : at.values()) if (now - t <= FRESH_MS) return true;
        return !Double.isNaN(adapterVolts) && now - adapterVoltsAt <= FRESH_MS;
    }

    /** Forgets everything (the connection ended). */
    public synchronized void clear() {
        values.clear();
        at.clear();
        adapterVolts = Double.NaN;
    }
}
