package com.thecarlauncher.screens;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;

import com.thecarlauncher.LauncherActivity;
import com.thecarlauncher.R;
import com.thecarlauncher.apps.AppRepository;
import com.thecarlauncher.mib.Mib;
import com.thecarlauncher.mib.MibBar;
import com.thecarlauncher.mib.MibTile;
import com.thecarlauncher.util.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** All apps as MIB tiles. Long-press: pin to MENU page 2, use as phone/radio app, info, uninstall. */
public class AppsScreen extends Screen implements AppRepository.Listener {
    private final List<AppRepository.App> shown = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();
    private GridView grid;
    private String query = "";
    private MibBar.Key searchKey;

    public AppsScreen(LauncherActivity act) {
        super(act);
    }

    @Override
    public String title() { return s("Aplikacije", "Apps"); }

    private String s(String sr, String en) { return Mib.t(act, sr, en); }

    @Override
    protected View create(ViewGroup parent) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        grid = new GridView(act);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Mib.px(act, 40));
        grid.setVerticalSpacing(Mib.px(act, 18));
        grid.setPadding(Mib.px(act, 70), Mib.px(act, 26), Mib.px(act, 70), Mib.px(act, 16));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setFastScrollEnabled(true);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppRepository.launch(act, shown.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                menu(v, shown.get(pos));
                return true;
            }
        });
        r.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MibBar bar = new MibBar(act);
        searchKey = bar.add(s("Pretraga", "Search"), R.drawable.ui_ic_search, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialogs.text(act, s("Pretraga aplikacija", "Search apps"), s("Naziv aplikacije", "App name"), query,
                        new Dialogs.TextEntered() {
                            @Override
                            public void onText(String text) {
                                query = text;
                                filter();
                            }
                        });
            }
        });
        bar.add(s("Sve", "All"), R.drawable.ui_ic_apps, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                query = "";
                filter();
            }
        });
        bar.add("MENU", R.drawable.ui_ic_home, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.MENU, true);
            }
        });
        bar.gap(3);
        bar.add("Setup", R.drawable.ui_key_setup, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.show(LauncherActivity.SETTINGS, true);
            }
        });
        r.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MibBar.height(act)));
        return r;
    }

    @Override
    public void onShow() {
        AppRepository.get(act).addListener(this);
        filter();
    }

    @Override
    public void onHide() {
        AppRepository.get(act).removeListener(this);
    }

    @Override
    public void onAppsChanged() {
        filter();
    }

    private void filter() {
        String q = query.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppRepository.App a : AppRepository.get(act).all()) {
            if (q.length() == 0 || a.label.toLowerCase(Locale.getDefault()).contains(q)) shown.add(a);
        }
        searchKey.setActive(q.length() > 0);
        adapter.notifyDataSetChanged();
    }

    /** The long-press choices, in the middle of the screen (an Android pop-up menu hung at the edge, out of reach). */
    private void menu(View anchor, final AppRepository.App a) {
        final AppRepository repo = AppRepository.get(act);
        new com.thecarlauncher.mib.MibMenu(act, a.label)
                .add(repo.isFavourite(a) ? s("Ukloni iz MENU", "Remove from MENU") : s("Dodaj u MENU", "Add to MENU"), new Runnable() {
                    public void run() { repo.toggleFavourite(a); }
                })
                .add(s("Koristi kao telefon", "Use as phone app"), new Runnable() {
                    public void run() { Prefs.put(act, Prefs.PHONE_PKG, a.pkg); }
                })
                .add(s("Koristi kao radio", "Use as radio app"), new Runnable() {
                    public void run() { Prefs.put(act, Prefs.RADIO_PKG, a.pkg); }
                })
                .add(s("Informacije o aplikaciji", "App info"), new Runnable() {
                    public void run() { AppRepository.showInfo(act, a.pkg); }
                })
                .add(s("Deinstaliraj", "Uninstall"), true, new Runnable() {
                    public void run() { AppRepository.uninstall(act, a.pkg); }
                })
                .show(anchor);
    }

    private class GridAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            AppRepository.App a = shown.get(p);
            MibTile t = new MibTile(act, a.label, a.icon, true);
            t.setClickable(false);
            t.setDuplicateParentStateEnabled(true);
            t.setLabelSize(23);
            int w = (grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight() - 3 * Mib.px(act, 40)) / 4;
            t.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (Math.max(w, Mib.px(act, 150)) * 0.6f) + Mib.px(act, 53)));
            return t;
        }
    }
}
