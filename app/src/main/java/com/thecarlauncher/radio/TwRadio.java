package com.thecarlauncher.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.thecarlauncher.util.AppLog;
import com.thecarlauncher.util.Prefs;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The tuner of a TW head unit, seen the way the factory radio app (com.tw.radio) sees it: its own TWUtil object
 * for module 1, the same message ids, and the messages it understands (read from that app's code, checked against
 * the 28 Oct 2026 recording): 0x401 with arg1 0 = flags (arg2 64 while a seek runs), 1 or 5 = band, 2 = frequency
 * (and the station name as text), 3 = signal, 4 = preset slot; 0x402 = one entry of the preset list (arg1 = slot |
 * band &lt;&lt; 8 | PI &lt;&lt; 16, arg2 = frequency, text = name); 0x404 and 0x405 = RDS name and text; 0x301 =
 * the unit's active source (radio when arg1 == 1). Frequencies are in 10 kHz on FM (10230 = 102.30 MHz) and in kHz
 * on AM. The launcher only mirrors and remote controls it: the factory radio app keeps playing and owns the audio.
 * Commands go out only with the "Allow sending commands" switch on.
 */
public final class TwRadio {
    public interface Listener { void onTwRadioChanged(); }

    /** Presets per band on the unit: 18 slots, shown as three pages of six. The slots belong to the band the unit is on. */
    public static final int SLOTS = 18;
    public static final int PAGE = 6;
    public static final int BANDS = 3; // FM1, FM2, AM

    private static final int MODULE = 1;
    private static final short[] IDS = {265, 266, 274, 513, 515, 769, 1025, 1026, 1028, 1029, 1030, -25088, -25087};
    private static final String TRY = "twradio_try", BAD = "twradio_bad", SUSPECT = "twradio_suspect";

    /** True for the ids of the radio channel (so the recorder does not log what this channel delivers a second time). */
    public static boolean listensTo(int what) {
        for (short id : IDS) if ((id & 0xFFFF) == (what & 0xFFFF)) return true;
        return false;
    }

    /**
     * One unfinished open of the radio channel (the app died while opening it). The first strike is forgiven, since an
     * ordinary kill in that short window looks the same; the second one in a row disables the channel for good.
     */
    public static void addSuspect(Context c) {
        SharedPreferences sp = Prefs.of(c);
        int n = sp.getInt(SUSPECT, 0) + 1;
        sp.edit().putInt(SUSPECT, n).putBoolean(TRY, false).putBoolean(BAD, n >= 2).commit();
    }
    private static TwRadio sInstance;

    /** What the tuner last said; pure data so the decoding can be tested. */
    static final class State {
        int band = -1, freq, signal, preset = -1, source = -1;
        String name = "", text = "";
        /** A name that differs from the shown one, waiting to be seen a second time (RDS often arrives with a bad letter). */
        private String pendingName = "";
        /** The preset list the unit sent, per band and slot; a frequency of 0 = nothing stored (or not sent yet). */
        final int[][] presetFreq = new int[BANDS][SLOTS];
        final String[][] presetName = new String[BANDS][SLOTS];

        /**
         * A station name is taken when nothing is shown yet or when it arrives twice in a row: the RDS name flickers
         * ("A HABER", "A HABEa", "A HABER@") and the screen should not follow every bad read.
         */
        private boolean takeName(String s) {
            if (s.equals(name)) {
                pendingName = "";
                return false;
            }
            if (name.isEmpty() || s.equals(pendingName)) {
                name = s;
                pendingName = "";
                if (preset >= 0 && band >= 0 && presetFreq[band][preset] == freq) presetName[band][preset] = s; // the slot's own station
                return true;
            }
            pendingName = s;
            return false;
        }

        /** Notes the playing station as the content of a slot of the current band; true when that changed what is known. */
        boolean rememberPreset(int slot) {
            if (band < 0 || slot < 0 || slot >= SLOTS || !plausible(band, freq)) return false;
            if (presetFreq[band][slot] == freq && presetName[band][slot] != null) return false;
            presetFreq[band][slot] = freq;
            presetName[band][slot] = name;
            return true;
        }

