package com.thecarlauncher.mib;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.util.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The standby screen of a MIB: a black screen with the time large in the middle and the date under it, shown after the
 * ignition has been off for a few seconds. A touch brings the launcher back (and it stays away until the ignition has
 * changed). The picture moves a few pixels every minute, so a screen that stays on all night does not burn in. The
 * screen's brightness is not touched.
 */
public final class StandbyClock extends FrameLayout {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView time, date;
    private final LinearLayout box;
    private int lastMinute = -1;
    private boolean up;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            main.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    public StandbyClock(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
        setVisibility(GONE);
        setClickable(true); // takes every touch while it is up
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        time = Mib.text(c, "", 190, Mib.regular(c), Mib.WHITE);
        time.setGravity(Gravity.CENTER);
        box.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        date = Mib.text(c, "", 36, Mib.regular(c), Mib.GREY);
        date.setGravity(Gravity.CENTER);
        box.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                CarEvents.get().dismissStandby();
                hide();
            }
        });
    }

    public void show() {
        if (up) return;
        up = true;
        lastMinute = -1;
        update();
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(600).start();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    public void hide() {
        up = false;
        main.removeCallbacks(tick);
        if (getVisibility() != VISIBLE) return;
        animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!up) setVisibility(GONE); // shown again meanwhile: the end action of a cancelled fade must not hide it
            }
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void update() {
        Context c = getContext();
        Date now = new Date();
        Locale loc = Mib.isTr(c) ? new Locale("tr") : Locale.getDefault();
        time.setText(new SimpleDateFormat(Prefs.use24h(c) ? "HH:mm" : "h:mm", loc).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", loc).format(now));
        int minute = (int) (now.getTime() / 60000L);
        if (minute != lastMinute) {
            lastMinute = minute;
            // a few pixels in a different direction every minute: nothing stays lit in the same place
            int[][] shifts = {{0, 0}, {18, -12}, {-16, 14}, {12, 16}, {-18, -10}};
            int[] s = shifts[minute % shifts.length];
            box.setTranslationX(Mib.px(c, s[0]));
            box.setTranslationY(Mib.px(c, s[1]));
        }
    }
}
