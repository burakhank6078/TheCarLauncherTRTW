package com.thecarlauncher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsBackupTest {
    @Test
    public void everyTypeSurvivesAWriteAndRead() {
        Map<String, Object> m = new HashMap<>();
        m.put("language", "tr");
        m.put("ambient_level", 70);
        m.put("last_fill", 1700000000000L);
        m.put("tone_bass", 1.5f);
        m.put("trip_from_obd", true);
        String text = SettingsBackup.encode(m);
        List<String[]> back = SettingsBackup.decode(text);
        Map<String, String> byKey = new HashMap<>();
        for (String[] e : back) byKey.put(e[1], e[0] + ":" + e[2]);
        assertEquals("s:tr", byKey.get("language"));
        assertEquals("i:70", byKey.get("ambient_level"));
        assertEquals("f:1.5", byKey.get("tone_bass"));
        assertEquals("b:true", byKey.get("trip_from_obd"));
    }

    @Test
    public void textWithTabsNewlinesAndBackslashesIsKept() {
        Map<String, Object> m = new HashMap<>();
        m.put("note", "a\tb\nc\\d");
        List<String[]> back = SettingsBackup.decode(SettingsBackup.encode(m));
        assertEquals(1, back.size());
        assertEquals("a\tb\nc\\d", back.get(0)[2]);
    }

    @Test
    public void runtimeStateIsLeftOut() {
        Map<String, Object> m = new HashMap<>();
        m.put("twprobe_crash_stage", 3);
        m.put("twradio_try", true);
        m.put("diag_log_enabled", true);
        m.put("obd_trip_values", "1,2,3");
        m.put("trip_log", "x");
        m.put("last_lat", 1.0f);
        m.put("accent", 5);
        String text = SettingsBackup.encode(m);
        assertTrue(text.contains("accent"));
        assertEquals(1, SettingsBackup.decode(text).size());
    }

    @Test
    public void brokenLinesAndBadValuesAreIgnored() {
        String text = "# header\n\ni\tcount\tnotanumber\nx\tkey\tvalue\nb\tflag\tmaybe\ns\tgood\tyes\nonlytwo\tfields\n";
        List<String[]> back = SettingsBackup.decode(text);
        assertEquals(1, back.size());
        assertEquals("good", back.get(0)[1]);
    }

    @Test
    public void safetySwitchesAndTheMapKeyAreNeverSavedOrRestored() {
        Map<String, Object> m = new HashMap<>();
        m.put(Prefs.TW_COMMANDS, true);
        m.put(Prefs.REC_PHONE, true);
        m.put(Prefs.CARTO_KEY, "secret");
        m.put("accent", 2);
        String text = SettingsBackup.encode(m);
        assertEquals(1, SettingsBackup.decode(text).size());
        // and a file that carries them (copied from elsewhere, or edited) cannot turn them on
        List<String[]> back = SettingsBackup.decode("b\tallow_car_commands\ttrue\nb\trec_phone_channel\ttrue\ns\tcarto_key\tx\ni\taccent\t3\n");
        assertEquals(1, back.size());
        assertEquals("accent", back.get(0)[1]);
    }

    @Test
    public void aBackupCannotBringRuntimeStateBack() {
        List<String[]> back = SettingsBackup.decode("i\ttwprobe_crash_stage\t5\nb\ttwradio_bad\ttrue\ns\tlanguage\ttr\n");
        assertEquals(1, back.size());
        assertEquals("language", back.get(0)[1]);
    }
}