        /** Applies one message; true when something shown changed. */
        boolean apply(int what, int arg1, int arg2, Object obj) {
            String str = obj instanceof String ? ((String) obj).trim() : null;
            boolean changed = false;
            switch (what) {
                case 1025:
                    switch (arg1) {
                        case 1:
                        case 5: // the unit reports the band with arg1 5 (seen on the car); the factory app reads arg1 1
                            if (arg2 >= 0 && arg2 < BANDS && band != arg2) {
                                band = arg2;
                                changed = true;
                            }
                            break;
                        case 2:
                            if (plausible(-1, arg2)) {
                                // The unit sends the band after the frequency of a band change (0/64, 3, 2, 4, 5 in the
                                // recording), so the size of the number decides: FM is 8000-11000, AM 400-1800.
                                boolean am = arg2 <= 1800;
                                if (band >= 0 && am != (band == 2)) {
                                    band = am ? 2 : 0; // FM1 until the band message says FM2
                                    preset = -1;
                                    changed = true;
                                }
                                if (freq != arg2) {
                                    freq = arg2;
                                    name = "";
                                    pendingName = "";
                                    text = "";
                                    // a slot number from before is stale unless this is that slot's station (4/slot follows a recall)
                                    if (preset >= 0 && band >= 0 && presetFreq[band][preset] != arg2) preset = -1;
                                    changed = true;
                                }
                            }
                            if (str != null && str.length() > 0 && takeName(str)) changed = true;
                            break;
                        case 3:
                            signal = arg2; // kept, but never shown: it changes constantly and must not redraw the screen
                            break;
                        case 4:
                            int p = arg2 >= 0 && arg2 < SLOTS ? arg2 : -1;
                            if (preset != p) {
                                preset = p;
                                changed = true;
                            }
                            // The unit says "slot p" right after the frequency of that slot: that is how the tiles learn.
                            if (rememberPreset(p)) changed = true;
                            break;
                        default:
                            break;
                    }
                    break;
                case 1026: {
                    // One entry of the preset list, for the band the unit is on (else the band in the message).
                    int slot = arg1 & 0xFF, b = band >= 0 ? band : (arg1 >> 8) & 0xFF;
                    if (slot < SLOTS && b >= 0 && b < BANDS) {
                        int f = plausible(b, arg2) ? arg2 : 0;
                        String n = str == null ? "" : str;
                        if (presetName[b][slot] == null || presetFreq[b][slot] != f || !n.equals(presetName[b][slot])) {
                            presetFreq[b][slot] = f;
                            presetName[b][slot] = n;
                            changed = true;
                        }
                    }
                    break;
                }
                case 1028:
                    if (str != null && str.length() > 0 && takeName(str)) changed = true;
                    break;
                case 1029:
                    if (str != null && !str.equals(text)) {
                        text = str;
                        changed = true;
                    }
                    break;
                case 769:
                    if (source != arg1) {
                        source = arg1;
                        changed = true;
                    }
                    break;
                default:
                    break;
            }
            return changed;
        }

        /** The unit's active source is the radio when its source message (0x301) says 1, as the factory radio app tests it. */
        boolean radioSource() { return source == 1; }

        /** FM sits in 80.00-110.00 MHz (8000-11000), AM in 400-1800 kHz; anything else is noise. */
        static boolean plausible(int band, int f) {
            boolean fm = f >= 8000 && f <= 11000, am = f >= 400 && f <= 1800;
            if (band == 2) return am;
            if (band >= 0) return fm;
            return fm || am;
        }
    }

