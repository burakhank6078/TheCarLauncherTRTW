package com.thecarlauncher.screens;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibPager;
import com.thecarlauncher.radio.FmScaleView;
import com.thecarlauncher.radio.RadioController;
import com.thecarlauncher.util.Ui;

/**
 * MIB "Radio" (FM): big frequency with seek boxes, three pages of six presets with station
 * names, "Lista stan." station list and "Ručno" manual tuning on the factory dial scale.
 */
public class RadioScreen extends Screen implements RadioController.Listener, com.thecarlauncher.radio.RadioLogos.Listener {
    private static final int PER_PAGE = 6;

    private RadioController radio;
    private TextView freq, unit, info, message, listTitle;
    private View presetsArea, manualArea, listArea, main;
    private MibPager pager;
    private LinearLayout dots;
    private FmScaleView scale;
    private ListView list;
    private MibBar.Key listKey, manualKey;
    private final Preset[] presets = new Preset[RadioController.PRESETS];
    private final StationAdapter adapter = new StationAdapter();
    private boolean manual;
    private int lastTile = -2;
    private Boolean lastAvail;

    public RadioScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return "Radio"; }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        radio = RadioController.get(act);
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        FrameLayout content = new FrameLayout(act);
        r.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Main view: frequency row + presets / manual scale ----
        FrameLayout m = new FrameLayout(act);
        main = m;
        content.addView(m, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int arrow = Mib.px(act, 76), side = Mib.px(act, 18), top = Mib.px(act, 14);
        m.addView(arrowBox(R.drawable.ui_key_prev, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (manual) radio.stepDown();
                else radio.seekDown();
            }
        }), boxParams(arrow, side, top, Gravity.LEFT));
        m.addView(arrowBox(R.drawable.ui_key_next, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (manual) radio.stepUp();
                else radio.seekUp();
            }
        }), boxParams(arrow, side, top, Gravity.RIGHT));

        LinearLayout fr = new LinearLayout(act);
        fr.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        freq = Mib.text(act, "87.5", 50, Mib.regular(act), Mib.WHITE);
        unit = Mib.text(act, " MHz", 29, Mib.semibold(act), Mib.WHITE);
        unit.setPadding(0, 0, 0, Mib.px(act, 6));
        fr.addView(freq);
        fr.addView(unit);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, arrow);
        fl.topMargin = top;
        fl.leftMargin = fl.rightMargin = arrow + side * 2;
        fr.setGravity(Gravity.CENTER);
        m.addView(fr, fl);

        // RDS station name / radio text under the frequency.
        info = Mib.text(act, "", 24, Mib.regular(act), Mib.GREY);
        info.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        il.topMargin = top + arrow + Mib.px(act, 8);
        il.leftMargin = il.rightMargin = arrow + side * 2;
        m.addView(info, il);

        // Presets: three pages of six, dots underneath.
        FrameLayout pa = new FrameLayout(act);
        presetsArea = pa;
        pager = new MibPager(act);
        for (int p = 0; p < RadioController.PRESETS / PER_PAGE; p++) {
            LinearLayout row = new LinearLayout(act);
            row.setPadding(Mib.px(act, 14), 0, Mib.px(act, 14), 0);
            for (int i = 0; i < PER_PAGE; i++) {
                Preset t = new Preset(p * PER_PAGE + i);
                presets[p * PER_PAGE + i] = t;
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                tl.leftMargin = tl.rightMargin = Mib.px(act, 2);
                row.addView(t, tl);
            }
            pager.addView(row);
        }
        FrameLayout.LayoutParams pgl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 160));
        pa.addView(pager, pgl);
        dots = new LinearLayout(act);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < pager.getChildCount(); i++) {
            View d = new View(act);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Mib.px(act, 13), Mib.px(act, 13));
            lp.leftMargin = lp.rightMargin = Mib.px(act, 5);
            dots.addView(d, lp);
        }
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 20), Gravity.BOTTOM);
        pa.addView(dots, dl);
        pager.setListener(new MibPager.Listener() {
            @Override
            public void onPage(int page) {
                updateDots(page);
            }
        });
        updateDots(0);
        FrameLayout.LayoutParams pal = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 222), Gravity.BOTTOM);
        pal.bottomMargin = Mib.px(act, 22);
        m.addView(pa, pal);

        // Manual tuning on the factory FM scale.
        scale = new FmScaleView(act);
        scale.setPadding(Mib.px(act, 30), 0, Mib.px(act, 30), 0);
        scale.setListener(new FmScaleView.Listener() {
            @Override
            public void onTune(int f, boolean done) {
                freq.setText(RadioController.format(f));
                if (done) radio.tune(f);
            }
        });
        manualArea = scale;
        scale.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Mib.px(act, 150), Gravity.CENTER_VERTICAL);
        sl.topMargin = Mib.px(act, 50);
        m.addView(scale, sl);

        // Tuner missing (not a QuickFish/K706 unit, or the MCU service refused us).
        message = Mib.text(act, "", 30, Mib.regular(act), Mib.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setSingleLine(false);
        message.setBackgroundColor(0xFF000000);
        message.setVisibility(View.GONE);
        message.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.openRadioApp();
            }
        });
        content.addView(message, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Station list ("Lista stan.") ----
        LinearLayout la = new LinearLayout(act);
        la.setOrientation(LinearLayout.VERTICAL);
        la.setPadding(Mib.px(act, 40), Mib.px(act, 10), Mib.px(act, 40), 0);
        la.setBackgroundColor(0xFF000000);
        la.setVisibility(View.GONE);
        listArea = la;
        listTitle = Mib.text(act, "", 26, Mib.semibold(act), Mib.WHITE);
        listTitle.setPadding(Mib.px(act, 6), 0, 0, Mib.px(act, 8));
        la.addView(listTitle);
        View rule = new View(act);
        rule.setBackgroundColor(Mib.BLUE);
        la.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 2))));
        list = new ListView(act);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xFF55565F));
        list.setDividerHeight(Math.max(1, Mib.px(act, 1)));
        list.setSelector(Mib.keyPressed());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                if (position == 0) {
                    if (radio.scanning()) radio.stopScan();
                    else radio.startScan();
                } else {
                    radio.tune(radio.stations().get(position - 1).freq);
                }
            }
        });
        la.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(la, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Soft keys ----
        MibBar bar = new MibBar(act);
        listKey = bar.add(s("Lista stan.", "Stations"), R.drawable.ui_key_list, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showList(listArea.getVisibility() != View.VISIBLE);
            }
        });
        manualKey = bar.add(s("Ručno", "Manual"), R.drawable.ui_key_manual, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!manual && radio.unitTuner() && !radio.canTuneManually()) {
                    // say what is missing instead of opening a dial that cannot tune
                    Ui.toast(act, !radio.canControl()
                            ? s("Upravljanje: uključite komande u Podešavanja → Vozilo", "Control: allow commands in Setup → Vehicle")
                            : !radio.shown() ? s("Radio nije aktivan izvor (otvorite radio aplikaciju)", "Radio is not the active source (open the radio app)")
                            : radio.isAm() ? s("Ručno podešavanje je samo za FM", "Manual tuning is for FM only")
                            : s("Radio još nije spreman", "The radio is not ready yet"));
                    return;
                }
                showList(false);
                setManual(!manual);
            }
        });
        bar.gap(4);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showSettings(SettingsScreen.PAGE_RADIO);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }

    private View arrowBox(int icon, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(act);
        b.setBackground(Mib.box(act));
        ImageView iv = Mib.icon(act, icon, Mib.WHITE);
        b.addView(iv, new FrameLayout.LayoutParams(Mib.px(act, 54), Mib.px(act, 36), Gravity.CENTER));
        b.setOnClickListener(l);
        return b;
    }

    private FrameLayout.LayoutParams boxParams(int size, int side, int top, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(size, size, Gravity.TOP | gravity);
        p.topMargin = top;
        p.leftMargin = p.rightMargin = side;
        return p;
    }

    private void updateDots(int page) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == page ? Mib.BLUE : 0xFFE6E6EA);
            View d = dots.getChildAt(i);
            d.setBackground(g);
            d.setScaleX(i == page ? 1.2f : 0.85f);
            d.setScaleY(i == page ? 1.2f : 0.85f);
        }
    }

    private void setManual(boolean on) {
        manual = on;
        manualKey.setActive(on);
        presetsArea.setVisibility(on ? View.GONE : View.VISIBLE);
        manualArea.setVisibility(on ? View.VISIBLE : View.GONE);
        onRadioChanged();
    }

    private void showList(boolean on) {
        listArea.setVisibility(on ? View.VISIBLE : View.GONE);
        listKey.setActive(on);
        if (on) onRadioChanged();
    }

    @Override
    public void onShow() {
        lastAvail = null;
        checkTuner();
        if (radio.available()) radio.start();
        radio.addListener(this);
        com.thecarlauncher.radio.RadioLogos.get(act).addListener(this);
        onRadioChanged();
    }

    /**
     * Shows either the radio or the "tuner not available" page, from what the controller says now. On a TW unit the
     * channel opens in the background and may fail after the page is already up, so this also runs on every update.
     */
    private void checkTuner() {
        boolean ok = radio.available();
        if (lastAvail != null && lastAvail == ok) return;
        lastAvail = ok;
        if (ok) {
            message.setVisibility(View.GONE);
            // On a TW unit the factory radio app plays; the unit gives no station list, so only the manual dial stays
            // (it tunes with the frequency command of the factory app's own frequency bar).
            listKey.setVisibility(radio.unitTuner() ? View.INVISIBLE : View.VISIBLE);
            manualKey.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.VISIBLE);
            String app = LauncherActivity.radioPackage(act);
            String why = radio.error() == null ? s("isključen u Podešavanjima", "switched off in Setup") : radio.error();
            message.setText(Mib.t(act, "FM tjuner nije dostupan na ovom uređaju.\n(" + why + ")\n\n"
                            + (app != null ? "Dodirnite za radio aplikaciju." : "Radio aplikacija nije pronađena."),
                    "FM tuner is not available on this unit.\n(" + why + ")\n\n"
                            + (app != null ? "Tap to open the radio app." : "No radio app found."),
                    "FM tuner bu cihazda kullanılamıyor.\n(" + why + ")\n\n"
                            + (app != null ? "Radyo uygulamasını açmak için dokunun." : "Radyo uygulaması bulunamadı.")));
        }
    }

    @Override
    public void onHide() {
        radio.removeListener(this);
        com.thecarlauncher.radio.RadioLogos.get(act).removeListener(this);
    }

    @Override
    public void onLogo() { onRadioChanged(); }

    @Override
    public boolean onBack() {
        if (listArea.getVisibility() == View.VISIBLE) {
            showList(false);
            return true;
        }
        if (manual) {
            setManual(false);
            return true;
        }
        return false;
    }

    @Override
    public void onRadioChanged() {
        if (freq == null) return;
        if (radio.unitTuner()) checkTuner();
        if (!radio.unitTuner() || (!radio.isAm() && radio.freq() > 0)) scale.setFreq(radio.freq());
        if (radio.unitTuner() && radio.presetIndexOfCurrent() >= 0 && radio.presetIndexOfCurrent() != lastTile) {
            // The pages are the unit's 18 slots: follow the page of the preset the unit is on.
            lastTile = radio.presetIndexOfCurrent();
            int page = lastTile / PER_PAGE;
            if (page < pager.getChildCount() && pager.page() != page) pager.setPage(page);
        }
        String ps = radio.ps().trim(), rt = radio.rt();
        String mhz = radio.isAm() ? " kHz" : " MHz";
        // The station's own name (RDS) replaces the frequency at the top when it is sent.
        boolean named = radio.shown() && ps.length() > 0 && !manual;
        freq.setText(named ? ps : radio.freqText());
        unit.setText(mhz);
        unit.setVisibility(named ? View.GONE : View.VISIBLE);
        StringBuilder sb = new StringBuilder(named ? radio.freqText() + mhz : "");
        if (rt.length() > 0) sb.append(sb.length() > 0 ? "  ·  " : "").append(rt);
        if (radio.stereo()) sb.append(sb.length() > 0 ? "   " : "").append("ST");
        if (!radio.shown()) sb.setLength(0);
        if (radio.unitTuner() && radio.shown() && !radio.canControl()) {
            sb.append(sb.length() > 0 ? "   " : "")
                    .append(s("Upravljanje: uključite komande u Podešavanja → Vozilo", "Control: allow commands in Setup → Vehicle"));
        }
        if (radio.unitTuner() && !radio.shown()) {
            // The factory radio app owns the audio: say so instead of showing an old frequency as if it were playing, and
            // do not claim "not active" before the unit has said which source is.
            if (radio.sourceKnown()) sb.append(s("Radio nije aktivan izvor (otvorite radio aplikaciju)", "Radio is not the active source (open the radio app)"));
            else if (!radio.canControl()) sb.append(s("Upravljanje: uključite komande u Podešavanja → Vozilo", "Control: allow commands in Setup → Vehicle"));
            else sb.append(s("Čekam radio…", "Waiting for the radio…"));
        }
        info.setText(sb.toString());
        int cur = radio.presetIndexOfCurrent();
        for (int i = 0; i < presets.length; i++) presets[i].bind(radio.preset(i), i == cur);
        listTitle.setText(s("Lista stanica", "Station list") + (radio.scanning() ? "  —  " + s("Pretraga…", "Scanning…") : ""));
        adapter.notifyDataSetChanged();
    }

    // ---- Preset tile -------------------------------------------------------------------------

    /**
     * Preset tile shaded like the MENU tiles: preset number top-right, the station's logo in the
     * middle when known, an accent line under the tile and the station name beneath it.
     */
    private class Preset extends LinearLayout {
        final int index;
        final FrameLayout face;
        final TextView number, name;
        final ImageView logo;
        final View line;

        Preset(int idx) {
            super(act);
            index = idx;
            setOrientation(VERTICAL);
            face = new FrameLayout(act);
            face.setBackground(Mib.tile(act));
            logo = new ImageView(act);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams lgl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            lgl.leftMargin = lgl.rightMargin = Mib.px(act, 18);
            lgl.topMargin = Mib.px(act, 30);
            lgl.bottomMargin = Mib.px(act, 12);
            face.addView(logo, lgl);
            number = Mib.text(act, String.valueOf(idx % PER_PAGE + 1), 34, Mib.semibold(act), Mib.WHITE);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
            nl.rightMargin = Mib.px(act, 10);
            nl.topMargin = Mib.px(act, 2);
            face.addView(number, nl);
            addView(face, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(act, 110)));
            line = new View(act);
            line.setBackgroundColor(Mib.BLUE);
            addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(act, 3))));
            name = Mib.text(act, "", 24, Mib.semibold(act), Mib.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, Mib.px(act, 6), 0, 0);
            addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            face.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (radio.unitTuner() && !radio.canRecall(index)) {
                        // The unit recalls a slot of the band it is on, so the band has to be known first.
                        Ui.toast(act, s("Radio još nije spreman", "The radio is not ready yet"));
                        return;
                    }
                    if (radio.preset(index) == null) save();
                    else radio.recallPreset(index);
                }
            });
            face.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    save();
                    return true;
                }
            });
        }

        void save() {
            if (radio.unitTuner() && !radio.canStore(index)) {
                // say what is missing instead of doing nothing
                Ui.toast(act, !radio.canControl()
                        ? s("Upravljanje: uključite komande u Podešavanja → Vozilo", "Control: allow commands in Setup → Vehicle")
                        : !radio.shown() ? s("Radio nije aktivan izvor (otvorite radio aplikaciju)", "Radio is not the active source (open the radio app)")
                        : s("Radio još nije spreman", "The radio is not ready yet"));
                return;
            }
            radio.savePreset(index);
            Ui.toast(act, Mib.t(act, "Sačuvano na " + (index % PER_PAGE + 1), "Stored on " + (index % PER_PAGE + 1), (index % PER_PAGE + 1) + " numaraya kaydedildi"));
        }

        void bind(RadioController.Station st, boolean current) {
            if (st == null) name.setText(s("Prazno", "Empty"));
            else if (RadioController.isUnitPreset(st)) name.setText(""); // a slot of the unit's own presets: the name is not known
            else name.setText(radio.stationLabel(st));
            android.graphics.Bitmap b = st != null && st.name.length() > 0
                    ? com.thecarlauncher.radio.RadioLogos.get(act).logo(st.name) : null;
            logo.setImageBitmap(b);
            int c = current ? Mib.BLUE : Mib.WHITE;
            number.setTextColor(c);
            name.setTextColor(c);
            ViewGroup.LayoutParams lp = line.getLayoutParams();
            int h = Math.max(2, Mib.px(act, current ? 5 : 3));
            if (lp.height != h) {
                lp.height = h;
                line.setLayoutParams(lp);
            }
        }
    }

    // ---- Station list ------------------------------------------------------------------------

    private class StationAdapter extends BaseAdapter {
        @Override
        public int getCount() { return radio == null ? 0 : radio.stations().size() + 1; }

        @Override
        public Object getItem(int i) { return i; }

        @Override
        public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Mib.px(act, 10), 0, Mib.px(act, 10), 0);
            row.setMinimumHeight(Mib.px(act, 72));
            ImageView iv;
            TextView t, f;
            if (pos == 0) {
                iv = Mib.icon(act, R.drawable.ui_ic_refresh, Mib.WHITE);
                t = Mib.text(act, radio.scanning() ? s("Zaustavi pretragu", "Stop scan")
                        : s("Ažuriraj listu stanica", "Update station list"), 28, Mib.regular(act), Mib.WHITE);
                f = Mib.text(act, "", 26, Mib.regular(act), Mib.GREY);
            } else {
                RadioController.Station st = radio.stations().get(pos - 1);
                boolean cur = st.freq == radio.freq();
                android.graphics.Bitmap lb = st.name.length() > 0 ? com.thecarlauncher.radio.RadioLogos.get(act).logo(st.name) : null;
                if (lb != null) {
                    iv = new ImageView(act);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setImageBitmap(lb);
                } else {
                    iv = Mib.icon(act, R.drawable.ui_ic_radio, cur ? Mib.BLUE : Mib.WHITE);
                }
                t = Mib.text(act, st.name.length() > 0 ? st.name : "FM", 28, Mib.regular(act), cur ? Mib.BLUE : Mib.WHITE);
                f = Mib.text(act, RadioController.format(st.freq) + " MHz", 26, Mib.regular(act), cur ? Mib.BLUE : Mib.GREY);
            }
            row.addView(iv, new LinearLayout.LayoutParams(Mib.px(act, 44), Mib.px(act, 44)));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = Mib.px(act, 18);
            row.addView(t, tl);
            row.addView(f);
            return row;
        }
    }
}
