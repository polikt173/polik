package ua.forma;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import org.json.JSONObject;
import java.time.*;
import java.util.function.Consumer;

final class HealthSync {
    static final String[] PERMS={"android.permission.health.READ_STEPS","android.permission.health.READ_HEART_RATE","android.permission.health.READ_SLEEP"};
    static JSONObject cached(Context c){
        JSONObject o=new JSONObject();
        try { if(Build.VERSION.SDK_INT<34){o.put("status","unsupported");return o;}
            o=new JSONObject(c.getSharedPreferences("forma_health",0).getString("snapshot","{}"));
            if(!LocalDate.now().toString().equals(o.optString("date")))o=new JSONObject();
            int n=0;String[] keys={"steps","pulse","sleep"};
            for(int i=0;i<3;i++){if(c.checkSelfPermission(PERMS[i])!=PackageManager.PERMISSION_GRANTED)o.remove(keys[i]);else n++;}
            o.put("status",n==0?"permission":n==3?"connected":"partial");
        }catch(Exception ignored){}return o;
    }
    static void permissions(Activity a){
        if(Build.VERSION.SDK_INT<34)return;
        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
        for(String p:PERMS)if(a.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(!missing.isEmpty())a.requestPermissions(missing.toArray(new String[0]),42);
        else try{a.startActivity(new Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME,a.getPackageName()));}catch(Exception ignored){}
    }
    static void refresh(Activity a,Runnable done){
        if(Build.VERSION.SDK_INT<34){done.run();return;}
        Api34.refresh(a,done);
    }
    static final class Api34 {
        static void refresh(Activity a,Runnable done){
            HealthConnectManager manager=a.getSystemService(HealthConnectManager.class);
            if(manager==null){done.run();return;}
            LocalDate day=LocalDate.now();ZoneId zone=ZoneId.systemDefault();Instant now=Instant.now(),start=day.atStartOfDay(zone).toInstant();
            JSONObject result=new JSONObject();try{result.put("date",day.toString());}catch(Exception ignored){}
            int[] remaining={3};
            Runnable finish=()->{if(--remaining[0]==0){
                a.getSharedPreferences("forma_health",0).edit().putString("snapshot",result.toString()).apply();
                FormaWidget.updateAll(a);done.run();
            }};
            query(a,manager,PERMS[0],StepsRecord.STEPS_COUNT_TOTAL,start,now,v->{put(result,"steps",v);finish.run();});
            query(a,manager,PERMS[1],HeartRateRecord.BPM_AVG,start,now,v->{put(result,"pulse",v);finish.run();});
            query(a,manager,PERMS[2],SleepSessionRecord.SLEEP_DURATION_TOTAL,now.minus(Duration.ofHours(24)),now,v->{put(result,"sleep",v==null?null:v/60000L);finish.run();});
        }
        static void put(JSONObject o,String key,Long value){if(value!=null)try{o.put(key,value);}catch(Exception ignored){}}
        static void query(Activity a,HealthConnectManager m,String perm,AggregationType<Long> type,Instant from,Instant to,Consumer<Long> result){
            if(a.checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED||!from.isBefore(to)){result.accept(null);return;}
            try{
                AggregateRecordsRequest<Long> req=new AggregateRecordsRequest.Builder<Long>(new TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(to).build()).addAggregationType(type).build();
                m.aggregate(req,a.getMainExecutor(),new OutcomeReceiver<AggregateRecordsResponse<Long>,HealthConnectException>(){
                    @Override public void onResult(AggregateRecordsResponse<Long> r){result.accept(r.get(type));}
                    @Override public void onError(HealthConnectException e){result.accept(null);}
                });
            }catch(Exception e){result.accept(null);}
        }
    }
}
