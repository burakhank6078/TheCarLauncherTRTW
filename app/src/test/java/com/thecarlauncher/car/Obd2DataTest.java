package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Obd2DataTest {
    @Test
    public void aValueIsKnownForFifteenSecondsAndThenForgotten() {
        Obd2Data d = Obd2Data.get();
        d.clear();
        d.set(Elm327Protocol.COOLANT, 88.0, 1000);
        assertEquals(88.0, d.get(Elm327Protocol.COOLANT, 1000 + Obd2Data.FRESH_MS), 0.001);
        assertTrue(Double.isNaN(d.get(Elm327Protocol.COOLANT, 1001 + Obd2Data.FRESH_MS)));
        assertTrue(Double.isNaN(d.get(Elm327Protocol.OIL, 1000))); // never given
        d.clear();
    }

    @Test
    public void theModuleVoltageStandsInWhenTheAdapterGivesNone() {
        Obd2Data d = Obd2Data.get();
        d.clear();
        d.set(Elm327Protocol.MODULE_VOLTAGE, 13.9, 5000);
        assertEquals(13.9, d.voltage(6000), 0.001);
        d.clear();
        assertTrue(Double.isNaN(d.voltage(6000)));
    }
}
