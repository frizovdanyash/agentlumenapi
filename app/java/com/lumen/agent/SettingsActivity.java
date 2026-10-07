package com.lumen.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private Store store;
    private Palette p;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new Store(this);
        setTheme(store.getBool("light", false)
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
        super.onCreate(savedInstanceState);
        p = Palette.of(this);
        Theme.statusBar(getWindow(), p);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(p.bg);
        container = Views.column(this);
        int pad = Theme.dp(this, 16);
        container.setPadding(pad, pad, pad, Theme.dp(this, 28));
        scroll.addView(container);
        setContentView(scroll);
        build();
    }

    private void section(String title) {
        TextView tv = Views.text(this, title, p, 13f, p.accent, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 22);
        lp.bottomMargin = Theme.dp(this, 6);
        container.addView(tv, lp);
    }

    private LinearLayout rowBlock(String title, String hint) {
        LinearLayout row = Views.row(this);
        LinearLayout labels = Views.column(this);
        labels.addView(Views.text(this, title, p, 14.5f, p.text, false));
        if (hint != null && !hint.isEmpty()) labels.addView(Views.text(this, hint, p, 12f, p.muted2, false));
        row.addView(labels, Views.lpw(1));
        container.addView(row);
        return row;
    }

    private void addView(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 8);
        container.addView(v, lp);
    }

    private void build() {
        TextView header = Views.text(this, "Настройки", p, 21f, p.text, true);
        container.addView(header);
        container.addView(Views.text(this, "Lumen Agent " + MainActivity.VERSION + " · создатель t.me/frizovdanya", p, 12f, p.muted2, false));

        section("Ключ и API");
        TextView keyMask = Views.text(this, store.getStr("key", "").isEmpty()
                ? "не задан" : "сохранён: " + safeKey(store.getStr("key", "")), p, 12.5f, p.muted, false);
        container.addView(keyMask);
        EditText keyField = Views.field(this, "lum_…", p);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout keyRow = Views.row(this);
        keyRow.addView(keyField, Views.lpw(1));
        keyRow.addView(Views.space(this, 8));
        TextView saveKey = Views.button(this, "Сохранить", p, true);
        keyRow.addView(saveKey);
        addView(keyRow);
        saveKey.setOnClickListener(v -> {
            String val = keyField.getText().toString().trim();
            if (val.length() < 12 || !isAscii(val)) {
                Toast.makeText(this, "Похоже, это не ключ — нужна строка вида lum_…", Toast.LENGTH_LONG).show();
                return;
            }
            store.setStr("key", val);
            keyField.setText("");
            keyMask.setText("сохранён: " + safeKey(val));
            Toast.makeText(this, "Ключ сохранён", Toast.LENGTH_SHORT).show();
        });

        LinearLayout keyActions = Views.row(this);
        TextView ping = Views.button(this, "Проверить связь", p, false);
        keyActions.addView(ping);
        keyActions.addView(Views.space(this, 8));
        TextView where = Views.button(this, "Где взять ключ", p, false);
        keyActions.addView(where);
        addView(keyActions);
        ping.setOnClickListener(v -> {
            String key = store.getStr("key", "");
            if (key.isEmpty()) {
                Toast.makeText(this, "Сначала сохрани ключ", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Проверяю…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final org.json.JSONObject r = ChatClient.ping(store.getStr("base", "https://lumen.unionium.org/api/v1"), key);
                runOnUiThread(() -> Toast.makeText(this,
                        (r.optBoolean("ok") ? "✔ " : "✖ ") + r.optString("message", ""), Toast.LENGTH_LONG).show());
            }).start();
        });
        where.setOnClickListener(v -> openUrl("https://lumen.unionium.org/chat"));

        rowBlock("Base URL", "адрес OpenAI-совместимого API");
        EditText base = Views.field(this, "https://lumen.unionium.org/api/v1", p);
        base.setText(store.getStr("base", "https://lumen.unionium.org/api/v1"));
        addView(base);
        TextView saveBase = Views.button(this, "Сохранить адрес", p, false);
        addView(saveBase);
        saveBase.setOnClickListener(v -> {
            String val = base.getText().toString().trim();
            if (val.isEmpty()) return;
            store.setStr("base", val);
            Toast.makeText(this, "Адрес сохранён", Toast.LENGTH_SHORT).show();
        });

        String[] models = Agent.MODEL_KEYS;
        LinearLayout modelRow = rowBlock("Модель по умолчанию",
                Agent.MODEL_LABELS[Agent.modelIndex(store.getStr("model", "mini"))] + " — " + Agent.MODEL_DESC[Agent.modelIndex(store.getStr("model", "mini"))]);
        TextView pickModel = Views.button(this, "Сменить", p, false);
        modelRow.addView(pickModel);
        final TextView modelHint = (TextView) ((LinearLayout) modelRow.getChildAt(0)).getChildAt(1);
        pickModel.setOnClickListener(v -> {
            final String[] labels = new String[models.length];
            for (int i = 0; i < models.length; i++) {
                labels[i] = Agent.MODEL_LABELS[i] + " — " + Agent.MODEL_DESC[i] + " · " + (Agent.MODEL_CTX[i] / 1000) + "k";
            }
            new AlertDialog.Builder(this, Theme.dialogTheme(p))
                    .setTitle("Модель")
                    .setItems(labels, (d, which) -> {
                        store.setStr("model", models[which]);
                        modelHint.setText(Agent.MODEL_LABELS[which] + " — " + Agent.MODEL_DESC[which]);
                    })
                    .show();
        });

        section("Выполнение команд");
        TextView engineTitle = Views.text(this, "Движок bash-команд", p, 14.5f, p.text, false);
        container.addView(engineTitle);
        final String[] engines = {"builtin", "termux", "auto"};
        final String[] engineNames = {"Встроенный", "Termux", "Авто"};
        LinearLayout engineRow = Views.row(this);
        final TextView[] engineButtons = new TextView[engines.length];
        for (int i = 0; i < engines.length; i++) {
            final int idx = i;
            engineButtons[i] = Views.button(this, engineNames[i], p, store.getStr("engine", "builtin").equals(engines[i]));
            engineButtons[i].setOnClickListener(v -> {
                store.setStr("engine", engines[idx]);
                if (idx > 0 && !Termux.available(this)) {
                    Toast.makeText(this, "Termux не найден — команды пойдут во встроенный шелл", Toast.LENGTH_LONG).show();
                }
                for (int k = 0; k < engines.length; k++) {
                    boolean on = k == idx;
                    engineButtons[k].setBackground(on ? Theme.gradient(this, 12, p.accent, p.accent2) : Theme.rounded(this, 12, p.card, p.line, 1));
                    engineButtons[k].setTextColor(on ? 0xFF1A1208 : p.text);
                }
            });
            engineRow.addView(engineButtons[i], Views.lpw(1));
            if (i < engines.length - 1) engineRow.addView(Views.space(this, 6));
        }
        addView(engineRow);
        container.addView(Views.text(this, "Встроенный — песочница приложения (ls, cat, echo, mkdir…). Termux — настоящий bash: pkg, python, git. Авто — простое локально, остальное в Termux.", p, 11.5f, p.muted2, false));

        SwitchRow showTools = addSwitch("Показывать вывод инструментов", "терминальные блоки прямо в чате", store.getBool("showTools", true));
        showTools.setOnChange(v -> store.setBool("showTools", v));
        SwitchRow confirm = addSwitch("Спрашивать перед опасными командами", "rm -rf, sudo, mkfs и подобное", store.getBool("confirm", true));
        confirm.setOnChange(v -> store.setBool("confirm", v));
        SwitchRow light = addSwitch("Светлая тема", "иначе тёмная", store.getBool("light", false));
        light.setOnChange(v -> {
            store.setBool("light", v);
            recreate();
        });

        section("Оформление");
        LinearLayout accentTitle = rowBlock("Акцент", "цвет интерфейса");
        LinearLayout swatches = Views.row(this);
        swatches.setGravity(Gravity.START);
        final int currentAccent = store.getInt("accent", 0);
        for (int i = 0; i < Palette.ACCENTS.length; i++) {
            final int idx = i;
            TextView sw = new TextView(this);
            int size = Theme.dp(this, 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Theme.dp(this, 9);
            sw.setLayoutParams(lp);
            sw.setBackground(Theme.gradient(this, 11, Palette.ACCENTS[i][0], Palette.ACCENTS[i][1]));
            sw.setAlpha(i == currentAccent ? 1f : 0.5f);
            sw.setOnClickListener(v -> {
                store.setInt("accent", idx);
                recreate();
            });
            swatches.addView(sw);
        }
        addView(swatches);

        LinearLayout radiusRow = rowBlock("Скругления", "");
        final String[] radiusKeys = {"small", "default", "large"};
        final String[] radiusNames = {"Малые", "Средние", "Большие"};
        LinearLayout radiusBox = Views.row(this);
        for (int i = 0; i < radiusKeys.length; i++) {
            final int idx = i;
            boolean on = store.getStr("radius", "default").equals(radiusKeys[i]);
            TextView b = Views.button(this, radiusNames[i], p, on);
            b.setOnClickListener(v -> {
                store.setStr("radius", radiusKeys[idx]);
                recreate();
            });
            radiusBox.addView(b, Views.lpw(1));
            if (i < radiusKeys.length - 1) radiusBox.addView(Views.space(this, 6));
        }
        addView(radiusBox);

        rowBlock("Размер текста", store.getInt("font", 15) + " sp");
        SeekBar font = new SeekBar(this);
        font.setMax(6);
        font.setProgress(store.getInt("font", 15) - 13);
        addView(font);
        font.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    store.setInt("font", 13 + progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                recreate();
            }
        });

        section("Данные и справка");
        container.addView(Views.text(this, "Рабочая папка агента: " + store.getStr("home", ""), p, 12f, p.muted2, false));

        LinearLayout danger = Views.row(this);
        TextView clearSessions = Views.button(this, "Очистить диалоги", p, false);
        clearSessions.setTextColor(p.err);
        TextView reset = Views.button(this, "Сбросить настройки", p, false);
        reset.setTextColor(p.err);
        danger.addView(clearSessions, Views.lpw(1));
        danger.addView(Views.space(this, 8));
        danger.addView(reset, Views.lpw(1));
        addView(danger);
        clearSessions.setOnClickListener(v -> new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Очистить диалоги?")
                .setMessage("Вся история переписки будет удалена.")
                .setPositiveButton("Удалить", (d, w) -> {
                    store.setStr("sessions", "[]");
                    Toast.makeText(this, "Диалоги очищены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show());
        reset.setOnClickListener(v -> new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Сбросить всё?")
                .setMessage("Ключ, настройки и история будут удалены.")
                .setPositiveButton("Сбросить", (d, w) -> {
                    store.clearAll();
                    Intent i = new Intent(this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Отмена", null)
                .show());

        TextView links = Views.text(this, "GitHub · Документация · @frizovdanya", p, 13f, p.accent, false);
        addView(links);
        links.setText("GitHub  ·  Документация  ·  @frizovdanya");
        links.setOnClickListener(v -> openUrl("https://github.com/frizovdanyash/agentlumenapi"));
        TextView docs = Views.button(this, "Открыть документацию", p, false);
        addView(docs);
        docs.setOnClickListener(v -> openUrl("https://frizovdanyash.github.io/agentlumenapi/"));
    }

    private interface OnSwitch {
        void change(boolean value);
    }

    private class SwitchRow {
        private final TextView knob;
        private OnSwitch handler;

        SwitchRow(TextView knob) {
            this.knob = knob;
        }

        void setOnChange(OnSwitch h) {
            this.handler = h;
        }

        void render(boolean on) {
            knob.setText(on ? "ВКЛ" : "выкл");
            knob.setTextColor(on ? p.accent : p.muted2);
            knob.setBackground(Theme.rounded(SettingsActivity.this, 999,
                    on ? Palette.withAlpha(p.accent, 30) : p.card2,
                    on ? p.accentLine : p.line2, 1));
        }

        void toggle() {
            boolean on = !"ВКЛ".equals(knob.getText().toString());
            render(on);
            if (handler != null) handler.change(on);
        }
    }

    private SwitchRow addSwitch(String title, String hint, boolean initial) {
        LinearLayout row = rowBlock(title, hint);
        TextView knob = Views.text(this, "", p, 12f, p.muted2, false);
        knob.setGravity(Gravity.CENTER);
        knob.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), Theme.dp(this, 12), Theme.dp(this, 6));
        knob.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(knob);
        SwitchRow sr = new SwitchRow(knob);
        sr.render(initial);
        row.setOnClickListener(v -> sr.toggle());
        knob.setOnClickListener(v -> sr.toggle());
        return sr;
    }

    private static String safeKey(String key) {
        if (key.length() < 14) return "сохранён";
        return key.substring(0, 10) + "…" + key.substring(key.length() - 4);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false;
        return true;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }
}
