package com.lumen.agent;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

public class Markdown {

    public static CharSequence render(String src, Palette p, Context ctx, float sizePx) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String text = src == null ? "" : src;
        String[] lines = text.split("\n", -1);
        boolean inCode = false;
        int codeBg = p.dark ? 0xFF0C0B0A : 0xFFF0EBE3;
        int codeFg = p.dark ? 0xFFE8E2D9 : 0xFF3A342C;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();

            if (t.startsWith("```")) {
                inCode = !inCode;
                continue;
            }

            if (inCode) {
                int start = out.length();
                out.append(line).append("\n");
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(codeBg), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(codeFg), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(0.92f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            if (t.isEmpty()) {
                out.append("\n");
                continue;
            }

            if (t.startsWith("#")) {
                int start = out.length();
                String body = t.replaceFirst("^#+\\s*", "");
                out.append(body).append("\n");
                out.setSpan(new StyleSpan(Typeface.BOLD), start, start + body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.07f), start, start + body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(p.accent), start, start + body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") || t.startsWith("• ")) {
                int start = out.length();
                String body = t.substring(2);
                out.append("•  ");
                int bodyStart = out.length();
                appendInline(out, body, p, codeBg);
                out.append("\n");
                out.setSpan(new LeadingMarginSpan.Standard(0, Theme.dp(ctx, 14)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(p.accent), start, bodyStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            if (t.startsWith(">")) {
                int start = out.length();
                String body = t.replaceFirst("^>\\s*", "");
                out.append(body).append("\n");
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, start + body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(p.muted), start, start + body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new LeadingMarginSpan.Standard(0, Theme.dp(ctx, 16)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            appendInline(out, line, p, codeBg);
            out.append("\n");
        }

        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == '\n') out.delete(end - 1, end);
        return out;
    }

    private static void appendInline(SpannableStringBuilder out, String line, Palette p, int codeBg) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);

            if (c == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i) {
                    int start = out.length();
                    out.append(line, i + 1, end);
                    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(p.accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(codeBg), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new RelativeSizeSpan(0.94f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }

            if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                int end = line.indexOf("**", i + 2);
                if (end > i) {
                    int start = out.length();
                    out.append(line, i + 2, end);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }

            if (c == '[') {
                int close = line.indexOf("](", i);
                int paren = close >= 0 ? line.indexOf(')', close + 2) : -1;
                if (close > i && paren > close) {
                    String label = line.substring(i + 1, close);
                    String url = line.substring(close + 2, paren);
                    int start = out.length();
                    out.append(label);
                    out.setSpan(new android.text.style.URLSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(p.info), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = paren + 1;
                    continue;
                }
            }

            out.append(c);
            i++;
        }
    }
}
