package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TripCoreTest {
    /** The wall clock starts at an arbitrary offset from the monotonic one. */
    private static final long WALL = 1_700_000_000_000L;

    /** Drives at kmh for the given seconds, one sample a second, starting at t0. Returns the end time. */
    private static long drive(TripCore c, long t0, float kmh, int seconds) {
        long t = t0;
        for (int i = 0; i < seconds; i++) {
            t += 1000;
            assertNull(c.step(t, WALL + t, kmh));
        }
        return t;
    }

    @Test
    public void aTripStartsAtSpeedAndEndsAfterFiveMinutesStanding() {
        TripCore c = new TripCore();
        assertFalse(c.active());
        c.step(0, WALL, 0);
        long t = drive(c, 0, 60f, 600); // 10 minutes at 60 km/h = 10 km
        assertTrue(c.active());
        assertEquals(10.0, c.km(), 0.1);
        TripCore.Trip trip = null;
        for (long s = t + 1000; s <= t + TripCore.STOP_END_MS + 1000 && trip == null; s += 1000) trip = c.step(s, WALL + s, 0);
        assertNotNull(trip);
        assertEquals(10.0, trip.km, 0.1);
        assertEquals(WALL + t, trip.end); // ended where it last moved
        assertEquals(60.0, trip.avgKmh(), 1.0);
        assertEquals(60f, trip.maxKmh, 0.01);
        assertFalse(c.active());
    }

    @Test
    public void aShortStopDoesNotEndTheTrip() {
        TripCore c = new TripCore();
        c.step(0, WALL, 50);
        long t = drive(c, 0, 50f, 300);
        t = drive(c, t, 0f, 120); // two minutes at a red light
        t = drive(c, t, 50f, 300);
        assertTrue(c.active());
        assertEquals(50.0 * 600 / 3600.0, c.km(), 0.2);
    }

    @Test
    public void aShuffleInACarParkIsNotATrip() {
        TripCore c = new TripCore();
        c.step(0, WALL, 8);
        long t = drive(c, 0, 8f, 20); // about 44 m
        TripCore.Trip trip = null;
        for (long s = t + 1000; s <= t + TripCore.STOP_END_MS + 2000 && trip == null; s += 1000) trip = c.step(s, WALL + s, 0);
        assertNull(trip);
        assertFalse(c.active());
    }

    @Test
    public void aGapInTheSamplesAddsNoDistance() {
        TripCore c = new TripCore();
        c.step(0, WALL, 90);
        c.step(1000, WALL + 1000, 90);
        c.step(61_000, WALL + 61_000, 90); // the unit was asleep for a minute
        assertTrue(c.km() < 0.05);
    }

    @Test
    public void theWallClockJumpingDoesNotDisturbTheTrip() {
        TripCore c = new TripCore();
        c.step(0, WALL, 60);
        long t = drive(c, 0, 60f, 120);
        // the wall clock goes back a day (as at engine start on this unit) while the monotonic clock runs on
        for (int i = 0; i < 120; i++) {
            t += 1000;
            assertNull(c.step(t, WALL - 86_400_000L + t, 60f));
        }
        assertTrue(c.active());
        assertEquals(60.0 * 240 / 3600.0, c.km(), 0.1); // distance kept adding
        TripCore.Trip trip = null;
        for (long s = t + 1000; s <= t + TripCore.STOP_END_MS + 1000 && trip == null; s += 1000) {
            trip = c.step(s, WALL - 86_400_000L + s, 0);
        }
        assertNotNull(trip); // and the stop rule still fired on time
    }

    /** A second-by-second speed list in km/h, one sample a second; returns the end time. */
    private static long speeds(TripCore c, long t0, float... kmh) {
        long t = t0;
        for (float v : kmh) {
            t += 1000;
            c.step(t, WALL + t, v);
        }
        return t;
    }

    @Test
    public void aHardBrakingAndAHardAccelerationAreCountedOnceEach() {
        TripCore c = new TripCore();
        c.step(0, WALL, 50);
        long t = speeds(c, 0, 50, 50, 50);
        t = speeds(c, t, 38, 26, 14, 8, 8);   // 12 km/h a second = 3.3 m/s2 for three seconds: one braking
        t = speeds(c, t, 20, 32, 44, 44);     // 12 km/h a second the other way: one hard acceleration
        assertEquals(1, c.brakes());
        assertEquals(1, c.accels());
        drive(c, t, 44f, 100);
        TripCore.Trip trip = c.finish();
        assertNotNull(trip);
        assertEquals(1, trip.brakes);
        assertEquals(1, trip.accels);
    }

    @Test
    public void gentleDrivingCountsNothingAndAGpsGapIsNotABraking() {
        TripCore c = new TripCore();
        c.step(0, WALL, 50);
        long t = speeds(c, 0, 52, 54, 56, 58, 60, 58, 56, 54, 52, 50); // 2 km/h a second: gentle
        assertEquals(0, c.brakes());
        assertEquals(0, c.accels());
        // the fix is lost for 6 seconds and the next sample says 0: a gap, not a stop from 50 in one second
        t += 6000;
        c.step(t, WALL + t, 0);
        assertEquals(0, c.brakes());
    }

    @Test
    public void theScoreFallsWithTheEventsPerDistance() {
        assertEquals(100, TripCore.score(0, 0, 40));
        assertEquals(84, TripCore.score(3, 2, 40));
        assertEquals(92, TripCore.score(1, 2, 40));
        assertEquals(0, TripCore.score(40, 40, 10));
        assertEquals(68, TripCore.score(1, 0, 1)); // a short trip counts as 5 km at least: 2 / 5 * 10 = 4 points, 4 x 8 = 32 off
    }

    @Test
    public void aSavedTripKeepsItsEvents() {
        TripCore c = new TripCore();
        c.step(0, WALL, 60);
        long t = speeds(c, 0, 60, 60, 60, 48, 36, 24, 12, 12);
        drive(c, t, 60f, 200);
        TripCore.Trip back = TripCore.fromSaved(c.save());
        assertNotNull(back);
        assertEquals(1, back.brakes);
        // an old saved state without the two fields still reads, the events are then unknown
        TripCore.Trip old = TripCore.fromSaved("1700000000000,1700000300000,5.000,300000,60.0");
        assertNotNull(old);
        assertEquals(-1, old.brakes);
        assertEquals(-1, old.score());
    }

    @Test
    public void aSavedTripCanBeClosedAfterARestart() {
        TripCore c = new TripCore();
        c.step(0, WALL, 60);
        drive(c, 0, 60f, 300);
        String saved = c.save();
        TripCore.Trip t = TripCore.fromSaved(saved);
        assertNotNull(t);
        assertEquals(5.0, t.km, 0.1);
        assertNull(TripCore.fromSaved(""));
        assertNull(TripCore.fromSaved("garbage"));
    }

    @Test
    public void aLostGpsFixIsNotABrakingAndItsReturnIsNotAPullAway() {
        TripCore c = new TripCore();
        c.step(0, WALL, 0);
        long t = drive(c, 0, 80f, 30);
        t += 1000;
        c.step(t, WALL + t, 0); // the fix is lost: fed as 0
        t = drive(c, t, 80f, 5); // and it is back
        assertEquals(0, c.brakes());
        assertEquals(0, c.accels());
    }
}
