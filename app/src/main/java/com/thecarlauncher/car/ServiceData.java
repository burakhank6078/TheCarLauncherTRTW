package com.thecarlauncher.car;

/**
 * What the car itself says about its next service, from Golf 7 frame 30 (asked for on request, like the
 * factory car app does). Two services, each with days and distance left: the inspection and the oil
 * change. A field has one of three states: no value, "in N" (still to go) or "overdue N". The unit
 * of the distances is km, or miles when the first byte is not zero. Read-only: nothing is ever
 * written to the car from here.
 */
public final class ServiceData {
    /** Which of the four values. */
    public static final int INSPECTION_DAYS = 0, INSPECTION_DIST = 1, OIL_DAYS = 2, OIL_DIST = 3;

    private static final ServiceData INSTANCE = new ServiceData();
    private final long[] value = new long[4];
    /** 0 = no value, 1 = still to go, 2 = overdue. */
    private final int[] state = new int[4];
    private boolean miles;
    private long at;

    private ServiceData() {}

    public static ServiceData get() { return INSTANCE; }

    /** Decodes the 10-byte payload of frame 30; false when it is too short. */
    public boolean decodeFrame(byte[] b) {
        if (b == null || b.length < 10) return false;
        synchronized (this) {
            miles = b[0] != 0;
            int s = b[1] & 0xFF;
            // Bits 7-6 inspection days, 5-4 inspection distance, 3-2 oil days, 1-0 oil distance.
            for (int i = 0; i < 4; i++) {
                int st = (s >> (6 - 2 * i)) & 3;
                state[i] = st == 1 || st == 2 ? st : 0;
                int raw = ((b[2 + 2 * i] & 0xFF) << 8) | (b[3 + 2 * i] & 0xFF);
                value[i] = (i == INSPECTION_DIST || i == OIL_DIST) ? raw * 100L : raw;
            }
            at = System.currentTimeMillis();
        }
        return true;
    }

    /** True once the car has answered. */
    public synchronized boolean known() { return at != 0; }

    /** 0 no value, 1 still to go, 2 overdue. */
    public synchronized int state(int which) { return state[which]; }

    /** Days or distance left (or overdue by), always positive. */
    public synchronized long value(int which) { return value[which]; }

    public synchronized boolean miles() { return miles; }
}
