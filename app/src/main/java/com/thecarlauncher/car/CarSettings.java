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

        /** Set for the few settings whose values are not yet certain enough to send: they are shown, not changed. */
        boolean readOnly;

        /** True for an on/off setting, which the screen draws as a tick box. */
        public boolean isCheck() { return kind == Kind.CHECK; }

        /** True when the screen may offer to change it: an on/off setting or a list of known values. */
        public boolean editable() {
            return !readOnly && (kind == Kind.CHECK || (kind == Kind.LIST && opts != null) || values != null);
        }

        /** The values the factory list offers for a setting that has no named options (seconds, levels). */
        int[] values;
        /** A value outside the list reads as its first entry (the ambient colour byte is shared by three lists). */
        boolean zeroDefault;

        Item choose(int... v) {
            values = v;
            return this;
        }

        public int optionCount() { return opts != null ? opts.length : values == null ? 0 : values.length; }

        public int optionValue(int i) { return opts != null ? opts[i].value : values[i]; }

        /** The text of a list value in the language of the translator. */
        public String optionLabel(int i, Translator t) {
            return opts != null ? t.tr(opts[i].en) : format(this, values[i], t);
        }

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

    /** A titled group of settings; cmd is the message number the factory page writes its changes with. */
    public static final class Section {
        public final String en;
        public final int cmd;
        public final List<Item> items = new ArrayList<>();

        Section(String en, int cmd) {
            this.en = en;
            this.cmd = cmd;
        }
    }

    /**
     * One page of the factory app: the frames it reads and how it asks for them. extRequest is the other way the factory app
     * asks (write 106 with {5, 1, frame}, used by the mirrors page); the usual one is write 10 with {1, frame}.
     */
    public static final class Page {
        public final String en;
        public final int[] frames;
        public final boolean extRequest;
        public final List<Section> sections;

        Page(String en, int[] frames, boolean extRequest, List<Section> sections) {
            this.en = en;
            this.frames = frames;
            this.extRequest = extRequest;
            this.sections = sections;
        }
    }

    private static final CarSettings INSTANCE = new CarSettings();
    private final Map<Integer, byte[]> frames = new HashMap<>();
    private final Map<String, Long> askedAt = new HashMap<>();
    private volatile Runnable listener;

    private CarSettings() {}

    public static CarSettings get() { return INSTANCE; }

    /** Called (on the thread that delivers car messages) after a frame of these pages came in. */
    public void setListener(Runnable r) { listener = r; }

    public void decodeFrame(int frame, byte[] b) {
        if (b == null || !isPageFrame(frame)) return;
        synchronized (this) {
            frames.put(frame, b.clone());
        }
        Runnable r = listener;
        if (r != null) r.run();
    }

    private static boolean isPageFrame(int frame) {
        for (Page p : pages()) for (int f : p.frames) if (f == frame) return true;
        return false;
    }

    /** True once every frame of the page has come in. */
    public synchronized boolean have(Page p) {
        for (int f : p.frames) if (!frames.containsKey(f)) return false;
        return true;
    }

    /** True once every light frame has come in. */
    public synchronized boolean haveLights() { return have(lightPage()); }

    /** Forgets what the car said about this page, so the next look asks from scratch. */
    public synchronized void forget(Page p) {
        for (int f : p.frames) frames.remove(f);
        askedAt.remove(p.en);
    }

    public synchronized void forget() {
        frames.clear();
        askedAt.clear();
    }

    /** Notes that the page's frames were just asked for (so the page does not ask again on every refresh). */
    public synchronized void asked(Page p) { askedAt.put(p.en, System.currentTimeMillis()); }

    /** True once the frames were asked for in this visit to the page; a refresh of the page does not ask again. */
    public synchronized boolean attempted(Page p) { return askedAt.containsKey(p.en); }

    /** A new visit to the list of pages may ask again (the values already read stay). */
    public synchronized void resetAttempts() { askedAt.clear(); }

    /** Milliseconds since the page's frames were last asked for, Long.MAX_VALUE if never. */
    public synchronized long sinceAsked(Page p) {
        Long t = askedAt.get(p.en);
        return t == null ? Long.MAX_VALUE : System.currentTimeMillis() - t;
    }

    /** The frames of the page the car has not sent yet. */
    public synchronized List<Integer> missing(Page p) {
        List<Integer> out = new ArrayList<>();
        for (int f : p.frames) if (!frames.containsKey(f)) out.add(f);
        return out;
    }

    /** The light frames the car has not sent yet. */
    public synchronized List<Integer> missingLights() { return missing(lightPage()); }

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
        return format(it, r.value, t);
    }

    private static String format(Item it, int v, Translator t) {
        switch (it.kind) {
            case CHECK:
                return t.tr(v != 0 ? "On" : "Off");
            case SECONDS:
                return v == 0 ? t.tr("Off") : v + " " + t.tr("sec");
            case LEVEL:
                return v == 0 ? t.tr("Off") : t.tr("Level") + " " + v;
            case COLOUR_NUMBER:
                return t.tr("Colour") + " " + v;
            case AMBIENT_COLOUR:
                return ambientColour(v, t);
            case NUMBER:
                return String.valueOf(v);
            default:
                if (it.opts != null) {
                    for (Opt o : it.opts) if (o.value == v) return t.tr(o.en);
                    if (it.zeroDefault) return t.tr(it.opts[0].en);
                }
                return String.valueOf(v);
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

    private static List<Page> pages;

    /** The pages of the factory app that the launcher knows, in the order of its list. */
    public static synchronized List<Page> pages() {
        if (pages == null) {
            List<Page> l = new ArrayList<>();
            l.add(new Page("Lights", LIGHT_FRAMES, false, buildLightSections()));
            l.add(new Page("Opening and closing", new int[]{100}, false, buildOpeningSections()));
            l.add(new Page("Mirrors and wipers", new int[]{105}, true, buildMirrorSections()));
            pages = l;
        }
        return pages;
    }

    public static Page lightPage() { return pages().get(0); }

    /** The settings of the factory app's light page, in its order, grouped under three headings. */
    public static List<Section> lightSections() { return lightPage().sections; }

    /** Opening and closing (frame 100, changes written with message 111). */
    private static List<Section> buildOpeningSections() {
        Section doors = new Section("Doors and windows", 111);
        doors.items.add(new Item("Convenience opening", 100, 2, 6, 2, 0, 7, 1, Kind.LIST,
                new Opt[]{new Opt(0, "Off"), new Opt(1, "Driver window"), new Opt(2, "All windows")}));
        doors.items.add(new Item("Door unlocking", 100, 3, 6, 2, 1, 7, 2, Kind.LIST,
                new Opt[]{new Opt(0, "Vehicle side"), new Opt(1, "Single door"), new Opt(2, "All doors")}));
        doors.items.add(check("Automatic locking", 100, 3, 5, 1, 6, 3));
        doors.items.add(check("Door lock sound confirmation", 100, 3, 2, 1, 3, 6));
        doors.items.add(check("Memory matching of the seat to the remote key", 100, 3, 4, 1, 5, 4));
        doors.items.add(check("Easy Open (tailgate sensor)", 100, 3, 3, 1, 4, 5));
        Section near = new Section("Approach", 111);
        near.items.add(check("Approach function active", 100, 2, 5, -1, 0, 7));
        near.items.add(check("Unlock vehicle when approaching", 100, 2, 4, 2, 5, 8));
        List<Section> out = new ArrayList<>();
        out.add(doors);
        out.add(near);
        return out;
    }

    /** Mirrors and wipers (frame 105, asked with message 106, changes written with message 110). */
    private static List<Section> buildMirrorSections() {
        Section mirrors = new Section("Mirrors", 110);
        mirrors.items.add(check("Synchronous adjustment", 105, 2, 7, 0, 7, 1));
        mirrors.items.add(check("Lower while reversing", 105, 2, 6, 0, 6, 2));
        mirrors.items.add(check("Fold away after parking", 105, 2, 5, 0, 5, 3));
        Section wipers = new Section("Wipers", 110);
        wipers.items.add(check("Automatic wiping in rain", 105, 3, 7, 1, 7, 4));
        wipers.items.add(check("Rear window wiping in reverse gear", 105, 3, 6, 1, 6, 5));
        wipers.items.add(check("Wipers in service position", 105, 3, 5, 1, 5, 6));
        List<Section> out = new ArrayList<>();
        out.add(mirrors);
        out.add(wipers);
        return out;
    }

    /**
     * One of the three ambient colour lists of the factory page: they all read and write the same byte (0 off, 1-3 the
     * three-colour set, 4-13 the ten-colour set, 14-33 the twenty-colour set); a value that belongs to another set reads as Off.
     */
    private static Item ambientSet(String en, int from, int to, String set) {
        List<Opt> l = new ArrayList<>();
        l.add(new Opt(0, "Off"));
        for (int v = from; v <= to; v++) {
            String name = set.equals("3") ? (v == 1 ? "White" : v == 2 ? "Orange" : "Blue") : "Colour " + (v - from + 1);
            l.add(new Opt(v, name));
        }
        Item it = new Item(en, 103, 2, 0, 8, 0, 7, 12, Kind.LIST, l.toArray(new Opt[0]));
        it.zeroDefault = true;
        return it;
    }

    private static List<Section> buildLightSections() {
        Section lamps = new Section("Headlights", 109);
        lamps.items.add(check("Automatic headlights in rain", 104, 3, 3, 0, 4, 4));
        lamps.items.add(check("Lane change flash", 104, 3, 2, 0, 3, 5));
        lamps.items.add(check("Daytime running lights", 104, 3, 0, 0, 1, 16));
        lamps.items.add(check("Dynamic light assist", 104, 3, 7, 0, 7, 1));
        lamps.items.add(check("Motorway light", 104, 3, 6, 0, 6, 2));
        lamps.items.add(new Item("Headlight switch-on time", 104, 3, 4, 2, 0, 5, 3, Kind.LIST,
                new Opt[]{new Opt(0, "Early"), new Opt(1, "Medium"), new Opt(2, "Late")}));
        lamps.items.add(new Item("Coming home light", 104, 7, 0, 8, 2, 7, 10, Kind.SECONDS, null).choose(0, 5, 10, 15, 20, 25, 30));
        lamps.items.add(new Item("Leaving home light", 104, 8, 0, 8, 2, 6, 11, Kind.SECONDS, null).choose(0, 5, 10, 15, 20, 25, 30));
        lamps.items.add(new Item("Traffic side (travel mode)", 104, 3, 1, 1, 0, 2, 6, Kind.LIST,
                new Opt[]{new Opt(1, "Right traffic"), new Opt(0, "Left traffic")}));
        lamps.items.add(new Item("Headlight range adjustment", 103, 1, 0, 8, 0, 3, 17, Kind.LEVEL, null).choose(0, 1, 2, 3, 4, 5, 6));
        lamps.items.add(new Item("Lane assist brightness", 103, 5, 0, 8, 0, 2, 18, Kind.NUMBER, null).choose(1, 2, 3, 4, 5));

        Section inner = new Section("Interior lighting", 109);
        inner.items.add(pct("Instrument and switch lighting", 104, 4, 1, 7, 7, 20));
        inner.items.add(pct("Door ambient light", 104, 5, 1, 6, 8, 20));
        inner.items.add(pct("Footwell light", 104, 6, 1, 5, 9, 20));
        inner.items.add(pct("Roof ambient light", 103, 3, 0, 6, 13, 20));
        inner.items.add(pct("Front ambient light", 103, 4, 0, 5, 14, 20));
        inner.items.add(pct("All ambient areas", 103, 6, 0, 4, 15, 10));
        inner.items.add(ambientSet("Three-colour ambient colour", 1, 3, "3"));
        inner.items.add(ambientSet("Ten-colour ambient colour", 4, 13, "10"));
        inner.items.add(ambientSet("Twenty-colour ambient colour", 14, 33, "20"));

        Section own = new Section("Personalised ambient lighting", 109);
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
        for (Page p : pages()) {
            out.add(p.en);
            for (Section s : p.sections) {
                out.add(s.en);
                for (Item it : s.items) {
                    out.add(it.en);
                    if (it.opts != null) for (Opt o : it.opts) if (!o.en.endsWith("%")) out.add(o.en);
                }
            }
        }
        for (String s : new String[]{"On", "Off", "sec", "Level", "Colour", "3-colour", "10-colour", "20-colour", "White", "Orange", "Blue"}) {
            out.add(s);
        }
        return out;
    }
}
