package com.thecarlauncher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.thecarlauncher.car.TripCore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TripExportTest {
    private static long at(int d, int h) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, d, h, 0, 0);
        return c.getTimeInMillis();
    }

    private static List<TripCore.Trip> trips() {
        List<TripCore.Trip> l = new ArrayList<>(); // newest first
        l.add(new TripCore.Trip(at(21, 9), at(21, 9) + 600_000L, 3.0, 480_000L, 60f, -1f));
        l.add(new TripCore.Trip(at(20, 8), at(20, 8) + 36 * 60_000L, 18.4, 30 * 60_000L, 92f, 6.8f, 1, 2));
        return l;
    }

    @Test
    public void oneLinePerTripOldestFirstWithTheUnknownLeftEmpty() {
        String[] lines = TripExport.csv(trips(), ',', '.', false).split("\r\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("Start,End,Duration (min),Distance (km)"));
        assertEquals("2026-10-20 08:00,2026-10-20 08:36,36,18.40,36.8,92,6.8,1,2,83", lines[1]); // 4 event points over 18.4 km
        assertEquals("2026-10-21 09:00,2026-10-21 09:10,10,3.00,22.5,60,,,,", lines[2]);
    }

    @Test
    public void aTurkishSpreadsheetGetsSemicolonsAndDecimalCommas() {
        String[] lines = TripExport.csv(trips(), ';', ',', true).split("\r\n");
        assertTrue(lines[0].startsWith("Başlangıç;Bitiş;Süre (dk);Mesafe (km)"));
        assertTrue(lines[1].contains(";18,40;"));
        assertTrue(lines[1].contains(";6,8;"));
    }
}
