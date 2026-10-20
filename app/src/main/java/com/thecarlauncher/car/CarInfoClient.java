package com.thecarlauncher.car;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;

import com.thecarlauncher.util.AppLog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only client of the TW car info service (com.tw.carinfoservice, "CarServiceAidl"), the same
 * service the unit's own apps ask for speed, fuel consumption, battery voltage, handbrake and so on.
 * While "Take trip data from OBD" is on it polls every value the service offers and writes the ones
 * that change to the diagnostic recorder, so the next drive tells which value is which.
 * Only get-style calls are made; nothing is sent to the car.
 *
 * The service's method names are not published, only its call numbers; the types below were read
 * from the service itself ('i' int, 'f' float, 'I' int[], 'F' float[]).
 */
public final class CarInfoClient implements ServiceConnection {
    private static final String PACKAGE = "com.tw.carinfoservice";
    private static final String ACTION = "com.tw.carinfoservice.CarService.Bind";
    private static final String DESCRIPTOR = "com.tw.carinfoservice.CarServiceAidl";
    private static final long INTERVAL_MS = 2000;

    /** Call number and the type of its answer. */
    private static final int[] CODES = {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
            26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38, 39, 40, 41, 42, 43, 44, 45, 48, 49, 50, 51, 52, 53, 54, 55,
            56, 57, 58, 59, 60, 61};
    private static final String TYPES = "fiiIIffiiiiiiiIiFFFffiiiiffffiiiiiiiiiIiiIIIIIFFFFiIiif";

    private static CarInfoClient instance;

    private final Context app;
    private HandlerThread thread;
    private Handler io;
    private volatile IBinder binder;
    private boolean wanted;
    private final Map<Integer, String> last = new HashMap<>();

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!wanted || io == null) return;
            if (binder == null) return; // reconnecting starts a new loop
            readAll();
            io.postDelayed(this, INTERVAL_MS);
        }
    };

    private CarInfoClient(Context c) { app = c.getApplicationContext(); }

    public static synchronized CarInfoClient get(Context c) {
        if (instance == null) instance = new CarInfoClient(c);
        return instance;
    }

    /** Starts or stops reading; follows the OBD switch in Setup > Vehicle. */
    public synchronized void setEnabled(boolean on) {
        if (on == wanted) return;
        wanted = on;
        if (on) {
            thread = new HandlerThread("carinfo");
            thread.start();
            io = new Handler(thread.getLooper());
            Intent i = new Intent(ACTION).setPackage(PACKAGE);
            try {
                if (!app.bindService(i, this, Context.BIND_AUTO_CREATE)) AppLog.d(app, "OBD", "car info service not found");
            } catch (Throwable t) {
                AppLog.d(app, "OBD", "bind failed: " + t);
            }
        } else {
            try {
                app.unbindService(this);
            } catch (Throwable ignored) {
            }
            binder = null;
            if (io != null) io.removeCallbacksAndMessages(null);
            if (thread != null) thread.quitSafely();
            thread = null;
            io = null;
        }
    }

    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder service) {
        binder = service;
        AppLog.d(app, "OBD", "connected to " + name.flattenToShortString());
        if (io != null) {
            io.removeCallbacks(poll);
            io.post(new Runnable() {
                @Override
                public void run() {
                    last.clear(); // on the polling thread, which also reads it
                    poll.run();
                }
            });
        }
    }

    @Override
    public synchronized void onServiceDisconnected(ComponentName name) {
        binder = null;
        AppLog.d(app, "OBD", "disconnected");
    }

    private void readAll() {
        IBinder b = binder;
        if (b == null) return;
        for (int i = 0; i < CODES.length; i++) {
            int code = CODES[i];
            String v = read(b, code, TYPES.charAt(i));
            if (v == null) continue;
            String prev = last.get(code);
            if (!v.equals(prev)) {
                last.put(code, v);
                AppLog.d(app, "OBD", "#" + code + " " + v);
            }
        }
    }

    /** One get-call; null when it failed. */
    private static String read(IBinder b, int code, char type) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!b.transact(code, data, reply, 0)) return null;
            reply.readException();
            switch (type) {
                case 'i': return String.valueOf(reply.readInt());
                case 'f': return String.valueOf(reply.readFloat());
                case 'I': return Arrays.toString(reply.createIntArray());
                default: return Arrays.toString(reply.createFloatArray());
            }
        } catch (Throwable t) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
