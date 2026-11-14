package com.thecarlauncher.media;

import android.content.Context;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * The "concert hall" effect (like the one in Samsung's SoundAlive): a hall reverb plus a wider stereo picture on the audio
 * session of the launcher's own player. Four steps: off, light, medium and concert. It is made from Android's own effects, so
 * it only reaches what the launcher plays itself (USB and local music). Bluetooth music and the radio reach the speakers by
 * another path (the unit's chip), where Android effects do not apply. Off by default: in a car a hall tends to blur the
 * sound, so even the strongest step is kept moderate.
 */
public final class Hall {
    public static final int OFF = 0, LIGHT = 1, MEDIUM = 2, CONCERT = 3;
    private static final String P_LEVEL = "hall_level";
    private static Hall sInstance;

    private final Context app;
    private final List<PresetReverb> reverbs = new ArrayList<>();
    private final List<Virtualizer> wides = new ArrayList<>();
    private final List<Integer> sessions = new ArrayList<>();
    private int level;
    private boolean failed;

    public static synchronized Hall get(Context c) {
        if (sInstance == null) sInstance = new Hall(c.getApplicationContext());
        return sInstance;
    }

    private Hall(Context c) {
        app = c;
        level = clamp(Prefs.integer(c, P_LEVEL, OFF));
    }

    public int level() { return level; }

    private static int clamp(int v) { return Math.max(OFF, Math.min(CONCERT, v)); }

    /** The reverb preset of a step: a room for light, a medium hall, then the large hall. */
    static short presetFor(int level) {
        switch (level) {
            case LIGHT: return PresetReverb.PRESET_MEDIUMROOM;
            case MEDIUM: return PresetReverb.PRESET_MEDIUMHALL;
            case CONCERT: return PresetReverb.PRESET_LARGEHALL;
            default: return PresetReverb.PRESET_NONE;
        }
    }

    /** How wide the stereo picture is made, 0 to 1000 as the Virtualizer counts. */
    static short strengthFor(int level) {
        switch (level) {
            case LIGHT: return 300;
            case MEDIUM: return 550;
            case CONCERT: return 800;
            default: return 0;
        }
    }

    public synchronized void set(int level) {
        this.level = clamp(level);
        Prefs.put(app, P_LEVEL, this.level);
        for (int i = sessions.size() - 1; i >= 0; i--) {
            try {
                apply(reverbs.get(i), wides.get(i));
            } catch (Throwable t) {
                release(i); // released by the system
            }
        }
    }

    /** Hooks the effect onto an audio session (the built-in player's). */
    public synchronized void attach(int sessionId) {
        PresetReverb r = null;
        Virtualizer v = null;
        try {
            r = new PresetReverb(0, sessionId);
            v = new Virtualizer(0, sessionId);
            apply(r, v);
            reverbs.add(r);
            wides.add(v);
            sessions.add(sessionId);
            failed = false;
        } catch (Throwable t) {
            // no such effect in this audio HAL (or no more effect slots)
            failed = true;
            try {
                if (r != null) r.release();
            } catch (Throwable ignored) {
            }
            try {
                if (v != null) v.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public synchronized void detach(int sessionId) {
        for (int i = sessions.size() - 1; i >= 0; i--) if (sessions.get(i) == sessionId) release(i);
    }

    private void release(int i) {
        try {
            reverbs.get(i).release();
        } catch (Throwable ignored) {
        }
        try {
            wides.get(i).release();
        } catch (Throwable ignored) {
        }
        reverbs.remove(i);
        wides.remove(i);
        sessions.remove(i);
    }

    private void apply(PresetReverb r, Virtualizer v) {
        boolean on = level != OFF;
        r.setPreset(presetFor(level));
        r.setEnabled(on);
        if (on) {
            try {
                v.setStrength(strengthFor(level));
            } catch (Throwable ignored) {
                // a chip that cannot widen: the reverb alone still works
            }
        }
        v.setEnabled(on);
    }

    /** "active" while an effect sits on the player, "idle" with none yet (no music played this run), "unsupported" when the unit refused it. */
    public synchronized String state() {
        if (!sessions.isEmpty()) return "active";
        return failed ? "unsupported" : "idle";
    }
}
