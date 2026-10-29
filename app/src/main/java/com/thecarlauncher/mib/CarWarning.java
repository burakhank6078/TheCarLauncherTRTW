package com.thecarlauncher.mib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.car.TwProbe;
import com.thecarlauncher.car.VehicleState;
import com.thecarlauncher.car.WarningLogic;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.screens.VehicleScreen;
import com.thecarlauncher.util.Prefs;

/**
 * The warning while the car is moving with a door open or the driver belt off, in the style of the MIB's own pop-ups
 * (the ones that come up when a phone connects): the picture dims a little, a black panel with a white frame and a
 * blue rule under its header rises in the middle with a warning triangle, a short title, the lines and an "OK" key.
 * WarningLogic decides when it shows, hides and comes back. Only the panel takes touches, so the map and the keys
 * stay usable. It exists inside the launcher's own window only, so it is not seen while another app is in front.
 * Needs the car data ("Take trip data from OBD") and a GPS speed.
 */
public class CarWarning extends FrameLayout {
    private static final long PREVIEW_MS = 6000, ASK_MS = 30000, DOORS_STALE_MS = 120000;
    private static final long BUSY_TICK_MS = 1000, IDLE_TICK_MS = 10000;
    private static final long IN_MS = 240, OUT_MS = 180;
    /** Speed above which an open door counts, and above which the belt counts. */
    private static final float DOOR_KMH = 5f, BELT_KMH = 10f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout panel, lines;
    private final TextView title;
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
        setBackgroundColor(0x66000000); // the picture behind dims a little
        setVisibility(GONE);

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Mib.panel(c));
        int pad = Mib.px(c, 26);
        panel.setPadding(pad, Mib.px(c, 20), pad, Mib.px(c, 20));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(new VehicleScreen.Mark(VehicleScreen.Mark.WARN));
        head.addView(icon, new LinearLayout.LayoutParams(Mib.px(c, 54), Mib.px(c, 54)));
        title = Mib.text(c, "", 36, Mib.semibold(c), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(c, 18);
        head.addView(title, tl);
        panel.addView(head);

        View rule = new View(c);
        rule.setBackgroundColor(Mib.BLUE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(c, 2)));
        rl.topMargin = Mib.px(c, 14);
        rl.bottomMargin = Mib.px(c, 14);
        panel.addView(rule, rl);

        lines = new LinearLayout(c);
        lines.setOrientation(LinearLayout.VERTICAL);
        panel.addView(lines);

        FrameLayout ok = new FrameLayout(c);
        ok.setBackground(Mib.box(c));
        TextView okText = Mib.text(c, Mib.t(c, "U redu", "OK", "Tamam"), 30, Mib.semibold(c), Mib.WHITE);
        okText.setGravity(Gravity.CENTER);
        ok.addView(okText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ok.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                apply(logic.dismiss(System.currentTimeMillis()), 0, false);
            }
        });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(Mib.px(c, 170), Mib.px(c, 62));
        ol.gravity = Gravity.RIGHT;
        ol.topMargin = Mib.px(c, 18);
        panel.addView(ok, ol);
        panel.setClickable(true); // the panel takes the touches that land on it; the rest of the screen stays usable

        int w = Mib.px(c, 780);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        pl.topMargin = Mib.px(c, 96);
        addView(panel, pl);
    }

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
        Context c = getContext();
        if (a == WarningLogic.Action.SHOW) {
            title.setText(Mib.t(c, "Pažnja", "Warning", "Dikkat"));
            lines.removeAllViews();
            for (String l : CarTexts.lines(c, doors, beltOff)) {
                TextView t = Mib.text(c, l, 31, Mib.semibold(c), Mib.WHITE);
                t.setSingleLine(false);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                tl.topMargin = Mib.px(c, 6);
                lines.addView(t, tl);
            }
            if (getVisibility() != VISIBLE || getAlpha() < 1f) {
                // the panel rises in from slightly smaller and slightly higher, the picture dims with it
                setVisibility(VISIBLE);
                setAlpha(0f);
                panel.setScaleX(0.92f);
                panel.setScaleY(0.92f);
                panel.setTranslationY(-Mib.px(c, 14));
                panel.setPivotX(Mib.px(c, 390));
                panel.setPivotY(0f);
                animate().alpha(1f).setDuration(IN_MS).setInterpolator(new DecelerateInterpolator()).start();
                panel.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(IN_MS)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        } else if (a == WarningLogic.Action.HIDE) {
            panel.animate().scaleX(0.96f).scaleY(0.96f).setDuration(OUT_MS).start();
            animate().alpha(0f).setDuration(OUT_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!logic.visible()) setVisibility(GONE);
                }
            }).start();
        }
    }
}
