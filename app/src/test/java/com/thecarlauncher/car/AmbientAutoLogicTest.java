package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AmbientAutoLogicTest {
    private static final int NONE = AmbientAutoLogic.NONE;

    private static AmbientAutoLogic.Params on(int night, int day) {
        return new AmbientAutoLogic.Params(true, true, 30, night, day);
    }

    @Test
    public void switchedOffItNeverTouchesTheStrip() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = new AmbientAutoLogic.Params(false, true, 30, 40, 100);
        assertEquals(NONE, l.step(1000, 1, 1, p));
        assertEquals(NONE, l.step(100_000, 0, 0, p));
    }

    @Test
    public void theStripGoesDarkOnlyAfterTheDelayOnceTheIgnitionIsOff() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = on(40, 100);
        assertEquals(100, l.step(0, 1, 0, p));
        assertEquals(NONE, l.step(1000, 0, 0, p));      // off for the first moment: left as it is
        assertEquals(NONE, l.step(30_000, 0, 0, p));    // 29 s
        assertEquals(0, l.step(31_000, 0, 0, p));       // 30 s: dark
        assertEquals(100, l.step(60_000, 1, 0, p));     // ignition on again: back to the day share
    }

    @Test
    public void withoutTheSwitchTheStripStaysAsItIsWhenTheIgnitionGoesOff() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = new AmbientAutoLogic.Params(true, false, 0, 40, 100);
        l.step(0, 1, 0, p);
        assertEquals(NONE, l.step(500_000, 0, 0, p));
    }

    @Test
    public void headlightsSwitchBetweenTheNightAndTheDayShareAfterAShortHold() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = on(40, 90);
        assertEquals(90, l.step(0, 1, 0, p));
        assertEquals(90, l.step(1000, 1, 1, p));        // just on: still the day share
        assertEquals(90, l.step(4000, 1, 1, p));
        assertEquals(40, l.step(5000, 1, 1, p));        // held 4 s: night
        assertEquals(40, l.step(6000, 1, 0, p));        // a flash of the high beam changes nothing
        assertEquals(40, l.step(7000, 1, 1, p));
        assertEquals(40, l.step(9000, 1, 0, p));
        assertEquals(90, l.step(13_000, 1, 0, p));      // off for 4 s: day again
    }

    @Test
    public void aShareOfZeroMeansDoNotTouch() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = on(0, 100);
        l.step(0, 1, 1, p);
        assertEquals(NONE, l.step(5000, 1, 1, p));
        assertEquals(NONE, l.step(20_000, 1, 0, p));     // the lights went off, the hold is not over yet
        assertEquals(100, l.step(24_000, 1, 0, p));      // day share 100
    }

    @Test
    public void whenTheLightsAreNotKnownTheLastDecisionHolds() {
        AmbientAutoLogic l = new AmbientAutoLogic();
        AmbientAutoLogic.Params p = on(40, 100);
        l.step(0, 1, 1, p);
        assertEquals(40, l.step(5000, 1, 1, p));
        assertEquals(40, l.step(6000, 1, -1, p));
        assertEquals(NONE, l.step(7000, -1, -1, p));    // the ignition is not known either: nothing is sent
    }
}
