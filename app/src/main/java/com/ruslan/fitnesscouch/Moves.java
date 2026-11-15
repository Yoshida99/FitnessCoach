package com.ruslan.fitnesscouch;

import java.util.HashMap;
import java.util.Map;

import static com.ruslan.fitnesscouch.Figure.*;

/** Анимации всех упражнений программы: две крайние позы и снаряды. */
final class Moves {
    static final Map<String, Anim> ALL = new HashMap<>();
    static final Anim STAND = new Anim(false, ANKLE, new P(100, 116), new P(100, 116));

    static Anim get(String id) {
        Anim a = ALL.get(id);
        return a != null ? a : STAND;
    }

    static {
        // Жим штанги лёжа — вид сбоку, голова слева.
        ALL.put("bench", new Anim(false, HIP,
                new P(118, 92).torso(-90).legs(75, 25).arms(186, 180),
                new P(118, 92).torso(-90).legs(75, 25).arms(186, 172).armsS(-.65f, 1)) {
            void props(Geo g, Pen p) {
                p.line(72, 50, 72, GROUND, 3, PROP);
                bench(p, 62, 132, 98);
            }

            void gear(Geo g, Pen p) { plate(p, g.wrA, 10); }
        });

        // Жим гантелей на наклонной скамье 30°.
        ALL.put("incline_db", new Anim(false, HIP,
                new P(112, 95).torso(-120).legs(78, 14).arms(178, 180),
                new P(112, 95).torso(-120).legs(78, 14).arms(178, 168).armsS(-.6f, 1)) {
            void props(Geo g, Pen p) {
                p.line(g.hip[0] + 2, g.hip[1] + 10, g.hip[0] + 2, GROUND, 3, PROP);
                p.line(g.hip[0] - 22, g.hip[1] - 4, g.hip[0] - 22, GROUND, 3, PROP);
                backPad(p, g, 40);
                p.rect(g.hip[0] - 10, g.hip[1] + 5.5f, g.hip[0] + 14, g.hip[1] + 11, 2, PROP_EDGE);
            }

            void gear(Geo g, Pen p) { db(p, g.wrA); }
        });

        // Разводка гантелей — вид со стороны головы: руки раскрываются в стороны.
        ALL.put("db_fly", new Anim(true, HIP, false,
                new P(100, 96).torsoS(.12f).headS(.45f).arms(184, 188),
                new P(100, 96).torsoS(.12f).headS(.45f).arms(84, 80)) {
            void props(Geo g, Pen p) { bench(p, 82, 118, 100); }

            void gear(Geo g, Pen p) {
                db(p, g.wrA);
                db(p, g.wrB);
            }
        });

        // Трицепс с канатом из-за головы: стоя спиной к блоку.
        ALL.put("triceps_rope", new Anim(false, ANKLE,
                new P(112, 116).torso(166).legB(-14, -14).arms(152, -40),
                new P(112, 116).torso(166).legB(-14, -14).arms(152, -210)) {
            void props(Geo g, Pen p) { p.rect(18, 14, 26, GROUND, 2, PROP); }

            void gear(Geo g, Pen p) {
                cable(p, g.wrA, 30, 58);
                p.circle(g.wrA[0], g.wrA[1], 2.4f, GOLD, 0, 0);
            }
        });

        // Скручивания на полу.
        ALL.put("crunch", new Anim(false, HIP,
                new P(116, 112).torso(-91).legs(140, 18).arms(92, 92),
                new P(116, 112).torso(-124).legs(140, 18).arms(112, 112)) {
            void props(Geo g, Pen p) { p.rect(40, GROUND - 1.5f, 170, GROUND + 1, 1.5f, PROP_EDGE); }
        });

        // Становая тяга.
        ALL.put("deadlift", new Anim(false, ANKLE,
                new P(100, 116).torso(124).legs(62, -14).arms(-8, -8),
                new P(100, 116).torso(178).arms(0, 0)) {
            void gear(Geo g, Pen p) { plate(p, g.wrA, 11); }
        });

        // Тяга верхнего блока — локти идут в стороны и вниз, поэтому сбоку рука «укорачивается».
        ALL.put("lat_pulldown", new Anim(false, HIP,
                new P(96, 96).torso(188).legs(88, 2).arms(172, 176),
                new P(96, 96).torso(196).legs(88, 2).arms(172, 172).armsS(-1, 1)) {
            void props(Geo g, Pen p) {
                p.rect(150, 6, 158, GROUND, 2, PROP);
                p.line(100, 6, 154, 6, 1.1f, CABLE);
                p.line(95, 104, 95, GROUND, 3, PROP);
                p.rect(80, 101, 112, 106, 2, PROP_EDGE);
                p.rect(108, 85, 128, 90, 2, PROP_EDGE);
            }

            void gear(Geo g, Pen p) {
                cable(p, g.wrA, 100, 6);
                p.circle(g.wrA[0], g.wrA[1], 3.4f, GOLD, 0, 0);
            }
        });

        // Горизонтальная тяга блока сидя.
        ALL.put("seated_row", new Anim(false, HIP,
                new P(78, 104).torso(160).legs(100, 75).arms(92, 92),
                new P(78, 104).torso(190).legs(100, 75).arms(-40, 85)) {
            void props(Geo g, Pen p) {
                p.rect(150, 20, 158, GROUND, 2, PROP);
                p.line(64, 110, 64, GROUND, 3, PROP);
                p.line(92, 110, 92, GROUND, 3, PROP);
                p.rect(58, 106, 98, 111, 2, PROP_EDGE);
                p.rect(127, 88, 132, 116, 1.5f, PROP_EDGE);
            }

            void gear(Geo g, Pen p) {
                cable(p, g.wrA, 142, 100);
                p.rect(g.wrA[0] - 1.6f, g.wrA[1] - 4.5f, g.wrA[0] + 1.6f, g.wrA[1] + 4.5f, 1, GOLD);
            }
        });

        // Подъём EZ-грифа на бицепс.
        ALL.put("ez_curl", new Anim(false, ANKLE,
                new P(100, 116).arms(4, 6),
                new P(100, 116).arms(4, 158)) {
            void gear(Geo g, Pen p) { plate(p, g.wrA, 7.5f); }
        });

        // Молотки — руки по очереди.
        ALL.put("hammer", new Anim(false, ANKLE,
                new P(100, 116).arms(4, 6).armB(4, 158),
                new P(100, 116).arms(4, 158).armB(4, 6)) {
            void gearBack(Geo g, Pen p) { db(p, g.wrB); }

            void gear(Geo g, Pen p) { db(p, g.wrA); }
        });

        // Приседания со штангой на спине.
        ALL.put("squat", new Anim(false, ANKLE,
                new P(100, 116).torso(178).arms(-60, 129),
                new P(100, 116).torso(150).legs(82, -32).arms(-88, 101)) {
            void gear(Geo g, Pen p) { plate(p, add(add(g.sh, vec(g.tor + 90, 6)), vec(g.tor, -4)), 9); }
        });

        // Жим ногами 45°.
        ALL.put("leg_press", new Anim(false, HIP,
                new P(70, 98).torso(-125).legs(175, 69).foot(156).arms(20, 60),
                new P(70, 98).torso(-125).legs(128, 128).foot(97).arms(20, 60)) {
            void props(Geo g, Pen p) {
                p.line(88, 95, 150, 33, 3, PROP);
                p.line(88, 95, 88, GROUND, 3, PROP);
                p.line(60, 104, 60, GROUND, 3, PROP);
                backPad(p, g, 40);
                p.rect(g.hip[0] - 12, g.hip[1] + 5.5f, g.hip[0] + 10, g.hip[1] + 11, 2, PROP_EDGE);
                float[] perp = vec(45, 1), c = add(g.anA, vec(135, 3));
                plate(p, add(c, vec(135, 10)), 8);
                p.line(c[0] - perp[0] * 13, c[1] - perp[1] * 13, c[0] + perp[0] * 13, c[1] + perp[1] * 13, 4.5f, PROP_EDGE);
            }
        });

        // Выпады с гантелями.
        ALL.put("lunge", new Anim(false, ANKLE,
                new P(120, 116).legs(6, -4).legB(-24, -24).footB(70),
                new P(120, 116).legs(86, -6).legB(-12, -96).footB(150)) {
            void gearBack(Geo g, Pen p) { db(p, g.wrB); }

            void gear(Geo g, Pen p) { db(p, g.wrA); }
        });

        // Жим гантелей сидя — вид спереди.
        ALL.put("db_ohp", new Anim(true, HIP,
                new P(100, 88).legs(12, 4).legsS(.4f).arms(92, 180),
                new P(100, 88).legs(12, 4).legsS(.4f).arms(166, 176)) {
            void props(Geo g, Pen p) {
                p.rect(86, 36, 114, 90, 3, PROP_EDGE);
                p.line(84, 96, 84, GROUND, 3, PROP);
                p.line(116, 96, 116, GROUND, 3, PROP);
                p.rect(78, 91, 122, 97, 2, PROP_EDGE);
            }

            void gear(Geo g, Pen p) {
                dbSide(p, g.wrA);
                dbSide(p, g.wrB);
            }
        });

        // Махи гантелями в стороны — вид спереди.
        ALL.put("lateral", new Anim(true, ANKLE,
                new P(106.5f, 116).arms(12, 14),
                new P(106.5f, 116).arms(86, 82)) {
            void gear(Geo g, Pen p) {
                db(p, g.wrA);
                db(p, g.wrB);
            }
        });

        // Задняя дельта в «бабочке»: руки из положения «вперёд» разводятся в стороны.
        ALL.put("rear_delt", new Anim(true, HIP,
                new P(100, 88).legs(12, 4).legsS(.4f).arms(90, 90).armsS(.28f, .28f),
                new P(100, 88).legs(12, 4).legsS(.4f).arms(90, 90)) {
            void props(Geo g, Pen p) {
                p.rect(93, 20, 107, 88, 3, PROP);
                p.line(84, 96, 84, GROUND, 3, PROP);
                p.line(116, 96, 116, GROUND, 3, PROP);
                p.rect(78, 91, 122, 97, 2, PROP_EDGE);
                p.line(100, 26, g.wrA[0], g.wrA[1], 2.5f, PROP_EDGE);
                p.line(100, 26, g.wrB[0], g.wrB[1], 2.5f, PROP_EDGE);
            }

            void gear(Geo g, Pen p) {
                p.line(g.wrA[0], g.wrA[1] - 4.5f, g.wrA[0], g.wrA[1] + 4.5f, 2.6f, GOLD);
                p.line(g.wrB[0], g.wrB[1] - 4.5f, g.wrB[0], g.wrB[1] + 4.5f, 2.6f, GOLD);
            }
        });

        // Подъём на носки в тренажёре: носки на месте, пятки поднимаются.
        ALL.put("calf", new Anim(false, ANKLE,
                new P(100, 116).arms(20, 160),
                new P(100, 111).arms(20, 160).foot(52)) {
            void props(Geo g, Pen p) {
                float lift = 116 - g.anA[1];
                p.rect(70, 6, 78, GROUND, 2, PROP);
                p.rect(60, 96 - lift, 88, 116 - lift, 1.5f, GOLD_DARK);
                p.line(60, 96 - lift, 88, 96 - lift, 1.5f, GOLD);
                p.line(74, g.sh[1] - 2, g.sh[0], g.sh[1] - 2, 3, PROP_EDGE);
            }

            void gear(Geo g, Pen p) { p.rect(g.sh[0] - 8, g.sh[1] - 4.5f, g.sh[0] + 13, g.sh[1] + 0.5f, 2.5f, PROP_EDGE); }
        });

        // Ролик для пресса с колен.
        ALL.put("ab_wheel", new Anim(false, KNEE,
                new P(88, 114).torso(115).legs(-10, -84).arms(0, 0),
                new P(88, 114).torso(92).legs(-50, -84).arms(68, 68)) {
            void props(Geo g, Pen p) { p.rect(66, GROUND - 1.5f, 100, GROUND + 1, 1.5f, PROP_EDGE); }

            void gear(Geo g, Pen p) {
                float[] c = pt(g.wrA[0], GROUND - 7);
                p.circle(c[0], c[1], 7, GOLD_DARK, GOLD, 2);
                p.circle(c[0], c[1], 1.8f, GOLD, 0, 0);
            }
        });
    }
}
