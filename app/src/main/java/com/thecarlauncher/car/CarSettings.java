package com.thecarlauncher.car;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Golf 7 vehicle settings the way the unit's factory "Araç ayarı" app (com.tw.car, v114) reads them, so the launcher
 * can show them in its own Setup. Each page of that app asks the car for a few state frames (write 0x501 with number 10 and
 * the data {1, frame}) and reads its values from the frames that come back (0x501 with the frame number as arg1); a value
 * is a few bits of one byte and has an "available" bit in the first bytes. Changing a setting is write(0x501, 109,
 * {key, value}); the key is kept in {@link Item#writeKey} for that, nothing is sent from here yet.
 *
 * First page: lights (frames 104, 103, 102), read only. The texts are English; the screen translates them.
 */
public final class CarSettings {
    public static final int[] LIGHT_FRAMES = {104, 103, 102};

    /** Turns an English text into the language of the screen (the dynamic parts are put together after it). */
    public interface Translator {
        String tr(String en);
    }

    /** One value of a list setting and its name. */
    static final class Opt {
        final int value;
        final String en;

        Opt(int value, String en) {
            this.value = value;
            this.en = en;
        }
    }

    /** One setting: where its value and its "available" bit sit in a frame, and what the values mean. */
    public static final class Item {
        public final String en;
        public final int frame, writeKey;
        final int index, shift, bits, enIndex, enBit;
        final Opt[] opts;
        final Kind kind;

        /** True for an on/off setting, which the screen draws as a tick box. */
        public boolean isCheck() { return kind == Kind.CHECK; }

        Item(String en, int frame, int index, int shift, int bits, int enIndex, int enBit, int writeKey, Kind kind, Opt[] opts) {
            this.en = en;
            this.frame = frame;
            this.index = index;
            this.shift = shift;
            this.bits = bits;
            this.enIndex = enIndex;
            this.enBit = enBit;
            this.writeKey = writeKey;
            this.kind = kind;
            this.opts = opts;
        }
    }

    enum Kind { CHECK, LIST, NUMBER, SECONDS, LEVEL, COLOUR_NUMBER, AMBIENT_COLOUR }

    /** What the car said about one setting. */
    public static final class Reading {
        public final boolean known, available;
        public final int value;

        Reading(boolean known, boolean available, int value) {
            this.known = known;
            this.available = available;
            this.value = value;
        }
    }

    /** A titled group of settings. */
    public static final class Section {
        public final String en;
        public final List<Item> items = new ArrayList<>();

        Section(String en) { this.en = en; }
    }

    private static final CarSettings INSTANCE = new CarSettings();
    private final Map<Integer, byte[]> frames = new HashMap<>();
    private volatile Runnable listener;

    private CarSettings() {}

    public static CarSettings get() { return INSTANCE; }

    /** Called (on the thread that delivers car messages) after a frame of these pages came in. */
    public void setListener(Runnable r) { listener = r; }

    public void decodeFrame(int frame, byte[] b) {
        if (b == null || (frame != 102 && frame != 103 && frame != 104)) return;
        synchronized (this) {
            frames.put(frame, b.clone());
        }
        Runnable r = listener;
        if (r != null) r.run();
    }

    /** True once every light frame has come in. */
    public synchronized boolean haveLights() {
        for (int f : LIGHT_FRAMES) if (!frames.containsKey(f)) return false;
        return true;
    }

    public synchronized void forget() {
        frames.clear();
        askedAt = 0;
        attempted = false;
    }

    private long askedAt;
    private boolean attempted;

    /** Notes that the light frames were just asked for (so the page does not ask again on every refresh). */
    public synchronized void asked() {
        askedAt = System.currentTimeMillis();
        attempted = true;
    }

    /** True once the frames were asked for in this visit to the page; a refresh of the page does not ask again. */
    public synchronized boolean attempted() { return attempted; }

    /** A new visit to the page may ask again (the values already read stay). */
    public synchronized void resetAttempt() { attempted = false; }

    /** Milliseconds since the light frames were last asked for, Long.MAX_VALUE if never. */
    public synchronized long sinceAsked() {
        return askedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - askedAt;
    }

    /** The light frames the car has not sent yet. */
    public synchronized List<Integer> missingLights() {
        List<Integer> out = new ArrayList<>();
        for (int f : LIGHT_FRAMES) if (!frames.containsKey(f)) out.add(f);
        return out;
    }

    public synchronized Reading read(Item it) {
        byte[] f = frames.get(it.frame);
        if (f == null || it.index >= f.length) return new Reading(false, false, 0);
        int v = ((f[it.index] & 0xFF) >> it.shift) & ((1 << it.bits) - 1);
        boolean on = it.enIndex < 0 || (it.enIndex < f.length && ((f[it.enIndex] >> it.enBit) & 1) != 0);
        return new Reading(true, on, v);
    }

    /** The value as text for the screen, "—" while the car has not answered. */
    public String text(Item it, Translator t) {
        Reading r = read(it);
        if (!r.known) return "—";
        switch (it.kind) {
            case CHECK:
                return t.tr(r.value != 0 ? "On" : "Off");
            case SECONDS:
                return r.value == 0 ? t.tr("Off") : r.value + " " + t.tr("sec");
            case LEVEL:
                return r.value == 0 ? t.tr("Off") : t.tr("Level") + " " + r.value;
            case COLOUR_NUMBER:
                return t.tr("Colour") + " " + r.value;
            case AMBIENT_COLOUR:
                return ambientColour(r.value, t);
            case NUMBER:
                return String.valueOf(r.value);
            default:
                if (it.opts != null) for (Opt o : it.opts) if (o.value == r.value) return t.tr(o.en);
                return String.valueOf(r.value);
        }
    }

    /**
     * The ambient colour byte: 0 off, 1-3 the three-colour set, 4-13 the ten-colour set, 14-33 the twenty-colour set (the
     * factory app shows one list for each set and fills the one the byte belongs to).
     */
    static String ambientColour(int v, Translator t) {
        if (v == 0) return t.tr("Off");
        if (v <= 3) return t.tr("3-colour") + ": " + t.tr(v == 1 ? "White" : v == 2 ? "Orange" : "Blue");
        if (v <= 13) return t.tr("10-colour") + ": " + t.tr("Colour") + " " + (v - 3);
        if (v <= 33) return t.tr("20-colour") + ": " + t.tr("Colour") + " " + (v - 13);
        return String.valueOf(v);
    }

    // ---- The light page ---------------------------------------------------------------------

    private static Opt[] percent(int from, int to, int step) {
        List<Opt> l = new ArrayList<>();
        for (int v = from; v <= to; v += step) l.add(new Opt(v, v + " %"));
        return l.toArray(new Opt[0]);
    }

    private static Item check(String en, int frame, int index, int bit, int enIndex, int enBit, int key) {
        return new Item(en, frame, index, bit, 1, enIndex, enBit, key, Kind.CHECK, null);
    }

    private static Item pct(String en, int frame, int index, int enIndex, int enBit, int key, int step) {
        return new Item(en, frame, index, 0, 8, enIndex, enBit, key, Kind.LIST, percent(0, 100, step));
    }

    /** The settings of the factory app's light page, in its order, grouped under three headings. */
    public static List<Section> lightSections() {
        Section lamps = new Section("Headlights");
        lamps.items.add(check("Automatic headlights in rain", 104, 3, 3, 0, 4, 4));
        lamps.items.add(check("Lane change flash", 104, 3, 2, 0, 3, 5));
        lamps.items.add(check("Daytime running lights", 104, 3, 0, 0, 1, 16));
        lamps.items.add(check("Dynamic light assist", 104, 3, 7, 0, 7, 1));
        lamps.items.add(check("Motorway light", 104, 3, 6, 0, 6, 2));
        lamps.items.add(new Item("Headlight switch-on time", 104, 3, 4, 2, 0, 5, 3, Kind.LIST,
                new Opt[]{new Opt(0, "Early"), new Opt(1, "Medium"), new Opt(2, "Late")}));
        lamps.items.add(new Item("Coming home light", 104, 7, 0, 8, 2, 7, 10, Kind.SECONDS, null));
        lamps.items.add(new Item("Leaving home light", 104, 8, 0, 8, 2, 6, 11, Kind.SECONDS, null));
        lamps.items.add(new Item("Traffic side (travel mode)", 104, 3, 1, 1, 0, 2, 6, Kind.LIST,
                new Opt[]{new Opt(1, "Right traffic"), new Opt(0, "Left traffic")}));
        lamps.items.add(new Item("Headlight range adjustment", 103, 1, 0, 8, 0, 3, 17, Kind.LEVEL, null));
        lamps.items.add(new Item("Lane assist brightness", 103, 5, 0, 8, 0, 2, 18, Kind.NUMBER, null));

        Section inner = new Section("Interior lighting");
        inner.items.add(pct("Instrument and switch lighting", 104, 4, 1, 7, 7, 20));
        inner.items.add(pct("Door ambient light", 104, 5, 1, 6, 8, 20));
        inner.items.add(pct("Footwell light", 104, 6, 1, 5, 9, 20));
        inner.items.add(pct("Roof ambient light", 103, 3, 0, 6, 13, 20));
        inner.items.add(pct("Front ambient light", 103, 4, 0, 5, 14, 20));
        inner.items.add(pct("All ambient areas", 103, 6, 0, 4, 15, 10));
        inner.items.add(new Item("Ambient colour", 103, 2, 0, 8, 0, 7, 12, Kind.AMBIENT_COLOUR, null));

        Section own = new Section("Personalised ambient lighting");
        own.items.add(new Item("Lighting tone", 102, 1, 0, 8, 0, 6, 33, Kind.LIST, new Opt[]{
                new Opt(0, "Off"), new Opt(1, "Infinite"), new Opt(2, "Eternal"), new Opt(3, "Longing"),
                new Opt(4, "Intoxicated"), new Opt(5, "Vitality"), new Opt(6, "Personalised"), new Opt(7, "Automatic")}));
        own.items.add(pct("Lighting tone brightness", 102, 2, 0, 6, 34, 10));
        own.items.add(new Item("Main ambient colour", 102, 3, 0, 8, 0, 2, 35, Kind.COLOUR_NUMBER, null));
        own.items.add(new Item("Secondary ambient colour", 102, 4, 0, 8, 0, 2, 36, Kind.COLOUR_NUMBER, null));
        own.items.add(new Item("Colour combination", 102, 5, 0, 8, 0, 2, 37, Kind.NUMBER, null));
        own.items.add(pct("Window area brightness", 102, 6, 0, 7, 38, 10));
        own.items.add(pct("Centre console area brightness", 102, 7, 0, 7, 39, 10));
        own.items.add(pct("Doorway area brightness", 102, 8, 0, 7, 40, 10));
        own.items.add(pct("Foot area brightness", 102, 9, 0, 7, 41, 10));

        List<Section> out = new ArrayList<>();
        out.add(lamps);
        out.add(inner);
        out.add(own);
        return out;
    }

    /** Every English text the light page can show, for the check that each has a Turkish translation. */
    public static List<String> lightTexts() {
        List<String> out = new ArrayList<>();
        for (Section s : lightSections()) {
            out.add(s.en);
            for (Item it : s.items) {
                out.add(it.en);
                if (it.opts != null) for (Opt o : it.opts) if (!o.en.endsWith("%")) out.add(o.en);
            }
        }
        for (String s : new String[]{"On", "Off", "sec", "Level", "Colour", "3-colour", "10-colour", "20-colour", "White", "Orange", "Blue"}) {
            out.add(s);
        }
        return out;
    }
}
