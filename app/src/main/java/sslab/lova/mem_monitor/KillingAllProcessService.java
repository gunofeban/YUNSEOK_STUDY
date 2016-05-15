package sslab.lova.mem_monitor;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by JEFF on 2015-11-17.
 */
public class KillingAllProcessService extends Service {

    private ActivityManager activityManager;

    private Context mContext;

    private int killingCount =0;
    private int touchCount=0;
    private View mViewGroup;
    private String killingMessage="";

    int SDK = Build.VERSION.SDK_INT;

    PowerManager pm;
    PowerManager.WakeLock wakeLock;

    ArrayList<String> currentProcessList;

    boolean isAlarm= false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService( Context.POWER_SERVICE );
        wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, "MY TAG" );
        wakeLock.acquire();
        Log.d("LOTTE","KillingAllProcessService onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("LOTTE", "KillingAppProcessService onStartCommand");

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        mContext = this;

        if(intent != null){
            isAlarm = intent.getBooleanExtra("repeateAlarm",false);
        }

        loadCount();

        if(killingCount<=3){
            //그러면 6시간에 한번씩 죽이겠다.
            Log.d("LOTTE", "6시간에 한번씩 죽이겠음 Count :" + String.valueOf(killingCount));
            killingMessage = "6시간에 한번씩 죽인정보 " + String.valueOf(killingCount) + "번 째";
            repeatSettingAlarm();

            currentProcessList = convertRunningAppProcessToList(activityManager.getRunningAppProcesses());
            Intent i = new Intent(mContext, TopViewService.class);
            i.putExtra("isKillingServiceCall", true);
            i.putExtra("currentProcessList", currentProcessList);

            startKill();

            startService(i);
            saveCount();
            wakeLock.release();
            stopSelf();
        }else{
            //터치 3번 발생하면 죽이겠다
            Log.d("LOTTE", "터치 3번에 시작하겠음 Count :" + String.valueOf(killingCount));
            regReceiver();
            createTopView();

            if(killingCount == 6){
                killingCount = 0;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public void regReceiver(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(screenOnOff,intentFilter);
    }

    private void createTopView() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        lp.width = 0;
        lp.height = 0;

        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        lp.format = PixelFormat.TRANSLUCENT;
        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;

        mViewGroup = new View(this);

        mViewGroup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Thread t1 = new Thread(new Runnable() {
                    public void run() {
                        touchCount++;
                        Log.d("LOTTE", "터치카운트 : " + String.valueOf(touchCount));

                        if(touchCount == 3){

                            Log.d("LOTTE", "터치카운트가 3이 되었으므로 킬링 시작!! ");
                            WindowManager wm2 = (WindowManager) getSystemService(WINDOW_SERVICE);
                            wm2.removeView(mViewGroup);
                            killingMessage = "3번 터치 후 킬링 시작한 정보 " + String.valueOf(killingCount-3) + "번 째";
                            repeatSettingAlarm();

                            currentProcessList = convertRunningAppProcessToList(activityManager.getRunningAppProcesses());
                            Intent i = new Intent(mContext, TopViewService.class);
                            i.putExtra("isKillingServiceCall", true);
                            i.putExtra("currentProcessList", currentProcessList);

                            startKill();
                            startService(i);

                            touchCount = 0;

                            saveCount();
                            wakeLock.release();
                            stopSelf();
                        }
                    }
                });
                t1.start();

                return false;
            }
        });
        wm.addView(mViewGroup, lp);
    }
    public void loadCount() {
        try {
            Log.d("LOTTE", "loadCount");
            FileInputStream fileIn = new FileInputStream(Environment.getExternalStorageDirectory() + "/SSLAB/killingcount.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Object obj = in.readObject();
            killingCount = (int) obj;
            in.close();
            fileIn.close();
            killingCount++;
        } catch (Exception e) {
            try {
                Log.d("LOTTE", "saveCount");
                killingCount = 1;
                FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory() + "/SSLAB/killingcount.ser");
                ObjectOutputStream objOut = new ObjectOutputStream(fileout);
                objOut.writeObject(killingCount);
                objOut.close();
                fileout.close();

            } catch (Exception e2) {
                e2.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    public void saveCount() {
        try {
            Log.d("LOTTE", "save count count 는 "+String.valueOf(killingCount));
            FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory() + "/SSLAB/killingcount.ser");
            ObjectOutputStream objOut = new ObjectOutputStream(fileout);
            objOut.writeObject(killingCount);
            objOut.close();
            fileout.close();

        } catch (Exception e2) {
            e2.printStackTrace();
        }

    }

    BroadcastReceiver screenOnOff = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent){
            touchCount = 0;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("LOTTE","killingAllProcessService onDestory");
    }

    public void repeatSettingAlarm(){

        AlarmManager alarm = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(mContext,KillingAllProcessService.class);
        intent.putExtra("repeateAlarm",true);
        PendingIntent pintent = PendingIntent.getService(this, 0, intent, 0);
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 21600000, pintent);

        //180000 3분
        //300000 5분
        //600000 10분
        //1800000
        //3600000 1시간
        //10800000 3시간
        //21600000 6시간d

        Intent intent2 = new Intent(mContext,AlarmService.class);
        intent2.putExtra("killingMessage",killingMessage);

        Log.d("LOTTE","repeatSettingAlarm 에서 killingMessage : " + killingMessage);

        PendingIntent pintent2 = PendingIntent.getService(this, 0, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 21600000, pintent2);
    }


    public ArrayList<String> convertRunningAppProcessToList(List<ActivityManager.RunningAppProcessInfo> rl) {

        ArrayList<String> list = new ArrayList<String>();

        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : rl) {
            if (runningAppProcessInfo.pid != android.os.Process.myPid()) {
                String str = "";

                try {
                    FileInputStream fis = new FileInputStream("/proc/" + runningAppProcessInfo.pid + "/oom_adj");
                    BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
                    str = bufferedReader.readLine();
                    double oom_adj = Double.parseDouble(str);

                    if (SDK >= 20) {
                        if (oom_adj > 4) {
                            list.add(runningAppProcessInfo.processName);
                        }
                        /**
                         * SDK 20 이하에서의 서비스, 프로세스분류
                         */
                    } else {
                        if (oom_adj > 3 ){
                            list.add(runningAppProcessInfo.processName);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } /// process  (Background process)

        return list;
    }

    public void startKill() {
        Log.d("LOTTE", "start kill 호출 ");
        final List<ActivityManager.RunningAppProcessInfo> ps = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : ps) {
            if (runningAppProcessInfo.pid != android.os.Process.myPid()) {
                android.os.Process.sendSignal(runningAppProcessInfo.pid, Process.SIGNAL_KILL);
                activityManager.killBackgroundProcesses(runningAppProcessInfo.processName);
            }
        }

        Log.d("LOTTE", "startKill finish");
    }
}
