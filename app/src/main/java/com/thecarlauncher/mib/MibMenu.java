package com.thecarlauncher.mib;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A short list of choices in the middle of the screen, in the MIB's own pop-up look (black panel, white frame, blue rule
 * under the title, the picture behind dimmed). Used where an Android pop-up menu would hang at the screen edge next to
 * the thing that was pressed, which is hard to reach in a car. A tap outside the panel closes it.
 */
public final class MibMenu {
    private final Context c;
    private final String title;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Boolean> warn = new ArrayList<>();

    public MibMenu(Context c, String title) {
        this.c = c;
        this.title = title;
    }

    public MibMenu add(String label, Runnable action) {
        return add(label, false, action);
    }

    /** {@code destructive} draws the label in a warning colour (uninstall and the like). */
    public MibMenu add(String label, boolean destructive, Runnable action) {
        labels.add(label);
        actions.add(action);
        warn.add(destructive);
        return this;
    }

    public void show(View anchor) {
        final View root = anchor.getRootView();
        final PopupWindow pw = new PopupWindow(c);
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pw.dismiss();
            }
        });

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Mib.panel(c));
        panel.setClickable(true); // a tap on the panel itself must not close it
        int pad = Mib.px(c, 8);
        panel.setPadding(pad, pad, pad, pad);

        TextView t = Mib.text(c, title, 32, Mib.semibold(c), Mib.WHITE);
        t.setPadding(Mib.px(c, 18), Mib.px(c, 10), Mib.px(c, 18), Mib.px(c, 10));
        panel.addView(t);
        View rule = new View(c);
        rule.setBackgroundColor(Mib.BLUE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Mib.px(c, 2)));
        rl.leftMargin = rl.rightMargin = Mib.px(c, 8);
        panel.addView(rule, rl);

        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                View sep = new View(c);
                sep.setBackgroundColor(0xFF4C4D57);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Mib.px(c, 1)));
                sl.leftMargin = sl.rightMargin = Mib.px(c, 14);
                panel.addView(sep, sl);
            }
            final Runnable action = actions.get(i);
            TextView row = Mib.text(c, labels.get(i), 30, Mib.semibold(c), warn.get(i) ? 0xFFF0675E : Mib.WHITE);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Mib.px(c, 18), 0, Mib.px(c, 18), 0);
            row.setBackground(Mib.keyPressed());
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pw.dismiss();
                    if (action != null) action.run();
                }
            });
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mib.px(c, 76)));
        }

        int width = Math.min(Mib.px(c, 620), Math.max(Mib.px(c, 320), root.getWidth() - Mib.px(c, 60)));
        dim.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        pw.setContentView(dim);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        pw.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        pw.setFocusable(true);
        pw.setOutsideTouchable(true);
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        pw.showAtLocation(root, Gravity.CENTER, 0, 0);
    }
}
