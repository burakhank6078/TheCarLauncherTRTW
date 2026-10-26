package com.thecarlauncher.car;

/**
 * When the door / belt warning is shown, kept apart from the views so it can be tested: a new problem shows
 * at once, it hides itself after {@link #SHOW_MS}, a tap dismisses it until the problem changes, and while the
 * same problem lasts it comes back every {@link #REPEAT_MS}.
 */
public final class WarningLogic {
    public static final long SHOW_MS = 8000, REPEAT_MS = 60000;

    /** What the view has to do after a step. */
    public enum Action { NONE, SHOW, HIDE }

    private String key = "";
    private boolean visible, dismissed;
    private long shownAt, hiddenAt;

    /** One look at the car: open doors (mask), belt off. SHOW also means "refresh the text" when already shown. */
    public Action step(long now, int doors, boolean beltOff) {
        if (doors == 0 && !beltOff) {
            key = "";
            dismissed = false;
            return hide(now);
        }
        String k = doors + ":" + beltOff;
        if (!k.equals(key)) {
            key = k;
            dismissed = false;
            return show(now);
        }
        if (visible && now - shownAt > SHOW_MS) return hide(now);
        if (!visible && !dismissed && now - hiddenAt > REPEAT_MS) return show(now);
        return Action.NONE;
    }

    /** The driver tapped the warning: gone until the problem changes. */
    public Action dismiss(long now) {
        if (!visible) return Action.NONE;
        dismissed = true;
        return hide(now);
    }

    public boolean visible() { return visible; }

    private Action show(long now) {
        visible = true;
        shownAt = now;
        return Action.SHOW;
    }

    private Action hide(long now) {
        if (!visible) return Action.NONE;
        visible = false;
        hiddenAt = now;
        return Action.HIDE;
    }
}
