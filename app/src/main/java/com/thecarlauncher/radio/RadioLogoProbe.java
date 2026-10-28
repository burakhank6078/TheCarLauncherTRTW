package com.thecarlauncher.radio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks, read-only, for the station logos the factory radio app uses: it finds a logo by the station's RDS PI code
 * and frequency in a text file (radio_data.txt: "PI,frequency,icon" per line) and loads logo/icon_NAME.png from a folder
 * or from RadioLogo.zip, kept in /sdcard/iNand/radio, /system/etc/radio or /system_tw/etc/radio. The result goes into the
 * diagnostic recording so it is known what this unit has before the launcher reads it.
 */
public final class RadioLogoProbe {
    private static final String[] BASES = {"/sdcard/iNand/radio", "/system/etc/radio", "/system_tw/etc/radio",
            "/system/etc", "/system_tw/etc"};

    private RadioLogoProbe() {}

    /** One line per place: what is in it that has "radio" or "logo" in its name, and the first lines of radio_data.txt. */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (String base : BASES) {
            try {
                describe(new File(base), out);
            } catch (Throwable t) {
                out.add(base + " error " + t);
            }
        }
        return out;
    }

    static void describe(File dir, List<String> out) {
        if (!dir.isDirectory()) {
            out.add(dir.getPath() + " none");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            out.add(dir.getPath() + " not readable");
            return;
        }
        StringBuilder sb = new StringBuilder(dir.getPath()).append(':');
        int shown = 0;
        for (File f : files) {
            String l = f.getName().toLowerCase(Locale.ROOT);
            if (!l.contains("radio") && !l.contains("logo")) continue;
            if (shown++ >= 12) {
                sb.append(" ...");
                break;
            }
            sb.append(' ').append(f.getName());
            if (f.isDirectory()) {
                String[] kids = f.list();
                sb.append("/(").append(kids == null ? "?" : String.valueOf(kids.length)).append(')');
            } else {
                sb.append('(').append(f.length()).append(')');
            }
        }
        if (shown == 0) sb.append(" nothing named radio or logo");
        out.add(sb.toString());
        for (String name : new String[]{"radio_data.txt", "radio_data_dny.txt"}) {
            File data = new File(dir, name);
            if (!data.isFile()) continue;
            StringBuilder head = new StringBuilder(data.getPath()).append(" first lines:");
            try (BufferedReader r = new BufferedReader(new FileReader(data))) {
                String line;
                for (int i = 0; i < 3 && (line = r.readLine()) != null; i++) head.append(" [").append(line).append(']');
            } catch (Exception e) {
                head.append(" unreadable ").append(e.getClass().getSimpleName());
            }
            out.add(head.toString());
        }
    }
}
