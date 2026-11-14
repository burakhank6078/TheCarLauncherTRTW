package com.thecarlauncher.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.audiofx.PresetReverb;

import org.junit.Test;

public class HallTest {
    @Test
    public void offMeansNoReverbAndNoWidening() {
        assertEquals(PresetReverb.PRESET_NONE, Hall.presetFor(Hall.OFF));
        assertEquals(0, Hall.strengthFor(Hall.OFF));
    }

    @Test
    public void eachStepIsWiderThanTheLastAndTheConcertStepUsesTheLargeHall() {
        assertTrue(Hall.strengthFor(Hall.LIGHT) > 0);
        assertTrue(Hall.strengthFor(Hall.MEDIUM) > Hall.strengthFor(Hall.LIGHT));
        assertTrue(Hall.strengthFor(Hall.CONCERT) > Hall.strengthFor(Hall.MEDIUM));
        assertTrue(Hall.strengthFor(Hall.CONCERT) <= 1000);
        assertEquals(PresetReverb.PRESET_LARGEHALL, Hall.presetFor(Hall.CONCERT));
        assertEquals(PresetReverb.PRESET_MEDIUMROOM, Hall.presetFor(Hall.LIGHT)); // the light step is a room, not a hall
    }
}
