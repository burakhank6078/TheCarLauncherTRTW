package com.thecarlauncher.mib;

import static org.junit.Assert.assertNotEquals;

import com.thecarlauncher.car.CarSettings;

import org.junit.Test;

/** Every text of the car's light settings has a Turkish translation (the table is checked here, the call sites are not scanned). */
public class CarSettingsTextsTest {
    @Test
    public void everyLightTextIsTranslated() {
        for (String en : CarSettings.lightTexts()) {
            assertNotEquals("no Turkish text for \"" + en + "\"", en, Tr.get(en));
        }
    }
}
