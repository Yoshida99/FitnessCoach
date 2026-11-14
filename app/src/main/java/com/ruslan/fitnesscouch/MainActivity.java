package com.ruslan.fitnesscouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    static final int BG = 0xFF050506, CARD = 0xFF111215, CARD2 = 0xFF1C1E24, LINE = 0x40D9B45A,
            TEXT = 0xFFF7F7F6, MUTED = 0xFF9A9EA8, SILVER = 0xFFDADCE1, GOLD = 0xFFD9B45A, RED = 0xFFF06969;
    static final String DEFAULT_MODEL = "openai/gpt-4o-mini", FALLBACK_MODEL = "deepseek/deepseek-chat";
    static final Locale RU = new Locale("ru");
    static final int MATCH = -1, WRAP = -2;

    interface NumCb { void on(float v); }

    Store st;
    SharedPreferences p;
    LinearLayout shell, body, bottom;
    ScrollView scroll;
    final ArrayList<Runnable> stack = new ArrayList<>();
    final Handler ui = new Handler(Looper.getMainLooper());
    String screenId = "";
    Object backCb;

    // Таймер отдыха
    LinearLayout timerBar;
    TextView timerText;
    ProgressBar timerProg;
    long timerEnd;
    int timerTotal;
    AudioTrack track;
    Ringtone ring;

    // AI
    boolean aiBusy;
    String aiError, aiNote, chatDraft = "";
    EditText chatInput;
    JSONArray modelCache;

    // ════════════════════════════════ Жизненный цикл ════════════════════════════════

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        p = st.p;

        shell = col();
        shell.setBackgroundColor(BG);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = col();
        body.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(body, new FrameLayout.LayoutParams(MATCH, WRAP));
        shell.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        buildTimerBar();
        bottom = col();
        shell.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(shell);

        // targetSdk 36: окно всегда «от края до края», поэтому отступы под системные панели
        // и клавиатуру выставляем сами.
        shell.setOnApplyWindowInsetsListener((v, in) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(s.left, s.top, s.right, s.bottom);
            } else {
                v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            }
            return in;
        });

        stack.add(this::home);
        if (!p.contains("name")) stack.add(() -> profile(true));
        render();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stopSignal();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) back();
        else super.onBackPressed();
    }

    // ════════════════════════════════ Навигация ════════════════════════════════

    void render() {
        if (chatInput != null) chatDraft = chatInput.getText().toString();
        chatInput = null;
        body.removeAllViews();
        bottom.removeAllViews();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenId = "";
        stack.get(stack.size() - 1).run();
        syncBack();
    }

    void open(Runnable screen) {
        hideKeyboard();
        stack.add(screen);
        render();
        scroll.scrollTo(0, 0);
    }

    void back() {
        hideKeyboard();
        if (stack.size() > 1) stack.remove(stack.size() - 1);
        render();
        scroll.scrollTo(0, 0);
    }

    void goHome() {
        hideKeyboard();
        while (stack.size() > 1) stack.remove(stack.size() - 1);
        render();
        scroll.scrollTo(0, 0);
    }

    /** Перерисовать текущий экран, сохранив прокрутку. */
    void refresh() {
        final int y = scroll.getScrollY();
        render();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    /** Системная «назад» на Android 13+: перехватываем её, только когда есть куда возвращаться. */
    void syncBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = stack.size() > 1;
        if (need && backCb == null) {
            OnBackInvokedCallback cb = this::back;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCb = cb;
        } else if (!need && backCb != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCb);
            backCb = null;
        }
    }

    void keepScreenOn() { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }

    // ════════════════════════════════ Профиль (данные) ════════════════════════════════

    String name() { return p.getString("name", ""); }
    boolean male() { return p.getBoolean("male", true); }
    int age() { return p.getInt("age", 30); }
    float height() { return p.getFloat("height", 177f); }
    float goal() { return p.getFloat("goal", 77f); }
    float weight() { return st.latest("bw", 70f); }

    /** Суточные цели: {ккал, белок, жиры, углеводы}. Формула Миффлина — Сан Жеора, активность 1.55. */
    int[] targets() {
        float w = weight();
        float bmr = 10 * w + 6.25f * height() - 5 * age() + (male() ? 5 : -161);
        float kcal = bmr * 1.55f + (goal() > w + 0.5f ? 300 : goal() < w - 0.5f ? -400 : 0);
        int k = Math.round(kcal / 50f) * 50;
        int prot = Math.round(w * 2f), fat = Math.round(w * 0.9f);
        return new int[]{k, prot, fat, Math.max(0, (k - prot * 4 - fat * 9) / 4)};
    }

    // ════════════════════════════════ Главная ════════════════════════════════

    void home() {
        screenId = "home";
        Calendar now = Calendar.getInstance();
        Program.Day day = Program.forDow(now.get(Calendar.DAY_OF_WEEK));

        body.addView(label(new SimpleDateFormat("EEEE, d MMMM", RU).format(now.getTime())));
        body.addView(t(name().isEmpty() ? "Fitness Couch" : "Привет, " + name(), 28, TEXT, true));
        weekStrip();

        LinearLayout c = card();
        if (day != null) {
            float[] pr = dayProgress(day, Store.today());
            c.addView(label("Сегодня · " + day.tag));
            c.addView(t(day.title, 23, TEXT, true));
            c.addView(t(day.sub, 14, MUTED, false));
            c.addView(bar((int) pr[0], (int) pr[1]));
            c.addView(t((int) pr[0] + " из " + (int) pr[1] + " подходов", 13, SILVER, false));
            String cta = pr[0] == 0 ? "Начать тренировку" : pr[0] < pr[1] ? "Продолжить" : "Выполнено — открыть";
            c.addView(btn(cta, true, () -> open(() -> workout(day))));
        } else {
            Program.Day next = Program.next(now.get(Calendar.DAY_OF_WEEK));
            c.addView(label("Сегодня"));
            c.addView(t("День отдыха", 23, TEXT, true));
            c.addView(t("Следующая тренировка: " + next.tag + " — " + next.title, 14, MUTED, false));
            c.addView(btn("Всё равно потренироваться", false, () -> open(this::program)));
        }
        body.addView(c);

        float w = weight(), g = goal(), start = p.getFloat("start_w", w);
        LinearLayout gc = card();
        gc.addView(label("Вес тела"));
        gc.addView(t(Fmt.n(w) + " кг  →  " + Fmt.n(g) + " кг", 21, TEXT, true));
        float span = g - start;
        int pct = span == 0 ? 100 : Math.round(Math.max(0, Math.min(1, (w - start) / span)) * 100);
        gc.addView(bar(pct, 100));
        float rate = st.weeklyRate();
        String left = Math.abs(g - w) < 0.05f ? "цель достигнута" : "осталось " + Fmt.n(Math.abs(g - w)) + " кг";
        gc.addView(t(left + (Float.isNaN(rate) ? "" : " · темп " + Fmt.signed(rate) + " кг/нед"), 13, SILVER, false));
        gc.setOnClickListener(v -> open(this::progress));
        body.addView(gc);

        String[][] tiles = {
                {"AI-тренер", "Вопросы по технике, весам, питанию"}, {"Программа", "4 тренировки в неделю"},
                {"Прогресс", "Рекорды, графики, календарь"}, {"Питание", "Калории и белок на день"},
                {"Таймер", "Отдых между подходами"}, {"Настройки", "Профиль, AI, данные"}};
        Runnable[] go = {this::chat, this::program, this::progress, this::nutrition, this::timerScreen, this::settings};
        for (int i = 0; i < tiles.length; i += 2) {
            LinearLayout r = row();
            for (int j = i; j < i + 2; j++) {
                final Runnable dest = go[j];
                LinearLayout tile = card();
                tile.setMinimumHeight(dp(86));
                tile.addView(t(tiles[j][0], 16, TEXT, true));
                tile.addView(t(tiles[j][1], 12, MUTED, false));
                tile.setOnClickListener(v -> open(dest));
                clickable(tile, 20);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1);
                lp.setMargins(j == i ? 0 : dp(5), dp(10), j == i ? dp(5) : 0, 0);
                r.addView(tile, lp);
            }
            body.addView(r);
        }
    }

    void weekStrip() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        String today = Store.today();
        Set<String> days = st.days();
        String[] names = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
        LinearLayout r = row();
        r.setPadding(0, dp(14), 0, dp(4));
        for (int i = 0; i < 7; i++) {
            String iso = Store.iso(c.getTime());
            boolean done = days.contains(iso), isToday = iso.equals(today);
            boolean planned = Program.forDow(c.get(Calendar.DAY_OF_WEEK)) != null;
            LinearLayout cell = col();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView nm = t(names[i], 11, isToday ? GOLD : MUTED, isToday);
            nm.setGravity(Gravity.CENTER);
            cell.addView(nm);
            TextView dot = t(done ? "✓" : String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 13,
                    done ? 0xFF000000 : planned ? SILVER : MUTED, done || isToday);
            dot.setGravity(Gravity.CENTER);
            dot.setPadding(0, 0, 0, 0);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(done ? GOLD : 0x00000000);
            if (!done && (planned || isToday)) g.setStroke(dp(isToday ? 2 : 1), isToday ? GOLD : LINE);
            dot.setBackground(g);
            cell.addView(dot, new LinearLayout.LayoutParams(dp(36), dp(36)));
            r.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1));
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        body.addView(r);
    }

    // ════════════════════════════════ Программа и тренировка ════════════════════════════════

    void program() {
        topBar("Программа");
        String today = Store.today();
        for (Program.Day d : Program.DAYS) {
            float[] pr = dayProgress(d, today);
            LinearLayout c = card();
            c.addView(label(d.tag));
            c.addView(t(d.title, 20, TEXT, true));
            StringBuilder list = new StringBuilder();
            for (Program.Ex e : d.ex) list.append(list.length() > 0 ? "\n" : "").append(e.name).append(" — ").append(e.target());
            c.addView(t(list.toString(), 13, MUTED, false));
            if (pr[0] > 0) c.addView(t("Сегодня записано: " + (int) pr[0] + " из " + (int) pr[1], 13, GOLD, true));
            c.setOnClickListener(v -> open(() -> workout(d)));
            clickable(c, 20);
            body.addView(c);
        }
    }

    /** {сделано подходов, всего подходов, тоннаж} за дату. */
    float[] dayProgress(Program.Day d, String date) {
        float done = 0, total = 0, tons = 0;
        for (Program.Ex e : d.ex) {
            total += e.sets;
            JSONObject s = st.session(e.id, date);
            int n = 0;
            for (int i = 0; i < Store.count(s); i++) {
                if (Store.rep(s, i) > 0) { n++; tons += Store.rep(s, i) * Store.wt(s, i); }
            }
            done += Math.min(n, e.sets);
        }
        return new float[]{done, total, tons};
    }

    void workout(Program.Day d) {
        screenId = "workout";
        keepScreenOn();
        topBar(d.tag + " · " + d.title);
        final ProgressBar pb = bar(0, 1);
        final TextView sum = t("", 14, GOLD, true);
        final Runnable upd = () -> {
            float[] pr = dayProgress(d, Store.today());
            pb.setMax((int) pr[1]);
            pb.setProgress((int) pr[0]);
            sum.setText((int) pr[0] + " из " + (int) pr[1] + " подходов · тоннаж " + Fmt.n(Math.round(pr[2])) + " кг");
        };
        upd.run();
        body.addView(pb);
        body.addView(sum);
        body.addView(t("Нажми на подход, когда сделал его. Ещё нажатие — минус повтор. Удержание — ввести число.", 12, MUTED, false));
        for (Program.Ex e : d.ex) body.addView(exCard(e, upd));
        body.addView(btn("Завершить тренировку", true, () -> open(() -> summary(d))));
    }

    LinearLayout exCard(final Program.Ex e, final Runnable upd) {
        LinearLayout c = card();
        final JSONObject last = st.lastBefore(e.id, Store.today());
        final float[] w = {e.bodyweight() ? 0 : p.getFloat("w_" + e.id, e.w)};
        final float sug = suggest(e, last);

        LinearLayout head = row();
        head.addView(t(e.name, 17, TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        head.addView(t(e.target(), 14, GOLD, true));
        c.addView(head);
        c.addView(t(last == null ? "Записей пока нет" : "Прошлый раз, " + Store.human(last.optString("d")) + ": " + describe(last),
                13, MUTED, false));
        final TextView hint = t(advise(e, last, sug, w[0]), 13, SILVER, false);
        c.addView(hint);

        if (!e.bodyweight()) {
            final TextView wText = t(Fmt.n(w[0]) + " кг", 22, GOLD, true);
            wText.setGravity(Gravity.CENTER);
            final NumCb setW = v -> {
                w[0] = Math.max(0, Math.round(v * 4) / 4f);
                p.edit().putFloat("w_" + e.id, w[0]).apply();
                wText.setText(Fmt.n(w[0]) + " кг");
                hint.setText(advise(e, last, sug, w[0]));
            };
            wText.setOnClickListener(v -> numberDialog("Вес, кг", Fmt.n(w[0]), true, setW));
            hint.setOnClickListener(v -> { if (sug > 0 && w[0] < sug) setW.on(sug); });
            LinearLayout wr = row();
            wr.setPadding(0, dp(6), 0, dp(2));
            wr.addView(square("−", () -> setW.on(w[0] - e.step)), new LinearLayout.LayoutParams(dp(52), dp(46)));
            wr.addView(wText, new LinearLayout.LayoutParams(0, dp(46), 1));
            wr.addView(square("+", () -> setW.on(w[0] + e.step)), new LinearLayout.LayoutParams(dp(52), dp(46)));
            c.addView(wr);
        }

        LinearLayout sets = row();
        sets.setPadding(0, dp(6), 0, 0);
        JSONObject cur = st.session(e.id, Store.today());
        for (int i = 0; i < e.sets; i++) {
            final int idx = i;
            final TextView chip = t("", 17, TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, 0, 0, 0);
            paintChip(chip, Store.rep(cur, i));
            chip.setOnClickListener(v -> {
                JSONObject s = st.session(e.id, Store.today());
                int r = Store.rep(s, idx);
                if (r == 0) {
                    st.logSet(e, idx, e.max, w[0]);
                    startRest(e.rest);
                } else {
                    st.logSet(e, idx, r - 1, Store.wt(s, idx));
                }
                paintChip(chip, Store.rep(st.session(e.id, Store.today()), idx));
                upd.run();
            });
            chip.setOnLongClickListener(v -> {
                JSONObject s = st.session(e.id, Store.today());
                final int r = Store.rep(s, idx);
                final float sw = r > 0 ? Store.wt(s, idx) : w[0];
                numberDialog("Подход " + (idx + 1) + ": повторов", r > 0 ? String.valueOf(r) : "", false, val -> {
                    int n = Math.max(0, Math.min(999, Math.round(val)));
                    st.logSet(e, idx, n, sw);
                    if (r == 0 && n > 0) startRest(e.rest);
                    paintChip(chip, n);
                    upd.run();
                });
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(i == 0 ? 0 : dp(6), 0, 0, 0);
            sets.addView(chip, lp);
        }
        c.addView(sets);

        final LinearLayout cues = col();
        cues.setVisibility(View.GONE);
        for (String s : e.cues) cues.addView(t("•  " + s, 14, SILVER, false));
        cues.addView(t("Острая боль в суставе или спине — остановись, сбрось вес или замени упражнение.", 13, RED, false));
        cues.addView(link("Спросить AI-тренера про технику", () ->
                ask("Разбери технику упражнения «" + e.name + "»: главные ошибки и как их исправить.")));
        LinearLayout foot = row();
        foot.addView(link("Техника", () -> cues.setVisibility(cues.getVisibility() == View.GONE ? View.VISIBLE : View.GONE)));
        foot.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        foot.addView(link("Отдых " + Fmt.mmss(e.rest), () -> startRest(e.rest)));
        c.addView(foot);
        c.addView(cues);
        return c;
    }

    void paintChip(TextView chip, int reps) {
        chip.setText(reps > 0 ? String.valueOf(reps) : "·");
        chip.setTextColor(reps > 0 ? 0xFF000000 : MUTED);
        chip.setBackground(tap(reps > 0 ? GOLD : CARD2, 14, reps > 0 ? 0 : LINE));
    }

    /** «60 кг × 8 · 8 · 7;  57.5 кг × 6» */
    static String describe(JSONObject s) {
        StringBuilder b = new StringBuilder();
        float cw = -1;
        for (int i = 0; i < Store.count(s); i++) {
            int r = Store.rep(s, i);
            if (r == 0) continue;
            float w = Store.wt(s, i);
            if (w != cw) {
                if (b.length() > 0) b.append(";  ");
                if (w > 0) b.append(Fmt.n(w)).append(" кг × ");
                cw = w;
            } else {
                b.append(" · ");
            }
            b.append(r);
        }
        return b.length() == 0 ? "—" : b.toString();
    }

    /** Двойная прогрессия: если в прошлый раз все подходы сделаны по верхней границе — вес пора поднять. */
    static float suggest(Program.Ex e, JSONObject last) {
        if (last == null || e.bodyweight()) return 0;
        int done = 0;
        float lw = 0;
        for (int i = 0; i < Store.count(last); i++) {
            int r = Store.rep(last, i);
            if (r == 0) continue;
            if (r < e.max) return 0;
            done++;
            lw = Math.max(lw, Store.wt(last, i));
        }
        return done >= e.sets ? lw + e.step : 0;
    }

    static String advise(Program.Ex e, JSONObject last, float sug, float curW) {
        if (last == null) return "Возьми вес, с которым в конце подхода остаётся 1–2 повтора в запасе.";
        int done = 0, low = 0, top = 0;
        for (int i = 0; i < Store.count(last); i++) {
            int r = Store.rep(last, i);
            if (r == 0) continue;
            done++;
            if (r < e.min) low++;
            if (r >= e.max) top++;
        }
        if (e.bodyweight()) {
            return done >= e.sets && top == done ? "Все подходы по " + e.max + " — добавь 1–2 повтора или отягощение."
                    : "Цель — " + e.max + " повторов в каждом подходе.";
        }
        if (sug > 0) {
            return curW >= sug ? "Вес повышен. Цель — не меньше " + e.min + " повторов в каждом подходе."
                    : "Все подходы по " + e.max + " — пора добавить. Нажми сюда, чтобы поставить " + Fmt.n(sug) + " кг.";
        }
        if (low >= 2) return "Два и более подхода ниже " + e.min + " повторов — вес тяжеловат. Оставь его или сбрось на шаг.";
        return "Оставь вес и добирай до " + e.max + " повторов в каждом подходе.";
    }

    void summary(Program.Day d) {
        topBar("Итог тренировки");
        String today = Store.today();
        float[] pr = dayProgress(d, today);
        if (pr[0] == 0) {
            body.addView(t("Сегодня подходы не записаны. Отмечай их на экране тренировки — тогда здесь появится итог, а тренер сможет подсказать веса.",
                    15, SILVER, false));
            body.addView(btn("На главную", false, this::goHome));
            return;
        }
        LinearLayout stats = row();
        stats.addView(stat("Подходы", (int) pr[0] + " / " + (int) pr[1]), statLp(false));
        stats.addView(stat("Тоннаж", Fmt.n(Math.round(pr[2])) + " кг"), statLp(true));
        body.addView(stats);

        LinearLayout c = card();
        final StringBuilder log = new StringBuilder();
        for (Program.Ex e : d.ex) {
            JSONObject s = st.session(e.id, today);
            float[] before = st.best(e.id, today), now = st.best(e.id, null);
            boolean record = s != null && before[0] > 0 && now[0] > before[0] + 0.01f;
            LinearLayout r = row();
            r.addView(t(e.name, 15, s == null ? MUTED : TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1));
            if (record) r.addView(t("РЕКОРД", 11, GOLD, true));
            c.addView(r);
            c.addView(t(s == null ? "пропущено" : describe(s), 13, s == null ? MUTED : SILVER, false));
            log.append(e.name).append(" (план ").append(e.target()).append("): ")
                    .append(s == null ? "пропущено" : describe(s)).append(record ? " — личный рекорд" : "").append("\n");
        }
        body.addView(c);
        body.addView(btn("Разбор от AI-тренера", true, () -> ask("Разбери мою сегодняшнюю тренировку «" + d.title + "»:\n" + log
                + "Что получилось, где резерв и какие веса ставить в следующий раз?")));
        body.addView(btn("На главную", false, this::goHome));
    }

    // ════════════════════════════════ Таймер отдыха ════════════════════════════════

    void buildTimerBar() {
        timerBar = col();
        timerBar.setBackgroundColor(CARD2);
        timerBar.setVisibility(View.GONE);
        timerProg = bar(0, 1000);
        timerBar.addView(timerProg, new LinearLayout.LayoutParams(MATCH, dp(3)));
        LinearLayout r = row();
        r.setPadding(dp(18), dp(4), dp(8), dp(4));
        timerText = t("", 20, GOLD, true);
        r.addView(timerText, new LinearLayout.LayoutParams(0, WRAP, 1));
        r.addView(link("+30 с", () -> { if (timerEnd > 0) { timerEnd += 30000; timerTotal += 30; } }));
        r.addView(link("Стоп", this::stopRest));
        timerBar.addView(r);
        shell.addView(timerBar, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (timerEnd == 0) return;
            long left = timerEnd - SystemClock.elapsedRealtime();
            if (left <= 0) {
                timerEnd = 0;
                timerText.setText("Отдых окончен — следующий подход");
                timerProg.setProgress(0);
                vibrateEnd();
                playSignal(signal());
                ui.postDelayed(() -> { if (timerEnd == 0) timerBar.setVisibility(View.GONE); }, 5000);
                return;
            }
            timerText.setText("Отдых  " + Fmt.mmss((left + 999) / 1000));
            timerProg.setProgress((int) (left / (timerTotal * 1f)));
            ui.postDelayed(this, 200);
        }
    };

    void startRest(int sec) {
        timerTotal = sec;
        timerEnd = SystemClock.elapsedRealtime() + sec * 1000L;
        timerBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(tick);
        tick.run();
    }

    void stopRest() {
        timerEnd = 0;
        ui.removeCallbacks(tick);
        timerBar.setVisibility(View.GONE);
    }

    void timerScreen() {
        keepScreenOn();
        topBar("Таймер");
        body.addView(t("Таймер запускается сам после каждого записанного подхода и виден внизу на любом экране. Здесь можно запустить его вручную.",
                14, MUTED, false));
        int[] presets = {60, 90, 120, 150, 180, 240};
        for (int i = 0; i < presets.length; i += 3) {
            LinearLayout r = row();
            for (int j = i; j < i + 3; j++) {
                final int sec = presets[j];
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1);
                lp.setMargins(j == i ? 0 : dp(8), dp(10), 0, 0);
                TextView b = square(Fmt.mmss(sec), () -> startRest(sec));
                b.setTextSize(20);
                r.addView(b, lp);
            }
            body.addView(r);
        }
        body.addView(btn("Остановить", false, this::stopRest));
        body.addView(btn("Сигнал в конце отдыха: " + Signal.name(signal()), false, () -> open(this::signalScreen)));
    }

    void vibrateEnd() {
        long[] pattern = {0, 350, 150, 350, 150, 600};
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else v.vibrate(pattern, -1);
        } catch (Exception ignored) {
        }
    }

    /** Выбранный сигнал. Старая настройка «звук выключен» из 2.0 переносится как «Без звука». */
    String signal() {
        String s = p.getString("signal", "");
        return s.isEmpty() ? (p.getBoolean("beep", true) ? Signal.DEFAULT : "none") : s;
    }

    /** Сигнал идёт по каналу будильника: он громче канала уведомлений и не глушится беззвучным режимом. */
    void playSignal(String id) {
        stopSignal();
        if ("none".equals(id)) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            if ("system".equals(id)) {
                ring = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (ring != null) {
                    ring.setAudioAttributes(attrs);
                    ring.play();
                }
                return;
            }
            short[] pcm = Signal.build(id);
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Signal.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            final AudioTrack tr = new AudioTrack(attrs, format, pcm.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            tr.write(pcm, 0, pcm.length);
            tr.play();
            track = tr;
            ui.postDelayed(() -> { if (track == tr) stopSignal(); }, Signal.durationMs(id) + 400);
        } catch (Exception ignored) {
        }
    }

    void stopSignal() {
        try {
            if (track != null) track.release();
            if (ring != null && ring.isPlaying()) ring.stop();
        } catch (Exception ignored) {
        }
        track = null;
        ring = null;
    }

    void signalScreen() {
        topBar("Сигнал таймера");
        body.addView(t("Нажми на вариант, чтобы выбрать его и послушать. Сигнал играет на громкости будильника, поэтому слышен и в беззвучном режиме.",
                14, MUTED, false));
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            body.addView(t("Громкость будильника на телефоне сейчас на нуле — сигнала не будет слышно. Прибавь её в настройках звука.", 14, RED, false));
        }
        String cur = signal();
        for (int i = 0; i < Signal.IDS.length; i++) {
            final String id = Signal.IDS[i];
            boolean sel = id.equals(cur);
            body.addView(btn((sel ? "✓  " : "") + Signal.NAMES[i], sel, () -> {
                p.edit().putString("signal", id).apply();
                refresh();
                playSignal(id);
            }));
        }
        body.addView(t("В конце отдыха телефон также вибрирует тремя импульсами.", 13, MUTED, false));
    }

    // ════════════════════════════════ Прогресс ════════════════════════════════

    void progress() {
        topBar("Прогресс");
        Calendar c = Calendar.getInstance();
        int sinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        LinearLayout stats = row();
        stats.addView(stat("Эта неделя", st.trainedSince(sinceMonday) + " / " + Program.DAYS.length), statLp(false));
        stats.addView(stat("30 дней", String.valueOf(st.trainedSince(30))), statLp(true));
        stats.addView(stat("Всего", String.valueOf(st.days().size())), statLp(true));
        body.addView(stats);

        float w = weight(), g = goal();
        LinearLayout bw = card();
        bw.addView(label("Вес тела"));
        bw.addView(t(Fmt.n(w) + " кг", 26, TEXT, true));
        float[] vals = st.values("bw");
        if (vals.length >= 2) bw.addView(new LineChart(this, vals, g, "", GOLD, MUTED), new LinearLayout.LayoutParams(MATCH, dp(140)));
        bw.addView(t(trendText(st.weeklyRate(), w, g), 13, SILVER, false));
        float[] waist = st.values("waist");
        if (waist.length > 0) {
            String ws = "Талия: " + Fmt.n(waist[waist.length - 1]) + " см";
            if (waist.length > 1) ws += " (" + Fmt.signed(waist[waist.length - 1] - waist[0]) + " см с первого замера)";
            bw.addView(t(ws, 13, SILVER, false));
        }
        bw.addView(btn("Записать вес и талию", false, () -> open(this::measure)));
        body.addView(bw);

        calendarCard();

        LinearLayout rc = card();
        rc.addView(label("Личные рекорды"));
        LinkedHashMap<String, Program.Ex> uniq = new LinkedHashMap<>();
        for (Program.Day d : Program.DAYS) for (Program.Ex e : d.ex) if (!uniq.containsKey(e.id)) uniq.put(e.id, e);
        int shown = 0;
        for (final Program.Ex e : uniq.values()) {
            float[] b = st.best(e.id, null);
            if (b[0] <= 0) continue;
            shown++;
            LinearLayout r = row();
            r.setMinimumHeight(dp(48));
            LinearLayout nameCol = col();
            nameCol.addView(t(e.name, 15, TEXT, true));
            nameCol.addView(t(b[1] > 0 ? "расчётный максимум на 1 раз ≈ " + Fmt.n(Math.round(b[0])) + " кг" : "свой вес", 12, MUTED, false));
            r.addView(nameCol, new LinearLayout.LayoutParams(0, WRAP, 1));
            r.addView(t(b[1] > 0 ? Fmt.n(b[1]) + " кг × " + (int) b[2] : (int) b[2] + " повт.", 16, GOLD, true));
            r.setOnClickListener(v -> open(() -> exercise(e)));
            clickable(r, 10);
            rc.addView(r);
        }
        if (shown == 0) rc.addView(t("Рекорды появятся после первой записанной тренировки.", 14, MUTED, false));
        body.addView(rc);
        body.addView(btn("Анализ прогресса от AI-тренера", true, () ->
                ask("Проанализируй мой прогресс по журналу тренировок и весу тела. Что идёт хорошо, что буксует и что поменять в ближайшие две недели?")));
    }

    static String trendText(float rate, float w, float g) {
        if (Float.isNaN(rate)) return "Взвешивайся раз в неделю утром натощак — через две записи появится темп.";
        String r = "Темп за последние недели: " + Fmt.signed(rate) + " кг/нед. ";
        if (g > w + 0.5f) {
            if (rate < 0.1f) return r + "Вес стоит — добавь 150–200 ккал в день.";
            if (rate <= 0.5f) return r + "Хороший темп для набора без лишнего жира.";
            return r + "Быстровато: часть прибавки уйдёт в жир. Убери 150–200 ккал.";
        }
        if (g < w - 0.5f) {
            if (rate > -0.2f) return r + "Вес почти не уходит — убери 150–200 ккал в день.";
            if (rate >= -0.8f) return r + "Хороший темп снижения.";
            return r + "Слишком быстро: рискуешь потерять мышцы. Добавь 150–200 ккал.";
        }
        return r + "Вес у цели — держи.";
    }

    void calendarCard() {
        LinearLayout c = card();
        Calendar cal = Calendar.getInstance();
        c.addView(label(new SimpleDateFormat("LLLL yyyy", RU).format(cal.getTime())));
        String today = Store.today();
        Set<String> days = st.days();
        int month = cal.get(Calendar.MONTH);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int cellNo = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        LinearLayout r = row();
        for (int i = 0; i < cellNo; i++) r.addView(new View(this), new LinearLayout.LayoutParams(0, dp(40), 1));
        while (cal.get(Calendar.MONTH) == month) {
            if (cellNo % 7 == 0 && r.getChildCount() > 0) {
                c.addView(r);
                r = row();
            }
            String iso = Store.iso(cal.getTime());
            boolean done = days.contains(iso), isToday = iso.equals(today);
            TextView cell = t(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 13, done ? 0xFF000000 : isToday ? GOLD : SILVER, done || isToday);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, 0, 0, 0);
            cell.setBackground(shape(done ? GOLD : 0x00000000, 10, isToday && !done ? GOLD : 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            r.addView(cell, lp);
            cellNo++;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        while (r.getChildCount() < 7) r.addView(new View(this), new LinearLayout.LayoutParams(0, dp(40), 1));
        c.addView(r);
        body.addView(c);
    }

    void exercise(Program.Ex e) {
        topBar(e.name);
        JSONArray a = st.sessions(e.id);
        float[] series = new float[a.length()];
        for (int i = 0; i < series.length; i++) {
            JSONObject s = a.optJSONObject(i);
            for (int k = 0; k < Store.count(s); k++) {
                float w = Store.wt(s, k);
                series[i] = Math.max(series[i], w > 0 ? Store.e1rm(w, Store.rep(s, k)) : Store.rep(s, k));
            }
        }
        if (series.length >= 2) {
            LinearLayout c = card();
            c.addView(label(e.bodyweight() ? "Лучший подход, повторы" : "Расчётный максимум на 1 раз, кг"));
            c.addView(new LineChart(this, series, 0, "", GOLD, MUTED), new LinearLayout.LayoutParams(MATCH, dp(150)));
            body.addView(c);
        }
        LinearLayout c = card();
        c.addView(label("Последние тренировки"));
        for (int i = a.length() - 1; i >= Math.max(0, a.length() - 15); i--) {
            JSONObject s = a.optJSONObject(i);
            LinearLayout r = row();
            r.addView(t(Store.human(s.optString("d")), 13, MUTED, false), new LinearLayout.LayoutParams(dp(76), WRAP));
            r.addView(t(describe(s), 14, TEXT, false), new LinearLayout.LayoutParams(0, WRAP, 1));
            c.addView(r);
        }
        if (a.length() == 0) c.addView(t("Записей пока нет.", 14, MUTED, false));
        body.addView(c);
    }

    void measure() {
        topBar("Замеры");
        body.addView(label("Вес, кг"));
        final EditText w = input("", true);
        w.setText(Fmt.n(weight()));
        body.addView(w);
        body.addView(label("Талия, см — по желанию"));
        final EditText waist = input("например, 84", true);
        body.addView(waist);
        body.addView(t("Талию меряй раз в неделю утром на уровне пупка. Вес растёт, а талия стоит — значит, набираешь мышцы, а не живот.",
                13, MUTED, false));
        body.addView(btn("Сохранить", true, () -> {
            Float wv = num(w), ws = num(waist);
            if (wv == null || wv < 30 || wv > 250) { toast("Проверь вес"); return; }
            if (waist.getText().length() > 0 && (ws == null || ws < 40 || ws > 200)) { toast("Проверь талию"); return; }
            st.addPoint("bw", Store.today(), wv);
            if (ws != null) st.addPoint("waist", Store.today(), ws);
            toast("Записано");
            back();
        }));
    }

    // ════════════════════════════════ Питание ════════════════════════════════

    void nutrition() {
        topBar("Питание");
        int[] tg = targets();
        LinearLayout c = card();
        c.addView(label("Цель на день"));
        c.addView(t(tg[0] + " ккал", 26, TEXT, true));
        c.addView(t("Белок " + tg[1] + " г  ·  жиры " + tg[2] + " г  ·  углеводы " + tg[3] + " г", 14, GOLD, true));
        c.addView(t("Рассчитано по твоему весу, росту и возрасту (формула Миффлина — Сан Жеора, 3–4 тренировки в неделю). "
                + "Это отправная точка: сверяйся с темпом веса на экране «Прогресс».", 12, MUTED, false));
        body.addView(c);

        String[][] meals = {
                {"Завтрак", "Овсянка 80 г + 3 яйца + банан", "650", "30"},
                {"Обед", "Рис или гречка + курица 200 г + овощи", "850", "50"},
                {"Перекус", "Творог 200 г + орехи 20 г", "450", "36"},
                {"Ужин", "Рыба или мясо + картофель или гречка + салат", "750", "42"}};
        int kcal = 0, prot = 0;
        String date = Store.today();
        for (int i = 0; i < meals.length; i++) {
            if (p.getBoolean("meal_" + i + "_" + date, false)) {
                kcal += Integer.parseInt(meals[i][2]);
                prot += Integer.parseInt(meals[i][3]);
            }
        }
        LinearLayout e = card();
        e.addView(label("Съедено сегодня"));
        e.addView(bar(kcal, tg[0]));
        e.addView(t("≈ " + kcal + " из " + tg[0] + " ккал  ·  белок ≈ " + prot + " из " + tg[1] + " г", 14, SILVER, false));
        body.addView(e);

        for (int i = 0; i < meals.length; i++) {
            final String key = "meal_" + i + "_" + date;
            final boolean eaten = p.getBoolean(key, false);
            LinearLayout m = card();
            LinearLayout r = row();
            LinearLayout info = col();
            info.addView(t(meals[i][0], 17, TEXT, true));
            info.addView(t(meals[i][1], 14, SILVER, false));
            info.addView(t("≈ " + meals[i][2] + " ккал · белок ≈ " + meals[i][3] + " г", 12, MUTED, false));
            r.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1));
            TextView mark = t(eaten ? "✓" : "", 18, 0xFF000000, true);
            mark.setGravity(Gravity.CENTER);
            mark.setPadding(0, 0, 0, 0);
            mark.setBackground(shape(eaten ? GOLD : 0x00000000, 12, eaten ? 0 : LINE));
            r.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
            m.addView(r);
            m.setOnClickListener(v -> { p.edit().putBoolean(key, !eaten).apply(); refresh(); });
            clickable(m, 20);
            body.addView(m);
        }
        body.addView(btn("Меню на неделю от AI-тренера", true, () -> ask("Составь меню на неделю под мои цели: "
                + tg[0] + " ккал и " + tg[1] + " г белка в день. Простые продукты из обычного магазина, с граммовками.")));
    }

    // ════════════════════════════════ AI-тренер ════════════════════════════════

    String key() { return p.getString("or_key", "").trim(); }

    String model() {
        String m = p.getString("model", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    /** Всё, что тренер должен знать об атлете, собирается из живых данных перед каждым запросом. */
    String coachContext() {
        Calendar now = Calendar.getInstance();
        Program.Day today = Program.forDow(now.get(Calendar.DAY_OF_WEEK));
        int[] tg = targets();
        float rate = st.weeklyRate(), waist = st.latest("waist", 0);
        StringBuilder s = new StringBuilder();
        s.append("Ты персональный фитнес-тренер в приложении Fitness Couch. Отвечай по-русски, конкретно и по делу, ")
                .append("простым текстом без Markdown-разметки. Называй точные веса, подходы и повторы, опираясь на данные ниже. ")
                .append("Не ставь диагнозов; при боли советуй снизить нагрузку и обратиться к врачу.\n\n");
        s.append("Атлет: ").append(name().isEmpty() ? "без имени" : name()).append(male() ? ", мужчина, " : ", женщина, ")
                .append(age()).append(" лет, рост ").append(Fmt.n(height())).append(" см, вес ").append(Fmt.n(weight()))
                .append(" кг, цель ").append(Fmt.n(goal())).append(" кг");
        if (waist > 0) s.append(", талия ").append(Fmt.n(waist)).append(" см");
        if (!Float.isNaN(rate)) s.append(", темп веса ").append(Fmt.signed(rate)).append(" кг/нед");
        s.append(". Хочет набрать мышцы без роста живота.\n");
        s.append("Питание: цель ").append(tg[0]).append(" ккал, белок ").append(tg[1]).append(" г в день.\n");
        s.append("Сегодня ").append(new SimpleDateFormat("EEEE, d MMMM yyyy", RU).format(now.getTime())).append(": ")
                .append(today == null ? "день отдыха" : today.title).append(". Тренировок за 7 дней: ")
                .append(st.trainedSince(7)).append(", за 30 дней: ").append(st.trainedSince(30)).append(".\n\n");
        s.append("Программа (план; рабочий вес; последняя записанная тренировка):\n");
        String date = Store.today();
        for (Program.Day d : Program.DAYS) {
            s.append(d.tag).append(" — ").append(d.title).append(":\n");
            for (Program.Ex e : d.ex) {
                s.append("- ").append(e.name).append(", ").append(e.target());
                if (!e.bodyweight()) s.append(", ").append(Fmt.n(p.getFloat("w_" + e.id, e.w))).append(" кг");
                JSONObject cur = st.session(e.id, date), last = st.lastBefore(e.id, date);
                if (cur != null) s.append("; сегодня: ").append(describe(cur));
                if (last != null) s.append("; ").append(Store.human(last.optString("d"))).append(": ").append(describe(last));
                s.append("\n");
            }
        }
        return s.toString();
    }

    void ask(String q) {
        if (key().length() < 10) {
            toast("Сначала добавь ключ OpenRouter");
            open(this::aiSettings);
            return;
        }
        if (aiBusy) {
            toast("Тренер ещё отвечает");
            return;
        }
        st.addChat("user", q);
        runAi();
    }

    void runAi() {
        aiBusy = true;
        aiError = null;
        aiNote = null;
        if ("chat".equals(screenId)) {
            render();
            scrollBottom();
        } else {
            open(this::chat);
        }
        final String key = key(), model = model();
        final JSONArray msgs = new JSONArray();
        try {
            msgs.put(new JSONObject().put("role", "system").put("content", coachContext()));
            JSONArray h = st.chat();
            for (int i = Math.max(0, h.length() - 12); i < h.length(); i++) msgs.put(h.get(i));
        } catch (Exception ignored) {
        }
        new Thread(() -> {
            String ans = null, note = null;
            Exception err = null;
            try {
                ans = Ai.chat(key, model, msgs);
            } catch (Exception e1) {
                err = e1;
                if (Ai.retryable(e1) && !model.equals(FALLBACK_MODEL)) {
                    try {
                        ans = Ai.chat(key, FALLBACK_MODEL, msgs);
                        note = "Модель " + model + " не ответила — ответила запасная " + FALLBACK_MODEL + ".";
                    } catch (Exception ignored) {
                    }
                }
            }
            final String fAns = ans, fNote = note, fErr = ans == null ? Ai.explain(err) : null;
            ui.post(() -> {
                aiBusy = false;
                aiError = fErr;
                aiNote = fNote;
                if (fAns != null) st.addChat("assistant", fAns);
                if (isFinishing() || isDestroyed()) return;
                if ("chat".equals(screenId)) {
                    render();
                    scrollBottom();
                } else {
                    toast(fAns != null ? "Тренер ответил — открой чат" : fErr);
                }
            });
        }).start();
    }

    void scrollBottom() { scroll.post(() -> scroll.scrollTo(0, body.getHeight())); }

    void chat() {
        screenId = "chat";
        topBar("AI-тренер");
        if (key().length() < 10) {
            body.addView(t("Тренер работает через OpenRouter. Нужен твой ключ — он хранится только на этом телефоне.", 15, SILVER, false));
            body.addView(btn("Добавить ключ", true, () -> open(this::aiSettings)));
            return;
        }
        JSONArray a = st.chat();
        if (a.length() == 0) {
            body.addView(t("Спроси про технику, веса, питание или восстановление. Тренер видит твой профиль, программу и журнал подходов — "
                    + "чем аккуратнее ведёшь журнал, тем точнее советы.", 15, SILVER, false));
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            bubble(m.optString("content"), "user".equals(m.optString("role")), false);
        }
        if (aiBusy) bubble("Тренер думает…", false, true);
        if (aiNote != null) body.addView(t(aiNote, 12, MUTED, false));
        JSONObject lastMsg = a.optJSONObject(a.length() - 1);
        boolean unanswered = !aiBusy && lastMsg != null && "user".equals(lastMsg.optString("role"));
        if (aiError != null) body.addView(t(aiError, 14, RED, false));
        if (aiError != null || unanswered) {
            LinearLayout r = row();
            r.addView(link("Повторить", this::runAi));
            r.addView(link("Настройки AI", () -> open(this::aiSettings)));
            body.addView(r);
        }
        if (a.length() > 0 && !aiBusy) {
            body.addView(link("Очистить переписку", () -> confirm("Удалить всю переписку с тренером?", () -> {
                p.edit().remove("chat").apply();
                aiError = null;
                aiNote = null;
                refresh();
            })));
        }

        bottom.setBackgroundColor(CARD);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        chips.setPadding(dp(12), dp(8), dp(12), 0);
        String[][] quick = {
                {"Что сегодня?", "Что мне тренировать сегодня и с какими весами? Учти прошлые тренировки."},
                {"Жим не растёт", "Жим лёжа не растёт. Посмотри мой журнал и скажи, что поменять."},
                {"Меню на день", "Составь меню на день под мои калории и белок, с граммовками."},
                {"Убрать живот", "Как мне набирать мышцы и при этом не растить живот? Дай конкретные правила."}};
        for (final String[] q : quick) {
            TextView ch = t(q[0], 13, SILVER, false);
            ch.setPadding(dp(14), dp(9), dp(14), dp(9));
            ch.setBackground(tap(CARD2, 18, LINE));
            ch.setOnClickListener(v -> ask(q[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.setMargins(0, 0, dp(8), 0);
            chips.addView(ch, lp);
        }
        hs.addView(chips);
        bottom.addView(hs);

        LinearLayout r = row();
        r.setPadding(dp(12), dp(8), dp(12), dp(10));
        chatInput = input("Вопрос тренеру…", false);
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        chatInput.setMaxLines(4);
        chatInput.setText(chatDraft);
        chatInput.setSelection(chatInput.getText().length());
        r.addView(chatInput, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView send = square("→", () -> {
            String q = chatInput.getText().toString().trim();
            if (q.length() < 2) { toast("Напиши вопрос"); return; }
            if (aiBusy) { toast("Тренер ещё отвечает"); return; }
            chatInput.setText("");
            ask(q);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(50));
        lp.setMargins(dp(8), 0, 0, 0);
        r.addView(send, lp);
        bottom.addView(r);
        scrollBottom();
    }

    void bubble(String text, boolean user, boolean faint) {
        TextView v = t(text, 15, faint ? MUTED : TEXT, false);
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        v.setLineSpacing(dp(2), 1f);
        v.setBackground(shape(user ? CARD2 : CARD, 16, user ? 0 : LINE));
        if (!faint) v.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.setMargins(user ? dp(44) : 0, dp(8), user ? 0 : dp(20), 0);
        body.addView(v, lp);
    }

    void aiSettings() {
        topBar("Настройки AI");
        body.addView(t("Тренер работает через OpenRouter. Ключ создаётся на openrouter.ai/keys, для платных моделей нужен баланс. "
                + "Ключ хранится только на этом телефоне и не попадает в резервную копию.", 14, MUTED, false));
        body.addView(label("Ключ OpenRouter"));
        final EditText key = input("sk-or-…", false);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(p.getString("or_key", ""));
        body.addView(key);
        body.addView(label("Модель"));
        final EditText model = input(DEFAULT_MODEL, false);
        model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        model.setText(model());
        body.addView(model);
        final Runnable save = () -> p.edit().putString("or_key", key.getText().toString().trim())
                .putString("model", model.getText().toString().trim()).apply();
        body.addView(btn("Выбрать модель из списка", false, () -> {
            save.run();
            if (key().length() < 10) { toast("Сначала вставь ключ"); return; }
            open(this::models);
        }));
        final TextView status = t("", 14, SILVER, false);
        body.addView(btn("Сохранить", true, () -> { save.run(); toast("Сохранено"); back(); }));
        body.addView(btn("Проверить соединение", false, () -> {
            save.run();
            if (key().length() < 10) { toast("Сначала вставь ключ"); return; }
            status.setTextColor(SILVER);
            status.setText("Проверяю…");
            final String k = key(), m = model();
            new Thread(() -> {
                String res;
                boolean ok = false;
                try {
                    JSONArray msgs = new JSONArray().put(new JSONObject().put("role", "user").put("content", "Ответь одним словом: работает"));
                    Ai.chat(k, m, msgs);
                    res = "Соединение работает, модель " + m + " отвечает.";
                    ok = true;
                } catch (Exception ex) {
                    res = Ai.explain(ex);
                }
                final String fr = res;
                final boolean fok = ok;
                ui.post(() -> { status.setTextColor(fok ? GOLD : RED); status.setText(fr); });
            }).start();
        }));
        body.addView(status);
    }

    void models() {
        screenId = "models";
        topBar("Выбор модели");
        if (modelCache == null) {
            final TextView status = t("Загружаю список моделей…", 14, MUTED, false);
            body.addView(status);
            final String k = key();
            new Thread(() -> {
                JSONArray res = null;
                String err = null;
                try {
                    res = Ai.models(k);
                } catch (Exception ex) {
                    err = Ai.explain(ex);
                }
                final JSONArray fr = res;
                final String fe = err;
                ui.post(() -> {
                    if (fr != null && fr.length() > 0) {
                        modelCache = fr;
                        if ("models".equals(screenId)) render();
                    } else {
                        status.setTextColor(RED);
                        status.setText(fe != null ? fe : "Список пуст. Введи название модели вручную.");
                    }
                });
            }).start();
            return;
        }
        final EditText search = input("Поиск: gpt, claude, deepseek, free…", false);
        body.addView(search);
        final LinearLayout list = col();
        body.addView(list);
        final Runnable fill = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.US);
            int shown = 0, matched = 0;
            for (int i = 0; i < modelCache.length(); i++) {
                JSONObject m = modelCache.optJSONObject(i);
                final String id = m == null ? "" : m.optString("id");
                if (id.isEmpty() || !(id.toLowerCase(Locale.US).contains(q) || m.optString("name").toLowerCase(Locale.US).contains(q))) continue;
                matched++;
                if (shown >= 40) continue;
                shown++;
                LinearLayout c = card();
                c.addView(t(id, 15, id.equals(model()) ? GOLD : TEXT, true));
                c.addView(t(price(m.optJSONObject("pricing")), 12, MUTED, false));
                c.setOnClickListener(v -> {
                    p.edit().putString("model", id).apply();
                    toast("Модель: " + id);
                    back();
                });
                clickable(c, 20);
                list.addView(c);
            }
            if (matched > shown) list.addView(t("Показано " + shown + " из " + matched + " — уточни поиск.", 13, MUTED, false));
            if (matched == 0) list.addView(t("Ничего не найдено.", 13, MUTED, false));
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { fill.run(); }
        });
        fill.run();
    }

    static String price(JSONObject pr) {
        if (pr == null) return "цена не указана";
        try {
            double in = Double.parseDouble(pr.optString("prompt", "0")) * 1e6, out = Double.parseDouble(pr.optString("completion", "0")) * 1e6;
            if (in <= 0 && out <= 0) return "бесплатно";
            return String.format(Locale.US, "$%.2f вход · $%.2f выход за 1 млн токенов", in, out);
        } catch (Exception e) {
            return "цена не указана";
        }
    }

    // ════════════════════════════════ Настройки и профиль ════════════════════════════════

    void settings() {
        topBar("Настройки");
        body.addView(btn("Профиль", false, () -> open(() -> profile(false))));
        body.addView(btn("AI-тренер: ключ и модель", false, () -> open(this::aiSettings)));
        body.addView(btn("Сигнал в конце отдыха: " + Signal.name(signal()), false, () -> open(this::signalScreen)));

        body.addView(label("Резервная копия"));
        body.addView(t("Данные хранятся только на этом телефоне. Перед сменой телефона или переустановкой скопируй их и сохрани, например, в «Избранном» в мессенджере.",
                13, MUTED, false));
        body.addView(btn("Скопировать все данные", false, () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Fitness Couch", st.export()));
            toast("Скопировано в буфер обмена");
        }));
        body.addView(btn("Восстановить из копии", false, () -> open(this::restore)));
        body.addView(btn("Сбросить все данные", false, () -> confirm("Удалить журнал, замеры, чат и профиль? Отменить это нельзя.", () -> {
            p.edit().clear().putBoolean("migrated", true).apply();
            modelCache = null;
            stopRest();
            stack.clear();
            stack.add(this::home);
            stack.add(() -> profile(true));
            render();
        })));
        body.addView(t("Fitness Couch 2.0.1", 12, MUTED, false));
    }

    void restore() {
        topBar("Восстановление");
        body.addView(t("Вставь сюда текст резервной копии. Текущие данные будут заменены.", 14, MUTED, false));
        final EditText in = input("{\"app\":\"fitnesscouch\"…", false);
        in.setMinLines(5);
        in.setMaxLines(8);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        body.addView(in);
        body.addView(btn("Восстановить", true, () -> {
            if (st.restore(in.getText().toString())) {
                toast("Данные восстановлены");
                goHome();
            } else {
                toast("Это не похоже на резервную копию");
            }
        }));
    }

    void profile(final boolean first) {
        if (first) {
            body.addView(label("Fitness Couch"));
            body.addView(t("Пара вопросов о тебе", 26, TEXT, true));
            body.addView(t("По этим данным считаются калории и цель по весу, и их видит AI-тренер.", 14, MUTED, false));
        } else {
            topBar("Профиль");
        }
        body.addView(label("Имя"));
        final EditText name = input("Как к тебе обращаться", false);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(first ? "Руслан" : name());
        body.addView(name);

        final boolean[] male = {male()};
        body.addView(label("Пол"));
        final LinearLayout sex = row();
        final Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            sex.removeAllViews();
            for (int i = 0; i < 2; i++) {
                final boolean m = i == 0;
                TextView b = t(m ? "Мужской" : "Женский", 15, male[0] == m ? 0xFF000000 : SILVER, true);
                b.setGravity(Gravity.CENTER);
                b.setBackground(tap(male[0] == m ? GOLD : CARD2, 14, male[0] == m ? 0 : LINE));
                b.setOnClickListener(v -> { male[0] = m; paint[0].run(); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
                lp.setMargins(i == 0 ? 0 : dp(8), dp(4), 0, 0);
                sex.addView(b, lp);
            }
        };
        paint[0].run();
        body.addView(sex);

        body.addView(label("Возраст"));
        final EditText age = input("полных лет", true);
        if (p.contains("age")) age.setText(String.valueOf(age()));
        body.addView(age);
        body.addView(label("Рост, см"));
        final EditText h = input("", true);
        h.setText(Fmt.n(height()));
        body.addView(h);
        body.addView(label("Вес сейчас, кг"));
        final EditText w = input("", true);
        w.setText(Fmt.n(weight()));
        body.addView(w);
        body.addView(label("Цель по весу, кг"));
        final EditText g = input("", true);
        g.setText(Fmt.n(goal()));
        body.addView(g);

        body.addView(btn(first ? "Начать" : "Сохранить", true, () -> {
            Float a = num(age), hv = num(h), wv = num(w), gv = num(g);
            if (name.getText().toString().trim().isEmpty()) { toast("Введи имя"); return; }
            if (a == null || a < 12 || a > 90) { toast("Проверь возраст"); return; }
            if (hv == null || hv < 120 || hv > 230) { toast("Проверь рост"); return; }
            if (wv == null || wv < 30 || wv > 250) { toast("Проверь вес"); return; }
            if (gv == null || gv < 30 || gv > 250) { toast("Проверь цель по весу"); return; }
            SharedPreferences.Editor e = p.edit().putString("name", name.getText().toString().trim())
                    .putBoolean("male", male[0]).putInt("age", Math.round(a)).putFloat("height", hv).putFloat("goal", gv);
            if (!p.contains("start_w")) e.putFloat("start_w", wv);
            e.apply();
            if (st.series("bw").length() == 0 || Math.abs(wv - weight()) > 0.01f) st.addPoint("bw", Store.today(), wv);
            back();
        }));
    }

    // ════════════════════════════════ Кирпичики интерфейса ════════════════════════════════

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBaselineAligned(false);
        return l;
    }

    GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    /** Фон с откликом на нажатие. */
    Drawable tap(int fill, int radiusDp, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(fill, radiusDp, stroke), shape(0xFFFFFFFF, radiusDp, 0));
    }

    /** Добавляет отклик на нажатие поверх готового фона. */
    void clickable(View v, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(radiusDp));
        v.setForeground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, mask));
    }

    TextView t(String s, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, dp(3), 0, dp(3));
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", sp >= 20 ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    TextView label(String s) {
        TextView v = t(s.toUpperCase(RU), 11, GOLD, true);
        v.setLetterSpacing(0.14f);
        v.setPadding(0, dp(10), 0, dp(2));
        return v;
    }

    LinearLayout card() {
        LinearLayout l = col();
        l.setPadding(dp(16), dp(12), dp(16), dp(14));
        l.setBackground(shape(CARD, 20, LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(10), 0, 0);
        l.setLayoutParams(lp);
        return l;
    }

    TextView btn(String s, boolean primary, final Runnable r) {
        TextView b = t(s, 16, primary ? 0xFF000000 : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(14), dp(14), dp(14));
        b.setMinHeight(dp(52));
        b.setBackground(tap(primary ? GOLD : CARD2, 16, primary ? 0 : LINE));
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(12), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    TextView square(String s, final Runnable r) {
        TextView b = t(s, 22, TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(tap(CARD2, 14, LINE));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    TextView link(String s, final Runnable r) {
        TextView b = t(s, 14, GOLD, true);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinHeight(dp(44));
        b.setBackground(tap(0x00000000, 12, 0));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    ProgressBar bar(int value, int max) {
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(Math.max(1, max));
        pb.setProgress(value);
        pb.setProgressTintList(ColorStateList.valueOf(GOLD));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A3D45));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(6));
        lp.setMargins(0, dp(8), 0, dp(4));
        pb.setLayoutParams(lp);
        return pb;
    }

    LinearLayout stat(String title, String value) {
        LinearLayout c = card();
        c.addView(label(title));
        c.addView(t(value, 22, TEXT, true));
        return c;
    }

    LinearLayout.LayoutParams statLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1);
        lp.setMargins(gap ? dp(8) : 0, dp(10), 0, 0);
        return lp;
    }

    EditText input(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF6E727C);
        e.setBackground(shape(CARD2, 14, LINE));
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(4), 0, 0);
        e.setLayoutParams(lp);
        return e;
    }

    void topBar(String title) {
        LinearLayout r = row();
        TextView backBtn = t("‹", 30, GOLD, false);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setPadding(0, 0, 0, dp(4));
        backBtn.setContentDescription("Назад");
        backBtn.setBackground(tap(0x00000000, 22, 0));
        backBtn.setOnClickListener(v -> back());
        r.addView(backBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        r.addView(t(title, 22, TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(-dp(10), 0, 0, dp(4));
        body.addView(r, lp);
    }

    static Float num(EditText e) {
        try {
            return Float.parseFloat(e.getText().toString().trim().replace(',', '.'));
        } catch (Exception ex) {
            return null;
        }
    }

    void numberDialog(String title, String initial, boolean decimal, final NumCb cb) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        in.setText(initial);
        in.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(in);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(title).setView(wrap)
                .setPositiveButton("Готово", (d, which) -> {
                    Float v = num(in);
                    if (v != null) cb.on(v);
                })
                .setNegativeButton("Отмена", null).create();
        if (dlg.getWindow() != null) dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        in.requestFocus();
    }

    void confirm(String message, final Runnable yes) {
        new AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("Да", (d, which) -> yes.run())
                .setNegativeButton("Отмена", null).show();
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        f.clearFocus();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
