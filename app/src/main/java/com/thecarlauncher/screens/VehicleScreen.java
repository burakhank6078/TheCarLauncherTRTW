package com.thecarlauncher.screens;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.car.CarData;
import com.thecarlauncher.car.ObdData;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPopup;
import com.thecarlauncher.nav.LocationTracker;
import com.thecarlauncher.nav.TripComputer;
import com.thecarlauncher.util.Prefs;
import com.thecarlauncher.util.Ui;

import java.util.List;
import java.util.Locale;

/**
 * MIB "CAR": paged with the < > boxes like the factory menu —
 * Status vozila, Od starta, Dugoročno, Od dopune, Gorivo, Servis.
 * Everything comes from GPS and what the driver enters (no diagnostic adapter).
 */
public class VehicleScreen extends Screen implements LocationTracker.Listener {
    private static final int STATUS = 0, TYRES = 1, START = 2, LONG = 3, REFUEL = 4, FUEL = 5, SERVICE = 6, PAGES = 7;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView title;
    private FrameLayout body;
    private MibBar.Key izbor, actionKey;
    private int page;
    /** Value cells of the data pages, refreshed every second. */
    private TextView[] cells = new TextView[6];
    /** Values of the OBD-style trip layout (distance, time, speed, consumption, range), or null. */
    private TextView[] trip;
    private int tripPage;
    /** The page area (behind title and arrows) and the full-page background picture of the trip pages. */
    private FrameLayout pageRoot;
    private ImageView tripBg;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            main.postDelayed(this, 1000);
        }
    };

    public VehicleScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Vozilo", "Vehicle"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    private String pageTitle(int p) {
        switch (p) {
            case STATUS: return s("Status vozila", "Vehicle status");
            case START: return s("Od starta", "Since start");
            case LONG: return s("Dugoročno", "Long-term");
            case REFUEL: return s("Od dopune", "Since refuelling");
            case FUEL: return s("Gorivo", "Fuel");
            case TYRES: return s("Pritisak u gumama", "Tyre pressure");
            default: return s("Servis", "Service");
        }
    }

    @Override
    protected View create(ViewGroup parent) {
        TripComputer.get(act);
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        FrameLayout content = new FrameLayout(act);
        pageRoot = content;
        r.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int arrow = Mib.px(act, 76), side = Mib.px(act, 18), top = Mib.px(act, 14);
        body = new FrameLayout(act);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        bl.topMargin = top + arrow + Mib.px(act, 6);
        content.addView(body, bl);

        content.addView(arrowBox(R.drawable.ui_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage((page + PAGES - 1) % PAGES);
            }
        }), boxParams(arrow, side, top, Gravity.LEFT));
        content.addView(arrowBox(R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage((page + 1) % PAGES);
            }
        }), boxParams(arrow, side, top, Gravity.RIGHT));
        title = Mib.text(act, "", 40, Mib.regular(act), Mib.WHITE);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, arrow);
        tl.topMargin = top;
        tl.leftMargin = tl.rightMargin = arrow + side * 2;
        content.addView(title, tl);

        MibBar bar = new MibBar(act);
        izbor = bar.add(s("Izbor", "Select"), R.drawable.ui_ic_car_s, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPages(v);
            }
        });
        bar.add(s("Mediji", "Media"), R.drawable.ui_key_music, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.MEDIA, true);
            }
        });
        actionKey = bar.add("", R.drawable.ui_ic_refresh, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAction();
            }
        });
        bar.gap(3);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage(SERVICE);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        setPage(STATUS);
        return r;
    }

    private View arrowBox(int icon, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Mib.box(act));
        b.addView(Mib.icon(act, icon, Mib.WHITE), new FrameLayout.LayoutParams(Mib.px(act, 54), Mib.px(act, 36), Gravity.CENTER));
        b.setOnClickListener(l);
        return b;
    }

    private FrameLayout.LayoutParams boxParams(int size, int side, int top, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(size, size, Gravity.TOP | gravity);
        p.topMargin = top;
        p.leftMargin = p.rightMargin = side;
        return p;
    }

    private void showPages(View key) {
        MibPopup p = new MibPopup(act).columns(1);
        int[] icons = {R.drawable.ui_ic_car_s, R.drawable.ui_ic_car_s, R.drawable.ui_ic_clock, R.drawable.ui_ic_route,
                R.drawable.ui_ic_speedo, R.drawable.ui_ic_speedo, R.drawable.ui_ic_eq};
        for (int i = 0; i < PAGES; i++) {
            final int idx = i;
            p.add(pageTitle(i), icons[i], i == page, new Runnable() {
                @Override
                public void run() {
                    setPage(idx);
                }
            });
        }
        // Shortcut to the Ambient lighting screen (colours and level, also sent to the BLE strip).
        p.add(s("Ambijentalno osvetljenje", "Ambient lighting"), R.drawable.ui_ic_eq, false, new Runnable() {
            @Override
            public void run() {
                act.show(LauncherActivity.AMBIENT, true);
            }
        });
        p.show(act.getWindow().getDecorView(), key);
    }

    private void setPage(int p) {
        page = p;
        title.setText(pageTitle(p));
        body.removeAllViews();
        cells = new TextView[6];
        trip = null;
        if (tripBg != null) {
            pageRoot.removeView(tripBg);
            tripBg = null;
        }
        boolean obd = Prefs.bool(act, Prefs.TRIP_OBD, false);
        if (obd && (p == START || p == LONG || p == REFUEL)) buildTrip(p);
        else switch (p) {
            case STATUS: buildStatus(); break;
            case START:
                buildCells(new int[]{R.drawable.ui_ic_clock, R.drawable.ui_ic_route, R.drawable.ui_ic_speedo,
                                R.drawable.ui_ic_car_s, R.drawable.ui_ic_compass, R.drawable.ui_ic_mountain},
                        new String[]{s("Vreme vožnje", "Driving time"), s("Pređeni put", "Distance"),
                                s("Prosečna brzina", "Average speed"), s("Trenutna brzina", "Current speed"),
                                s("Smer", "Heading"), s("Nadmorska visina", "Altitude")});
                break;
            case LONG:
                buildCells(new int[]{R.drawable.ui_ic_clock, R.drawable.ui_ic_route, R.drawable.ui_ic_speedo,
                                R.drawable.ui_ic_car_s},
                        new String[]{s("Vreme vožnje", "Driving time"), s("Pređeni put", "Distance"),
                                s("Prosečna brzina", "Average speed"), s("Kilometraža", "Odometer")});
                break;
            case REFUEL:
                buildCells(new int[]{R.drawable.ui_ic_route, R.drawable.ui_ic_speedo, R.drawable.ui_ic_nav,
                                R.drawable.ui_ic_clock, R.drawable.ui_ic_eq, R.drawable.ui_ic_speedo},
                        new String[]{s("Pređeni put", "Distance"), s("Prosečna potrošnja", "Avg. consumption"),
                                s("Domet", "Range"), s("Poslednje točenje", "Last fill-up"),
                                s("Cena na 100 km", "Cost per 100 km"), s("Prosečna cena goriva", "Avg. fuel price")});
                break;
            case FUEL: buildFuel(); break;
            case TYRES: buildTyres(); break;
            default: buildService(); break;
        }
        boolean action = p == FUEL || (!obd && (p == START || p == LONG || p == REFUEL));
        actionKey.setVisibility(action ? View.VISIBLE : View.INVISIBLE);
        if (p == START || p == LONG) actionKey.setLabel(s("Resetuj", "Reset")).setIcon(R.drawable.ui_ic_refresh);
        else actionKey.setLabel(s("Točenje", "Fill-up")).setIcon(R.drawable.ui_ic_plus);
        update();
    }

    private void onAction() {
        if (page == START) {
            TripComputer.get(act).reset();
        } else if (page == LONG) {
            TripComputer.get(act).resetLongTerm();
        } else {
            addRefuel();
            return;
        }
        update();
    }

    // ---- Status vozila -----------------------------------------------------------------------

    private void buildStatus() {
        final ImageView car = new ImageView(act);
        car.setImageBitmap(com.thecarlauncher.util.CarImages.get(act, com.thecarlauncher.util.CarImages.EXTERIOR));
        car.setScaleType(ImageView.ScaleType.FIT_END);
        car.setAdjustViewBounds(false);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        body.addView(car, cl);
        // Size the picture after layout: right half of the page, bottom aligned like the factory one.
        body.post(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) car.getLayoutParams();
                lp.width = (int) (body.getWidth() * 0.6f);
                lp.height = body.getHeight() + Mib.px(act, 40);
                lp.rightMargin = Mib.px(act, 10);
                lp.topMargin = -Mib.px(act, 40);
                car.setLayoutParams(lp);
            }
        });

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 44), Mib.px(act, 6), 0, 0);
        List<CarData.Notice> notices = CarData.get(act).notices();
        if (notices.isEmpty()) {
            col.addView(statusLine(new Mark(Mark.OK), s("Ne postoje unosi.", "No entries.")));
        } else {
            for (CarData.Notice n : notices) col.addView(statusLine(new Mark(n.urgent ? Mark.ALERT : Mark.WARN), n.text));
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(Mib.px(act, 470),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        body.addView(col, ll);

        // Framed button like the factory "Start-stop" one.
        LinearLayout btn = new LinearLayout(act);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setBackground(Mib.box(act));
        btn.setPadding(Mib.px(act, 14), 0, Mib.px(act, 30), 0);
        btn.addView(Mib.icon(act, R.drawable.ui_ic_eq, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
        TextView bt = Mib.text(act, s("Servis", "Service"), 34, Mib.regular(act), Mib.WHITE);
        bt.setPadding(Mib.px(act, 14), 0, 0, 0);
        btn.addView(bt);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPage(SERVICE);
            }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Mib.px(act, 320), Mib.px(act, 60),
                Gravity.BOTTOM | Gravity.LEFT);
        bl.leftMargin = Mib.px(act, 30);
        bl.bottomMargin = Mib.px(act, 34);
        body.addView(btn, bl);
    }

    // ---- Pritisak u gumama -------------------------------------------------------------------

    /**
     * Tyre pressure page: the car picture on the right (the one chosen under Setup > Vehicle), a hint
     * and one button on the left that opens the unit's own car settings, where the tyre pressure
     * monitor is reset. No pressure is shown and nothing is sent to the car from here.
     */
    private void buildTyres() {
        final ImageView car = new ImageView(act);
        car.setImageBitmap(com.thecarlauncher.util.CarImages.get(act, com.thecarlauncher.util.CarImages.TYRES));
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(car, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.BOTTOM));
        body.post(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) car.getLayoutParams();
                lp.width = (int) (body.getWidth() * 0.5f);
                lp.rightMargin = Mib.px(act, 30);
                lp.bottomMargin = Mib.px(act, 20);
                car.setLayoutParams(lp);
            }
        });

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 44), Mib.px(act, 10), 0, 0);
        TextView hint = Mib.text(act, s("Nakon podešavanja pritiska pritisnite Resetuj pritisak u gumama.",
                "After adjusting the pressure, press Reset tyre pressure."), 31, Mib.regular(act), Mib.WHITE);
        hint.setSingleLine(false);
        col.addView(hint);
        body.addView(col, new FrameLayout.LayoutParams(Mib.px(act, 470), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Framed button like the factory one on the status page.
        LinearLayout btn = new LinearLayout(act);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setBackground(Mib.box(act));
        btn.setPadding(Mib.px(act, 14), 0, Mib.px(act, 30), 0);
        btn.addView(Mib.icon(act, R.drawable.ui_ic_car_s, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
        TextView bt = Mib.text(act, s("Podešavanja guma", "Tyre pressure settings"), 30, Mib.regular(act), Mib.WHITE);
        bt.setPadding(Mib.px(act, 14), 0, 0, 0);
        btn.addView(bt);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!com.thecarlauncher.util.FactoryCar.open(act)) {
                    Ui.toast(act, s("Aplikacija nije pronađena", "App not found"));
                }
            }
        });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Mib.px(act, 440), Mib.px(act, 60),
                Gravity.BOTTOM | Gravity.LEFT);
        bl.leftMargin = Mib.px(act, 30);
        bl.bottomMargin = Mib.px(act, 34);
        body.addView(btn, bl);

        // Reset button: sends the tyre pressure monitor reset the unit's own car app sends (frame 75),
        // after a confirmation, and only while "Take trip data from OBD" is on in Setup > Vehicle.
        LinearLayout reset = new LinearLayout(act);
        reset.setGravity(Gravity.CENTER_VERTICAL);
        reset.setBackground(Mib.box(act));
        reset.setPadding(Mib.px(act, 14), 0, Mib.px(act, 30), 0);
        reset.addView(Mib.icon(act, R.drawable.ui_ic_refresh, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
        TextView rt = Mib.text(act, s("Resetuj pritisak u gumama", "Reset tyre pressure"), 30, Mib.regular(act), Mib.WHITE);
        rt.setPadding(Mib.px(act, 14), 0, 0, 0);
        reset.addView(rt);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmTyreReset();
            }
        });
        FrameLayout.LayoutParams rl2 = new FrameLayout.LayoutParams(Mib.px(act, 440), Mib.px(act, 60),
                Gravity.BOTTOM | Gravity.LEFT);
        rl2.leftMargin = Mib.px(act, 30);
        rl2.bottomMargin = Mib.px(act, 34 + 60 + 14);
        body.addView(reset, rl2);
    }

    // ---- Trip pages in the original MIB 2 layout (OBD data) ------------------------------------

    private TextView tripText(LinearLayout parent) {
        TextView t = Mib.text(act, "", 36, Mib.regular(act), Mib.WHITE);
        t.setPadding(0, Mib.px(act, 6), 0, Mib.px(act, 6));
        parent.addView(t);
        return t;
    }

    /**
     * Like the factory page: distance and time on the left, average speed and consumption on the
     * right and the range with a fuel pump at the bottom right, on the amoled black page. The
     * chosen "Fuel consumption picture" is the whole page background, also behind the title and arrows (car, road and lines drawn in
     * the picture itself); with no picture chosen the page stays plain black.
     * The values come from {@link ObdData}; "---" shows until they are known.
     */
    private void buildTrip(int p) {
        tripPage = p;
        trip = new TextView[5];
        if (!com.thecarlauncher.util.CarImages.NONE.equals(
                com.thecarlauncher.util.CarImages.mode(act, com.thecarlauncher.util.CarImages.TRIPS))) {
            tripBg = new ImageView(act);
            tripBg.setImageBitmap(com.thecarlauncher.util.CarImages.get(act, com.thecarlauncher.util.CarImages.TRIPS));
            tripBg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pageRoot.addView(tripBg, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Left values end at about 30 % of the width, right values start at 50 % (as on the factory page).
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setWeightSum(1f);
        top.setPadding(0, Mib.px(act, 10), 0, 0);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.RIGHT);
        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        trip[0] = tripText(left);
        trip[1] = tripText(left);
        trip[2] = tripText(right);
        trip[3] = tripText(right);
        for (int i = 0; i < 2; i++) trip[i].setGravity(Gravity.RIGHT);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.30f));
        top.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 0.20f));
        top.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.50f));
        body.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout range = new LinearLayout(act);
        range.setGravity(Gravity.CENTER_VERTICAL);
        trip[4] = Mib.text(act, "", 36, Mib.regular(act), Mib.WHITE);
        range.addView(trip[4]);
        ImageView pump = new ImageView(act);
        pump.setImageDrawable(new FuelIcon());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44));
        pl.leftMargin = Mib.px(act, 22);
        range.addView(pump, pl);
        FrameLayout.LayoutParams rg = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        rg.rightMargin = Mib.px(act, 44);
        rg.bottomMargin = Mib.px(act, 34);
        body.addView(range, rg);
    }

    private void updateTrip() {
        int src = tripPage == START ? ObdData.START : tripPage == REFUEL ? ObdData.REFUEL : ObdData.LONG;
        ObdData d = ObdData.get();
        boolean metric = Prefs.metric(act);
        double f = metric ? 1 : 0.621371;
        String du = metric ? "km" : "mi", su = LocationTracker.unit(act);
        double dist = d.get(src, ObdData.DIST), ms = d.get(src, ObdData.TIME), spd = d.get(src, ObdData.SPEED);
        double cons = d.get(src, ObdData.CONS), range = d.get(src, ObdData.RANGE);
        trip[0].setText(Double.isNaN(dist) ? "--- " + du : String.format(Locale.US, "%.0f %s", dist * f, du));
        trip[1].setText(Double.isNaN(ms) ? "--:-- h" : hm((long) ms));
        trip[2].setText("Ø " + (Double.isNaN(spd) ? "---" : String.format(Locale.US, "%.0f", spd * f)) + " " + su);
        trip[3].setText("Ø " + (Double.isNaN(cons) ? "--.-" : String.format(Locale.US, "%.1f", cons)) + " l/100km");
        trip[4].setText(Double.isNaN(range) ? "--- " + du : String.format(Locale.US, "%.0f %s", range * f, du));
    }

    /** A small white fuel pump, drawn crisp at any size. */
    private static final class FuelIcon extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        FuelIcon() {
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float w = b.width(), h = b.height();
            p.setStrokeWidth(Math.max(2f, w * 0.07f));
            float l = b.left + w * 0.12f, t = b.top + h * 0.08f, r = b.left + w * 0.58f, bt = b.top + h * 0.92f;
            c.drawRect(l, t, r, bt, p);
            c.drawRect(l + w * 0.08f, t + h * 0.10f, r - w * 0.08f, t + h * 0.38f, p);
            Path hose = new Path();
            hose.moveTo(r, t + h * 0.26f);
            hose.lineTo(b.left + w * 0.84f, t + h * 0.26f);
            hose.lineTo(b.left + w * 0.84f, t + h * 0.66f);
            c.drawPath(hose, p);
            c.drawLine(l - w * 0.06f, bt, r + w * 0.06f, bt, p);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private long lastTyreReset;

    /** The reasons TwProbe gives are English; Tr.java has the Turkish ones, others show as they are. */
    private String reason(String r) {
        String answered = "TWUtil answered ";
        if (r.startsWith(answered)) return Mib.t(act, r, r, "TWUtil şu yanıtı verdi: " + r.substring(answered.length()));
        return Mib.t(act, r, r);
    }

    private void confirmTyreReset() {
        if (!Prefs.bool(act, Prefs.TW_COMMANDS, false)) {
            Ui.toast(act, s("Uključite \"Dozvoli slanje komandi jedinici\" u Podešavanja → Vozilo",
                    "Switch on \"Allow sending commands to the unit\" in Setup → Vehicle"));
            return;
        }
        Dialogs.choose(act, s("Resetovati pritisak u gumama? Pritisak mora biti podešen.",
                "Reset the tyre pressure monitor? The pressure must be set."),
                new String[]{s("Resetuj", "Reset"), s("Otkaži", "Cancel")}, -1, new Dialogs.Chosen() {
                    public void onChosen(int i) {
                        if (i != 0) return;
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - lastTyreReset < 10000) return; // never twice in a row
                        lastTyreReset = now;
                        // Same message as the factory car app: frame 75, bytes (1, 1).
                        com.thecarlauncher.car.TwProbe.get().writeAsync(act, 1281, 75, 2, new byte[]{1, 1},
                                new com.thecarlauncher.car.TwProbe.Result() {
                                    public void done(boolean ok, String error) {
                                        Ui.toast(act, ok ? s("Komanda poslata", "Command sent")
                                                : s("Slanje nije uspelo", "Could not send") + ": " + reason(error));
                                    }
                                });
                    }
                });
    }

    private View statusLine(Drawable mark, String text) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Mib.px(act, 6), 0, Mib.px(act, 6));
        ImageView iv = new ImageView(act);
        iv.setImageDrawable(mark);
        row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 34), Mib.px(act, 34)));
        TextView t = Mib.text(act, text, 31, Mib.regular(act), Mib.WHITE);
        t.setSingleLine(false);
        t.setPadding(Mib.px(act, 22), 0, 0, 0);
        row.addView(t);
        return row;
    }

    /** Green tick / amber or red warning triangle, drawn crisp at any size. */
    private static final class Mark extends Drawable {
        static final int OK = 0, WARN = 1, ALERT = 2;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Mark(int type) { this.type = type; }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            c.save();
            c.translate(getBounds().left, getBounds().top);
            Path path = new Path();
            if (type == OK) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.14f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(0xFF3CC24A);
                path.moveTo(w * 0.12f, h * 0.55f);
                path.lineTo(w * 0.40f, h * 0.82f);
                path.lineTo(w * 0.88f, h * 0.18f);
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(type == ALERT ? 0xFFE23B2E : 0xFFF2B01E);
                path.moveTo(w * 0.5f, h * 0.06f);
                path.lineTo(w * 0.96f, h * 0.92f);
                path.lineTo(w * 0.04f, h * 0.92f);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFF000000);
                c.drawRect(w * 0.45f, h * 0.34f, w * 0.55f, h * 0.66f, p);
                c.drawCircle(w * 0.5f, h * 0.78f, w * 0.06f, p);
            }
            c.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- Data cells (Od starta / Dugoročno / Od dopune) --------------------------------------

    private void buildCells(int[] icons, String[] labels) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 44), 0, Mib.px(act, 44), Mib.px(act, 14));
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 3 == 0) {
                if (row != null) {
                    View mid = new View(act);
                    mid.setBackgroundColor(0xFF4C4D57);
                    col.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
                }
                row = new LinearLayout(act);
                row.setGravity(Gravity.CENTER_VERTICAL);
                col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }
            cells[i] = cell(row, icons[i], labels[i]);
        }
        if (labels.length % 3 != 0) {
            for (int i = labels.length % 3; i < 3; i++) row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        body.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView cell(LinearLayout row, int icon, String label) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        LinearLayout cap = new LinearLayout(act);
        cap.setGravity(Gravity.CENTER);
        cap.addView(Mib.icon(act, icon, Mib.GREY), new LinearLayout.LayoutParams(Mib.px(act, 32), Mib.px(act, 32)));
        TextView l = Mib.text(act, label, 24, Mib.regular(act), Mib.GREY);
        l.setPadding(Mib.px(act, 8), 0, 0, 0);
        cap.addView(l);
        c.addView(cap);
        TextView v = Mib.text(act, "–", 50, Mib.semibold(act), Mib.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, Mib.px(act, 8), 0, 0);
        c.addView(v);
        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    // ---- Gorivo (fuel log) / Servis lists ----------------------------------------------------

    private LinearLayout list() {
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(true);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Mib.px(act, 40), 0, Mib.px(act, 40), Mib.px(act, 10));
        sv.addView(col);
        body.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        col.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));
        return col;
    }

    private TextView row(LinearLayout col, int icon, String label, String value, final Runnable click) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Mib.px(act, 10), 0, Mib.px(act, 10), 0);
        row.setMinimumHeight(Mib.px(act, 70));
        if (click != null) {
            row.setBackground(Mib.keyPressed());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    click.run();
                }
            });
        }
        if (icon != 0) row.addView(Mib.icon(act, icon, Mib.WHITE), new LinearLayout.LayoutParams(Mib.px(act, 34), Mib.px(act, 34)));
        TextView t = Mib.text(act, label, 28, Mib.regular(act), Mib.WHITE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = Mib.px(act, icon != 0 ? 18 : 0);
        row.addView(t, tl);
        TextView v = Mib.text(act, value == null ? "" : value, 26, Mib.regular(act), Mib.GREY);
        row.addView(v);
        col.addView(row);
        View div = new View(act);
        div.setBackgroundColor(0xFF55565F);
        col.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(act, 1))));
        return v;
    }

    private void checkRow(LinearLayout col, String label, boolean on, Runnable click) {
        TextView v = row(col, 0, label, null, click);
        LinearLayout row = (LinearLayout) v.getParent();
        ImageView box = Mib.icon(act, on ? R.drawable.ui_ic_check_on : R.drawable.ui_ic_check_off, Mib.WHITE);
        row.addView(box, new LinearLayout.LayoutParams(Mib.px(act, 42), Mib.px(act, 42)));
    }

    private String km(double v) {
        return v < 0 ? "–" : String.format(Locale.US, "%,.0f km", v).replace(',', '.');
    }

    private void buildFuel() {
        final CarData cd = CarData.get(act);
        LinearLayout col = list();
        row(col, R.drawable.ui_ic_plus, s("Dodaj točenje", "Add fill-up"), null, new Runnable() {
            public void run() { addRefuel(); }
        });
        double avg = cd.averageConsumption();
        row(col, R.drawable.ui_ic_speedo, s("Prosečna potrošnja", "Average consumption"),
                avg < 0 ? s("potrebna 2 puna rezervoara", "needs 2 full tanks") : String.format(Locale.US, "%.1f l/100 km", avg), null);
        List<CarData.Refuel> l = cd.refuels();
        for (int i = l.size() - 1; i >= 0; i--) {
            CarData.Refuel r = l.get(i);
            String v = String.format(Locale.US, "%.1f l", r.litres) + (r.price > 0 ? String.format(Locale.US, "  ·  %.2f din/l", r.price) : "")
                    + "  ·  " + km(r.odoKm);
            row(col, R.drawable.ui_ic_clock, CarData.date(r.time) + (r.full ? "" : s("  (delimično)", "  (partial)")), v, null);
        }
        if (!l.isEmpty()) {
            row(col, R.drawable.ui_ic_back, s("Obriši poslednje točenje", "Delete last fill-up"), null, new Runnable() {
                public void run() {
                    cd.removeLastRefuel();
                    setPage(FUEL);
                }
            });
        }
    }

    private void addRefuel() {
        final CarData cd = CarData.get(act);
        double last = cd.averagePrice();
        Dialogs.refuel(act, s("Točenje goriva", "Fill-up"), s("Litara", "Litres"), s("Cena po litru", "Price per litre"),
                s("Pun rezervoar", "Full tank"), last > 0 ? String.format(Locale.US, "%.2f", last) : null,
                new Dialogs.RefuelEntered() {
                    public void onRefuel(double litres, double price, boolean full) {
                        cd.addRefuel(litres, price, full);
                        Ui.toast(act, s("Točenje sačuvano", "Fill-up saved"));
                        setPage(page);
                    }
                });
    }

    private void buildService() {
        final CarData cd = CarData.get(act);
        LinearLayout col = list();
        row(col, R.drawable.ui_ic_car_s, s("Kilometraža", "Odometer"),
                cd.hasOdometer() ? km(cd.odometer()) : s("unesite", "enter"), new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Trenutna kilometraža (km)", "Current odometer (km)"), "150000",
                                cd.hasOdometer() ? String.valueOf(Math.round(cd.odometer())) : null, new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        cd.setOdometer(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.ui_ic_eq, s("Sledeća zamena ulja", "Next oil change"),
                cd.oilDueKm() > 0 ? km(cd.oilDueKm()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Zamena ulja na (km)", "Oil change at (km)"), "165000",
                                cd.oilDueKm() > 0 ? String.valueOf(Math.round(cd.oilDueKm())) : null, new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        cd.setOilDueKm(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.ui_ic_clock, s("Godišnji servis", "Annual service"),
                cd.oilDueDate() > 0 ? CarData.date(cd.oilDueDate()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.date(act, cd.oilDueDate(), new Dialogs.DatePicked() {
                            public void onDate(long t) {
                                cd.setOilDueDate(t);
                                setPage(SERVICE);
                            }
                        });
                    }
                });
        row(col, R.drawable.ui_ic_refresh, s("Servis obavljen danas", "Service done today"),
                s("+15.000 km / +1 god.", "+15,000 km / +1 yr"), new Runnable() {
                    public void run() {
                        cd.oilServiceDone();
                        Ui.toast(act, s("Sledeći servis zakazan", "Next service scheduled"));
                        setPage(SERVICE);
                    }
                });
        row(col, R.drawable.ui_ic_flag, s("Registracija ističe", "Registration expires"),
                cd.registrationDate() > 0 ? CarData.date(cd.registrationDate()) : "–", new Runnable() {
                    public void run() {
                        Dialogs.date(act, cd.registrationDate(), new Dialogs.DatePicked() {
                            public void onDate(long t) {
                                cd.setRegistrationDate(t);
                                setPage(SERVICE);
                            }
                        });
                    }
                });
        checkRow(col, s("Zimske gume su montirane", "Winter tyres fitted"), cd.winterTyres(), new Runnable() {
            public void run() {
                cd.setWinterTyres(!cd.winterTyres());
                setPage(SERVICE);
            }
        });
        checkRow(col, s("Podsetnik za zimske gume (1.11. – 1.4.)", "Winter tyre reminder (1 Nov – 1 Apr)"), cd.tyreReminder(),
                new Runnable() {
                    public void run() {
                        cd.setTyreReminder(!cd.tyreReminder());
                        setPage(SERVICE);
                    }
                });
        row(col, R.drawable.ui_ic_speedo, s("Zapremina rezervoara", "Fuel tank"),
                String.format(Locale.US, "%.0f l", cd.tank()), new Runnable() {
                    public void run() {
                        Dialogs.number(act, s("Zapremina rezervoara (l)", "Fuel tank (l)"), "44",
                                String.format(Locale.US, "%.0f", cd.tank()), new Dialogs.NumberEntered() {
                                    public void onNumber(double v) {
                                        if (v > 5) cd.setTank(v);
                                        setPage(SERVICE);
                                    }
                                });
                    }
                });
        row(col, R.drawable.ui_ic_car_s, s("Vozilo i slike", "Vehicle and pictures"), Prefs.vehicle(act), new Runnable() {
            public void run() { act.showSettings(SettingsScreen.PAGE_VEHICLE); }
        });
    }

    // ---- Live values -------------------------------------------------------------------------

    @Override
    public void onShow() {
        LocationTracker.get(act).addListener(this);
        setPage(STATUS); // the car key always opens the status page first
        main.post(ticker);
    }

    @Override
    public void onHide() {
        LocationTracker.get(act).removeListener(this);
        main.removeCallbacks(ticker);
    }

    @Override
    public boolean onBack() {
        if (page == STATUS) return false;
        setPage(STATUS);
        return true;
    }

    @Override
    public void onLocationUpdate(LocationTracker t) {
        if (page != START || cells[3] == null) return;
        String unit = LocationTracker.unit(act);
        cells[3].setText(Math.round(t.speed(act)) + " " + unit);
        cells[4].setText(t.hasFix() ? LocationTracker.heading(t.bearing()) + String.format(Locale.US, "  %.0f°", t.bearing()) : "–");
        Location l = t.location();
        if (l != null && l.hasAltitude() && t.hasFix()) {
            cells[5].setText(Prefs.metric(act) ? String.format(Locale.US, "%.0f m", l.getAltitude())
                    : String.format(Locale.US, "%.0f ft", l.getAltitude() * 3.28084));
        } else {
            cells[5].setText("–");
        }
    }

    private static String hm(long ms) {
        long m = ms / 60000;
        return String.format(Locale.US, "%d:%02d h", m / 60, m % 60);
    }

    private void update() {
        if (trip != null) {
            updateTrip();
            return;
        }
        if (cells[0] == null) return;
        TripComputer tc = TripComputer.get(act);
        boolean metric = Prefs.metric(act);
        String du = metric ? "km" : "mi";
        double f = metric ? 1 : 0.621371;
        if (page == START) {
            cells[0].setText(hm(tc.elapsedMs()));
            cells[1].setText(String.format(Locale.US, "%.1f %s", tc.distance(), du));
            cells[2].setText(String.format(Locale.US, "%.0f %s", tc.averageSpeed(), LocationTracker.unit(act)));
        } else if (page == LONG) {
            cells[0].setText(hm(tc.longTermMs()));
            cells[1].setText(String.format(Locale.US, "%.0f %s", tc.longTermKm() * f, du));
            double h = tc.longTermMs() / 3600000.0;
            cells[2].setText(h < 0.01 ? "–" : String.format(Locale.US, "%.0f %s", tc.longTermKm() * f / h, LocationTracker.unit(act)));
            CarData cd = CarData.get(act);
            cells[3].setText(cd.hasOdometer() ? km(cd.odometer()) : "–");
        } else if (page == REFUEL) {
            CarData cd = CarData.get(act);
            double since = cd.kmSinceRefuel(), avg = cd.averageConsumption(), range = cd.range(), price = cd.averagePrice();
            cells[0].setText(since < 0 ? "–" : String.format(Locale.US, "%.0f km", since));
            cells[1].setText(avg < 0 ? "–" : String.format(Locale.US, "%.1f l/100", avg));
            cells[2].setText(range < 0 ? "–" : String.format(Locale.US, "%.0f km", range));
            List<CarData.Refuel> l = cd.refuels();
            cells[3].setText(l.isEmpty() ? "–" : CarData.date(l.get(l.size() - 1).time));
            cells[4].setText(avg < 0 || price < 0 ? "–" : String.format(Locale.US, "%.0f din", avg * price));
            cells[5].setText(price < 0 ? "–" : String.format(Locale.US, "%.1f din", price));
        }
    }
}
