package ua.forma;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FormaWidget extends AppWidgetProvider {
    static final String PREFS="forma_widget";
    static void save(Context ctx,String json){
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("data",json).apply();
        updateAll(ctx);
    }
    static void updateAll(Context ctx){
        AppWidgetManager m=AppWidgetManager.getInstance(ctx);
        for(int id:m.getAppWidgetIds(new ComponentName(ctx,FormaWidget.class)))m.updateAppWidget(id,build(ctx,id));
    }
    @Override public void onUpdate(Context ctx,AppWidgetManager m,int[] ids){
        for(int id:ids)m.updateAppWidget(id,build(ctx,id));
    }
    @Override public void onAppWidgetOptionsChanged(Context ctx,AppWidgetManager m,int id,Bundle opts){m.updateAppWidget(id,build(ctx,id));}
    private static final int INK=0xff542c19,MUT=0xff895839,ORANGE=0xffe98525,TRACK=0xffebd5c2,TILE=0x99ffffff;
    private static float ratio(int a,int b){return b<=0?0:Math.max(0,Math.min(1,(float)a/b));}
    private static void text(Canvas c,Paint p,String s,float x,float y,float size,int col,boolean bold,Paint.Align align){
        p.setStyle(Paint.Style.FILL);p.setColor(col);p.setTextSize(size);p.setTextAlign(align);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,p);
    }
    private static void tile(Canvas c,Paint p,float x,float y,float w,float h){
        p.setStyle(Paint.Style.FILL);p.setColor(TILE);c.drawRoundRect(new RectF(x,y,x+w,y+h),14,14,p);
    }
    private static void macro(Canvas c,Paint p,String name,int a,int b,float x,float y,float width,int color,String unit){
        tile(c,p,x,y,width,50);
        text(c,p,name,x+10,y+18,14,INK,true,Paint.Align.LEFT);
        text(c,p,a+" / "+(b>0?String.valueOf(b):"—")+unit,x+width-10,y+18,14,INK,false,Paint.Align.RIGHT);
        p.setColor(TRACK);c.drawRoundRect(new RectF(x+10,y+30,x+width-10,y+39),5,5,p);
        float len=(width-20)*ratio(a,b);if(len>0){p.setColor(color);c.drawRoundRect(new RectF(x+10,y+30,x+10+len,y+39),5,5,p);}
    }
    private static void stat(Canvas c,Paint p,String label,String value,float x,float y,float w){
        tile(c,p,x,y,w,53);text(c,p,label,x+w/2,y+18,13,MUT,false,Paint.Align.CENTER);text(c,p,value,x+w/2,y+39,15,INK,true,Paint.Align.CENTER);
    }
    static RemoteViews build(Context ctx,int id){
        int k=0,kt=2000,p=0,pt=100,f=0,ft=0,carb=0,ct=0,w=0,wt=2000,ex=0;boolean en=false;Date now=new Date();
        try{JSONObject o=new JSONObject(ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("data","{}"));
            kt=o.optInt("kt",kt);pt=o.optInt("pt",pt);ft=o.optInt("ft");ct=o.optInt("ct");wt=o.optInt("wt",wt);en=o.optInt("en")==1;
            if(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now).equals(o.optString("d"))){k=o.optInt("k");p=o.optInt("p");f=o.optInt("f");carb=o.optInt("c");w=o.optInt("w");ex=o.optInt("ex");}
        }catch(Exception ignored){}
        Locale loc=en?Locale.US:Locale.forLanguageTag("uk-UA");String unit=en?" g":" г";
        Bitmap b=Bitmap.createBitmap(600,365,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint q=new Paint(Paint.ANTI_ALIAS_FLAG);
        text(c,q,"F",12,27,29,ORANGE,true,Paint.Align.LEFT);
        text(c,q,en?"Forma":"Форма",37,27,24,INK,true,Paint.Align.LEFT);
        text(c,q,new SimpleDateFormat("d MMM",loc).format(now),588,24,17,MUT,false,Paint.Align.RIGHT);
        RectF r=new RectF(27,48,199,220);q.setStyle(Paint.Style.STROKE);q.setStrokeWidth(12);q.setStrokeCap(Paint.Cap.ROUND);q.setColor(TRACK);c.drawArc(r,0,360,false,q);q.setColor(ORANGE);if(ratio(k,kt)>0)c.drawArc(r,-90,360*ratio(k,kt),false,q);
        text(c,q,String.valueOf(k),113,133,35,INK,true,Paint.Align.CENTER);
        text(c,q,"/ "+kt+(en?" kcal":" ккал"),113,158,17,MUT,false,Paint.Align.CENTER);
        text(c,q,en?"Consumed":"Спожито",113,182,15,MUT,false,Paint.Align.CENTER);
        macro(c,q,en?"Protein":"Білки",p,pt,220,43,368,ORANGE,unit);
        macro(c,q,en?"Fat":"Жири",f,ft,220,101,368,0xffdda139,unit);
        macro(c,q,en?"Carbs":"Вуглеводи",carb,ct,220,159,368,0xffc96b35,unit);
        stat(c,q,en?"Water":"Вода",String.format(loc,"%.1f / %.1f",w/1000f,wt/1000f)+(en?" L":" л"),10,235,188);
        stat(c,q,en?"Workouts":"Тренування",ex>0?(en?"Done: ":"Готово: ")+ex:"—",206,235,188);
        stat(c,q,kt>=k?(en?"Remaining":"Залишилось"):(en?"Over":"Перебір"),Math.abs(kt-k)+(en?" kcal":" ккал"),402,235,188);
        JSONObject health=HealthSync.cached(ctx);
        long steps=health.optLong("steps",-1),pulse=health.optLong("pulse",-1),sleep=health.optLong("sleep",-1);
        stat(c,q,en?"Steps":"Кроки",steps<0?"—":String.valueOf(steps),10,298,188);
        stat(c,q,en?"Avg heart rate":"Сер. пульс",pulse<0?"—":pulse+(en?" bpm":" уд/хв"),206,298,188);
        stat(c,q,en?"Sleep / 24h":"Сон / 24 год",sleep<0?"—":(sleep/60)+(en?" h ":" год ")+(sleep%60)+(en?" m":" хв"),402,298,188);
        RemoteViews v=new RemoteViews(ctx.getPackageName(),R.layout.widget);v.setImageViewBitmap(R.id.wImage,b);
        v.setContentDescription(R.id.wImage,(en?"Calories ":"Калорії ")+k+" / "+kt+"; "+(en?"Protein ":"Білки ")+p+" / "+pt+"; "+(en?"Fat ":"Жири ")+f+" / "+ft+"; "+(en?"Carbs ":"Вуглеводи ")+carb+" / "+ct+"; "+(en?"Water ":"Вода ")+w+" ml; "+(en?"Workouts ":"Тренування ")+ex);
        PendingIntent open=PendingIntent.getActivity(ctx,0,new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);v.setOnClickPendingIntent(R.id.wRoot,open);return v;
    }
}
