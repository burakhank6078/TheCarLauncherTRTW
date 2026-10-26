package com.thecarlauncher.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DayNightTest {
    @Test
    public void lightsOnForLongEnoughMakeItNight() {
        DayNight.Core c = new DayNight.Core();
        assertFalse(c.step(0, 1));
        assertFalse(c.step(DayNight.HOLD_MS - 1, 1));
        assertFalse(c.night);
        assertTrue(c.step(DayNight.HOLD_MS, 1));
        assertTrue(c.night);
    }

    @Test
    public void aFlashDoesNotFlipIt() {
        DayNight.Core c = new DayNight.Core();
        c.step(0, 1);
        c.step(1000, 0); // back to off within a second
        assertFalse(c.step(DayNight.HOLD_MS + 1000, 0));
        assertFalse(c.night);
    }

    @Test
    public void lightsOffForLongEnoughMakeItDayAgain() {
        DayNight.Core c = new DayNight.Core();
        c.step(0, 1);
        c.step(DayNight.HOLD_MS, 1);
        assertTrue(c.night);
        c.step(10000, 0);
        assertTrue(c.night); // not yet
        assertTrue(c.step(10000 + DayNight.HOLD_MS, 0));
        assertFalse(c.night);
    }

    @Test
    public void unknownLightsKeepTheMode() {
        DayNight.Core c = new DayNight.Core();
        c.step(0, 1);
        c.step(DayNight.HOLD_MS, 1);
        assertFalse(c.step(60000, -1));
        assertTrue(c.night);
    }

    @Test
    public void anInterruptedChangeStartsTheCountAgain() {
        DayNight.Core c = new DayNight.Core();
        c.step(0, 1);
        c.step(3000, -1); // the service went quiet in between
        assertFalse(c.step(5000, 1)); // counting restarts at 5000
        assertTrue(c.step(5000 + DayNight.HOLD_MS, 1));
    }
}
