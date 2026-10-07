package com.lumen.agent;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

public class Theme {

    public static int dp(Context ctx, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }

    public static int radiusPx(Context ctx, Palette p) {
        Store store = new Store(ctx);
        String r = store.getStr("radius", "default");
        if (r.equals("small")) return dp(ctx, 10);
        if (r.equals("large")) return dp(ctx, 22);
        return dp(ctx, 16);
    }

    public static float fontPx(Context ctx) {
        Store store = new Store(ctx);
        int sp = store.getInt("font", 15);
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }

    public static GradientDrawable rounded(Context ctx, int radiusDp, int fill, int stroke, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(ctx, radiusDp));
        if (stroke != 0) d.setStroke(dp(ctx, strokeWidthDp), stroke);
        return d;
    }

    public static GradientDrawable gradient(Context ctx, int radiusDp, int from, int to) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    public static void card(View v, Palette p, int radiusDp) {
        v.setBackground(rounded(v.getContext(), radiusDp, p.card, p.line, 1));
    }

    public static void chip(View v, Palette p) {
        v.setBackground(rounded(v.getContext(), 999, p.card, p.line2, 1));
    }

    public static void primary(View v, Palette p, int radiusDp) {
        v.setBackground(gradient(v.getContext(), radiusDp, p.accent, p.accent2));
    }

    public static void iconButton(View v, Palette p) {
        v.setBackground(rounded(v.getContext(), 11, p.card, p.line, 1));
    }

    public static void text(TextView tv, int color, float sizeSp, boolean bold) {
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
    }

    public static void mono(TextView tv) {
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
    }

    public static void pressable(View v) {
        final float normal = 1f;
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) view.setAlpha(0.65f);
            else if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) view.setAlpha(normal);
            return false;
        });
    }

    public static void statusBar(android.view.Window w, Palette p) {
        w.setStatusBarColor(p.dark ? 0xFF12100E : 0xFFEFE9E0);
        w.setNavigationBarColor(p.dark ? 0xFF12100E : 0xFFEFE9E0);
    }

    public static int dialogTheme(Palette p) {
        return p.dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }
}
