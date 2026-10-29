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
        assertEquals("10-colour: Colour 2", cs.text(item("Ambient colour"), EN));
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
        assertEquals(27, keys.size());
    }
}
