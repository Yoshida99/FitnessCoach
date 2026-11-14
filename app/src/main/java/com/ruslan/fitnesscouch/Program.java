package com.ruslan.fitnesscouch;

import java.util.Calendar;

/** Тренировочная программа. Чтобы поменять упражнения, подходы или веса — правь только этот файл. */
final class Program {

    static final class Ex {
        final String id, name;
        final int sets, min, max, rest;
        final float w, step;
        final String[] cues;

        Ex(String id, String name, int sets, int min, int max, float w, float step, int rest, String[] cues) {
            this.id = id; this.name = name; this.sets = sets; this.min = min; this.max = max;
            this.w = w; this.step = step; this.rest = rest; this.cues = cues;
        }

        boolean bodyweight() { return w <= 0; }

        String target() { return sets + " × " + (min == max ? String.valueOf(min) : min + "–" + max); }
    }

    static final class Day {
        final int dow;
        final String tag, title, sub;
        final Ex[] ex;

        Day(int dow, String tag, String title, String sub, Ex[] ex) {
            this.dow = dow; this.tag = tag; this.title = title; this.sub = sub; this.ex = ex;
        }
    }

    // ── Техника: по три главные подсказки на упражнение ─────────────────────
    static final String[] C_BENCH = {
            "Лопатки сведены и опущены, стопы плотно в пол.",
            "Гриф опускается на низ груди, локти под углом около 45° к корпусу.",
            "Таз не отрывай, гриф не отбивай от груди."};
    static final String[] C_INCLINE = {
            "Скамья 30°, не выше — иначе работу забирают плечи.",
            "Внизу гантели на уровне груди, предплечья вертикально.",
            "Жми вверх и чуть внутрь, не стукай гантели друг о друга."};
    static final String[] C_FLY = {
            "Локти слегка согнуты и зафиксированы на всё движение.",
            "Опускай до лёгкого растяжения груди, не ниже линии плеч.",
            "Вес небольшой: это упражнение на ощущение, а не на рекорд."};
    static final String[] C_TRICEPS = {
            "Локти смотрят вперёд и не разъезжаются в стороны.",
            "Двигается только предплечье, плечо неподвижно.",
            "Внизу полное растяжение, вверху полное разгибание."};
    static final String[] C_CRUNCH = {
            "Скручивай корпус, а не тяни себя руками за голову.",
            "Поясница прижата к полу, выдох на подъёме.",
            "Медленно вниз — пресс работает и на опускании."};
    static final String[] C_DEADLIFT = {
            "Гриф над серединой стопы, спина ровная от старта до верха.",
            "Толкай пол ногами, гриф скользит вдоль ног.",
            "Не округляй поясницу. Если округляется — вес слишком большой."};
    static final String[] C_LATPULL = {
            "Сначала опусти лопатки, потом тяни локти вниз.",
            "Тяни к верху груди, корпус отклонён совсем немного.",
            "Без рывков корпусом; наверху полностью растягивай широчайшие."};
    static final String[] C_ROW = {
            "Спина ровная, корпус не раскачивается.",
            "Тяни к поясу и своди лопатки в конце.",
            "Локти идут вдоль корпуса, плечи к ушам не поднимай."};
    static final String[] C_CURL = {
            "Локти прижаты к корпусу и не уходят вперёд.",
            "Без раскачки спиной — если помогаешь корпусом, сбрось вес.",
            "Опускай медленно, 2–3 секунды."};
    static final String[] C_HAMMER = {
            "Хват нейтральный: ладони смотрят друг на друга.",
            "Локти неподвижны, поднимай без рывка.",
            "Внизу руку разгибай полностью."};
    static final String[] C_SQUAT = {
            "Стопы на ширине плеч, колени идут по направлению носков.",
            "Спина ровная, опускайся минимум до параллели.",
            "Пятки не отрываются; вставая, не своди колени внутрь."};
    static final String[] C_LEGPRESS = {
            "Поясница и таз прижаты к спинке на всём движении.",
            "Опускай платформу, пока таз не начинает подкручиваться.",
            "Вверху колени не выпрямляй до щелчка."};
    static final String[] C_LUNGE = {
            "Шаг достаточно длинный: в нижней точке оба колена около 90°.",
            "Корпус вертикально, вес на пятке передней ноги.",
            "Колено передней ноги не заваливается внутрь."};
    static final String[] C_OHP = {
            "Спина прижата к спинке, поясницу сильно не прогибай.",
            "Внизу гантели на уровне ушей, предплечья вертикально.",
            "Жми вверх без рывка, вверху локти не выключай жёстко."};
    static final String[] C_LATERAL = {
            "Лёгкий наклон вперёд, локти чуть согнуты.",
            "Поднимай локтями до уровня плеч, не выше.",
            "Без раскачки; опускай под контролем."};
    static final String[] C_REARDELT = {
            "Грудь прижата к спинке, руки почти прямые.",
            "Разводи руки в стороны и назад за счёт задней дельты, лопатки не своди.",
            "Короткая пауза в конечной точке."};
    static final String[] C_CALF = {
            "Полная амплитуда: внизу растяжение, вверху пауза 1 секунда.",
            "Не пружинь — работай медленно.",
            "Колени почти прямые."};
    static final String[] C_WHEEL = {
            "Таз подкручен, поясница не провисает.",
            "Катись вперёд только до точки, где держишь спину ровной.",
            "Возвращайся прессом, а не руками. Больно в пояснице — сократи амплитуду."};

