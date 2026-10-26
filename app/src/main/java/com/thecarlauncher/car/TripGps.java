package com.thecarlauncher.car;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.thecarlauncher.util.Prefs;

/**
 * The GPS speed for the trip history, on its own listener and thread, so trips are recorded whatever app is in front
 * (the map's tracker stops when the launcher leaves the screen). Whether the unit hands out GPS fixes to a background app
 * is up to its Android; when it does not, nothing is recorded and nothing wrong is written. Switched by "Trip history"
 * in Setup > Vehicle (default on). Without a fix for a few seconds the speed counts as zero, so a trip can still end.
 */
public final class TripGps implements LocationListener {
    private static final long NO_FIX_MS = 5000, CHECK_MS = 2000;
    private static TripGps sInstance;

    private final Context app;
    private HandlerThread thread;
    private Handler io;
    private boolean running;
    private volatile long lastFixAt;
    private volatile float kmh;

    public static synchronized TripGps get(Context c) {
        if (sInstance == null) sInstance = new TripGps(c.getApplicationContext());
        return sInstance;
    }

    private TripGps(Context c) { app = c; }

    /** Starts or stops listening to match the switch and the location permission; safe to call as often as you like. */
    @SuppressWarnings("MissingPermission")
    public synchronized void sync() {
        boolean want = Prefs.bool(app, Prefs.TRIP_HISTORY, true)
                && app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (want == running) return;
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        try {
            if (want) {
                if (!lm.getAllProviders().contains(LocationManager.GPS_PROVIDER)) return;
                thread = new HandlerThread("trip-gps");
                thread.start();
                io = new Handler(thread.getLooper());
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, thread.getLooper());
                running = true;
                lastFixAt = 0;
                io.postDelayed(check, CHECK_MS);
            } else {
                lm.removeUpdates(this);
                io.removeCallbacks(check);
                thread.quitSafely();
                thread = null;
                io = null;
                running = false;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // no permission after all: stays off
        }
    }

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            Handler h = io;
            if (h == null) return;
            // No fix for a while: the car counts as standing, so a trip can end (and the speed is not stuck on its last value).
            if (SystemClock.elapsedRealtime() - lastFixAt > NO_FIX_MS) {
                kmh = 0;
                TripLog.get(app).tick(SystemClock.elapsedRealtime(), System.currentTimeMillis(), 0f);
            }
            h.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    public void onLocationChanged(Location l) {
        float v = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
        kmh = v;
        lastFixAt = SystemClock.elapsedRealtime();
        TripLog.get(app).tick(lastFixAt, System.currentTimeMillis(), v);
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
}
