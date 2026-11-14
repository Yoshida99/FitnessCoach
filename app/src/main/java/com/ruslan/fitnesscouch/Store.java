package com.ruslan.fitnesscouch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Все данные приложения: профиль, журнал подходов, замеры, чат. Хранится локально на телефоне. */
final class Store {
    static final int MAX_SESSIONS = 80, MAX_CHAT = 60;
    final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("fc2", Context.MODE_PRIVATE);
        if (!p.getBoolean("migrated", false)) {
            // Перенос ключа и замеров из v1.07, чтобы не вводить заново.
            SharedPreferences old = c.getSharedPreferences("fc107", Context.MODE_PRIVATE);
            SharedPreferences.Editor e = p.edit().putBoolean("migrated", true);
            if (old.contains("or_key")) e.putString("or_key", old.getString("or_key", ""));
            if (old.contains("model")) e.putString("model", old.getString("model", ""));
            e.apply();
            if (old.contains("weight")) addPoint("bw", today(), old.getFloat("weight", 70f));
            if (old.getFloat("waist", 0f) > 0) addPoint("waist", today(), old.getFloat("waist", 0f));
        }
    }

    // ── Даты ────────────────────────────────────────────────────────────────
    static String iso(Date d) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d); }

    static String today() { return iso(new Date()); }

    static String human(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("d MMM", new Locale("ru")).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    // ── Журнал подходов ─────────────────────────────────────────────────────
    JSONArray sessions(String id) {
        try {
            return new JSONArray(p.getString("log_" + id, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    JSONObject session(String id, String date) {
        JSONArray a = sessions(id);
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && date.equals(s.optString("d"))) return s;
        }
        return null;
    }

    /** Последняя тренировка этого упражнения до указанной даты. */
    JSONObject lastBefore(String id, String date) {
        JSONArray a = sessions(id);
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && s.optString("d").compareTo(date) < 0) return s;
        }
        return null;
    }

    static int rep(JSONObject s, int i) {
        JSONArray r = s == null ? null : s.optJSONArray("r");
        return r == null ? 0 : r.optInt(i, 0);
    }

    static float wt(JSONObject s, int i) {
        JSONArray w = s == null ? null : s.optJSONArray("w");
        return w == null ? 0f : (float) w.optDouble(i, 0);
    }

    static int count(JSONObject s) {
        JSONArray r = s == null ? null : s.optJSONArray("r");
        return r == null ? 0 : r.length();
    }

    /** Записать подход (reps = 0 стирает его). */
    void logSet(Program.Ex e, int idx, int reps, float w) {
        String date = today();
        try {
            JSONArray a = sessions(e.id);
            int at = -1;
            for (int i = a.length() - 1; i >= 0; i--) {
                if (date.equals(a.getJSONObject(i).optString("d"))) { at = i; break; }
            }
            JSONObject s = at >= 0 ? a.getJSONObject(at) : new JSONObject().put("d", date);
            JSONArray r = s.optJSONArray("r"), ws = s.optJSONArray("w");
            if (r == null) r = new JSONArray();
            if (ws == null) ws = new JSONArray();
            int n = Math.max(Math.max(r.length(), e.sets), idx + 1);
            for (int i = r.length(); i < n; i++) r.put(0);
            for (int i = ws.length(); i < n; i++) ws.put(0);
            r.put(idx, Math.max(0, reps));
            ws.put(idx, reps > 0 ? w : 0);
            s.put("r", r).put("w", ws);

            boolean any = false;
            for (int i = 0; i < r.length(); i++) any |= r.optInt(i, 0) > 0;
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, a.length() - MAX_SESSIONS); i < a.length(); i++) {
                if (i != at) out.put(a.get(i));
            }
            if (any) out.put(s);
            p.edit().putString("log_" + e.id, out.toString()).apply();
        } catch (JSONException ignored) {
        }
        syncDay(date);
    }

    static float e1rm(float w, int reps) {
        if (reps <= 0 || w <= 0) return 0;
        return reps == 1 ? w : w * (1f + reps / 30f);
    }

    /**
     * Лучший подход: {оценка, вес, повторы}. Оценка — расчётный 1ПМ, для упражнений со своим весом — повторы.
     * before != null — учитывать только тренировки до этой даты.
     */
    float[] best(String id, String before) {
        float[] b = {0, 0, 0};
        JSONArray a = sessions(id);
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null || (before != null && s.optString("d").compareTo(before) >= 0)) continue;
            for (int k = 0; k < count(s); k++) {
                int r = rep(s, k);
                float w = wt(s, k);
                float score = w > 0 ? e1rm(w, r) : r;
                if (r > 0 && score > b[0]) { b[0] = score; b[1] = w; b[2] = r; }
            }
        }
        return b;
    }

    // ── Дни тренировок ──────────────────────────────────────────────────────
    Set<String> days() { return new HashSet<>(p.getStringSet("days", new HashSet<String>())); }

    private void syncDay(String date) {
        boolean any = false;
        Set<String> seen = new HashSet<>();
        for (Program.Day d : Program.DAYS) {
            for (Program.Ex e : d.ex) if (seen.add(e.id) && session(e.id, date) != null) any = true;
        }
        Set<String> days = days();
        if (any ? days.add(date) : days.remove(date)) p.edit().putStringSet("days", days).apply();
    }

    int trainedSince(int daysBack) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -daysBack + 1);
        String from = iso(c.getTime());
        int n = 0;
        for (String d : days()) if (d.compareTo(from) >= 0) n++;
        return n;
    }

    // ── Замеры (вес тела, талия) ────────────────────────────────────────────
    JSONArray series(String key) {
        try {
            return new JSONArray(p.getString("s_" + key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    void addPoint(String key, String date, float v) {
        try {
            JSONArray a = series(key), out = new JSONArray();
            for (int i = Math.max(0, a.length() - 365); i < a.length(); i++) {
                if (!date.equals(a.getJSONObject(i).optString("d"))) out.put(a.get(i));
            }
            out.put(new JSONObject().put("d", date).put("v", v));
            p.edit().putString("s_" + key, out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    float latest(String key, float def) {
        JSONArray a = series(key);
        JSONObject o = a.optJSONObject(a.length() - 1);
        return o == null ? def : (float) o.optDouble("v", def);
    }

    float[] values(String key) {
        JSONArray a = series(key);
        float[] v = new float[a.length()];
        for (int i = 0; i < v.length; i++) v[i] = (float) a.optJSONObject(i).optDouble("v", 0);
        return v;
    }

    /** Средний темп изменения веса, кг в неделю, по записям за последние 28 дней. NaN — данных мало. */
    float weeklyRate() {
        JSONArray a = series("bw");
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long now = System.currentTimeMillis(), day = 86400000L;
            JSONObject first = null, last = a.optJSONObject(a.length() - 1);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (now - f.parse(o.getString("d")).getTime() <= 28 * day) { first = o; break; }
            }
            if (first == null || last == null) return Float.NaN;
            float span = (f.parse(last.getString("d")).getTime() - f.parse(first.getString("d")).getTime()) / (float) day;
            if (span < 7) return Float.NaN;
            return (float) ((last.getDouble("v") - first.getDouble("v")) / span * 7);
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    // ── Чат ─────────────────────────────────────────────────────────────────
    JSONArray chat() {
        try {
            return new JSONArray(p.getString("chat", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    void addChat(String role, String content) {
        try {
            JSONArray a = chat(), out = new JSONArray();
            for (int i = Math.max(0, a.length() - MAX_CHAT + 1); i < a.length(); i++) out.put(a.get(i));
            out.put(new JSONObject().put("role", role).put("content", content));
            p.edit().putString("chat", out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ── Резервная копия ─────────────────────────────────────────────────────
    /** Все данные одной строкой JSON. API-ключ в копию не попадает. */
    String export() {
        JSONArray out = new JSONArray();
        try {
            for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
                Object v = en.getValue();
                if (en.getKey().equals("or_key") || v == null) continue;
                JSONObject o = new JSONObject().put("k", en.getKey());
                if (v instanceof Boolean) o.put("t", "b").put("v", v);
                else if (v instanceof Float) o.put("t", "f").put("v", ((Float) v).doubleValue());
                else if (v instanceof Integer) o.put("t", "i").put("v", v);
                else if (v instanceof Long) o.put("t", "l").put("v", v);
                else if (v instanceof Set) o.put("t", "ss").put("v", new JSONArray((Set<?>) v));
                else o.put("t", "s").put("v", String.valueOf(v));
                out.put(o);
            }
            return new JSONObject().put("app", "fitnesscouch").put("v", 2).put("data", out).toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Восстановить данные из копии. Возвращает false, если текст не похож на копию. */
    boolean restore(String json) {
        try {
            JSONObject root = new JSONObject(json.trim());
            if (!"fitnesscouch".equals(root.optString("app"))) return false;
            JSONArray data = root.getJSONArray("data");
            String key = p.getString("or_key", "");
            SharedPreferences.Editor e = p.edit().clear();
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.getJSONObject(i);
                String k = o.getString("k"), t = o.getString("t");
                if (t.equals("b")) e.putBoolean(k, o.getBoolean("v"));
                else if (t.equals("f")) e.putFloat(k, (float) o.getDouble("v"));
                else if (t.equals("i")) e.putInt(k, o.getInt("v"));
                else if (t.equals("l")) e.putLong(k, o.getLong("v"));
                else if (t.equals("ss")) {
                    Set<String> set = new HashSet<>();
                    JSONArray arr = o.getJSONArray("v");
                    for (int j = 0; j < arr.length(); j++) set.add(arr.getString(j));
                    e.putStringSet(k, set);
                } else e.putString(k, o.getString("v"));
            }
            if (key.length() > 0) e.putString("or_key", key);
            e.putBoolean("migrated", true).apply();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
