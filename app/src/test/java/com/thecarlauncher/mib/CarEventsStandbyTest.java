package com.thecarlauncher.mib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarEventsStandbyTest {
    @Test
    public void theStandbyKeyAsksForTheClockScreenAndTheNextPressSendsItAway() {
        CarEvents ce = CarEvents.get();
        ce.dismissStandby();
        assertFalse(ce.standbyRequested());
        ce.toggleStandby();
        assertTrue(ce.standbyRequested());
        ce.toggleStandby();
        assertFalse(ce.standbyRequested());
    }

    @Test
    public void aTouchOnTheClockScreenEndsTheRequest() {
        CarEvents ce = CarEvents.get();
        ce.requestStandby();
        assertTrue(ce.standbyRequested());
        ce.dismissStandby();
        assertFalse(ce.standbyRequested());
    }
}
