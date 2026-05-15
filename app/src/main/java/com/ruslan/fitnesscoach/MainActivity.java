
package com.ruslan.fitnesscoach;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.animation.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root;
    SharedPreferences p;
    CountDownTimer timer;
    TextView activeTimer;
    int timerLeftSec=0;
    boolean timerPaused=false;

    final int BG=Color.rgb(5,6,8), CARD=Color.rgb(18,20,25), CARD2=Color.rgb(28,31,38);
    final int TEXT=Color.rgb(246,247,249), MUTED=Color.rgb(155,164,178), SILVER=Color.rgb(214,217,226), GOLD=Color.rgb(255,213,60), GREEN=Color.rgb(70,220,130), RED=Color.rgb(245,110,110);
    final String DEFAULT_MODEL="openai/gpt-4o-mini";
    final String FALLBACK_MODEL="deepseek/deepseek-chat";

    static class Ex {String name,sets,reps,weight,group,img; Ex(String n,String s,String r,String w,String g,String img){name=n;sets=s;reps=r;weight=w;group=g;this.img=img;}}
    static class W {String day,title,sub,img; Ex[] ex; W(String d,String t,String s,String img,Ex[] e){day=d;title=t;sub=s;this.img=img;ex=e;}}

    W[] workouts = new W[]{
        new W("ПН","Грудь + трицепс","Жим • грудь • пресс","chest",new Ex[]{
            new Ex("Жим штанги лёжа","4","6–8","45","верх","chest"), new Ex("Жим гантелей на наклонной","4","10","18","верх","chest"), new Ex("Разводка гантелей","3","12","10","изоляция","chest"), new Ex("Брусья / гравитрон","3","8–10","0","верх","chest"), new Ex("Трицепс на блоке","4","12","30","изоляция","chest"), new Ex("Скручивания","4","20","0","пресс","exercise_demo"), new Ex("Планка","3","60 сек","0","пресс","exercise_demo")
        }),
        new W("СР","Спина + бицепс","Тяги • ширина • руки","back",new Ex[]{
            new Ex("Становая тяга","4","5","65","низ","back"), new Ex("Тяга верхнего блока","4","10","45","верх","back"), new Ex("Тяга штанги к поясу","4","8","40","верх","back"), new Ex("Тяга сидя в блоке","3","12","45","верх","back"), new Ex("EZ-гриф на бицепс","4","10","20","изоляция","back"), new Ex("Молотки","3","12","12","изоляция","back")
        }),
        new W("ПТ","Ноги + плечи","Масса • база • мощь","legs",new Ex[]{
            new Ex("Присед / Смит","4","8","50","низ","legs"), new Ex("Жим ногами","4","12","140","низ","legs"), new Ex("Выпады","3","10","12","низ","legs"), new Ex("Жим гантелей сидя","4","10","16","верх","legs"), new Ex("Махи в стороны","4","15","8","изоляция","legs"), new Ex("Задняя дельта","3","15","0","изоляция","legs")
        }),
        new W("ВС","Верх тела","Сила • масса • добивка","chest",new Ex[]{
            new Ex("Жим лёжа","5","5","50","верх","chest"), new Ex("Тяга верхнего блока","4","8","50","верх","back"), new Ex("Жим гантелей","3","10","20","верх","chest"), new Ex("Тяга блока сидя","3","10","45","верх","back"), new Ex("Бицепс","3","12","0","изоляция","back"), new Ex("Трицепс","3","12","0","изоляция","chest"), new Ex("Пресс","1","10 минут","0","пресс","exercise_demo")
        })
    };

    public void onCreate(Bundle b){
        super.onCreate(b);
        p=getSharedPreferences("fitness_coach_108",0);
        if(!p.contains("weight")) p.edit().putFloat("weight",70f).putFloat("waist",0f).putInt("streak",0).putString("model",DEFAULT_MODEL).apply();
        if(!p.getBoolean("onboarding_done",false)) onboarding(); else splash();
    }


    void onboarding(){
        screen("");
        LinearLayout hero=card3d();
        hero.addView(txt("FITNESS COACH 1.08",30,TEXT,true));
        hero.addView(txt("Luxury Black • glassmorphism • AI Coach inside app",14,SILVER,false));
        hero.addView(txt("Цель: 77 кг, убрать живот, +мышцы",16,GOLD,true));
        hero.addView(txt("График: ПН / СР / ПТ / ВС",15,MUTED,false));
        root.addView(hero);
        LinearLayout perks=card3d();
        perks.addView(txt("Premium UX",22,TEXT,true));
        perks.addView(txt("• Плавные анимации карточек и кнопок",14,SILVER,false));
        perks.addView(txt("• Таймер 60/90/120/180 + пауза/стоп",14,SILVER,false));
        perks.addView(txt("• AI-анализ прогресса веса и тренировок",14,SILVER,false));
        perks.addView(txt("• Haptic feedback и красивый фокус",14,SILVER,false));
        root.addView(perks);
        button("Начать",()->{p.edit().putBoolean("onboarding_done",true).apply();splash();});
    }

    void splash(){
        FrameLayout f=new FrameLayout(this);
        ImageView bg=new ImageView(this); bg.setImageResource(res("bg")); bg.setScaleType(ImageView.ScaleType.CENTER_CROP); f.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        ImageView logo=new ImageView(this); logo.setImageResource(res("hero_icon")); box.addView(logo,new LinearLayout.LayoutParams(dp(178),dp(178)));
        TextView title=txt("FITNESS COACH",32,TEXT,true); title.setGravity(Gravity.CENTER); box.addView(title);
        TextView v=txt("v1.08 • 3D LUXURY BLACK",14,SILVER,true); v.setGravity(Gravity.CENTER); box.addView(v);
        f.addView(box,new FrameLayout.LayoutParams(-1,-1)); setContentView(f);
        new Handler().postDelayed(this::home,650);
    }

    void screen(String title){
        ScrollView sc=new ScrollView(this);
        FrameLayout frame=new FrameLayout(this);
        ImageView bg=new ImageView(this); bg.setImageResource(res("bg")); bg.setScaleType(ImageView.ScaleType.CENTER_CROP); frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(28),dp(18),dp(34)); frame.addView(root);
        sc.addView(frame); setContentView(sc);
        if(title.length()>0) root.addView(txt(title,30,TEXT,true));
    }

    int res(String name){ return getResources().getIdentifier(name,"drawable",getPackageName()); }

    void home(){
        screen("Fitness Coach");
        add("Версия 1.08 • premium glass UI • OpenRouter AI",14,MUTED,false);
        space(10);
        W today=today();
        LinearLayout hero=card3d();
        ImageView im=new ImageView(this); im.setImageResource(res(today.img)); im.setScaleType(ImageView.ScaleType.CENTER_CROP); hero.addView(im,new LinearLayout.LayoutParams(-1,dp(135)));
        hero.addView(txt("Сегодня • "+today.day+" • "+today.title,23,TEXT,true));
        hero.addView(txt(today.sub+" • "+doneIn(today)+"/"+today.ex.length+" выполнено",14,SILVER,false));
        hero.setOnClickListener(v->workout(today));
        root.addView(hero);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("Вес",p.getFloat("weight",70)+" кг","цель 77",SILVER),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(metric("Серия",p.getInt("streak",0)+"","дней",GOLD),new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        progress();
        button("💬 AI Coach",this::chat);
        button("🚀 Тренировка дня",()->workout(today));
        button("🏋️ Все тренировки",this::workouts);
        button("📊 Статистика / PR",this::stats);
        button("🍽 Питание",this::nutrition);
        button("⏱ Таймер",this::timerScreen);
        button("🔑 OpenRouter API",this::apiSettings);
        button("⚙️ Настройки",this::settings);
        bottomNav("home");
    }


    void bottomNav(String tab){
        LinearLayout nav=card3d();
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(navBtn("🏠","Главная",tab.equals("home"),this::home),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(navBtn("🏋️","Тренировки",tab.equals("workout"),this::workouts),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(navBtn("📊","Прогресс",tab.equals("stats"),this::stats),new LinearLayout.LayoutParams(0,-2,1));
        nav.addView(navBtn("🤖","AI",tab.equals("ai"),this::chat),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(nav);
    }
    LinearLayout navBtn(String icon,String label,boolean active,Runnable run){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);b.setPadding(dp(6),dp(4),dp(6),dp(4));
        b.setBackground(round(active?Color.argb(145,255,213,60):Color.argb(120,34,38,46),16));
        TextView i=txt(icon,18,active?Color.BLACK:SILVER,true);i.setGravity(Gravity.CENTER);b.addView(i);
        TextView t=txt(label,11,active?Color.BLACK:SILVER,true);t.setGravity(Gravity.CENTER);b.addView(t);
        b.setOnClickListener(v->{vibrate();run.run();});
        return b;
    }

    W today(){int d=Calendar.getInstance().get(Calendar.DAY_OF_WEEK); if(d==Calendar.MONDAY)return workouts[0]; if(d==Calendar.WEDNESDAY)return workouts[1]; if(d==Calendar.FRIDAY)return workouts[2]; if(d==Calendar.SUNDAY)return workouts[3]; return workouts[0];}

    void progress(){float w=p.getFloat("weight",70);int pc=Math.max(0,Math.min(100,(int)(((w-70)/7)*100)));LinearLayout c=card3d();c.addView(txt("Прогресс к 77 кг",18,TEXT,true));ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(pc);c.addView(pb);c.addView(txt(pc+"% • осталось "+String.format(Locale.US,"%.1f",77-w)+" кг",13,MUTED,false));root.addView(c);}
    LinearLayout metric(String a,String b,String c,int col){LinearLayout m=card3d();m.addView(txt(a,12,MUTED,false));m.addView(txt(b,22,col,true));m.addView(txt(c,12,SILVER,false));return m;}
    int doneIn(W w){int d=0;for(int i=0;i<w.ex.length;i++)if(p.getBoolean(w.day+"_"+i,false))d++;return d;}

    void workout(W w){
        screen("");
        LinearLayout head=card3d();
        head.addView(txt(w.day+" • "+w.title,27,TEXT,true));
        head.addView(txt(w.sub,15,MUTED,false));
        int done=doneIn(w), total=w.ex.length;
        head.addView(txt("Выполнено: "+done+"/"+total+" • "+((done*100)/total)+"%",14,SILVER,true));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(total);bar.setProgress(done);head.addView(bar);root.addView(head);
        activeTimer=txt("⏱ таймер готов",17,SILVER,true);activeTimer.setGravity(Gravity.CENTER);root.addView(activeTimer);
        button("⏸ Пауза / ▶ Продолжить",this::togglePauseTimer);
        button("⏹ Остановить таймер",()->stopTimer());

        for(int i=0;i<w.ex.length;i++){
            Ex e=w.ex[i]; String key=w.day+"_"+i, weightKey="weight_"+w.day+"_"+i;
            LinearLayout c=card3d();
            LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL);
            CheckBox cb=new CheckBox(this); cb.setText(e.name); cb.setTextColor(TEXT); cb.setTextSize(18); cb.setTypeface(Typeface.DEFAULT_BOLD); cb.setChecked(p.getBoolean(key,false));
            cb.setOnCheckedChangeListener((b,ch)->{p.edit().putBoolean(key,ch).apply(); if(ch){updatePR(e); vibrate(); toast("Выполнено"); startTimer(90);} });
            top.addView(cb,new LinearLayout.LayoutParams(0,-2,1));
            TextView pr=badge("PR"); top.addView(pr,new LinearLayout.LayoutParams(dp(48),dp(34))); c.addView(top);
            c.addView(txt("Подходы: "+e.sets+" • Повторы: "+e.reps,14,MUTED,false));
            String current=p.getString(weightKey,e.weight);
            TextView wt=txt("Вес: "+current+" кг",18,GOLD,true); c.addView(wt);
            LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.addView(mini("−2.5",()->changeWeight(weightKey,current,-2.5,w)),new LinearLayout.LayoutParams(0,dp(42),1));
            controls.addView(mini("+2.5",()->changeWeight(weightKey,current,2.5,w)),new LinearLayout.LayoutParams(0,dp(42),1));
            controls.addView(mini("+5",()->changeWeight(weightKey,current,5,w)),new LinearLayout.LayoutParams(0,dp(42),1));
            c.addView(controls);
            c.addView(txt("Прогрессия: "+advice(e),13,SILVER,false));
            LinearLayout timers=new LinearLayout(this);timers.setOrientation(LinearLayout.HORIZONTAL);
            timers.addView(mini("60",()->startTimer(60)),new LinearLayout.LayoutParams(0,dp(42),1));
            timers.addView(mini("90",()->startTimer(90)),new LinearLayout.LayoutParams(0,dp(42),1));
            timers.addView(mini("120",()->startTimer(120)),new LinearLayout.LayoutParams(0,dp(42),1));
            timers.addView(mini("180",()->startTimer(180)),new LinearLayout.LayoutParams(0,dp(42),1));
            c.addView(timers);
            Button demo=mini("📷 техника",()->exerciseDemo(e)); c.addView(demo,new LinearLayout.LayoutParams(-1,dp(44)));
            root.addView(c);space(8);
        }
        button("✅ Завершить тренировку",()->{SharedPreferences.Editor ed=p.edit();for(int i=0;i<w.ex.length;i++){ed.putBoolean(w.day+"_"+i,true);updatePR(w.ex[i]);}ed.putInt("streak",p.getInt("streak",0)+1).putBoolean("cal_"+date(),true).apply();toast("Тренировка записана");workout(w);});
        button("💬 AI: обновить советы по тренировке",()->askAI("Я делаю тренировку "+w.day+" "+w.title+". Проанализируй веса и дай советы. Если нужен PR, подскажи что обновить."));
        back();
    }

    void changeWeight(String key,String current,double delta,W w){
        try{double v=Double.parseDouble(current.replace(",","."));v=Math.max(0,v+delta);String s=(v%1==0)?String.valueOf((int)v):String.valueOf(v);p.edit().putString(key,s).apply();toast("Вес изменён: "+s+" кг");workout(w);}catch(Exception e){toast("Вес не число");}
    }
    void updatePR(Ex e){
        try{double v=Double.parseDouble(p.getString("weight_tmp",e.weight).replace(",","."));}catch(Exception ex){}
    }
    String advice(Ex e){if(e.group.equals("верх"))return"+2.5 кг если уверенно";if(e.group.equals("низ"))return"+5 кг если техника чистая";if(e.group.equals("изоляция"))return"контроль, не гони вес";return"качество важнее веса";}
    String date(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

    void exerciseDemo(Ex e){
        screen("Техника: "+e.name);
        ImageView im=new ImageView(this);im.setImageResource(res(e.img));im.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(im,new LinearLayout.LayoutParams(-1,dp(220)));
        LinearLayout c=card3d();c.addView(txt(e.name,24,TEXT,true));
        c.addView(txt("1. Сделай 1–2 разминочных подхода.",15,SILVER,false));
        c.addView(txt("2. Держи корпус стабильно, без рывков.",15,SILVER,false));
        c.addView(txt("3. Последние 1–2 повтора тяжёлые, но техника чистая.",15,SILVER,false));
        c.addView(txt("4. Если есть боль — снизь вес или замени упражнение.",15,RED,true));
        root.addView(c);
        button("💬 Спросить AI по технике",()->askAI("Объясни технику упражнения: "+e.name+" для меня простыми словами."));
        back();
    }

    TextView badge(String s){TextView v=txt(s,13,Color.BLACK,true);v.setGravity(Gravity.CENTER);v.setBackground(round(GOLD,18));return v;}
    Button mini(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(TEXT);b.setBackground(round(Color.argb(150,35,39,48),16));b.setOnClickListener(v->r.run());return b;}

    void startTimer(int sec){if(timer!=null)timer.cancel();timerLeftSec=sec;timerPaused=false;timer=new CountDownTimer(sec*1000L,1000){public void onTick(long ms){timerLeftSec=(int)(ms/1000);if(activeTimer!=null)activeTimer.setText("⏱ отдых "+String.format(Locale.US,"%02d:%02d",timerLeftSec/60,timerLeftSec%60));}public void onFinish(){timerLeftSec=0;if(activeTimer!=null)activeTimer.setText("🔥 отдых закончен");vibrate();toast("Отдых закончен");}}.start();}
    void togglePauseTimer(){if(timerLeftSec<=0){toast("Сначала запусти таймер");return;}if(timerPaused){startTimer(timerLeftSec);toast("Таймер продолжен");}else {if(timer!=null)timer.cancel();timerPaused=true;if(activeTimer!=null)activeTimer.setText("⏸ пауза "+String.format(Locale.US,"%02d:%02d",timerLeftSec/60,timerLeftSec%60));toast("Таймер на паузе");}}
    void stopTimer(){if(timer!=null){timer.cancel();timer=null;}timerLeftSec=0;timerPaused=false;if(activeTimer!=null)activeTimer.setText("⏱ таймер остановлен");toast("Таймер остановлен");}

    void workouts(){screen("Тренировки");for(W w:workouts){LinearLayout c=card3d();ImageView im=new ImageView(this);im.setImageResource(res(w.img));im.setScaleType(ImageView.ScaleType.CENTER_CROP);c.addView(im,new LinearLayout.LayoutParams(-1,dp(130)));c.addView(txt(w.day+" • "+w.title,22,TEXT,true));c.addView(txt(w.sub,14,MUTED,false));c.addView(txt(doneIn(w)+"/"+w.ex.length+" выполнено",14,SILVER,true));c.setOnClickListener(v->workout(w));root.addView(c);space(8);}back();}

    void stats(){
        screen("Статистика / PR");
        record("Жим лёжа","60 кг","цель 80 кг");
        record("Становая","80 кг","цель 120 кг");
        record("Вес",p.getFloat("weight",70)+" кг","цель 77 кг");
        record("Талия",p.getFloat("waist",0)==0?"не задано":p.getFloat("waist",0)+" см","замер 1 раз/неделю");
        add("Авто-PR: при отметке упражнения приложение фиксирует выполнение. В следующей версии можно расширить до истории подходов.",13,MUTED,false);
        button("✍️ Обновить замеры",this::measure);
        button("💬 AI: проанализировать прогресс",()->askAI("Проанализируй мой прогресс: вес 70 к цели 77, жим 60, становая 80. Что улучшить?"));
        back();
    }
    void record(String a,String b,String c){LinearLayout x=card3d();x.addView(txt("🏆 "+a,18,TEXT,true));x.addView(txt(b,26,GOLD,true));x.addView(txt(c,13,MUTED,false));root.addView(x);space(8);}
    void measure(){screen("Замеры");EditText w=input("Вес",1,true);w.setText(String.valueOf(p.getFloat("weight",70)));root.addView(w);EditText waist=input("Талия",1,true);float old=p.getFloat("waist",0);waist.setText(old==0?"":String.valueOf(old));root.addView(waist);button("Сохранить",()->{try{SharedPreferences.Editor e=p.edit();e.putFloat("weight",Float.parseFloat(w.getText().toString().replace(",",".")));if(waist.getText().length()>0)e.putFloat("waist",Float.parseFloat(waist.getText().toString().replace(",",".")));e.apply();stats();}catch(Exception ex){toast("Введи числа");}});back();}

    void nutrition(){screen("Питание");ImageView im=new ImageView(this);im.setImageResource(res("nutrition"));im.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(im,new LinearLayout.LayoutParams(-1,dp(180)));meal("Завтрак","Овсянка 80 г + 3 яйца + банан","≈650 ккал");meal("Обед","Рис/гречка + курица 200 г + овощи","≈850 ккал");meal("Перекус","Творог 200 г + орехи 20 г","≈450 ккал");meal("Ужин","Рыба/мясо + картофель/гречка + салат","≈750 ккал");add("Цель: 2700–2900 ккал • белок 140–150 г",15,SILVER,true);button("💬 Составить меню AI",()->askAI("Составь меню на неделю на массу без роста живота."));back();}
    void meal(String t,String food,String cal){LinearLayout c=card3d();c.addView(txt("🍽 "+t,19,TEXT,true));c.addView(txt(food,15,SILVER,false));c.addView(txt(cal,13,MUTED,false));CheckBox cb=new CheckBox(this);cb.setText("Съел");cb.setTextColor(SILVER);String key="meal_"+t+"_"+date();cb.setChecked(p.getBoolean(key,false));cb.setOnCheckedChangeListener((b,ch)->p.edit().putBoolean(key,ch).apply());c.addView(cb);root.addView(c);space(8);}

    void chat(){screen("AI Coach");add("Модель: "+p.getString("model",DEFAULT_MODEL),13,SILVER,true);LinearLayout h=card3d();String hist=p.getString("chat","");if(hist.length()==0)hist="AI Coach готов. Вставь OpenRouter ключ и спрашивай.";h.addView(txt(hist,15,TEXT,false));root.addView(h);EditText q=input("Напиши вопрос...",4,false);root.addView(q);button("➡️ Отправить",()->{String s=q.getText().toString().trim();if(s.length()<2){toast("Напиши вопрос");return;}askAI(s);});button("🍗 Меню на массу",()->askAI("Составь меню на день на массу без роста живота."));button("🏋️ Что тренировать сегодня?",()->askAI("Что тренировать сегодня по моему графику?"));button("💪 Почему не растёт жим?",()->askAI("Жим лёжа стоит. Что делать?"));button("🧹 Очистить чат",()->{p.edit().putString("chat","").apply();chat();});back();}
    void askAI(String q){String key=p.getString("or_key","");if(key.length()<20){toast("Сначала вставь OpenRouter API ключ");apiSettings();return;}append("Ты: "+q+"\n\nТренер: печатает...\n\n");chat();new Thread(()->{String ans;try{ans=openRouter(q,p.getString("model",DEFAULT_MODEL));}catch(Exception ex){try{ans=openRouter(q,"deepseek/deepseek-chat");}catch(Exception ex2){ans=friendly(ex2.getMessage());}}String old=p.getString("chat","");old=old.replace("Тренер: печатает...\n\n","Тренер: "+ans+"\n\n");p.edit().putString("chat",old).apply();runOnUiThread(this::chat);}).start();}
    void append(String s){p.edit().putString("chat",p.getString("chat","")+s).apply();}
    String openRouter(String q,String model)throws Exception{URL url=new URL("https://openrouter.ai/api/v1/chat/completions");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setRequestProperty("Authorization","Bearer "+p.getString("or_key",""));c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("HTTP-Referer","https://local.fitness.coach");c.setRequestProperty("X-Title","Fitness Coach");c.setDoOutput(true);c.setConnectTimeout(30000);c.setReadTimeout(90000);String sys="Ты фитнес-тренер Руслана. Рост 177, вес 70, цель 77, убрать живот, жим 60, становая 80, график ПН/СР/ПТ/ВС. Отвечай по-русски, конкретно.";String body="{\"model\":\""+esc(model)+"\",\"messages\":[{\"role\":\"system\",\"content\":\""+esc(sys)+"\"},{\"role\":\"user\",\"content\":\""+esc(q)+"\"}],\"temperature\":0.5}";OutputStream os=c.getOutputStream();os.write(body.getBytes("UTF-8"));os.close();int code=c.getResponseCode();InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();String r=read(is);if(code<200||code>=300)throw new Exception("HTTP "+code+": "+r);String out=extract(r);return out.length()>0?out:r.substring(0,Math.min(1000,r.length()));}
    String extract(String j){String m="\"content\":\"";int i=j.indexOf(m);if(i<0)return"";i+=m.length();StringBuilder s=new StringBuilder();boolean e=false;for(int k=i;k<j.length();k++){char ch=j.charAt(k);if(e){if(ch=='n')s.append('\n');else if(ch=='t')s.append('\t');else s.append(ch);e=false;}else if(ch=='\\')e=true;else if(ch=='\"')break;else s.append(ch);}return s.toString();}
    String friendly(String m){if(m==null)m="";if(m.contains("401"))return"OpenRouter ключ неверный.";if(m.contains("402")||m.toLowerCase().contains("credits"))return"Нет баланса OpenRouter или модель платная.";if(m.contains("404"))return"Модель не найдена. Попробуй openai/gpt-4o-mini или deepseek/deepseek-chat.";return"Ошибка OpenRouter: "+m;}
    void apiSettings(){screen("OpenRouter API");EditText key=input("sk-or-...",3,false);key.setText(p.getString("or_key",""));root.addView(key);EditText model=input("openai/gpt-4o-mini",1,false);model.setText(p.getString("model",DEFAULT_MODEL));root.addView(model);button("💾 Сохранить",()->{p.edit().putString("or_key",key.getText().toString().trim()).putString("model",model.getText().toString().trim()).apply();toast("Сохранено");home();});button("✅ Проверить",()->{p.edit().putString("or_key",key.getText().toString().trim()).putString("model",model.getText().toString().trim()).apply();askAI("Ответь коротко: OpenRouter работает.");});add("Модели: openai/gpt-4o-mini, deepseek/deepseek-chat, anthropic/claude-3.7-sonnet.",13,SILVER,false);back();}

    void calendar(){screen("Календарь");Calendar cal=Calendar.getInstance();int month=cal.get(Calendar.MONTH);cal.set(Calendar.DAY_OF_MONTH,1);LinearLayout row=null;while(cal.get(Calendar.MONTH)==month){if(row==null||row.getChildCount()==4){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);root.addView(row);}String dt=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.getTime());boolean ok=p.getBoolean("cal_"+dt,false);TextView cell=txt((ok?"✓ ":"• ")+cal.get(Calendar.DAY_OF_MONTH),16,ok?GOLD:SILVER,true);cell.setGravity(Gravity.CENTER);cell.setBackground(round(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(cell,lp);cal.add(Calendar.DAY_OF_MONTH,1);}back();}
    void timerScreen(){screen("Таймер");activeTimer=txt("01:30",62,SILVER,true);activeTimer.setGravity(Gravity.CENTER);root.addView(activeTimer);button("60 сек",()->startTimer(60));button("90 сек",()->startTimer(90));button("120 сек",()->startTimer(120));button("180 сек",()->startTimer(180));button("⏸ Пауза / ▶ Продолжить",this::togglePauseTimer);button("⏹ Остановить",this::stopTimer);back();}
    void settings(){screen("Настройки");button("Сбросить галочки недели",()->{SharedPreferences.Editor e=p.edit();for(W w:workouts)for(int i=0;i<w.ex.length;i++)e.putBoolean(w.day+"_"+i,false);e.apply();home();});button("Очистить чат",()->{p.edit().putString("chat","").apply();toast("Чат очищен");});button("Сбросить серию",()->{p.edit().putInt("streak",0).apply();home();});back();}

    String read(InputStream is)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));StringBuilder sb=new StringBuilder();String l;while((l=br.readLine())!=null)sb.append(l);return sb.toString();}
    String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
    void vibrate(){try{((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(80);}catch(Exception e){}}
    void animatePress(View v){ObjectAnimator.ofFloat(v,"scaleX",1f,0.97f,1f).setDuration(170).start();ObjectAnimator.ofFloat(v,"scaleY",1f,0.97f,1f).setDuration(170).start();}
    LinearLayout card3d(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(210,40,45,55),Color.argb(180,10,12,16)});g.setCornerRadius(dp(30));g.setStroke(dp(1),Color.argb(130,255,255,255));l.setBackground(g);l.setElevation(dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));l.setLayoutParams(lp);l.setAlpha(0f);l.animate().alpha(1f).setDuration(300).start();return l;}
    void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(Color.BLACK);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(round(SILVER,24));b.setPadding(dp(10),dp(13),dp(10),dp(13));b.setOnClickListener(v->{vibrate();animatePress(v);r.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));root.addView(b,lp);}
    EditText input(String hint,int lines,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setBackground(round(CARD2,28));e.setPadding(dp(16),dp(14),dp(16),dp(14));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    TextView txt(String s,int z,int col,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(col);v.setPadding(0,dp(5),0,dp(5));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    void add(String s,int z,int col,boolean bold){root.addView(txt(s,z,col,bold));}
    void back(){button("← Назад",this::home);}
    void space(int h){root.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(h)));}
    GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    int dp(int v){return(int)(v*getResources().getDisplayMetrics().density);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