    /**
     * The tiles are the unit's 18 preset slots of the band it is on, three pages of six (the factory app keeps one list
     * of 18 and asks for it again when the band changes); the tile of a slot is the slot itself.
     */
    public static int tileOf(int band, int slot) {
        return band < 0 || band >= BANDS || slot < 0 || slot >= SLOTS ? -1 : slot;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final State state = new State();
    private volatile Class<?> cls;
    private final Object openLock = new Object();
    private volatile Object util;
    private volatile Handler io;
    private volatile boolean opened, failed;
    private boolean opening;
    private volatile int attempts;
    private volatile long nextTryAt;
    private volatile String error;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_MS = 10000;

    public static synchronized TwRadio get(Context c) {
        if (sInstance == null) sInstance = new TwRadio(c.getApplicationContext());
        return sInstance;
    }

    private TwRadio(Context c) {
        app = c;
        if (Prefs.of(c).getBoolean(TRY, false)) addSuspect(c); // the app died while the channel was being opened
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onTwRadioChanged();
            }
        });
    }

    /** True when TWUtil exists on this unit and opening the channel has not crashed or failed before. */
    public boolean available() {
        if (Prefs.of(app).getBoolean(BAD, false)) {
            error = "the radio channel stopped the app earlier - not repeated";
            return false;
        }
        if (failed) return false;
        if (cls != null) return true;
        try {
            cls = Class.forName("android.tw.john.TWUtil");
            return true;
        } catch (Throwable t) {
            failed = true;
            error = "TWUtil is not on this unit";
            return false;
        }
    }

    public String error() { return error; }

    /**
     * Starts opening the channel in the background (once, with a few retries when the unit refuses it) so the
     * screen never waits for it; false only when the tuner can never be used here.
     */
    public boolean listen() {
        if (!available()) return false;
        synchronized (this) {
            if (opened || opening || System.currentTimeMillis() < nextTryAt) return true;
            opening = true;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    openNow();
                } finally {
                    synchronized (TwRadio.this) {
                        opening = false;
                    }
                    changed();
                }
            }
        }, "tw-radio-open");
        t.start();
        return true;
    }

    /**
     * Opens the channel the way the factory radio app does, on the calling thread (the recorder uses this too, so the
     * module is opened once in this process). True when it is open. A refusal is tried again later, up to three times.
     */
    public boolean openNow() {
        synchronized (openLock) {
            return openLocked();
        }
    }

    private boolean openLocked() {
        if (opened) return true;
        if (!available()) return false;
        SharedPreferences sp = Prefs.of(app);
        try {
            sp.edit().putBoolean(TRY, true).commit();
            Object u = cls.getConstructor(int.class).newInstance(MODULE);
            Method open = cls.getMethod("open", short[].class);
            Object r = open.invoke(u, (Object) IDS);
            if (!Integer.valueOf(0).equals(r)) {
                giveUp("the unit refused the radio channel (" + r + ")");
                return false;
            }
            Method go = cls.getMethod("start");
            go.invoke(u);
            HandlerThread t = new HandlerThread("tw-radio");
            t.start();
            io = new Handler(t.getLooper()) {
                @Override
                public void handleMessage(Message m) {
                    try {
                        boolean ch;
                        synchronized (state) {
                            ch = state.apply(m.what, m.arg1, m.arg2, m.obj);
                        }
                        if (ch) changed();
                        com.thecarlauncher.car.TwProbe.get().radioTap(m); // the recorder sees the same messages
                    } catch (Throwable ignored) {
                        // an odd message must never kill the thread
                    }
                }
            };
            cls.getMethod("addHandler", String.class, Handler.class).invoke(u, "carlauncher-radio", io);
            util = u;
            opened = true;
            error = null;
            sp.edit().putInt(SUSPECT, 0).commit(); // it opened fine: earlier strikes are forgotten
            AppLog.d(app, "RADIO", "radio channel open (module " + MODULE + ", " + IDS.length + " ids)");
            requestState();
            return true;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            giveUp(String.valueOf(c));
            return false;
        } finally {
            sp.edit().putBoolean(TRY, false).commit();
        }
    }

    /** One failed attempt: try again in ten seconds, and after the third give up for this run. */
    private void giveUp(String why) {
        error = why;
        attempts++;
        nextTryAt = System.currentTimeMillis() + RETRY_MS;
        if (attempts >= MAX_ATTEMPTS) failed = true;
        AppLog.d(app, "RADIO", "radio channel failed (" + attempts + "/" + MAX_ATTEMPTS + "): " + why);
    }

    /** True once the channel is open. */
    public boolean isOpen() { return opened; }

    // ---- State ---------------------------------------------------------------------------------

    public int freq() { synchronized (state) { return state.freq; } }

    /** 0 and 1 = FM, 2 = AM, -1 = not known yet. */
    public int band() { synchronized (state) { return state.band; } }

    public boolean isAm() { return band() == 2; }

    public String name() { synchronized (state) { return state.name; } }

    public String text() { synchronized (state) { return state.text; } }

    public int signal() { synchronized (state) { return state.signal; } }

    /** True when the unit's active source is the radio. */
    public boolean isRadioSource() { synchronized (state) { return state.radioSource(); } }

    /** The preset tile of the current station (band page and slot), or -1. */
    public int currentTile() {
        synchronized (state) { return tileOf(state.band, state.preset); }
    }

    // ---- Commands (only with "Allow sending commands") ---------------------------------------------

    private boolean canSend() {
        return opened && Prefs.bool(app, Prefs.TW_COMMANDS, false);
    }

    private void send(final int what, final int a, final int b) {
        if (!canSend()) return;
        io.post(new Runnable() {
            @Override
            public void run() {
                try {
                    cls.getMethod("write", int.class, int.class, int.class).invoke(util, what, a, b);
                    AppLog.d(app, "RADIO", "write 0x" + Integer.toHexString(what) + " " + a + "/" + b);
                } catch (Throwable t) {
                    AppLog.d(app, "RADIO", "write failed: " + (t.getCause() != null ? t.getCause() : t));
                }
            }
        });
    }

    /**
     * Asks the unit for what it only says when it changes, like the factory radio app does when it starts: the
     * active source (0x301, 255), the tuner state with band, frequency and preset (0x401, 255) and the RDS status
     * (0x404, 255). Two-argument writes.
     */
    public void requestState() {
        if (!canSend()) return;
        io.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Method w = cls.getMethod("write", int.class, int.class);
                    w.invoke(util, 769, 255);
                    w.invoke(util, 1025, 255);
                    w.invoke(util, 1028, 255);
                    AppLog.d(app, "RADIO", "write 0x301 255, 0x401 255, 0x404 255");
                } catch (Throwable t) {
                    AppLog.d(app, "RADIO", "write failed: " + (t.getCause() != null ? t.getCause() : t));
                }
            }
        });
    }

    /** True once the unit has said which source is active (so "not the radio" can be told from "not known yet"). */
    public boolean sourceKnown() { synchronized (state) { return state.source >= 0; } }

    /**
     * The direction argument of a seek or step: 0 = up (next), 1 = down (previous), as the factory radio app sends them.
     * The first version had it the other way round and the frequency ran down on "next" (28 Oct 2026 recording).
     */
    static int directionArg(boolean up) { return up ? 0 : 1; }

    /** Seek to the next (up) or previous station. */
    public void seek(boolean up) { send(1025, 1, directionArg(up)); }

    /** One fine step up or down. */
    public void step(boolean up) { send(1025, 2, directionArg(up)); }

    /** The slot to recall for a tile, or -1 while the band is not known (the slot is recalled on the band the unit is on). */
    public static int recallSlot(int tile, int currentBand) {
        return currentBand >= 0 && tile >= 0 && tile < SLOTS ? tile : -1;
    }

    /** The preset list entry of a tile on the current band: its frequency (0 = empty or not sent yet) and name. */
    public int presetFreq(int tile) {
        synchronized (state) {
            return state.band < 0 || tile < 0 || tile >= SLOTS ? 0 : state.presetFreq[state.band][tile];
        }
    }

    public String presetName(int tile) {
        synchronized (state) {
            String n = state.band < 0 || tile < 0 || tile >= SLOTS ? null : state.presetName[state.band][tile];
            return n == null ? "" : n;
        }
    }

    /** True when the station that is playing can be stored in the slot of a tile now (radio is the source, a frequency is known). */
    public boolean canStore(int tile) {
        return canRecall(tile) && canSend() && freq() > 0 && isRadioSource();
    }

    /**
     * Stores the station that is playing in the slot of a tile, as a long press on a preset does in the factory radio
     * app: write(1025, 8, slot). The tile shows it at once. False when it cannot be done now.
     */
    public boolean store(int tile) {
        if (!canStore(tile)) return false;
        send(1025, 8, tile);
        synchronized (state) {
            state.rememberPreset(tile);
        }
        changed();
        return true;
    }

    /** True when a frequency of the current band can be tuned directly from the launcher. */
    public boolean canTune() { return canSend() && isRadioSource() && band() >= 0; }

    /**
     * Tunes to a frequency, as the factory radio app does when its frequency bar is let go: write(1026, 255, frequency)
     * (FM in 10 kHz, 8750-10800; AM in kHz, 522-1620). False when it cannot be done now or the value is out of range.
     */
    public boolean tune(int f) {
        int band = band();
        boolean inRange = band == 2 ? f >= 522 && f <= 1620 : f >= 8750 && f <= 10800;
        if (!canTune() || !inRange) return false;
        send(1026, 255, f);
        return true;
    }

    /** Goes to the preset of a tile on the current band; false while the band is not known. */
    public boolean recall(int tile) {
        int band = band();
        int slot = recallSlot(tile, band);
        if (slot < 0) return false;
        send(1026, slot, band);
        return true;
    }

    /** True when the tile can be recalled now. */
    public boolean canRecall(int tile) { return recallSlot(tile, band()) >= 0; }

    /** True while commands can go out (the channel is open and the switch is on). */
    public boolean controllable() { return canSend(); }
}
