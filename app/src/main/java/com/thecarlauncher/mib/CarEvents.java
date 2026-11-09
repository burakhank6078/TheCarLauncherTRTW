package com.thecarlauncher.mib;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.thecarlauncher.car.BatteryHistory;
import com.thecarlauncher.car.CarData;
import com.thecarlauncher.car.CarEventsLogic;
import com.thecarlauncher.car.TripCore;
import com.thecarlauncher.car.TripGps;
import com.thecarlauncher.car.TripLog;
import com.thecarlauncher.car.VehicleState;
import com.thecarlauncher.screens.VehicleScreen;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the launcher does when the car changes state: when the ignition goes off the trip is closed and summed up, a weak
 * battery or a charging fault is announced, the resting voltage is kept for the battery history, and the clock screen is
 * asked for once the ignition has been off for a few seconds. The rules are in {@link CarEventsLogic}; this is the part
 * that knows about Android.
 *
 * The watching runs on its own thread once a second from the moment the launcher has started, whatever app is in front, so
 * the trip is closed and the battery noted at the right moment. What has to be shown (the summary, a warning) is queued and
 * handed to the launcher's window by {@link #poll} when the launcher is on screen: a notice that was made while another app
 * was in front appears when the launcher comes back, if that is within half an hour. The clock screen is only ever shown
 * inside the launcher.
 */
public final class CarEvents {
    /** Where the results go: the pop-up panel and the clock screen of the launcher's window. */
    public interface Sink {
        void notice(String title, List<String> lines, int mark, long ms);

        void standby(boolean on);
    }

    private static final class Notice {
        final String title;
        final List<String> lines;
        final int mark;
        final long at;

        Notice(String title, List<String> lines, int mark, long at) {
            this.title = title;
            this.lines = lines;
            this.mark = mark;
            this.at = at;
        }
    }

    private static final CarEvents INSTANCE = new CarEvents();
    private static final long NOTICE_MS = 12_000, KEEP_MS = 30 * 60_000L, STEP_MS = 1000, UI_SPEED_MS = 3000;
    private static final int MAX_PENDING = 4;

    private final CarEventsLogic logic = new CarEventsLogic();
    private final List<Notice> pending = new ArrayList<>();
    private Context app;
    private Handler io;
    private long offSince, lastShown, uiSpeedAt;
    private float uiKmh;
    private boolean dismissed, standbyOn;

    private CarEvents() {}

    public static CarEvents get() { return INSTANCE; }

    /** Starts the watching (once; later calls do nothing). */
    public synchronized void start(Context c) {
        if (io != null) return;
        app = c.getApplicationContext();
        HandlerThread t = new HandlerThread("car-events");
        t.start();
        io = new Handler(t.getLooper());
        io.post(new Runnable() {
            @Override
            public void run() {
                try {
                    step(app);
                } catch (RuntimeException ignored) {
                    // one bad look at the car must not stop the watching
                }
                io.postDelayed(this, STEP_MS);
            }
        });
    }

    /** A touch on the clock screen: it stays away until the ignition changes. */
    public synchronized void dismissStandby() { dismissed = true; }

    /** The launcher's own speed, used when the background GPS listener has nothing (it is switched off or has no fix). */
    public synchronized void noteSpeed(float kmh) {
        uiKmh = kmh;
        uiSpeedAt = SystemClock.elapsedRealtime();
    }

    /** One look at the car; the results are queued, nothing is shown from here. */
    synchronized void step(Context c) {
        long now = SystemClock.elapsedRealtime();
        float kmh = TripGps.get(c).speedKmh();
        if (Float.isNaN(kmh)) kmh = now - uiSpeedAt <= UI_SPEED_MS ? uiKmh : 0f;
        VehicleState vs = VehicleState.get();
        int ign = vs.ignition();
        com.thecarlauncher.util.AmbientAuto.get().step(c, now); // the strip follows the ignition and the headlights, if asked to
        boolean events = Prefs.bool(c, Prefs.CAR_EVENTS, true);
        CarEventsLogic.Event e = logic.step(now, ign, vs.voltage(), kmh, vs.engineRunning());
        switch (e) {
            case IGNITION_OFF: {
                TripCore.Trip t = TripLog.get(c).finishNow(); // the trip always ends here, the summary only when it is wanted
                if (events && t != null) queue(Mib.t(c, "Vožnja završena", "Trip finished"), tripLines(c, t), VehicleScreen.Mark.OK, now);
                break;
            }
            case WEAK_BATTERY:
                BatteryHistory.add(c, logic.voltage());
                if (events) queue(Mib.t(c, "Akumulator je slab", "Battery is weak", "Akü zayıf"), weakLines(c, logic.voltage()),
                        VehicleScreen.Mark.WARN, now);
                break;
            case CHARGING_FAULT:
                if (events) queue(Mib.t(c, "Mogući problem sa punjenjem", "Possible charging problem"),
                        faultLines(c, logic.voltage()), VehicleScreen.Mark.WARN, now);
                break;
            case REST_SAMPLE:
                BatteryHistory.add(c, logic.voltage());
                break;
            default:
                break;
        }
        // how long the ignition has been off, for the clock screen
        if (ign == 0) {
            if (offSince == 0) offSince = now;
        } else {
            offSince = 0;
            dismissed = false;
        }
    }

    private void queue(String title, List<String> lines, int mark, long now) {
        pending.add(new Notice(title, lines, mark, now));
        while (pending.size() > MAX_PENDING) pending.remove(0);
    }

    /**
     * Called about once a second from the launcher's clock while it is on screen: hands over the next queued notice (one
     * at a time, so they do not cover each other) and switches the clock screen on or off.
     */
    public synchronized void poll(Context c, Sink sink) {
        long now = SystemClock.elapsedRealtime();
        for (int i = pending.size() - 1; i >= 0; i--) if (now - pending.get(i).at > KEEP_MS) pending.remove(i);
        if (!pending.isEmpty() && now - lastShown >= NOTICE_MS + 600) {
            Notice n = pending.remove(0);
            lastShown = now;
            sink.notice(n.title, n.lines, n.mark, NOTICE_MS);
        }
        int ign = VehicleState.get().ignition();
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
