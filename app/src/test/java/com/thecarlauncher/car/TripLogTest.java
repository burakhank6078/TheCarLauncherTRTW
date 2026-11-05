package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TripLogTest {
    private static long at(int y, int m, int d, int h) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, 0, 0);
        return c.getTimeInMillis();
    }

    private static TripCore.Trip trip(long start, double km, int minutes, float cons) {
        return new TripCore.Trip(start, start + minutes * 60_000L, km, minutes * 60_000L, 90f, cons);
    }

    @Test
    public void tripsSurviveBeingWrittenAndRead() {
        List<TripCore.Trip> in = new ArrayList<>();
        in.add(trip(at(2026, 10, 20, 8), 18.4, 36, 6.8f));
        in.add(trip(at(2026, 10, 21, 9), 3.0, 8, -1f));
        List<TripCore.Trip> out = TripLog.decode(TripLog.encode(in));
        assertEquals(2, out.size());
        assertEquals(18.4, out.get(0).km, 0.01);
        assertEquals(6.8f, out.get(0).consumption, 0.01f);
        assertEquals(-1f, out.get(1).consumption, 0.01f);
        assertEquals(in.get(0).start, out.get(0).start);
        assertEquals(36 * 60_000L, out.get(0).movingMs);
    }

    @Test
    public void brokenLinesAreSkipped() {
        List<TripCore.Trip> out = TripLog.decode("garbage\n1,2,3\n100,50,500,60,900,70\n100,200,500,60,900,70\n\n");
        assertEquals(1, out.size()); // the line that ends before it starts is dropped too
    }

    @Test
    public void monthsAreAddedUpNewestFirstWithAWeightedConsumption() {
        List<TripCore.Trip> in = new ArrayList<>();
        in.add(trip(at(2026, 9, 28, 8), 100, 90, 5.0f));
        in.add(trip(at(2026, 10, 2, 8), 100, 90, 6.0f));
        in.add(trip(at(2026, 10, 3, 8), 300, 240, 8.0f));
        in.add(trip(at(2026, 10, 4, 8), 10, 20, -1f)); // no consumption: counts for distance only
        List<TripLog.Month> m = TripLog.months(in, 40.0);
        assertEquals(2, m.size());
        assertEquals(10, m.get(0).month);
        assertEquals(9, m.get(1).month);
        assertEquals(3, m.get(0).count);
        assertEquals(410.0, m.get(0).km, 0.01);
        // (100 km at 6 + 300 km at 8) over 400 km = 7.5 l/100
        assertEquals(7.5f, m.get(0).consumption, 0.01f);
        // 6 + 24 = 30 litres at 40 per litre
        assertEquals(1200.0, m.get(0).cost, 0.01);
        assertEquals(5.0f, m.get(1).consumption, 0.01f);
    }

    @Test
    public void noPriceMeansNoCost() {
        List<TripCore.Trip> in = new ArrayList<>();
        in.add(trip(at(2026, 10, 2, 8), 100, 90, 6.0f));
        TripLog.Month m = TripLog.months(in, -1).get(0);
        assertEquals(6.0f, m.consumption, 0.01f);
        assertTrue(m.cost < 0);
    }

    @Test
    public void theLatestTripsAreAddedUpWithWeightedFigures() {
        List<TripCore.Trip> newestFirst = new ArrayList<>();
        newestFirst.add(new TripCore.Trip(at(2026, 10, 4, 8), at(2026, 10, 4, 8) + 600_000L, 100, 3_600_000L, 90f, 8.0f, 0, 0));
        newestFirst.add(new TripCore.Trip(at(2026, 10, 3, 8), at(2026, 10, 3, 8) + 600_000L, 100, 3_600_000L, 90f, 6.0f, 4, 4));
        newestFirst.add(trip(at(2026, 10, 2, 8), 50, 40, -1f)); // no consumption, no score
        newestFirst.add(trip(at(2026, 10, 1, 8), 900, 600, 20f)); // beyond the first three
        TripLog.Recent r = TripLog.recent(newestFirst, 3);
        assertEquals(3, r.count);
        assertEquals(2, r.withConsumption);
        assertEquals(250.0, r.km, 0.01);
        assertEquals(7.0f, r.consumption, 0.01f);
        assertEquals(6.0f, r.best, 0.01f);
        assertEquals(8.0f, r.worst, 0.01f);
        assertTrue(r.score >= 0 && r.score <= 100);
    }

    @Test
    public void noTripsGiveNothingToAverage() {
        TripLog.Recent r = TripLog.recent(new ArrayList<TripCore.Trip>(), 10);
        assertEquals(0, r.count);
        assertEquals(-1f, r.consumption, 0.001f);
        assertEquals(-1, r.score);
    }
}
