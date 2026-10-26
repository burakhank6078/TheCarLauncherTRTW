package com.thecarlauncher.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The tuner messages as read from the factory radio app (not yet proven on the car). */
public class TwRadioTest {
    @Test
    public void frequencyBandAndNameAreTaken() {
        TwRadio.State s = new TwRadio.State();
        assertTrue(s.apply(1025, 1, 0, null)); // band 0 = FM1
        assertTrue(s.apply(1025, 2, 10230, "RADYO D"));
        assertEquals(10230, s.freq);
        assertEquals("RADYO D", s.name);
        assertFalse(s.apply(1025, 2, 10230, "RADYO D")); // nothing new
    }

    @Test
    public void newFrequencyClearsTheOldNameAndText() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 1, 0, null);
        s.apply(1025, 2, 10230, "RADYO D");
        s.apply(1029, 0, 0, "Now playing");
        assertEquals("Now playing", s.text);
        s.apply(1025, 2, 9890, null);
        assertEquals(9890, s.freq);
        assertEquals("", s.name);
        assertEquals("", s.text);
    }

    @Test
    public void amUsesKilohertzAndFmValuesAreNotAcceptedThere() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 1, 2, null); // AM
        assertTrue(s.apply(1025, 2, 1008, null));
        assertEquals(1008, s.freq);
        assertFalse(s.apply(1025, 2, 10230, null)); // an FM number on AM is noise
        assertEquals(1008, s.freq);
    }

    @Test
    public void noiseIsIgnored() {
        TwRadio.State s = new TwRadio.State();
        assertFalse(s.apply(1025, 1, 7, null)); // no such band
        assertFalse(s.apply(1025, 2, 5, null)); // not a frequency
        assertEquals(-1, s.band);
        assertEquals(0, s.freq);
    }

    @Test
    public void presetSlotAndSourceFollowTheUnit() {
        TwRadio.State s = new TwRadio.State();
        assertFalse(s.radioSource());
        s.apply(769, 1, 0, null); // source 1 = radio
        assertTrue(s.radioSource());
        s.apply(769, 8, 0, null); // 8 = Bluetooth music
        assertFalse(s.radioSource());
        s.apply(1025, 4, 3, null);
        assertEquals(3, s.preset);
        s.apply(1025, 4, -1, null);
        assertEquals(-1, s.preset);
    }

    @Test
    public void theRadioChannelKnowsItsOwnIds() {
        assertTrue(TwRadio.listensTo(1025));
        assertTrue(TwRadio.listensTo(0x9E00));
        assertFalse(TwRadio.listensTo(0x501)); // car frames come over the main channel
    }

    @Test
    public void onlySourceOneIsTheRadio() {
        TwRadio.State s = new TwRadio.State();
        s.apply(769, 129, 0, null); // low bits are 1 but it is not the radio
        assertFalse(s.radioSource());
        s.apply(769, 17, 0, null);
        assertFalse(s.radioSource());
        s.apply(769, 1, 0, null);
        assertTrue(s.radioSource());
    }

    @Test
    public void signalIsKeptButDoesNotCountAsAChange() {
        TwRadio.State s = new TwRadio.State();
        assertFalse(s.apply(1025, 3, 42, null));
        assertEquals(42, s.signal);
    }

    @Test
    public void onlyTilesOfTheCurrentBandCanBeRecalled() {
        assertEquals(2, TwRadio.recallSlot(2, 0));   // FM1 page, slot 3
        assertEquals(-1, TwRadio.recallSlot(8, 0));  // a tile of the FM2 page while on FM1
        assertEquals(2, TwRadio.recallSlot(8, 1));   // the same tile once the unit is on FM2
        assertEquals(-1, TwRadio.recallSlot(2, -1)); // band not known yet
    }

    @Test
    public void tilesAreThreePagesOfSixOneBandEach() {
        assertEquals(0, TwRadio.bandOfTile(5));
        assertEquals(1, TwRadio.bandOfTile(6));
        assertEquals(2, TwRadio.bandOfTile(17));
        assertEquals(5, TwRadio.slotOfTile(5));
        assertEquals(0, TwRadio.slotOfTile(12));
        assertEquals(13, TwRadio.tileOf(2, 1));
        assertEquals(-1, TwRadio.tileOf(-1, 1));
        assertEquals(-1, TwRadio.tileOf(1, 9));
    }
}
