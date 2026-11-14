package com.thecarlauncher.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import com.thecarlauncher.mib.CarEvents;

/**
 * The key that brings up the clock screen (the standby of a MIB), like the power key does on the real unit. The key is
 * learned: in Setup > Vehicle the user taps "Standby key" and presses a key of the unit or the steering wheel; whatever
 * arrives first is kept. Two kinds of key can arrive: an Android key event in the launcher's window, and the unit's own key
 * message (0x201, with the unit's key code), which is what the steering wheel and some panel keys send. The power key
 * is normally taken by the system itself and never reaches an app; then nothing arrives and the learning times out.
 *
 * Keys that already mean something (back, home, volume, the media keys and the unit's media, call and radio codes) are
 * refused, so learning cannot take them over.
 */
public final class StandbyKey {
    private static final String PREF = "standby_key";
    private static final long LEARN_MS = 20_000;

    /** The unit's key codes that the factory apps already use: play / pause, next, previous, answer, hang up. */
    private static final int[] TW_RESERVED = {18, 19, 20, 21, 24, 25};

    /** Told on the main thread what the learning ended with: the new key's description, or null when time ran out. */
    public interface Learn {
        void done(String description, boolean refused);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Learn learning;
    private static final Runnable TIMEOUT = new Runnable() {
        @Override
        public void run() { finish(null, false); }
    };

    private StandbyKey() {}

    /** "a:KEYCODE" for an Android key, "t:code" for the unit's, "" when none is set. */
    public static String get(Context c) { return Prefs.str(c, PREF, ""); }

    public static void clear(Context c) { Prefs.put(c, PREF, ""); }

    /** A short description for the settings row, "" when none is set. */
    public static String describe(Context c) {
        String k = get(c);
        if (k.startsWith("a:")) {
            try {
                return KeyEvent.keyCodeToString(Integer.parseInt(k.substring(2)));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return k.startsWith("t:") ? "#" + k.substring(2) : "";
    }

    /** Starts listening for the key to keep; the listener hears the result (a key, a refusal, or null after 20 seconds). */
    public static void learn(Learn l) {
        learning = l;
        MAIN.removeCallbacks(TIMEOUT);
        MAIN.postDelayed(TIMEOUT, LEARN_MS);
    }

    public static boolean isLearning() { return learning != null; }

    public static void cancelLearning() {
        MAIN.removeCallbacks(TIMEOUT);
        learning = null;
    }

    private static void finish(final String description, final boolean refused) {
        final Learn l = learning;
        learning = null;
        MAIN.removeCallbacks(TIMEOUT);
        if (l == null) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() { l.done(description, refused); }
        });
    }

    public static boolean reservedAndroid(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
                return true;
            default:
                return false;
        }
    }

    public static boolean reservedTw(int code) {
        for (int r : TW_RESERVED) if (r == code) return true;
        return false;
    }

    /** An Android key went down in the launcher's window. True when it was taken (learned or the standby key): do not pass it on. */
    public static boolean onAndroidKey(Context c, int keyCode) {
        if (learning != null) {
            if (reservedAndroid(keyCode)) {
                finish(null, true);
                return false;
            }
            Prefs.put(c, PREF, "a:" + keyCode);
            finish(KeyEvent.keyCodeToString(keyCode), false);
            return true;
        }
        if (("a:" + keyCode).equals(get(c))) {
            trigger(c);
            return true;
        }
        return false;
    }

    /** One of the unit's own key messages (0x201) came in with this key code (key down). Called on the thread that delivers them. */
    public static void onTwKey(Context c, int code) {
        if (learning != null) {
            if (reservedTw(code)) {
                finish(null, true);
                return;
            }
            Prefs.put(c, PREF, "t:" + code);
            finish("#" + code, false);
            return;
        }
        if (("t:" + code).equals(get(c))) trigger(c);
    }

    private static void trigger(Context c) {
        final Context app = c.getApplicationContext();
        CarEvents.get().toggleStandby();
        // the overlay trick of BringBack needs the main thread, and the unit's key messages arrive on another
        MAIN.post(new Runnable() {
            @Override
            public void run() { BringBack.toLauncher(app); } // the clock screen is part of the launcher: bring it up if another app is in front
        });
    }
}
