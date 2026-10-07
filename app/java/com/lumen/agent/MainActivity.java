package com.lumen.agent;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends Activity implements Agent.Listener, Agent.Confirmer {

    public static final String VERSION = "1.2.0";

    private Store store;
    private Palette p;
    private MiniShell shell;
    private Agent agent;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText input;
    private TextView sendBtn, modelChip, subtitle, hintEngine, hintCtx, tabChat, tabTerm, termEngineHint, termCwd, termTermux;
    private LinearLayout chatPane, termPane, termBar, dock;
    private FrameLayout content;
    private ScrollView termScroll;
    private LinearLayout termOutput;
    private EditText termInput;

    private Sess sess;
    private List<Sess> sessions = new ArrayList<>();
    private TextView typing;
    private TextView streamingView;
    private StringBuilder streamingBuf = new StringBuilder();
    private long lastStreamPaint;
    private boolean busy;
    private final List<String> termHistory = new ArrayList<>();
    private final java.util.Map<Sess.ToolCall, Views.ToolCard> cardViews = new java.util.HashMap<>();
    private int termHistoryIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new Store(this);
        setTheme(store.getBool("light", false)
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
        super.onCreate(savedInstanceState);
        p = Palette.of(this);
        Theme.statusBar(getWindow(), p);

        if (!store.has("home")) {
            java.io.File base = getExternalFilesDir(null);
            if (base == null) base = getFilesDir();
            java.io.File workspace = new java.io.File(base, "workspace");
            store.setStr("home", workspace.getAbsolutePath());
        }
        shell = new MiniShell(new java.io.File(store.getStr("home", getFilesDir().getAbsolutePath())));

        buildUi();
        sessions = Sess.loadAll(store);
        sess = sessions.isEmpty() ? Sess.create(store.getStr("model", "mini")) : sessions.get(sessions.size() - 1);
        if (!sessions.contains(sess)) sessions.add(sess);

        for (Sess.Msg m : sess.messages) {
            if ("tool-anchor".equals(m.role)) {
                for (Sess.ToolCall t : m.tools) {
                    t.running = false;
                    t.collapsed = true;
                }
            }
        }

        agent = new Agent(this, ui, shell, this, this);
        renderAll();
        updateHints();
        updateSubtitle();
        termBoot();

        if (store.getStr("key", "").isEmpty()) {
            ui.postDelayed(this::askKey, 260);
        }
        if (store.getStr("hintShown", "").isEmpty()) {
            store.setStr("hintShown", "1");
            Toast.makeText(this, "Ключ можно сменить в настройках (⚙)", Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi() {
        LinearLayout root = Views.column(this);
        root.setBackgroundColor(p.bg);

        LinearLayout header = Views.row(this);
        int hp = Theme.dp(this, 10);
        header.setPadding(hp, hp, hp, hp);
        header.setBackgroundColor(p.bg);

        TextView logo = Views.text(this, "✦", p, 17f, 0xFF1A1208, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 34), Theme.dp(this, 34)));
        logo.setBackground(Theme.gradient(this, 11, p.accent, p.accent2));
        header.addView(logo);
        header.addView(Views.space(this, 9));

        LinearLayout brand = Views.column(this);
        brand.addView(Views.text(this, "Lumen Agent", p, 15f, p.text, true));
        subtitle = Views.text(this, "готов к работе", p, 11f, p.muted2, false);
        brand.addView(subtitle);
        header.addView(brand, Views.lpw(1));

        modelChip = Views.text(this, store.getStr("model", "mini"), p, 12.5f, p.text, false);
        modelChip.setPadding(Theme.dp(this, 11), Theme.dp(this, 7), Theme.dp(this, 11), Theme.dp(this, 7));
        modelChip.setBackground(Theme.rounded(this, 999, p.card, p.line2, 1));
        Theme.pressable(modelChip);
        modelChip.setOnClickListener(v -> showModelDialog());
        header.addView(modelChip);
        header.addView(Views.space(this, 6));

        TextView newBtn = Views.iconButton(this, "✚", p);
        newBtn.setOnClickListener(v -> {
            saveSess();
            sess = Sess.create(store.getStr("model", "mini"));
            sessions.add(sess);
            renderAll();
            Toast.makeText(this, "Новый диалог", Toast.LENGTH_SHORT).show();
        });
        header.addView(newBtn);
        header.addView(Views.space(this, 5));

        TextView sessBtn = Views.iconButton(this, "☰", p);
        sessBtn.setOnClickListener(v -> showSessionsDialog());
        header.addView(sessBtn);
        header.addView(Views.space(this, 5));

        TextView setBtn = Views.iconButton(this, "⚙", p);
        setBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(setBtn);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        chatPane = Views.column(this);
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messages = Views.column(this);
        int mp = Theme.dp(this, 12);
        messages.setPadding(mp, mp, mp, mp);
        chatScroll.addView(messages);
        chatPane.addView(chatScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dock = Views.column(this);
        int dp = Theme.dp(this, 10);
        dock.setPadding(dp, Theme.dp(this, 6), dp, Theme.dp(this, 8));
        dock.setBackgroundColor(p.bg);
        LinearLayout composer = Views.row(this);
        composer.setPadding(Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4));
        composer.setBackground(Theme.rounded(this, 18, p.card, p.line, 1));
        input = new EditText(this);
        input.setHint("Спроси что угодно или поручи задачу…");
        input.setTextColor(p.text);
        input.setHintTextColor(p.muted2);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        input.setBackgroundColor(0x00000000);
        input.setPadding(Theme.dp(this, 10), Theme.dp(this, 8), Theme.dp(this, 10), Theme.dp(this, 8));
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composer.addView(input, Views.lpw(1));
        composer.addView(Views.space(this, 6));
        sendBtn = Views.text(this, "➤", p, 17f, 0xFF1A1208, true);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40)));
        Theme.primary(sendBtn, p, 14);
        Theme.pressable(sendBtn);
        composer.addView(sendBtn);
        dock.addView(composer);
        LinearLayout hints = Views.row(this);
        hints.setGravity(Gravity.CENTER);
        hintEngine = Views.text(this, "", p, 11f, p.muted2, false);
        hintCtx = Views.text(this, "", p, 11f, p.muted2, false);
        hints.addView(hintEngine);
        hints.addView(Views.text(this, "  ·  ", p, 11f, p.muted2, false));
        hints.addView(hintCtx);
        hints.addView(Views.text(this, "  ·  ", p, 11f, p.muted2, false));
        TextView author = Views.text(this, "@frizovdanya", p, 11f, p.accent, false);
        author.setOnClickListener(v -> openUrl("https://t.me/frizovdanya"));
        hints.addView(author);
        dock.addView(hints);
        chatPane.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(chatPane, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        termPane = Views.column(this);
        termPane.setVisibility(View.GONE);
        LinearLayout termHead = Views.row(this);
        int thp = Theme.dp(this, 10);
        termHead.setPadding(thp, thp, thp, thp);
        termEngineHint = Views.text(this, "", p, 11.5f, p.muted2, false);
        termCwd = Views.text(this, "", p, 11.5f, p.muted, false);
        termCwd.setSingleLine(true);
        termCwd.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        termHead.addView(termEngineHint);
        termHead.addView(Views.text(this, "  ·  ", p, 11.5f, p.muted2, false));
        termHead.addView(termCwd, Views.lpw(1));
        termTermux = Views.text(this, "", p, 11.5f, p.muted2, false);
        termHead.addView(termTermux);
        termPane.addView(termHead);

        termScroll = new ScrollView(this);
        termOutput = Views.column(this);
        int tp = Theme.dp(this, 12);
        termOutput.setPadding(tp, tp, tp, tp);
        termScroll.addView(termOutput);
        termPane.addView(termScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        android.widget.HorizontalScrollView keys = new android.widget.HorizontalScrollView(this);
        LinearLayout keysRow = Views.row(this);
        keysRow.setPadding(Theme.dp(this, 10), Theme.dp(this, 2), Theme.dp(this, 10), Theme.dp(this, 8));
        String[] quick = {"ls -l", "pwd", "cat ", "mkdir -p test", "help", "clear", "Termux"};
        for (String q : quick) {
            TextView k = Views.text(this, q, p, 11.5f, p.muted, false);
            k.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), Theme.dp(this, 10), Theme.dp(this, 6));
            k.setBackground(Theme.rounded(this, 10, p.card, p.line, 1));
            Theme.pressable(k);
            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            klp.rightMargin = Theme.dp(this, 6);
            k.setLayoutParams(klp);
            k.setOnClickListener(v -> {
                if ("Termux".equals(q)) {
                    store.setStr("engine", "termux");
                    updateHints();
                    termLine("движок переключён на Termux", p.muted2);
                    if (!Termux.available(this)) {
                        Toast.makeText(this, "Termux не установлен — команды пойдут во встроенный шелл", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                termInput.setText(q);
                termInput.setSelection(q.length());
            });
            keysRow.addView(k);
        }
        keys.addView(keysRow);
        termPane.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        termBar = Views.row(this);
        int bp = Theme.dp(this, 10);
        termBar.setPadding(bp, bp, bp, bp);
        termBar.setBackgroundColor(p.bg2);
        termBar.addView(Views.text(this, "❯", p, 15f, p.accent, true));
        termBar.addView(Views.space(this, 8));
        termInput = new EditText(this);
        termInput.setHint("команда — например: ls -l или pkg install fastfetch");
        termInput.setTextColor(p.text);
        termInput.setHintTextColor(p.muted2);
        termInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        termInput.setTypeface(Typeface.MONOSPACE);
        termInput.setBackgroundColor(0x00000000);
        termInput.setSingleLine(true);
        termInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        termBar.addView(termInput, Views.lpw(1));
        TextView run = Views.button(this, "Run", p, true);
        termBar.addView(run);
        termPane.addView(termBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(termPane, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout tabs = Views.row(this);
        tabs.setPadding(Theme.dp(this, 8), Theme.dp(this, 6), Theme.dp(this, 8), Theme.dp(this, 8));
        tabs.setBackgroundColor(p.bg);
        tabChat = tabButton("Чат", true);
        tabTerm = tabButton("Терминал", false);
        tabs.addView(tabChat, Views.lpw(1));
        tabs.addView(Views.space(this, 6));
        tabs.addView(tabTerm, Views.lpw(1));
        root.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        sendBtn.setOnClickListener(v -> sendOrStop());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN && !event.isShiftPressed())) {
                sendOrStop();
                return true;
            }
            return false;
        });
        run.setOnClickListener(v -> {
            String cmd = termInput.getText().toString();
            termInput.setText("");
            runTerm(cmd);
        });
        termInput.setOnEditorActionListener((v, actionId, event) -> {
            String cmd = termInput.getText().toString();
            termInput.setText("");
            runTerm(cmd);
            return true;
        });
        tabChat.setOnClickListener(v -> switchTab(true));
        tabTerm.setOnClickListener(v -> switchTab(false));
        chatScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> { });
    }

    private TextView tabButton(String label, boolean active) {
        TextView tv = Views.text(this, label, p, 13f, active ? p.text : p.muted, active);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 10));
        tv.setBackground(Theme.rounded(this, 12, active ? p.card : 0x00000000, active ? p.line : 0x00000000, 1));
        tv.setOnClickListener(v -> switchTab("Чат".equals(label)));
        return tv;
    }

    private void renderAll() {
        cardViews.clear();
        streamingView = null;
        streamingBuf.setLength(0);
        messages.removeAllViews();
        if (sess.visibleCount() == 0) {
            messages.addView(hero());
        } else {
            for (Sess.Msg m : sess.snapshot()) {
                if ("tool-anchor".equals(m.role)) {
                    if (store.getBool("showTools", true)) {
                        for (Sess.ToolCall t : m.tools) {
                            Views.ToolCard card = new Views.ToolCard(this, t, p);
                            cardViews.put(t, card);
                            messages.addView(card);
                        }
                    }
                    continue;
                }
                if (m.hidden || m.content == null || m.content.isEmpty()) continue;
                messages.addView(messageView(m));
            }
        }
        scrollDown(true);
    }

    private View hero() {
        LinearLayout box = Views.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Theme.dp(this, 26);
        box.setPadding(pad, pad, pad, pad);
        TextView big = Views.text(this, "✦", p, 30f, 0xFF1A1208, true);
        big.setGravity(Gravity.CENTER);
        big.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 64), Theme.dp(this, 64)));
        big.setBackground(Theme.gradient(this, 20, p.accent, p.accent2));
        box.addView(big);
        TextView title = Views.text(this, "Чем займёмся?", p, 18f, p.text, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Theme.dp(this, 14);
        box.addView(title, tlp);
        box.addView(Views.text(this, "Пишу код, работаю с файлами и запускаю команды в терминале.", p, 13f, p.muted, false));
        LinearLayout sugg = Views.row(this);
        sugg.setGravity(Gravity.CENTER);
        String[] examples = {"Покажи файлы в рабочей папке", "Создай файл и запусти его", "Что ты умеешь?"};
        for (String e : examples) {
            TextView b = Views.text(this, e, p, 12.5f, p.muted, false);
            b.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 12), Theme.dp(this, 8));
            b.setBackground(Theme.rounded(this, 999, p.card, p.line, 1));
            Theme.pressable(b);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.rightMargin = Theme.dp(this, 8);
            blp.topMargin = Theme.dp(this, 14);
            b.setLayoutParams(blp);
            b.setOnClickListener(v -> {
                input.setText(e);
                sendOrStop();
            });
            sugg.addView(b);
        }
        box.addView(sugg);
        return box;
    }

    private View messageView(Sess.Msg m) {
        if ("user".equals(m.role)) {
            LinearLayout wrapper = Views.row(this);
            wrapper.setGravity(Gravity.END);
            TextView bubble = Views.text(this, m.content, p, store.getInt("font", 15), p.text, false);
            bubble.setPadding(Theme.dp(this, 13), Theme.dp(this, 10), Theme.dp(this, 13), Theme.dp(this, 10));
            bubble.setBackground(Theme.rounded(this, 16, p.accentSoft, p.accentLine, 1));
            bubble.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Theme.dp(this, 40);
            lp.bottomMargin = Theme.dp(this, 12);
            wrapper.addView(bubble, lp);
            return wrapper;
        }
        LinearLayout wrapper = Views.row(this);
        wrapper.setGravity(Gravity.TOP);
        TextView avatar = Views.text(this, "✦", p, 13f, 0xFF1A1208, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26)));
        avatar.setBackground(Theme.gradient(this, 9, p.accent, p.accent2));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26));
        alp.rightMargin = Theme.dp(this, 9);
        alp.topMargin = Theme.dp(this, 2);
        wrapper.addView(avatar, alp);

        LinearLayout box = Views.column(this);
        TextView body = Views.text(this, "", p, store.getInt("font", 15), p.text, false);
        body.setText(Markdown.render(m.content, p, this, Theme.fontPx(this)));
        body.setTextIsSelectable(true);
        box.addView(body);

        TextView meta = Views.text(this, metaOf(m), p, 11f, p.muted2, false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Theme.dp(this, 4);
        box.addView(meta, mlp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = Theme.dp(this, 12);
        wrapper.addView(box, blp);
        return wrapper;
    }

    private String metaOf(Sess.Msg m) {
        StringBuilder sb = new StringBuilder();
        sb.append(Agent.MODEL_LABELS[Agent.modelIndex(m.model == null ? store.getStr("model", "mini") : m.model)]);
        if (m.promptTokens > 0 || m.completionTokens > 0) {
            sb.append(" · ↓").append(fmtTokens(m.promptTokens)).append(" ↑").append(fmtTokens(m.completionTokens));
        }
        if (m.ms > 0) sb.append(" · ").append(String.format(Locale.US, "%.1f с", m.ms / 1000.0));
        return sb.toString();
    }

    private static String fmtTokens(int n) {
        return n >= 1000 ? String.format(Locale.US, "%.1fk", n / 1000.0) : String.valueOf(n);
    }

    private void scrollDown(final boolean force) {
        chatScroll.post(() -> {
            int max = Math.max(0, messages.getHeight() - chatScroll.getHeight() + messages.getPaddingBottom());
            boolean near = chatScroll.getScrollY() > max - Theme.dp(MainActivity.this, 220);
            if (force || near) chatScroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void sendOrStop() {
        if (busy) {
            agent.stop();
            setBusy(false);
            Toast.makeText(this, "Остановлено", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        if (store.getStr("key", "").isEmpty()) {
            Toast.makeText(this, "Сначала введи API-ключ", Toast.LENGTH_SHORT).show();
            askKey();
            return;
        }
        input.setText("");
        streamingBuf.setLength(0);
        streamingView = null;

        agent.send(sess, text);
        renderAll();
        typing = Views.text(this, "думаю", p, 13f, p.muted2, false);
        LinearLayout typingRow = Views.row(this);
        typingRow.addView(typing);
        messages.addView(typingRow);
        animateTyping(0);
        scrollDown(true);
    }

    private Handler typingHandler;
    private int typingStep;

    private void animateTyping(final int step) {
        if (typing == null || typing.getParent() == null) return;
        typingStep = step;
        typing.setText("думаю" + new String(new char[(step % 3) + 1]).replace("\0", "."));
        if (typingHandler == null) typingHandler = new Handler(Looper.getMainLooper());
        typingHandler.postDelayed(() -> animateTyping(typingStep + 1), 420);
    }

    private void hideTyping() {
        if (typing != null && typing.getParent() instanceof ViewGroup) {
            ((ViewGroup) typing.getParent()).setVisibility(View.GONE);
        }
        typing = null;
    }

    @Override
    public void onDelta(final String text) {
        if (typing != null) hideTyping();
        streamingBuf.append(text);
        long now = System.currentTimeMillis();
        if (now - lastStreamPaint < 70) return;
        lastStreamPaint = now;
        if (streamingView == null) {
            LinearLayout wrapper = Views.row(this);
            TextView avatar = Views.text(this, "✦", p, 13f, 0xFF1A1208, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26)));
            avatar.setBackground(Theme.gradient(this, 9, p.accent, p.accent2));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26));
            alp.rightMargin = Theme.dp(this, 9);
            wrapper.addView(avatar, alp);
            streamingView = Views.text(this, "", p, store.getInt("font", 15), p.text, false);
            streamingView.setTextIsSelectable(true);
            wrapper.addView(streamingView, Views.lpw(1));
            messages.addView(wrapper);
        }
        streamingView.setText(Markdown.render(Agent.stripToolBlocks(streamingBuf.toString()), p, this, Theme.fontPx(this)));
        scrollDown(false);
    }

    @Override
    public void onAssistantDone(Sess.Msg msg) {
        hideTyping();
        msg.ms = 0;
        streamingView = null;
        streamingBuf.setLength(0);
        saveSess();
        renderAll();
        updateSubtitle();
    }

    @Override
    public void onToolCreated(Sess.ToolCall tool) {
        renderAll();
        scrollDown(false);
    }

    @Override
    public void onToolUpdated(Sess.ToolCall tool) {
        Views.ToolCard card = cardViews.get(tool);
        if (card != null) card.refresh(tool);
        else renderAll();
        scrollDown(false);
    }

    @Override
    public void onError(String message) {
        hideTyping();
        Sess.Msg msg = new Sess.Msg("assistant");
        msg.content = "**Ошибка:** " + message;
        if (message != null && (message.toLowerCase().contains("перегруж") || message.contains("429") || message.contains("50"))) {
            msg.content += "\n\n_Попробуй другую модель — чип сверху._";
        }
        sess.messages.add(msg);
        saveSess();
        renderAll();
        updateSubtitle();
    }

    @Override
    public void onBusy(boolean value) {
        setBusy(value);
    }

    @Override
    public void onStepDone() {
        saveSess();
        updateHints();
    }

    private void setBusy(boolean value) {
        busy = value;
        sendBtn.setText(value ? "■" : "➤");
        sendBtn.setBackground(value
                ? Theme.rounded(this, 14, p.card2, p.line2, 1)
                : Theme.gradient(this, 14, p.accent, p.accent2));
        sendBtn.setTextColor(value ? p.err : 0xFF1A1208);
        updateSubtitle();
    }

    private void updateSubtitle() {
        if (subtitle == null) return;
        subtitle.setText(busy ? "работаю…" : (sess.visibleCount() == 0 ? "готов к работе" : sess.title()));
    }

    private void updateHints() {
        String engine = store.getStr("engine", "builtin");
        String engineName = engine.equals("termux") ? "Termux" : engine.equals("auto") ? "авто" : "встроенный";
        if (hintEngine != null) hintEngine.setText("движок: " + engineName);
        String model = store.getStr("model", "mini");
        int idx = Agent.modelIndex(model);
        if (hintCtx != null) hintCtx.setText("модель " + model + " · окно " + (Agent.MODEL_CTX[idx] / 1000) + "k");
        if (termEngineHint != null) termEngineHint.setText("движок: " + engineName);
        if (termCwd != null) termCwd.setText(store.getStr("home", ""));
        if (termTermux != null) {
            boolean ok = Termux.available(this);
            termTermux.setText(ok ? "Termux найден" : "Termux не установлен");
            termTermux.setTextColor(ok ? p.ok : p.muted2);
        }
        if (modelChip != null) modelChip.setText(model);
    }

    private void switchTab(boolean chat) {
        chatPane.setVisibility(chat ? View.VISIBLE : View.GONE);
        termPane.setVisibility(chat ? View.GONE : View.VISIBLE);
        int active = chat ? 0xFF000000 : 0;
        tabChat.setBackground(Theme.rounded(this, 12, chat ? p.card : active, chat ? p.line : active, 1));
        tabChat.setTextColor(chat ? p.text : p.muted);
        tabTerm.setBackground(Theme.rounded(this, 12, chat ? active : p.card, chat ? active : p.line, 1));
        tabTerm.setTextColor(chat ? p.muted : p.text);
        if (!chat) termInput.requestFocus();
    }

    private void saveSess() {
        sess.updated = System.currentTimeMillis();
        if (!sessions.contains(sess)) sessions.add(sess);
        Sess.saveAll(store, sessions);
    }

    private void showModelDialog() {
        final String[] labels = new String[Agent.MODEL_KEYS.length];
        for (int i = 0; i < Agent.MODEL_KEYS.length; i++) {
            labels[i] = Agent.MODEL_LABELS[i] + " · " + (Agent.MODEL_CTX[i] / 1000) + "k — " + Agent.MODEL_DESC[i];
        }
        new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Выбери модель")
                .setItems(labels, (d, which) -> {
                    store.setStr("model", Agent.MODEL_KEYS[which]);
                    sess.model = Agent.MODEL_KEYS[which];
                    updateHints();
                    saveSess();
                    Toast.makeText(this, "Модель: " + Agent.MODEL_LABELS[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showSessionsDialog() {
        LinearLayout box = Views.column(this);
        final AlertDialog dialog = new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Диалоги")
                .setView(wrapScroll(box))
                .setNegativeButton("Закрыть", null)
                .create();
        final List<Sess> sorted = new ArrayList<>(sessions);
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).updated > sorted.get(i).updated) {
                    Sess tmp = sorted.get(i);
                    sorted.set(i, sorted.get(j));
                    sorted.set(j, tmp);
                }
            }
        }
        for (final Sess s : sorted) {
            LinearLayout row = Views.row(this);
            int pad = Theme.dp(this, 12);
            row.setPadding(pad, pad, pad, pad);
            row.setBackground(Theme.rounded(this, 12, s == sess ? p.accentSoft : p.card, s == sess ? p.accentLine : p.line, 1));
            LinearLayout info = Views.column(this);
            info.addView(Views.text(this, s.title(), p, 13.5f, p.text, true));
            info.addView(Views.text(this, s.visibleCount() + " сообщ. · " + s.model + " · " + new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new java.util.Date(s.updated)), p, 11.5f, p.muted2, false));
            row.addView(info, Views.lpw(1));
            TextView del = Views.text(this, "✕", p, 14f, p.err, false);
            del.setPadding(Theme.dp(this, 10), Theme.dp(this, 8), Theme.dp(this, 10), Theme.dp(this, 8));
            del.setOnClickListener(v -> {
                sessions.remove(s);
                if (s == sess) {
                    sess = sessions.isEmpty() ? Sess.create(store.getStr("model", "mini")) : sessions.get(sessions.size() - 1);
                    if (!sessions.contains(sess)) sessions.add(sess);
                }
                saveSess();
                renderAll();
                dialog.dismiss();
                showSessionsDialog();
            });
            row.addView(del);
            row.setOnClickListener(v -> {
                saveSess();
                sess = s;
                if (Agent.modelIndex(s.model) >= 0) store.setStr("model", s.model);
                store.setStr("model", s.model);
                renderAll();
                updateHints();
                updateSubtitle();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Theme.dp(this, 8);
            box.addView(row, rlp);
        }
        dialog.show();
    }

    private ScrollView wrapScroll(View inner) {
        ScrollView sc = new ScrollView(this);
        sc.addView(inner);
        int pad = Theme.dp(this, 8);
        inner.setPadding(pad, pad, pad, pad);
        return sc;
    }

    private void askKey() {
        LinearLayout box = Views.column(this);
        int pad = Theme.dp(this, 16);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Views.text(this, "AI-агент: чат, файлы, терминал. Ключ хранится только на этом устройстве.", p, 13f, p.muted, false));
        final EditText field = Views.field(this, "lum_…", p);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Theme.dp(this, 12);
        box.addView(field, flp);
        final TextView status = Views.text(this, "", p, 12.5f, p.muted, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Theme.dp(this, 8);
        box.addView(status, slp);
        TextView where = Views.button(this, "Где взять ключ?", p, false);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = Theme.dp(this, 10);
        box.addView(where, wlp);
        where.setOnClickListener(v -> openUrl("https://lumen.unionium.org/chat"));

        final AlertDialog dialog = new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Ключ Lumen")
                .setView(box)
                .setPositiveButton("Сохранить и проверить", null)
                .setNegativeButton("Позже", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String key = field.getText().toString().trim();
            if (key.length() < 12 || !isAscii(key)) {
                status.setTextColor(p.err);
                status.setText("Похоже, это не ключ — нужна строка вида lum_…");
                return;
            }
            status.setTextColor(p.muted);
            status.setText("Проверяю ключ…");
            new Thread(() -> {
                final JSONObject r = ChatClient.ping(store.getStr("base", "https://lumen.unionium.org/api/v1"), key);
                runOnUiThread(() -> {
                    store.setStr("key", key);
                    if (r.optBoolean("ok")) {
                        status.setTextColor(p.ok);
                        status.setText("✔ " + r.optString("message", "ключ принят"));
                        ui.postDelayed(() -> {
                            if (dialog.isShowing()) dialog.dismiss();
                            updateHints();
                        }, 700);
                    } else {
                        status.setTextColor(p.err);
                        status.setText("✖ " + r.optString("message", "не сработало") + " — ключ сохранён, можно поправить в настройках");
                    }
                });
            }).start();
        }));
        dialog.show();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false;
        return true;
    }

    @Override
    public boolean ask(String title, String detail) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = new boolean[]{false};
        ui.post(() -> {
            try {
                new AlertDialog.Builder(this, Theme.dialogTheme(p))
                        .setTitle(title)
                        .setMessage(detail)
                        .setPositiveButton("Выполнить", (d, w) -> {
                            result[0] = true;
                            latch.countDown();
                        })
                        .setNegativeButton("Отмена", (d, w) -> {
                            result[0] = false;
                            latch.countDown();
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    private void termBoot() {
        termLine("встроенный терминал · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, p.muted2);
        termLine("папка: " + store.getStr("home", ""), p.muted2);
        termLine("введи help, чтобы увидеть доступные команды", p.muted2);
    }

    private void termLine(String text, int color) {
        TextView tv = Views.text(this, text, p, 12.5f, color, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        termOutput.addView(tv);
        if (termOutput.getChildCount() > 400) termOutput.removeViewAt(0);
        termScroll.post(() -> termScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void runTerm(String raw) {
        final String cmd = raw == null ? "" : raw.trim();
        if (cmd.isEmpty()) return;
        termHistory.add(cmd);
        termHistoryIndex = termHistory.size();
        termLine("❯ " + cmd, p.accent);
        if ("clear".equals(cmd)) {
            termOutput.removeAllViews();
            return;
        }
        if (store.getBool("confirm", true) && isDangerous(cmd)) {
            confirmOnUi(cmd, () -> executeTerm(cmd));
            return;
        }
        executeTerm(cmd);
    }

    private static boolean isDangerous(String cmd) {
        return cmd.matches(".*(\\brm\\s+-[a-z]*r[a-z]*f?\\s+(/|\\*|~)|\\bsudo\\b|\\bmkfs\\b|\\bdd\\s+if=|\\bshutdown\\b|\\breboot\\b).*");
    }

    private void confirmOnUi(String detail, Runnable onYes) {
        new AlertDialog.Builder(this, Theme.dialogTheme(p))
                .setTitle("Опасная команда")
                .setMessage(detail)
                .setPositiveButton("Выполнить", (d, w) -> onYes.run())
                .setNegativeButton("Отмена", (d, w) -> termLine("отменено", p.muted2))
                .setCancelable(false)
                .show();
    }

    private void executeTerm(final String cmd) {
        new Thread(() -> {
            final JSONObject r = execShell(cmd);
            runOnUiThread(() -> {
                String out = r.optString("output", "");
                if (!out.trim().isEmpty()) termLine(out, r.optBoolean("ok") ? (p.dark ? 0xFFCFEFC9 : 0xFF2F5D3A) : p.err);
                termLine("[" + r.optString("engine", "встроенный") + "]", p.muted2);
            });
        }).start();
    }

    private JSONObject execShell(String cmd) {
        JSONObject out = new JSONObject();
        try {
            String engine = store.getStr("engine", "builtin");
            boolean termuxReady = Termux.available(this);
            boolean builtinCmd = cmd.matches("^\\s*(pwd|ls|cd|cat|echo|mkdir|touch|rm|mv|cp|head|tail|wc|grep|find|date|whoami|uname|du|help|clear)\\b.*");
            boolean useTermux = termuxReady && (engine.equals("termux") || (engine.equals("auto") && !builtinCmd));
            if (useTermux) {
                JSONObject r = Termux.run(this, cmd, store.getStr("home", ""), 60000);
                out.put("ok", r.optBoolean("ok", false));
                out.put("output", r.optString("output", ""));
                out.put("engine", "termux");
            } else {
                String res = shell.run(cmd);
                boolean ok = !res.startsWith("ошибка:");
                out.put("ok", ok);
                out.put("output", ok ? res : res.replaceFirst("^ошибка:\\s*", ""));
                out.put("engine", "встроенный");
            }
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("output", "ошибка: " + e.getMessage());
                out.put("engine", "встроенный");
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (termPane.getVisibility() == View.VISIBLE) {
            switchTab(true);
            return;
        }
        if (busy) {
            agent.stop();
            setBusy(false);
            return;
        }
        saveSess();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHints();
        subtitle.setText(busy ? "работаю…" : (sess == null || sess.visibleCount() == 0 ? "готов к работе" : sess.title()));
    }
}
