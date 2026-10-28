package com.thecarlauncher.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

/**
 * MIB "Ambijentalno osvetljenje": the Corsa C interior picture, a 30-colour
 * slider with < > steps, and a brightness key. The colour becomes the accent of the whole UI.
 */
public class AmbientScreen extends Screen {
    private static final int[] LEVELS = {100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 7, 5, 4, 3, 2, 1};

    private InteriorView interior;
    private ColorSlider slider;
    private TextView value;
    private int index;

    public AmbientScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public boolean hasOwnBack() { return true; }

    @Override
    public String title() { return s("Ambijentalno osvetljenje", "Ambient lighting"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private boolean en() { return "en".equals(Prefs.str(act, Prefs.LANGUAGE, "sr")); }

    private static int color(int i) { return (Integer) Mib.AMBIENT[i][2]; }

    private static int indexOf(int color) {
        for (int i = 0; i < Mib.AMBIENT.length; i++) if (color(i) == color) return i;
        return 0;
    }

    @Override
    protected View create(ViewGroup parent) {
        index = indexOf(Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT));
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);

        // Header: title, framed value box, back key.
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Mib.px(act, 22), Mib.px(act, 8), Mib.px(act, 16), Mib.px(act, 8));
        TextView t = Mib.text(act, title(), 34, Mib.regular(act), Mib.WHITE);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        value = Mib.text(act, "", 30, Mib.regular(act), Mib.WHITE);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(Mib.px(act, 16), 0, Mib.px(act, 16), 0);
        value.setBackground(Mib.box(act));
        head.addView(value, new LinearLayout.LayoutParams(Mib.px(act, 290), Mib.px(act, 58)));
        FrameLayout back = new FrameLayout(act);
        back.setBackground(Mib.box(act));
        back.addView(Mib.icon(act, R.drawable.ui_ic_back, Mib.WHITE),
                new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!onBack()) act.goBack();
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Mib.px(act, 66), Mib.px(act, 58));
        bl.leftMargin = Mib.px(act, 10);
        head.addView(back, bl);
        r.addView(head);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        r.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));

        // Interior with the light strips, slider row overlaid along the bottom.
        FrameLayout stage = new FrameLayout(act);
        interior = new InteriorView(act);
        stage.addView(interior, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 22), 0, Mib.px(act, 16), 0);
        int box = Mib.px(act, 62);
        row.addView(key(R.drawable.ui_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick((index + Mib.AMBIENT.length - 1) % Mib.AMBIENT.length, true);
            }
        }), new LinearLayout.LayoutParams(box, box));
        slider = new ColorSlider(act);
        slider.setListener(new ColorSlider.Listener() {
            @Override
            public void onPick(int i, boolean done) {
                pick(i, done);
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Mib.px(act, 62), 1f);
        sl.leftMargin = sl.rightMargin = Mib.px(act, 10);
        row.addView(slider, sl);
        row.addView(key(R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick((index + 1) % Mib.AMBIENT.length, true);
            }
        }), new LinearLayout.LayoutParams(box, box));
        if (com.thecarlauncher.util.AmbientBle.enabled(act)) {
            // The strip has two zones (the LAMP&FRGN app's Zone 1 and Zone 2): this key picks which one the colour and
            // brightness keys act on.
            FrameLayout zoneKey = new FrameLayout(act);
            zoneKey.setBackground(Mib.box(act));
            zoneLabel = Mib.text(act, zone == 0 ? Mib.t(act, "Sve", "All", "Hepsi") : String.valueOf(zone), 24, Mib.semibold(act), Mib.WHITE);
            zoneLabel.setGravity(Gravity.CENTER);
            zoneKey.addView(zoneLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            zoneKey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleZone();
                }
            });
            LinearLayout.LayoutParams zl = new LinearLayout.LayoutParams(Mib.px(act, 100), box);
            zl.leftMargin = Mib.px(act, 18);
            row.addView(zoneKey, zl);
        }
        FrameLayout sun = new FrameLayout(act);
        sun.setBackground(Mib.box(act));
        ImageView sv = new ImageView(act);
        sv.setImageDrawable(new Sun());
        sun.addView(sv, new FrameLayout.LayoutParams(Mib.px(act, 40), Mib.px(act, 40), Gravity.CENTER));
        sun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleLevel();
            }
        });
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(box, box);
        ul.leftMargin = Mib.px(act, 18);
        row.addView(sun, ul);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 90), Gravity.BOTTOM);
        rl.bottomMargin = Mib.px(act, 10);
        stage.addView(row, rl);
        r.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        show(index);
        return r;
    }

    private View key(int icon, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Mib.box(act));
        b.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 46), Mib.px(act, 30), Gravity.CENTER));
        b.setOnClickListener(l);
        return b;
    }

    /** Which part of the strip the colour and brightness keys act on: 0 = all, 1 = Zone 1, 2 = Zone 2. */
    private int zone;
    private TextView zoneLabel;

    private int level() {
        return zone == 0 ? Prefs.integer(act, Prefs.AMBIENT_LEVEL, 100) : com.thecarlauncher.util.AmbientBle.zoneLevel(act, zone);
    }

    private int currentColor() {
        return zone == 0 ? Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT) : com.thecarlauncher.util.AmbientBle.zoneColor(act, zone);
    }

    /** Makes both zones hold their own value, so changing one leaves the other as it is now. */
    private void splitZones() {
        Prefs.put(act, Prefs.AMBIENT_Z1, com.thecarlauncher.util.AmbientBle.zoneColor(act, 1));
        Prefs.put(act, Prefs.AMBIENT_Z2, com.thecarlauncher.util.AmbientBle.zoneColor(act, 2));
        Prefs.put(act, Prefs.AMBIENT_L1, com.thecarlauncher.util.AmbientBle.zoneLevel(act, 1));
        Prefs.put(act, Prefs.AMBIENT_L2, com.thecarlauncher.util.AmbientBle.zoneLevel(act, 2));
    }

    /** Next of: all, zone 1, zone 2. The pickers then show and change that part of the strip. */
    private void cycleZone() {
        zone = (zone + 1) % 3;
        if (zone != 0) splitZones();
        zoneLabel.setText(zone == 0 ? Mib.t(act, "Sve", "All", "Hepsi") : String.valueOf(zone));
        index = indexOf(currentColor());
        show(index);
        Ui.toast(act, zone == 0 ? Mib.t(act, "Cela traka", "The whole strip", "Tüm şerit")
                : Mib.t(act, "Zona " + zone, "Zone " + zone, "Bölge " + zone));
    }

    /** Preview while dragging; store and recolour the whole UI once the finger lifts. */
    private void pick(int i, boolean done) {
        show(i);
        if (!done) return;
        if (zone != 0) {
            // one zone of the strip: the screen's own accent colour stays as it is
            if (color(i) != com.thecarlauncher.util.AmbientBle.zoneColor(act, zone)) {
                Prefs.put(act, zone == 1 ? Prefs.AMBIENT_Z1 : Prefs.AMBIENT_Z2, color(i));
                com.thecarlauncher.util.AmbientBle.get(act).syncFromPrefs();
            }
            return;
        }
        if (color(i) != Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT)
                || Prefs.integer(act, Prefs.AMBIENT_Z1, 0) != 0 || Prefs.integer(act, Prefs.AMBIENT_Z2, 0) != 0) {
            Prefs.put(act, Prefs.AMBIENT, color(i));
            Prefs.put(act, Prefs.AMBIENT_Z1, 0); // both zones follow the main colour again
            Prefs.put(act, Prefs.AMBIENT_Z2, 0);
            com.thecarlauncher.util.AmbientBle.get(act).syncFromPrefs();
            act.restartUi();
        }
    }

    /** Lists the brightness steps (fine at the dim end for a dark cabin); the screen's own backlight is not touched. */
    private void cycleLevel() {
        int l = level(), checked = -1;
        String[] names = new String[LEVELS.length];
        for (int k = 0; k < LEVELS.length; k++) {
            names[k] = LEVELS[k] + " %";
            if (LEVELS[k] == l) checked = k;
        }
        // Own list (not Dialogs.choose) so the Cancel button is translated.
        new android.app.AlertDialog.Builder(act)
                .setTitle(s("Osvetljenost", "Brightness"))
                .setSingleChoiceItems(names, checked, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        d.dismiss();
                        int old = level();
                        if (LEVELS[i] == old && (zone != 0 || (Prefs.integer(act, Prefs.AMBIENT_L1, 0) == 0
                                && Prefs.integer(act, Prefs.AMBIENT_L2, 0) == 0))) return;
                        if (zone == 0) {
                            Prefs.put(act, Prefs.AMBIENT_LEVEL, LEVELS[i]);
                            Prefs.put(act, Prefs.AMBIENT_L1, 0); // both zones follow the main level again
                            Prefs.put(act, Prefs.AMBIENT_L2, 0);
                        } else {
                            Prefs.put(act, zone == 1 ? Prefs.AMBIENT_L1 : Prefs.AMBIENT_L2, LEVELS[i]);
                        }
                        com.thecarlauncher.util.AmbientBle.get(act).syncFromPrefs();
                        // The screen's accent stops dimming at 60 %; below that only the strip changes. A single zone
                        // never changes the screen.
                        if (zone == 0 && Math.max(60, old) != Math.max(60, LEVELS[i])) act.restartUi();
                        else show(index);
                    }
                })
                .setNegativeButton(s("Otkaži", "Cancel"), null)
                .show();
    }

    private void show(int i) {
        index = i;
        slider.setSelected(i);
        int lvl = level();
        String name = s((String) Mib.AMBIENT[i][0], (String) Mib.AMBIENT[i][1]);
        String zoneName = zone == 0 ? "" : Mib.t(act, "Zona " + zone, "Zone " + zone, "Bölge " + zone) + ": ";
        value.setText(zoneName + (lvl < 100 ? name + "  " + lvl + "%" : name));
    }

    @Override
    public void onShow() {
        show(indexOf(Prefs.integer(act, Prefs.AMBIENT, Mib.DEFAULT_AMBIENT)));
    }

    @Override
    public boolean onBack() { return false; }

    // ---- Interior picture ------------------------------------------------------------------------

    /** The Corsa C cabin, centre-cropped to fill the page. */
    static final class InteriorView extends View {
        private final Bitmap photo;
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        InteriorView(Context c) {
            super(c);
            photo = com.thecarlauncher.util.CarImages.get(c, com.thecarlauncher.util.CarImages.INTERIOR);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0 || photo == null) return;
            float scale = Math.max(w / (float) photo.getWidth(), h / (float) photo.getHeight());
            float pw = photo.getWidth() * scale, ph = photo.getHeight() * scale;
            float ox = (w - pw) / 2f, oy = (h - ph) / 2f;
            dst.set(ox, oy, ox + pw, oy + ph);
            src.set(0, 0, photo.getWidth(), photo.getHeight());
            c.drawBitmap(photo, src, dst, img);
        }
    }

    // ---- 30-colour slider ---------------------------------------------------------------------

    /** Row of colour cells in a dark frame; drag or tap to choose, the choice gets a white frame. */
    static final class ColorSlider extends View {
        interface Listener { void onPick(int index, boolean done); }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int selected;
        private Listener listener;

        ColorSlider(Context c) { super(c); }

        void setListener(Listener l) { listener = l; }

        void setSelected(int i) {
            selected = i;
            invalidate();
        }

        private float pad() { return Mib.px(getContext(), 8); }

        private int indexAt(float x) {
            int n = Mib.AMBIENT.length;
            float cw = (getWidth() - 2 * pad()) / n;
            return Math.max(0, Math.min(n - 1, (int) ((x - pad()) / cw)));
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = Mib.AMBIENT.length;
            float w = getWidth(), h = getHeight(), pad = pad();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xE6000000);
            r.set(0, 0, w, h);
            c.drawRect(r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, Mib.px(getContext(), 1.6f)));
            p.setColor(0xFFD6D6DC);
            c.drawRect(r, p);
            float cw = (w - 2 * pad) / n, top = pad * 1.4f, bottom = h - pad * 1.4f, gap = Math.max(1, cw * 0.12f);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                p.setColor((Integer) Mib.AMBIENT[i][2]);
                c.drawRect(pad + i * cw + gap / 2, top, pad + (i + 1) * cw - gap / 2, bottom, p);
            }
            // Selected cell: taller, framed in white.
            float x0 = pad + selected * cw - gap, x1 = pad + (selected + 1) * cw + gap;
            p.setColor((Integer) Mib.AMBIENT[selected][2]);
            c.drawRect(x0, pad * 0.5f, x1, h - pad * 0.5f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, Mib.px(getContext(), 2.5f)));
            p.setColor(0xFFFFFFFF);
            c.drawRect(x0, pad * 0.5f, x1, h - pad * 0.5f, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int i = indexAt(e.getX());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                case MotionEvent.ACTION_MOVE:
                    if (i != selected && listener != null) listener.onPick(i, false);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (listener != null) listener.onPick(i, true);
                    return true;
                default:
                    return true;
            }
        }
    }

    /** Brightness glyph for the key next to the slider. */
    private static final class Sun extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), s = b.width();
            p.setColor(Mib.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.07f);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(cx, cy, s * 0.2f, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(cx + (float) Math.cos(a) * s * 0.32f, cy + (float) Math.sin(a) * s * 0.32f,
                        cx + (float) Math.cos(a) * s * 0.45f, cy + (float) Math.sin(a) * s * 0.45f, p);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
