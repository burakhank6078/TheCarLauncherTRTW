package com.thecarlauncher.mib;

import android.content.Context;
import android.os.SystemClock;

import com.thecarlauncher.car.BatteryHistory;
import com.thecarlauncher.car.CarData;
import com.thecarlauncher.car.CarEventsLogic;
import com.thecarlauncher.car.TripCore;
import com.thecarlauncher.car.TripLog;
import com.thecarlauncher.car.VehicleState;
import com.thecarlauncher.screens.VehicleScreen;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the launcher does when the car changes state: when the ignition goes off the trip is closed and summed up in a
 * pop-up, a weak battery or a charging fault is announced, the resting voltage is kept for the battery history, and the
 * clock screen is asked for once the ignition has been off for a few seconds. The rules are in {@link CarEventsLogic};
 * this is the part that knows about Android. Called about once a second from the launcher's clock, so it only acts while
 * the launcher is on screen (a trip that ended meanwhile is summed up when it comes back).
 */
public final class CarEvents {
    /** Where the results go: the pop-up panel and the clock screen of the launcher's window. */
    public interface Sink {
        void notice(String title, List<String> lines, int mark, long ms);

        void standby(boolean on);
    }

    private static final CarEvents INSTANCE = new CarEvents();
    private static final long NOTICE_MS = 12_000;

    private final CarEventsLogic logic = new CarEventsLogic();
    private long offSince;
    private boolean dismissed, standbyOn;

    private CarEvents() {}

    public static CarEvents get() { return INSTANCE; }

    /** A touch on the clock screen: it stays away until the ignition changes. */
    public synchronized void dismissStandby() { dismissed = true; }

    public synchronized void tick(Context c, float kmh, Sink sink) {
        long now = SystemClock.elapsedRealtime();
        VehicleState vs = VehicleState.get();
        int ign = vs.ignition();
        boolean events = Prefs.bool(c, Prefs.CAR_EVENTS, true);
        CarEventsLogic.Event e = logic.step(now, ign, vs.voltage(), kmh, vs.engineRunning());
        switch (e) {
            case IGNITION_OFF: {
                TripCore.Trip t = TripLog.get(c).finishNow(); // the trip always ends here, the summary only when it is wanted
                if (events && t != null) sink.notice(Mib.t(c, "Vožnja završena", "Trip finished"), tripLines(c, t),
                        VehicleScreen.Mark.OK, NOTICE_MS);
                break;
            }
            case WEAK_BATTERY:
                BatteryHistory.add(c, logic.voltage());
                if (events) sink.notice(Mib.t(c, "Akumulator je slab", "Battery is weak", "Akü zayıf"), weakLines(c, logic.voltage()),
                        VehicleScreen.Mark.WARN, NOTICE_MS);
                break;
            case CHARGING_FAULT:
                if (events) sink.notice(Mib.t(c, "Mogući problem sa punjenjem", "Possible charging problem"),
                        faultLines(c, logic.voltage()), VehicleScreen.Mark.WARN, NOTICE_MS);
                break;
            case REST_SAMPLE:
                BatteryHistory.add(c, logic.voltage());
                break;
            default:
                break;
        }
        // the clock screen
        if (ign == 0) {
            if (offSince == 0) offSince = now;
        } else {
            offSince = 0;
            dismissed = false;
        }
        boolean want = CarEventsLogic.standbyWanted(Prefs.bool(c, Prefs.STANDBY_CLOCK, true), ign, offSince == 0 ? 0 : now - offSince, dismissed);
        if (want != standbyOn) {
            standbyOn = want;
            sink.standby(want);
        }
    }

    /** The lines of the trip summary: distance and time, speeds, consumption and cost, the driving score. */
    static List<String> tripLines(Context c, TripCore.Trip t) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.US, "%.1f km  ·  %s", t.km, minutes(c, t.durationMs())));
        out.add(Mib.t(c, "Prosečna brzina", "Average speed", "Ortalama hız") + String.format(Locale.US, " %.0f km/h  ·  ", t.avgKmh())
                + Mib.t(c, "najviše", "top speed") + String.format(Locale.US, " %.0f", t.maxKmh));
        if (t.consumption >= 0) {
            String s = Mib.t(c, "Prosečna potrošnja", "Average consumption", "Ortalama tüketim")
                    + String.format(Locale.US, " %.1f l/100 km", t.consumption);
            double price = CarData.get(c).averagePrice();
            if (price > 0) {
                // the price is in whatever currency it was typed under Fuel, so no unit is invented here
                s += "  ·  " + Mib.t(c, "gorivo", "fuel", "yakıt") + String.format(Locale.US, " ≈ %.0f", t.km * t.consumption / 100.0 * price);
            }
            out.add(s);
        }
        int score = t.score();
        if (score >= 0) {
            String s = Mib.t(c, "Ocena vožnje", "Driving score", "Sürüş puanı") + " " + score;
            if (t.brakes + t.accels > 0) {
                s += "  (" + t.brakes + " " + Mib.t(c, "naglo kočenje", "hard braking") + ", "
                        + t.accels + " " + Mib.t(c, "naglo ubrzanje", "hard acceleration") + ")";
            }
            out.add(s);
        }
        return out;
    }

    private static List<String> weakLines(Context c, float volts) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.US, "%.1f V", volts) + "  ·  " + Mib.t(c, "kontakt uključen, motor ugašen", "ignition on, engine off"));
        out.add(Mib.t(c, "Ne opterećujte akumulator; upalite motor ili ga proverite.", "Do not drain it further: start the engine or have it checked."));
        return out;
    }

    private static List<String> faultLines(Context c, float volts) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.US, "%.1f V", volts) + "  ·  " + Mib.t(c, "duže od 90 s tokom vožnje", "for more than 90 s while driving"));
        out.add(Mib.t(c, "Alternator možda ne puni akumulator; proverite u servisu.", "The alternator may not be charging the battery: have it checked."));
        return out;
    }

    private static String minutes(Context c, long ms) {
        long m = Math.max(1, Math.round(ms / 60000.0));
        return m >= 60 ? String.format(Locale.US, "%d:%02d h", m / 60, m % 60) : m + " " + Mib.t(c, "min", "min", "dk");
    }
}
