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
    public void amUsesKilohertzAndTheSizeOfTheNumberCorrectsAStaleBand() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 1, 2, null); // AM
        assertTrue(s.apply(1025, 2, 1008, null));
        assertEquals(1008, s.freq);
        // The unit sends the band after the frequency of a band change: an FM number while the band is still AM
        // means the unit has moved to FM, it is not noise to be thrown away.
        assertTrue(s.apply(1025, 2, 10230, null));
        assertEquals(10230, s.freq);
        assertEquals(0, s.band);
        assertTrue(s.apply(1025, 5, 1, null)); // and the band message that follows has the last word (FM2)
        assertEquals(1, s.band);
    }

    @Test
    public void aBandChangeWithTheFrequencyFirstIsFollowed() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 8980, null);
        s.apply(1025, 4, 10, null);
        assertEquals(10, s.preset);
        assertTrue(s.apply(1025, 2, 1008, null)); // AM frequency, band message not here yet
        assertEquals(2, s.band);
        assertEquals(1008, s.freq);
        assertEquals(-1, s.preset);               // the FM slot number does not belong to AM
        assertEquals(8980, s.presetFreq[0][10]);  // and what was learned on FM stays there
        assertEquals(0, s.presetFreq[2][10]);
    }

    @Test
    public void aSeekAwayFromAPresetDropsTheSlotNumber() {
        // 4/9 on 89.55, then a seek to 88.00: the unit sends no slot -1, so the old number would stay highlighted.
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 8955, null);
        s.apply(1025, 4, 9, null);
        assertEquals(9, s.preset);
        s.apply(1025, 2, 8800, null);
        assertEquals(-1, s.preset);
        s.apply(1025, 2, 8955, null);             // back on the station: the unit says the slot again when it is a recall
        s.apply(1025, 4, 9, null);
        assertEquals(9, s.preset);
        // a frequency that is the slot's own keeps the number (the unit may send 2 after 4)
        s.apply(1025, 2, 8955, null);
        assertEquals(9, s.preset);
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
    public void aTileIsASlotOfTheBandTheUnitIsOn() {
        assertEquals(2, TwRadio.recallSlot(2, 0));
        assertEquals(14, TwRadio.recallSlot(14, 1)); // the third page, whatever the band
        assertEquals(17, TwRadio.recallSlot(17, 2));
        assertEquals(-1, TwRadio.recallSlot(2, -1)); // band not known yet
        assertEquals(-1, TwRadio.recallSlot(18, 0)); // no such slot
        assertEquals(-1, TwRadio.recallSlot(-1, 0));
    }

    @Test
    public void thereAreEighteenSlotsInThreePagesOfSix() {
        assertEquals(18, TwRadio.SLOTS);
        assertEquals(3, TwRadio.SLOTS / TwRadio.PAGE);
        assertEquals(5, TwRadio.tileOf(0, 5));
        assertEquals(14, TwRadio.tileOf(0, 14)); // slots up to 14 were seen on FM in the recording
        assertEquals(17, TwRadio.tileOf(2, 17));
        assertEquals(-1, TwRadio.tileOf(-1, 1));
        assertEquals(-1, TwRadio.tileOf(1, 18));
    }

    @Test
    public void theBandIsAlsoTakenFromArg1Five() {
        // After a seek the unit sent 0x401/5 with arg2 0 (28 Oct 2026 recording).
        TwRadio.State s = new TwRadio.State();
        assertTrue(s.apply(1025, 5, 0, null));
        assertEquals(0, s.band);
        assertTrue(s.apply(1025, 5, 2, null));
        assertEquals(2, s.band);
        assertFalse(s.apply(1025, 5, 9, null)); // no such band
    }

    @Test
    public void theRecordedSeekIsFollowedAndTheFlagsAreNotAChange() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 0, 64, null);   // a seek is running
        assertTrue(s.apply(1025, 2, 8955, null));
        assertTrue(s.apply(1025, 4, 9, null));
        assertEquals(8955, s.freq);
        assertEquals(9, s.preset);
        assertTrue(s.apply(1025, 2, 8940, null));
        assertEquals(8940, s.freq);
    }

    @Test
    public void aFlickeringRdsNameDoesNotReachTheScreen() {
        // "A HABER" came as "A HABEa" and "A HABER@" in between (28 Oct 2026 recording).
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 8800, null);
        assertTrue(s.apply(1028, 0, 0, "A HABER"));
        assertFalse(s.apply(1028, 0, 0, "A HABEa"));
        assertEquals("A HABER", s.name);
        assertFalse(s.apply(1028, 0, 0, "A HABER"));
        assertFalse(s.apply(1028, 0, 0, "A HABER@"));
        assertEquals("A HABER", s.name);
        // a real change is taken when it is seen twice in a row
        assertFalse(s.apply(1028, 0, 0, "RADYO D"));
        assertTrue(s.apply(1028, 0, 0, "RADYO D"));
        assertEquals("RADYO D", s.name);
        // and a new frequency starts from a clean slate
        s.apply(1025, 2, 8835, null);
        assertEquals("", s.name);
        assertTrue(s.apply(1028, 0, 0, "A HABER"));
    }

    @Test
    public void thePresetListEntriesAreKeptPerBandAndSlot() {
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        // arg1 = slot | band << 8 | PI << 16, arg2 = frequency, text = name
        assertTrue(s.apply(1026, 12 | (0 << 8) | (0x322D << 16), 9020, "A HABER"));
        assertEquals(9020, s.presetFreq[0][12]);
        assertEquals("A HABER", s.presetName[0][12]);
        assertFalse(s.apply(1026, 12 | (0x322D << 16), 9020, "A HABER")); // the same again
        assertTrue(s.apply(1026, 3, 0, null)); // an empty slot (not a frequency) is still an answer
        assertEquals(0, s.presetFreq[0][3]);
        assertFalse(s.apply(1026, 40, 9020, "x")); // no such slot
        s.apply(1025, 5, 2, null); // on AM the list is the AM one
        assertTrue(s.apply(1026, 1, 1008, "TRT"));
        assertEquals(1008, s.presetFreq[2][1]);
        assertEquals(9020, s.presetFreq[0][12]);
    }

    @Test
    public void tilesLearnTheSlotsTheUnitReports() {
        // The recording: "2/8980" then "4/10" when preset 10 was recalled; a seek to another station sends no slot at all.
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 8980, null);
        assertTrue(s.apply(1025, 4, 10, null));
        assertEquals(8980, s.presetFreq[0][10]);
        s.apply(1025, 2, 8940, null);              // seeked away; the slot number is stale
        s.apply(1028, 0, 0, "OTHER");
        assertEquals("", s.presetName[0][10]);     // the other station's name must not land on slot 10
        s.apply(1025, 2, 8980, null);              // back on the preset's station, by recalling the slot
        s.apply(1025, 4, 10, null);
        s.apply(1028, 0, 0, "RADYO D");
        assertEquals("RADYO D", s.presetName[0][10]);
        assertEquals(0, s.presetFreq[1][10]);      // another band is untouched
    }

    @Test
    public void aNameThatArrivesAfterTheSlotWasStoredStillReachesTheTile() {
        // Stored right after tuning: the RDS name is not here yet, so the slot is first saved without one.
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 9890, null);
        s.preset = 3;
        assertTrue(s.rememberPreset(3));
        assertEquals("", s.presetName[0][3]);
        assertTrue(s.apply(1028, 0, 0, "RADYO D"));
        assertEquals("RADYO D", s.presetName[0][3]);
    }

    @Test
    public void everySlotWithThatFrequencyAndNoNameGetsTheName() {
        // The unit may hold the same station twice (slots 0 and 1 were both 102.40 in the recording).
        TwRadio.State s = new TwRadio.State();
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 10240, null);
        s.apply(1025, 4, 0, null);
        s.apply(1025, 4, 1, null);
        assertEquals(10240, s.presetFreq[0][1]);
        s.apply(1028, 0, 0, "RADYO D");
        assertEquals("RADYO D", s.presetName[0][1]);
        assertEquals("RADYO D", s.presetName[0][0]);       // the other slot with the same station
        assertEquals("", s.presetName[0][5] == null ? "" : s.presetName[0][5]);
    }

    @Test
    public void storingFillsTheSlotAtOnce() {
        TwRadio.State s = new TwRadio.State();
        assertFalse(s.rememberPreset(3));          // nothing is playing yet
        s.apply(1025, 5, 0, null);
        s.apply(1025, 2, 9890, null);
        assertTrue(s.rememberPreset(3));
        assertEquals(9890, s.presetFreq[0][3]);
        assertFalse(s.rememberPreset(3));          // the same again
        assertFalse(s.rememberPreset(18));         // no such slot
    }

    @Test
    public void nextIsZeroAndPreviousIsOneAsInTheFactoryApp() {
        assertEquals(0, TwRadio.directionArg(true));
        assertEquals(1, TwRadio.directionArg(false));
    }
}
