package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.car.TwProbe;
import com.thecarlauncher.car.VehicleState;
import com.thecarlauncher.car.WarningLogic;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.util.Prefs;

/**
 * A line or two of text just under the status bar (so the top bar stays usable) while the car is moving with
 * a door open or the driver belt off; WarningLogic decides when it shows, hides and comes back. It only
 * exists inside the launcher's own window, so it is not seen while another app is in front. Needs the car
 * data ("Take trip data from OBD") and a GPS speed.
 */
public class CarWarning extends FrameLayout {
    private static final long PREVIEW_MS = 6000, ASK_MS = 30000, DOORS_STALE_MS = 120000;
    private static final long BUSY_TICK_MS = 1000, IDLE_TICK_MS = 10000;
    /** Speed above which an open door counts, and above which the belt counts. */
    private static final float DOOR_KMH = 5f, BELT_KMH = 10f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView text;
    private final WarningLogic logic = new WarningLogic();
    private long previewUntil, lastAsk;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            boolean busy = false;
            try {
                busy = evaluate();
            } finally {
                main.postDelayed(this, busy ? BUSY_TICK_MS : IDLE_TICK_MS);
            }
        }
    };

    public CarWarning(Context c) {
        super(c);
        int sw = Math.max(1, Mib.px(c, 1));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2000000);
        bg.setStroke(sw, 0xFF55565C);
        setBackground(bg);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(c, 30), Mib.px(c, 6), Mib.px(c, 30), Mib.px(c, 6));
        text = Mib.text(c, "", 28, Mib.semibold(c), 0xFFF2B01E);
        text.setSingleLine(false);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
        setClickable(false);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                apply(logic.dismiss(System.currentTimeMillis()), 0, false);
            }
        });
    }

    /** Height of the banner: two lines of text. */
    public static int height(Context c) { return Mib.px(c, 92); }

    /** Shows a sample (driver and rear right door open, belt off) for a few seconds. */
    public void preview() {
        previewUntil = System.currentTimeMillis() + PREVIEW_MS;
        evaluate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** One look at the car; false when the feature is off (the next look can wait). */
    private boolean evaluate() {
        long now = System.currentTimeMillis();
        int doors = 0;
        boolean beltOff = false;
        boolean on = true;
        if (previewUntil > now) {
            doors = VehicleState.DOOR_DRIVER | VehicleState.DOOR_REAR_RIGHT;
            beltOff = true;
        } else if (Prefs.bool(getContext(), Prefs.CAR_WARNING, true) && Prefs.bool(getContext(), Prefs.TRIP_OBD, false)) {
            VehicleState vs = VehicleState.get();
            LocationTracker t = LocationTracker.get(getContext());
            float kmh = t.hasFix() ? t.speedMps() * 3.6f : 0f;
            int d = vs.doors();
            if (d > 0 && kmh >= DOOR_KMH) {
                // The car sends door frames only when a door changes, so an old "open" may be a missed "closed":
                // at speed it is only trusted when fresh, otherwise the car is asked again and the answer decides.
                if (vs.doorsAgeMs() <= DOORS_STALE_MS) doors = d;
                else askDoors(now);
            }
            beltOff = kmh >= BELT_KMH && vs.belt() == 0 && vs.engineRunning();
        } else {
            on = false;
        }
        apply(logic.step(now, doors, beltOff), doors, beltOff);
        return on || logic.visible();
    }

    private void askDoors(long now) {
        TwProbe probe = TwProbe.get();
        if (!probe.usable(getContext()) || now - lastAsk < ASK_MS) return;
        lastAsk = now;
        probe.requestFrameAsync(getContext(), 26);
        probe.requestFrameAsync(getContext(), 18);
    }

    private void apply(WarningLogic.Action a, int doors, boolean beltOff) {
        if (a == WarningLogic.Action.SHOW) {
            StringBuilder sb = new StringBuilder();
            for (String l : CarTexts.lines(getContext(), doors, beltOff)) sb.append(sb.length() == 0 ? "" : "\n").append(l);
            text.setText(sb);
            setClickable(true);
            if (getVisibility() != VISIBLE || getAlpha() < 1f) {
                setVisibility(VISIBLE);
                setAlpha(0f);
                animate().alpha(1f).setDuration(200).start();
            }
        } else if (a == WarningLogic.Action.HIDE) {
            setClickable(false); // taps go to what is underneath at once
            animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!logic.visible()) setVisibility(GONE);
                }
            }).start();
        }
    }
}
