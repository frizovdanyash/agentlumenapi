package com.lumen.agent;

import android.content.Context;
import android.graphics.Color;

public class Palette {

    public final boolean dark;
    public final int bg, bg2, card, card2, line, line2, text, muted, muted2, ok, err, info;
    public final int accent, accent2, accentSoft, accentLine;

    public static final String[] ACCENT_NAMES = {
            "Тёплая", "Закат", "Океан", "Изумруд", "Лаванда", "Роза", "Кибер", "Графит"
    };
    public static final int[][] ACCENTS = {
            {0xFFFF8A3D, 0xFFFF6A00},
            {0xFFFF6B6B, 0xFFFF9E3D},
            {0xFF4AA8FF, 0xFF1F6FE0},
            {0xFF3DDC97, 0xFF12A06A},
            {0xFF9B8AEE, 0xFF6F5CE0},
            {0xFFFF7AB6, 0xFFE0428A},
            {0xFF39E6C3, 0xFF0F8F93},
            {0xFFA7B0BA, 0xFF66707B}
    };

    private Palette(boolean dark, int accent, int accent2) {
        this.dark = dark;
        this.accent = accent;
        this.accent2 = accent2;
        this.accentSoft = withAlpha(accent, dark ? 46 : 38);
        this.accentLine = withAlpha(accent, dark ? 120 : 130);
        if (dark) {
            bg = 0xFF100E0C;
            bg2 = 0xFF16130F;
            card = 0xFF1B1714;
            card2 = 0xFF221C17;
            line = 0xFF2D2721;
            line2 = 0xFF3A322A;
            text = 0xFFF7F2EC;
            muted = 0xFFA89E91;
            muted2 = 0xFF7D7468;
            ok = 0xFF7FD88F;
            err = 0xFFFF7A6B;
            info = 0xFF7CC7FF;
        } else {
            bg = 0xFFF6F3EE;
            bg2 = 0xFFFFFFFF;
            card = 0xFFFFFFFF;
            card2 = 0xFFF1ECE4;
            line = 0xFFE3DCD1;
            line2 = 0xFFD3C9BB;
            text = 0xFF221C17;
            muted = 0xFF6B6157;
            muted2 = 0xFF938779;
            ok = 0xFF2E9E5B;
            err = 0xFFD8503F;
            info = 0xFF2E7BC4;
        }
    }

    public static Palette of(Context ctx) {
        Store store = new Store(ctx);
        int idx = store.getInt("accent", 0);
        if (idx < 0 || idx >= ACCENTS.length) idx = 0;
        return new Palette(store.getBool("light", false), ACCENTS[idx][0], ACCENTS[idx][1]);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public int radius() {
        return 14;
    }
}
