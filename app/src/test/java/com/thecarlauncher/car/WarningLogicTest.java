package com.thecarlauncher.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thecarlauncher.car.WarningLogic.Action;

import org.junit.Test;

public class WarningLogicTest {
    private static final int DRIVER = VehicleState.DOOR_DRIVER, REAR = VehicleState.DOOR_REAR_RIGHT;

    @Test
    public void nothingWrongShowsNothing() {
        WarningLogic w = new WarningLogic();
        assertEquals(Action.NONE, w.step(0, 0, false));
        assertFalse(w.visible());
    }

    @Test
    public void newProblemShowsAtOnceThenHidesByItself() {
        WarningLogic w = new WarningLogic();
        assertEquals(Action.SHOW, w.step(1000, DRIVER, false));
        assertTrue(w.visible());
        assertEquals(Action.NONE, w.step(1000 + WarningLogic.SHOW_MS, DRIVER, false));
        assertEquals(Action.HIDE, w.step(1001 + WarningLogic.SHOW_MS, DRIVER, false));
        assertFalse(w.visible());
    }

    @Test
    public void comesBackEveryMinuteWhileTheSameProblemLasts() {
        WarningLogic w = new WarningLogic();
        w.step(0, DRIVER, false);
        w.step(WarningLogic.SHOW_MS + 1, DRIVER, false); // hidden at 8001
        assertEquals(Action.NONE, w.step(8001 + WarningLogic.REPEAT_MS, DRIVER, false));
        assertEquals(Action.SHOW, w.step(8002 + WarningLogic.REPEAT_MS, DRIVER, false));
    }

    @Test
    public void tapSilencesUntilTheProblemChanges() {
        WarningLogic w = new WarningLogic();
        w.step(0, DRIVER, false);
        assertEquals(Action.HIDE, w.dismiss(500));
        assertEquals(Action.NONE, w.step(500 + WarningLogic.REPEAT_MS * 3, DRIVER, false));
        // a second door opens: a different problem, shown at once
        assertEquals(Action.SHOW, w.step(500 + WarningLogic.REPEAT_MS * 3 + 1, DRIVER | REAR, false));
        assertTrue(w.visible());
    }

    @Test
    public void problemGoneHidesAndForgetsTheTap() {
        WarningLogic w = new WarningLogic();
        w.step(0, DRIVER, false);
        w.dismiss(100);
        assertEquals(Action.NONE, w.step(200, 0, false));
        assertEquals(Action.SHOW, w.step(300, DRIVER, false)); // same door again after it was shut: shown again
    }

    @Test
    public void changeWhileShownRefreshesTheText() {
        WarningLogic w = new WarningLogic();
        w.step(0, DRIVER, false);
        assertEquals(Action.SHOW, w.step(1000, DRIVER, true));
        assertEquals(Action.HIDE, w.step(2000, 0, false));
    }
}
