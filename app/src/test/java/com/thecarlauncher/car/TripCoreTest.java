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
}
