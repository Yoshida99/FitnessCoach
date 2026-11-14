package com.ruslan.fitnesscouch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Клиент OpenRouter. Вызывать только из фонового потока. */
final class Ai {
    static final String BASE = "https://openrouter.ai/api/v1";

    static final class HttpError extends Exception {
        final int code;

        HttpError(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    static String chat(String key, String model, JSONArray messages) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.5)
                .put("max_tokens", 1200);
        JSONObject r = new JSONObject(request("POST", BASE + "/chat/completions", key, body.toString()));
        if (r.has("error")) throw new HttpError(r.getJSONObject("error").optInt("code", 500), errorText(r));
        JSONArray choices = r.optJSONArray("choices");
        JSONObject msg = choices == null || choices.length() == 0 ? null : choices.getJSONObject(0).optJSONObject("message");
        String text = msg == null || msg.isNull("content") ? "" : msg.optString("content", "").trim();
        if (text.isEmpty()) throw new HttpError(502, "Модель вернула пустой ответ");
        return text;
    }

    /** Список моделей: массив объектов с полями id, name, pricing. */
    static JSONArray models(String key) throws Exception {
        JSONArray all = new JSONArray();
        String url = BASE + "/models";
        for (int page = 0; page < 10 && url != null; page++) {
            JSONObject r = new JSONObject(request("GET", url, key, null));
            JSONArray data = r.optJSONArray("data");
            for (int i = 0; data != null && i < data.length(); i++) all.put(data.get(i));
            JSONObject links = r.optJSONObject("links");
            String next = links == null || links.isNull("next") ? null : links.optString("next", null);
            url = next != null && next.startsWith("https://openrouter.ai/") ? next : null;
        }
        return all;
    }

    private static String request(String method, String url, String key, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            if (key != null && key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("X-Title", "Fitness Couch");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            String text = read(ok ? c.getInputStream() : c.getErrorStream());
            if (!ok) {
                String msg = "";
                try {
                    msg = errorText(new JSONObject(text));
                } catch (Exception ignored) {
                }
                throw new HttpError(code, msg);
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String errorText(JSONObject r) {
        JSONObject e = r.optJSONObject("error");
        return e == null ? "" : e.optString("message", "");
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }

    /** Стоит ли повторить запрос с запасной моделью: да, если проблема в модели или сервере, а не в ключе. */
    static boolean retryable(Exception e) {
        if (!(e instanceof HttpError)) return false;
        int c = ((HttpError) e).code;
        return c == 400 || c == 404 || c == 408 || c == 429 || c >= 500;
    }

    static String explain(Exception e) {
        if (e instanceof HttpError) {
            int c = ((HttpError) e).code;
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (c == 401) return "Ключ OpenRouter не подходит. Проверь его в настройках AI.";
            if (c == 402) return "На балансе OpenRouter не хватает средств для этой модели.";
            if (c == 403) return "OpenRouter отклонил запрос (403). " + m;
            if (c == 404) return "Модель не найдена. Выбери другую из списка в настройках AI.";
            if (c == 429) return "Слишком много запросов. Подожди минуту и повтори.";
            return "Ошибка OpenRouter " + c + (m.isEmpty() ? "" : ": " + m);
        }
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException)
            return "Нет соединения с интернетом.";
        if (e instanceof java.net.SocketTimeoutException) return "Сервер не ответил вовремя. Попробуй ещё раз.";
        return "Не удалось получить ответ: " + e.getClass().getSimpleName();
    }
}
