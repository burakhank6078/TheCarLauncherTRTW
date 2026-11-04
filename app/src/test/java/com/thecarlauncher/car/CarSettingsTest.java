package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The light page of the factory "Arac ayari" app: which byte and bit holds each setting. */
public class CarSettingsTest {
    private static final CarSettings.Translator EN = new CarSettings.Translator() {
        public String tr(String en) { return en; }
    };

    private static CarSettings.Item item(String en) {
        for (CarSettings.Section s : CarSettings.lightSections()) {
            for (CarSettings.Item it : s.items) if (it.en.equals(en)) return it;
        }
        throw new AssertionError("no setting " + en);
    }

    private final CarSettings cs = CarSettings.get();

    @Before
    public void fresh() {
        cs.forget();
        cs.setListener(null);
    }

    @Test
    public void headlightFrame() {
        // byte 3 = 0010 1101: rain, lane flash and daytime lights on, left traffic, switch-on time 2; light assist and motorway off
        cs.decodeFrame(104, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x2D, 40, 20, 60, 10, 5});
        assertEquals("On", cs.text(item("Automatic headlights in rain"), EN));
        assertEquals("On", cs.text(item("Lane change flash"), EN));
        assertEquals("On", cs.text(item("Daytime running lights"), EN));
        assertEquals("Off", cs.text(item("Dynamic light assist"), EN));
        assertEquals("Off", cs.text(item("Motorway light"), EN));
        assertEquals("Late", cs.text(item("Headlight switch-on time"), EN));
        assertEquals("Left traffic", cs.text(item("Traffic side (travel mode)"), EN));
        assertEquals("40 %", cs.text(item("Instrument and switch lighting"), EN));
        assertEquals("20 %", cs.text(item("Door ambient light"), EN));
        assertEquals("60 %", cs.text(item("Footwell light"), EN));
        assertEquals("10 sec", cs.text(item("Coming home light"), EN));
        assertEquals("5 sec", cs.text(item("Leaving home light"), EN));
    }

    @Test
    public void rightTrafficAndAnOffTimer() {
        cs.decodeFrame(104, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x02, 0, 0, 0, 0, 0});
        assertEquals("Right traffic", cs.text(item("Traffic side (travel mode)"), EN));
        assertEquals("Off", cs.text(item("Coming home light"), EN));
        assertEquals("Early", cs.text(item("Headlight switch-on time"), EN));
    }

    @Test
    public void ambientFrame() {
        cs.decodeFrame(103, new byte[]{(byte) 0xFF, 3, 5, 40, 60, 3, 70});
        assertEquals("Level 3", cs.text(item("Headlight range adjustment"), EN));
        assertEquals("Colour 2", cs.text(item("Ten-colour ambient colour"), EN));
        assertEquals("Off", cs.text(item("Three-colour ambient colour"), EN));
        assertEquals("Off", cs.text(item("Twenty-colour ambient colour"), EN));
        assertEquals("40 %", cs.text(item("Roof ambient light"), EN));
        assertEquals("60 %", cs.text(item("Front ambient light"), EN));
        assertEquals("3", cs.text(item("Lane assist brightness"), EN));
        assertEquals("70 %", cs.text(item("All ambient areas"), EN));
    }

    @Test
    public void theThreeColourSetsOfTheAmbientColourByte() {
        assertEquals("Off", CarSettings.ambientColour(0, EN));
        assertEquals("3-colour: White", CarSettings.ambientColour(1, EN));
        assertEquals("3-colour: Blue", CarSettings.ambientColour(3, EN));
        assertEquals("10-colour: Colour 1", CarSettings.ambientColour(4, EN));
        assertEquals("10-colour: Colour 10", CarSettings.ambientColour(13, EN));
        assertEquals("20-colour: Colour 1", CarSettings.ambientColour(14, EN));
        assertEquals("20-colour: Colour 20", CarSettings.ambientColour(33, EN));
    }

    @Test
    public void personalisedFrame() {
        cs.decodeFrame(102, new byte[]{(byte) 0xFF, 2, 80, 3, 4, 1, 20, 40, 60, 80});
        assertEquals("Eternal", cs.text(item("Lighting tone"), EN));
        assertEquals("80 %", cs.text(item("Lighting tone brightness"), EN));
        assertEquals("Colour 3", cs.text(item("Main ambient colour"), EN));
        assertEquals("Colour 4", cs.text(item("Secondary ambient colour"), EN));
        assertEquals("1", cs.text(item("Colour combination"), EN));
        assertEquals("20 %", cs.text(item("Window area brightness"), EN));
        assertEquals("40 %", cs.text(item("Centre console area brightness"), EN));
        assertEquals("60 %", cs.text(item("Doorway area brightness"), EN));
        assertEquals("80 %", cs.text(item("Foot area brightness"), EN));
    }

    @Test
    public void aSettingTheCarDoesNotHaveIsMarkedUnavailable() {
        // the first bytes are the "available" bits: bit 1 of byte 0 is the daytime running lights, left clear here
        cs.decodeFrame(104, new byte[]{(byte) 0xFD, (byte) 0xFF, (byte) 0xFF, 0x01, 0, 0, 0, 0, 0});
        CarSettings.Reading drl = cs.read(item("Daytime running lights"));
        assertTrue(drl.known);
        assertFalse(drl.available);
        assertTrue(cs.read(item("Lane change flash")).available);
    }

    @Test
    public void nothingIsKnownBeforeTheCarAnswers() {
        assertFalse(cs.haveLights());
        assertFalse(cs.read(item("Footwell light")).known);
        assertEquals("—", cs.text(item("Footwell light"), EN));
        cs.decodeFrame(104, new byte[9]);
        cs.decodeFrame(103, new byte[7]);
        assertFalse(cs.haveLights());
        cs.decodeFrame(102, new byte[10]);
        assertTrue(cs.haveLights());
    }

    @Test
    public void aShortFrameIsNotReadPastItsEnd() {
        cs.decodeFrame(102, new byte[]{(byte) 0xFF, 2});
        assertFalse(cs.read(item("Foot area brightness")).known);
        assertTrue(cs.read(item("Lighting tone")).known);
    }

    @Test
    public void otherFramesAreIgnoredAndTheListenerIsTold() {
        final int[] calls = {0};
        cs.setListener(new Runnable() {
            public void run() { calls[0]++; }
        });
        cs.decodeFrame(26, new byte[12]);
        assertEquals(0, calls[0]);
        cs.decodeFrame(104, new byte[9]);
        assertEquals(1, calls[0]);
    }

    @Test
    public void everySettingHasAWriteKeyForTheNextStep() {
        List<Integer> keys = new ArrayList<>();
        for (CarSettings.Section s : CarSettings.lightSections()) {
            for (CarSettings.Item it : s.items) keys.add(it.writeKey);
        }
        assertTrue(keys.contains(16));  // daytime running lights
        assertTrue(keys.contains(41));  // foot area brightness
        assertEquals(29, keys.size()); // the three ambient colour lists share one key
    }

    private static CarSettings.Item itemOf(int page, String en) {
        for (CarSettings.Section s : CarSettings.pages().get(page).sections) {
            for (CarSettings.Item it : s.items) if (it.en.equals(en)) return it;
        }
        throw new AssertionError("no setting " + en);
    }

    @Test
    public void openingAndClosingFrame() {
        // available bits all set; window opening 2 (all), unlocking 1 (single door); belt-style checks as in the factory page
        cs.decodeFrame(100, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xA0, 0x74});
        assertEquals("All windows", cs.text(itemOf(1, "Convenience opening"), EN));
        assertEquals("Single door", cs.text(itemOf(1, "Door unlocking"), EN));
        assertEquals("On", cs.text(itemOf(1, "Automatic locking"), EN));
        assertEquals("On", cs.text(itemOf(1, "Memory matching of the seat to the remote key"), EN));
        assertEquals("Off", cs.text(itemOf(1, "Easy Open (tailgate sensor)"), EN));
        assertEquals("On", cs.text(itemOf(1, "Approach function active"), EN));
        assertEquals("Off", cs.text(itemOf(1, "Unlock vehicle when approaching"), EN));
    }

    @Test
    public void mirrorsAndWipersFrame() {
        // byte 2 = 1010 0000: mirror sync and fold-away on; byte 3 = 0100 0000: only the rear window wiping on
        cs.decodeFrame(105, new byte[]{(byte) 0xE0, (byte) 0xE0, (byte) 0xA0, 0x40});
        assertEquals("On", cs.text(itemOf(2, "Synchronous adjustment"), EN));
        assertEquals("Off", cs.text(itemOf(2, "Lower while reversing"), EN));
        assertEquals("On", cs.text(itemOf(2, "Fold away after parking"), EN));
        assertEquals("Off", cs.text(itemOf(2, "Automatic wiping in rain"), EN));
        assertEquals("On", cs.text(itemOf(2, "Rear window wiping in reverse gear"), EN));
        // the wiper service position is not available when its bit in byte 1 is clear
        cs.decodeFrame(105, new byte[]{(byte) 0xE0, (byte) 0xC0, (byte) 0xA0, 0x40});
        assertFalse(cs.read(itemOf(2, "Wipers in service position")).available);
    }

    @Test
    public void onlyCheckBoxesAndKnownListsMayBeChanged() {
        assertTrue(itemOf(0, "Daytime running lights").editable());
        assertTrue(itemOf(0, "Headlight switch-on time").editable());
        assertEquals(3, itemOf(0, "Headlight switch-on time").optionCount());
        assertTrue(itemOf(1, "Door unlocking").editable());
        assertTrue(itemOf(0, "Traffic side (travel mode)").editable());
        assertTrue(itemOf(0, "Coming home light").editable());
        assertEquals(7, itemOf(0, "Coming home light").optionCount());
        assertTrue(itemOf(0, "Ten-colour ambient colour").editable());
        assertFalse(itemOf(0, "Main ambient colour").editable());      // the allowed colours depend on flags of the frame
        assertFalse(itemOf(0, "Colour combination").editable());
    }

    @Test
    public void everyPageWritesWithItsOwnMessageAndKeysAreUniquePerMessage() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CarSettings.Page p : CarSettings.pages()) {
            for (CarSettings.Section s : p.sections) {
                for (CarSettings.Item it : s.items) seen.add(s.cmd + ":" + it.writeKey);
            }
        }
        assertEquals(109, CarSettings.pages().get(0).sections.get(0).cmd);
        assertEquals(111, CarSettings.pages().get(1).sections.get(0).cmd);
        assertEquals(110, CarSettings.pages().get(2).sections.get(0).cmd);
        assertTrue(CarSettings.pages().get(2).extRequest);
        assertFalse(CarSettings.pages().get(1).extRequest);
    }
}
