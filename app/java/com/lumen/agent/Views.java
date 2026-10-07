package com.lumen.agent;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

public class Views {

    public static LinearLayout column(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    public static LinearLayout row(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        return ll;
    }

    public static TextView text(Context ctx, String value, Palette p, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(value);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setLineSpacing(Theme.dp(ctx, 2), 1f);
        return tv;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static View space(Context ctx, int widthDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(ctx, widthDp), 1));
        return v;
    }

    public static View divider(Context ctx, Palette p) {
        View v = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(ctx, 0.6f)));
        lp.topMargin = Theme.dp(ctx, 8);
        lp.bottomMargin = Theme.dp(ctx, 8);
        v.setLayoutParams(lp);
        v.setBackgroundColor(p.line);
        return v;
    }

    public static TextView pill(Context ctx, String text, int color, Palette p) {
        TextView tv = text(ctx, text, p, 11f, color, false);
        tv.setPadding(Theme.dp(ctx, 8), Theme.dp(ctx, 3), Theme.dp(ctx, 8), Theme.dp(ctx, 3));
        tv.setBackground(Theme.rounded(ctx, 999, Palette.withAlpha(color, 26), Palette.withAlpha(color, 110), 1));
        return tv;
    }

    public static TextView button(Context ctx, String text, Palette p, boolean primary) {
        TextView tv = Views.text(ctx, text, p, 14f, primary ? 0xFF1A1208 : p.text, primary);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Theme.dp(ctx, 14), Theme.dp(ctx, 11), Theme.dp(ctx, 14), Theme.dp(ctx, 11));
        if (primary) Theme.primary(tv, p, 12);
        else Theme.chip(tv, p);
        Theme.pressable(tv);
        return tv;
    }

    public static TextView iconButton(Context ctx, String glyph, Palette p) {
        TextView tv = text(ctx, glyph, p, 17f, p.muted, false);
        tv.setGravity(Gravity.CENTER);
        int size = Theme.dp(ctx, 36);
        tv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        Theme.iconButton(tv, p);
        Theme.pressable(tv);
        return tv;
    }

    public static EditText field(Context ctx, String hint, Palette p) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setTextColor(p.text);
        et.setHintTextColor(p.muted2);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        et.setBackground(Theme.rounded(ctx, 12, p.card, p.line, 1));
        et.setPadding(Theme.dp(ctx, 12), Theme.dp(ctx, 11), Theme.dp(ctx, 12), Theme.dp(ctx, 11));
        et.setSingleLine(true);
        return et;
    }

    public static class ToolCard extends LinearLayout {

        private final TextView output;
        private final TextView status;
        private final Palette palette;
        private boolean collapsed;

        public ToolCard(Context ctx, final Sess.ToolCall tool, final Palette p) {
            super(ctx);
            this.palette = p;
            setOrientation(VERTICAL);
            int pad = Theme.dp(ctx, 11);
            setPadding(pad, pad, pad, pad);
            setBackground(Theme.rounded(ctx, 13, p.card, p.line, 1));

            LinearLayout bar = row(ctx);
            TextView marker = text(ctx, "⏺", p, 13f, p.accent, true);
            bar.addView(marker);
            bar.addView(space(ctx, 6));

            TextView name = text(ctx, tool.name, p, 13f, p.accent, true);
            bar.addView(name);
            bar.addView(space(ctx, 6));

            TextView detail = text(ctx, detailOf(tool), p, 11.5f, p.muted, false);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bar.addView(detail, lpw(1));
            bar.addView(space(ctx, 6));

            status = new TextView(ctx);
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            bar.addView(status);
            addView(bar);

            output = text(ctx, tool.output == null ? "" : tool.output, p, 12f, p.dark ? 0xFFCFEFC9 : 0xFF2F5D3A, false);
            output.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.topMargin = Theme.dp(ctx, 8);
            output.setLayoutParams(olp);
            output.setTextIsSelectable(true);
            addView(output);

            collapsed = tool.output != null && tool.output.length() > 260;
            refresh(tool);

            setOnClickListener(v -> {
                collapsed = !collapsed;
                output.setVisibility(collapsed ? GONE : VISIBLE);
            });
        }

        public void refresh(Sess.ToolCall tool) {
            if (tool.running) {
                status.setText("выполняется…");
                status.setTextColor(palette.accent);
                status.setBackground(Theme.rounded(getContext(), 999, Palette.withAlpha(palette.accent, 24), palette.accentLine, 1));
            } else if (tool.ok) {
                status.setText("✔ " + String.format(java.util.Locale.US, "%.2f с", tool.secs));
                status.setTextColor(palette.ok);
                status.setBackground(Theme.rounded(getContext(), 999, Palette.withAlpha(palette.ok, 22), Palette.withAlpha(palette.ok, 100), 1));
            } else {
                status.setText("✖ ошибка");
                status.setTextColor(palette.err);
                status.setBackground(Theme.rounded(getContext(), 999, Palette.withAlpha(palette.err, 22), Palette.withAlpha(palette.err, 100), 1));
            }
            int h = Theme.dp(getContext(), 5);
            status.setPadding(h * 3, h, h * 3, h);
            output.setText(tool.output == null ? "" : tool.output);
            output.setVisibility(collapsed ? GONE : VISIBLE);
        }

        private String detailOf(Sess.ToolCall tool) {
            JSONObject a = tool.args == null ? new JSONObject() : tool.args;
            if ("bash".equals(tool.name)) return a.optString("command", "");
            if ("write_file".equals(tool.name)) return a.optString("path", "") + " (" + a.optString("content", "").length() + " симв.)";
            if ("edit_file".equals(tool.name)) return a.optString("path", "");
            return a.optString("path", "");
        }
    }
}
