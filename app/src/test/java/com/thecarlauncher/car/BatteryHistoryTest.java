package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class BatteryHistoryTest {
    @Test
    public void readingsAreKeptInOrderAndTheOldestDropOut() {
        String s = "";
        for (int i = 0; i < BatteryHistory.MAX + 5; i++) s = BatteryHistory.append(s, 1000L + i, 12.0f + (i % 5) / 10f);
        List<BatteryHistory.Reading> l = BatteryHistory.decode(s);
        assertEquals(BatteryHistory.MAX, l.size());
        assertEquals(1005L, l.get(0).at);
        assertEquals(1000L + BatteryHistory.MAX + 4, l.get(l.size() - 1).at);
    }

    @Test
    public void brokenAndImplausibleEntriesAreSkipped() {
        List<BatteryHistory.Reading> l = BatteryHistory.decode("1:12.5;x;2:abc;3:99.0;4:12.3;:5;");
        assertEquals(2, l.size());
        assertEquals(12.3f, l.get(1).volts, 0.001f);
        assertTrue(BatteryHistory.decode(null).isEmpty());
    }

    @Test
    public void theAverageOfTheLastFew() {
        List<BatteryHistory.Reading> l = BatteryHistory.decode("1:12.0;2:12.4;3:12.6;4:12.8");
        assertEquals(12.6f, BatteryHistory.average(l, 3), 0.001f);
        assertEquals(12.45f, BatteryHistory.average(l, 10), 0.001f);
        assertTrue(Float.isNaN(BatteryHistory.average(BatteryHistory.decode(""), 5)));
    }
}