    static Ex bench(String name, int sets, int min, int max) { return new Ex("bench", name, sets, min, max, 60, 2.5f, 150, C_BENCH); }
    static Ex incline(int sets, int min, int max) { return new Ex("incline_db", "Жим гантелей 30°", sets, min, max, 20, 2, 120, C_INCLINE); }
    static Ex triceps() { return new Ex("triceps_rope", "Трицепс из-за головы, канат", 4, 12, 15, 40, 2.5f, 75, C_TRICEPS); }
    static Ex latpull() { return new Ex("lat_pulldown", "Тяга верхнего блока", 4, 10, 10, 50, 5, 120, C_LATPULL); }
    static Ex row() { return new Ex("seated_row", "Тяга сидя в блоке", 4, 10, 10, 50, 5, 120, C_ROW); }
    static Ex curl() { return new Ex("ez_curl", "EZ-гриф на бицепс", 3, 12, 12, 18, 2.5f, 75, C_CURL); }
    static Ex hammer() { return new Ex("hammer", "Молотки", 3, 10, 12, 10, 1, 75, C_HAMMER); }

    static final Day[] DAYS = {
            new Day(Calendar.MONDAY, "ПН", "Грудь + трицепс", "Жим · грудь · пресс", new Ex[]{
                    bench("Жим штанги лёжа", 4, 6, 8),
                    incline(4, 8, 12),
                    new Ex("db_fly", "Разводка гантелей", 3, 12, 12, 10, 1, 75, C_FLY),
                    triceps(),
                    new Ex("crunch", "Скручивания", 3, 20, 20, 0, 0, 60, C_CRUNCH)}),
            new Day(Calendar.WEDNESDAY, "СР", "Спина + бицепс", "Тяги · ширина · руки", new Ex[]{
                    new Ex("deadlift", "Становая тяга", 4, 5, 5, 80, 5, 180, C_DEADLIFT),
                    latpull(),
                    row(),
                    curl(),
                    hammer()}),
            new Day(Calendar.FRIDAY, "ПТ", "Ноги + плечи", "Масса · база · мощь", new Ex[]{
                    new Ex("squat", "Присед / Смит", 5, 8, 8, 60, 5, 180, C_SQUAT),
                    new Ex("leg_press", "Жим ногами", 4, 10, 10, 150, 10, 150, C_LEGPRESS),
                    new Ex("lunge", "Выпады (на каждую ногу)", 3, 10, 10, 30, 2.5f, 120, C_LUNGE),
                    new Ex("db_ohp", "Жим гантелей сидя", 4, 10, 10, 15, 1, 120, C_OHP),
                    new Ex("lateral", "Махи в стороны", 4, 15, 15, 7, 1, 60, C_LATERAL),
                    new Ex("rear_delt", "Задняя дельта, «бабочка»", 4, 15, 15, 30, 5, 60, C_REARDELT),
                    new Ex("calf", "Икры в блоке", 3, 20, 20, 50, 5, 60, C_CALF)}),
            new Day(Calendar.SUNDAY, "ВС", "Верх тела", "Сила · масса · добивка", new Ex[]{
                    bench("Жим лёжа", 4, 6, 10),
                    latpull(),
                    row(),
                    incline(3, 9, 12),
                    curl(),
                    hammer(),
                    triceps(),
                    new Ex("ab_wheel", "Пресс: ролик", 3, 10, 20, 0, 0, 60, C_WHEEL)})
    };

    static Day forDow(int dow) {
        for (Day d : DAYS) if (d.dow == dow) return d;
        return null;
    }

    /** Ближайший тренировочный день, начиная с завтрашнего. */
    static Day next(int dow) {
        for (int i = 1; i <= 7; i++) {
            Day d = forDow((dow - 1 + i) % 7 + 1);
            if (d != null) return d;
        }
        return DAYS[0];
    }

    static Ex find(String id) {
        for (Day d : DAYS) for (Ex e : d.ex) if (e.id.equals(id)) return e;
        return null;
    }
}
