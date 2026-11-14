package com.thecarlauncher.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class StandbyKeyTest {
    @Test
    public void keysThatAlreadyMeanSomethingCannotBeTaken() {
        assertTrue(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_BACK));
        assertTrue(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_HOME));
        assertTrue(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertFalse(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_F5));
        assertFalse(StandbyKey.reservedAndroid(KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void theUnitsMediaAndCallCodesAreRefusedToo() {
        for (int code : new int[]{18, 19, 20, 21, 24, 25}) assertTrue(StandbyKey.reservedTw(code));
        assertFalse(StandbyKey.reservedTw(33));
        assertFalse(StandbyKey.reservedTw(0));
    }
}
