package com.thecarlauncher.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The launcher's settings as one plain text file for a USB stick (or the Downloads folder), so a reinstall or a new
 * unit does not mean setting everything up again. One line per setting: type (s, i, l, f, b), key and value, tab
 * separated, with backslash escapes. Runtime state (crash markers, cached trip values, the trip history, the recorder
 * switch) is left out. Pictures, offline maps and the car's own data are not part of it.
 */
public final class SettingsBackup {
    public static final String FILE = "TheCarLauncher-ayarlar.txt";
    static final String HEADER = "# TheCarLauncher settings v1";

    private SettingsBackup() {}

    /**
     * Keys that are state of the running app, not choices of the user, plus the ones that must be switched on by hand on
     * each unit: the switches that let the launcher send commands to the car or listen to the phone channel (a file on a stick
     * can be copied or edited by anyone), and the map key, which is a secret and does not belong on a stick.
     */
    static boolean skip(String key) {
        return key.equals(Prefs.TW_COMMANDS) || key.equals(Prefs.REC_PHONE) || key.equals(Prefs.CARTO_KEY)
                || key.startsWith("twprobe_") || key.startsWith("twradio_") || key.equals(AppLog.LOG_ENABLED)
                || key.equals("obd_trip_values") || key.equals("trip_log") || key.equals("trip_active") || key.startsWith("last_");
    }

    static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** All settings as text, sorted by key; sets and unknown types are left out. */
    static String encode(Map<String, ?> all) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (String k : new TreeSet<>(all.keySet())) {
            if (skip(k)) continue;
            Object v = all.get(k);
            String t = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l"
                    : v instanceof Float ? "f" : v instanceof String ? "s" : null;
            if (t == null) continue;
            sb.append(t).append('\t').append(esc(k)).append('\t').append(esc(String.valueOf(v))).append('\n');
        }
        return sb.toString();
    }

    /** The lines of a backup as {type, key, value}; lines that are not understood are ignored. */
    static List<String[]> decode(String text) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 3);
            if (p.length < 3 || p[0].length() != 1 || "silfb".indexOf(p[0].charAt(0)) < 0) continue;
            String key = unesc(p[1]), value = unesc(p[2]);
            if (key.isEmpty() || skip(key)) continue;
            if (!valid(p[0].charAt(0), value)) continue;
            out.add(new String[]{p[0], key, value});
        }
        return out;
    }

    private static boolean valid(char type, String v) {
        try {
            switch (type) {
                case 'i': Integer.parseInt(v); return true;
                case 'l': Long.parseLong(v); return true;
                case 'f': Float.parseFloat(v); return true;
                case 'b': return v.equals("true") || v.equals("false");
                default: return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Writes the settings to the first place that accepts a file (USB, SD card, Downloads); the path, or null. */
    public static String export(Context c) {
        String text = encode(Prefs.of(c).getAll());
        for (File dir : AppLog.exportDirs(c)) {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) continue;
                File dest = new File(dir, FILE);
                OutputStream os = new FileOutputStream(dest);
                try {
                    os.write(text.getBytes("UTF-8"));
                } finally {
                    os.close();
                }
                if (dest.length() > 0) return dest.getPath();
            } catch (IOException ignored) {
                // try the next place
            }
        }
        return null;
    }

    /** The backup file, the first one found (USB first), or null. */
    public static File find(Context c) {
        for (File dir : AppLog.exportDirs(c)) {
            File f = new File(dir, FILE);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    /** Puts the saved settings back; the number of settings restored, or -1 when no usable file was found. */
    public static int restore(Context c) {
        File f = find(c);
        if (f == null) return -1;
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } finally {
                r.close();
            }
        } catch (IOException e) {
            return -1;
        }
        List<String[]> entries = decode(sb.toString());
        if (entries.isEmpty()) return -1;
        SharedPreferences.Editor ed = Prefs.of(c).edit();
        for (String[] e : entries) {
            switch (e[0].charAt(0)) {
                case 'i': ed.putInt(e[1], Integer.parseInt(e[2])); break;
                case 'l': ed.putLong(e[1], Long.parseLong(e[2])); break;
                case 'f': ed.putFloat(e[1], Float.parseFloat(e[2])); break;
                case 'b': ed.putBoolean(e[1], Boolean.parseBoolean(e[2])); break;
                default: ed.putString(e[1], e[2]);
            }
        }
        ed.commit();
        return entries.size();
    }
}
