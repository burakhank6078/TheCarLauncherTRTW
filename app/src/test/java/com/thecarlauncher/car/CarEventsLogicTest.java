package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thecarlauncher.car.CarEventsLogic.Event;

import org.junit.Test;

public class CarEventsLogicTest {
    private static final float NAN = Float.NaN;

    /** Steps once a second from t0 for the given seconds with fixed readings; returns the events that were not NONE. */
    private static java.util.List<Event> run(CarEventsLogic l, long t0, int seconds, int ign, float v, float kmh, boolean running) {
        java.util.List<Event> out = new java.util.ArrayList<>();
        for (int i = 1; i <= seconds; i++) {
            Event e = l.step(t0 + i * 1000L, ign, v, kmh, running);
            if (e != Event.NONE) out.add(e);
        }
        return out;
    }

    @Test
    public void theFirstKnownStateIsNotAnEventButAChangeIs() {
        CarEventsLogic l = new CarEventsLogic();
        assertEquals(Event.NONE, l.step(0, -1, NAN, 0, false));
        assertEquals(Event.NONE, l.step(1000, 1, 13.2f, 0, true));      // the launcher started with the ignition on
        assertEquals(Event.IGNITION_OFF, l.step(2000, 0, 12.8f, 0, false));
        assertEquals(Event.NONE, l.step(3000, 0, 12.6f, 0, false));
        assertEquals(Event.IGNITION_ON, l.step(4000, 1, 12.5f, 0, false));
    }

    @Test
    public void aWeakBatteryNeedsSeveralSecondsUnderTwelveVoltsWithTheEngineOff() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 0, 12.6f, 0, false);
        l.step(1000, 1, 11.9f, 0, false); // ignition on
        java.util.List<Event> e = run(l, 1000, 4, 1, 11.9f, 0, false);
        assertFalse(e.contains(Event.WEAK_BATTERY));                      // not yet 5 s
        e = run(l, 5000, 3, 1, 11.9f, 0, false);
        assertTrue(e.contains(Event.WEAK_BATTERY));
        assertEquals(11.9f, l.voltage(), 0.001f);
        assertTrue(run(l, 8000, 20, 1, 11.9f, 0, false).isEmpty());       // once per ignition cycle
    }

    @Test
    public void crankingDoesNotLookLikeAWeakBattery() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 0, 12.6f, 0, false);
        l.step(1000, 1, 12.5f, 0, false);
        // two seconds of cranking at 9.8 V (under the cranking floor), then the engine runs
        assertTrue(run(l, 1000, 2, 1, 9.8f, 0, false).isEmpty());
        assertTrue(run(l, 3000, 30, 1, 13.9f, 0, true).isEmpty());
    }

    @Test
    public void aHealthyRestVoltageIsRecordedOncePerCycle() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 0, 12.6f, 0, false);
        l.step(1000, 1, 12.5f, 0, false);
        java.util.List<Event> e = run(l, 1000, 10, 1, 12.5f, 0, false);
        assertEquals(1, e.size());
        assertEquals(Event.REST_SAMPLE, e.get(0));
        assertEquals(12.5f, l.voltage(), 0.001f);
    }

    @Test
    public void noRestSampleWhenTheEngineStartedFirst() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 0, 12.6f, 0, false);
        l.step(1000, 1, 12.5f, 0, false);
        assertTrue(run(l, 1000, 30, 1, 13.9f, 0, true).isEmpty());
    }

    @Test
    public void aChargingFaultNeedsAMinuteAndAHalfLowWhileMoving() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 1, 13.9f, 60, true);
        assertTrue(run(l, 0, 80, 1, 12.2f, 60, true).isEmpty());
        // a dip that recovers (the smart alternator) starts the count again
        assertTrue(run(l, 80000, 5, 1, 13.4f, 60, true).isEmpty());
        assertTrue(run(l, 85000, 80, 1, 12.2f, 60, true).isEmpty());
        java.util.List<Event> e = run(l, 165000, 20, 1, 12.2f, 60, true);
        assertTrue(e.contains(Event.CHARGING_FAULT));
        assertTrue(run(l, 185000, 120, 1, 12.2f, 60, true).isEmpty()); // once
    }

    @Test
    public void standingAtALightWithALowVoltageIsNotAChargingFault() {
        CarEventsLogic l = new CarEventsLogic();
        l.step(0, 1, 13.9f, 0, true);
        assertFalse(run(l, 0, 300, 1, 12.3f, 0, false).contains(Event.CHARGING_FAULT));
    }

    @Test
    public void theClockScreenWaitsForTheIgnitionToStayOff() {
        assertFalse(CarEventsLogic.standbyWanted(true, 0, 3000, false));
        assertTrue(CarEventsLogic.standbyWanted(true, 0, 8000, false));
        assertFalse(CarEventsLogic.standbyWanted(true, 0, 60000, true));   // touched: stays away until the ignition changes
        assertFalse(CarEventsLogic.standbyWanted(true, 1, 60000, false));
        assertFalse(CarEventsLogic.standbyWanted(false, 0, 60000, false));
        assertFalse(CarEventsLogic.standbyWanted(true, -1, 60000, false)); // ignition not known
    }
}
