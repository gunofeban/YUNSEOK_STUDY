package sslab.lova.mem_monitor;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by JEFF on 2015-11-06.
 */
public class CollectBroadcastMessageService extends Service {
    //    ArrayList<String> extractActionArr = new ArrayList<String>();
    BroadcastReceiver collectReceiver;
    Context mContext;
    BroadcastQueue bq = new BroadcastQueue();

    private ArrayList<String> actionArr = new ArrayList<String>();
    private ArrayList<String> extractActionArr = new ArrayList<String>();
    private ArrayList<String> newExtractActionArr;
    private ArrayList<String> installPackageName = new ArrayList<String>();

    private ArrayList<String> autoCreateList;

    private Map<String,ArrayList<String>> allBroadcastMap = new HashMap<String,ArrayList<String>>();



    @Override
    public void onCreate() {
        Log.d("LOTTE", "CollectBroadcastMessageService start!! ");
        mContext = this;

        loadBroadcastQueue();
        bq.insertRecord("android.intent.action.BOOT_COMPLETED");
        registerBroadcastReceiver();
//        loadTimer();
//        createTimer();
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                countStart();
//            }
//        }).start();
        super.onCreate();
    }

    /**
     * 파일에 저장된 BroadcastQueue 객체를 불러오는 소스
     * 왜냐하면 발생된 Broadcast 메시지를 계속해서 유지하고 있어야 어떤 어플리케이션이 어떤 Broadcast message로 실행됬는지 알 수 있기 때문
     */
    public void loadBroadcastQueue(){
        try {
            FileInputStream fileIn = new FileInputStream(Environment.getExternalStorageDirectory() + "/SSLAB/bq.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Object obj = in.readObject();
            bq = (BroadcastQueue) obj;
            in.close();
            fileIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 설치된 모든 어플리케이션의 매니패스트 문서를 파싱해서 broadcast message action을 빼오는 함수
     * 1. 설치된 어플리케이션 조사
     * 2. 매니패스트 파싱하여 action 빼오고
     * 3. 그 action을 이 앱의 broadcastreceiver에 동적으로 등록
     */
    public void registerBroadcastReceiver(){
        extractActionArr.clear();
        getInstallApplicationPackageNameInDevice();

        for(int i=0;i<installPackageName.size();i++){
            accessApplicationManifest(installPackageName.get(i));
        }
//        Log.d("OYSKAR", String.valueOf(extractActionArr.size()));
        /**
         * 추출된 extractActionArr의 중복 데이터 제거, 모든 action들을 뽑아 오는 것이기 때문에 당연히 중복이 될 수 있음.
         */
        HashSet hs = new HashSet(extractActionArr);

        // ArrayList 형태로 다시 생성
        newExtractActionArr = new ArrayList<String>(hs);

        collectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                // TODO Auto-generated method stub
                Log.e("TEST", arg1.getAction());
                bq.insertRecord(arg1.getAction());

                try {
                    FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory() + "/SSLAB/bq.ser");
                    ObjectOutputStream objOut = new ObjectOutputStream(fileout);
                    objOut.writeObject(bq);
                    objOut.close();
                    fileout.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }

                Util.saveLogToFile(Util.dateToStringYMDHMS(System.currentTimeMillis())+"/"+ arg1.getAction() + "\n", "/SSLAB/broadcastmessage.txt");
            }
        };
        IntentFilter intentFilter = new IntentFilter();

        for (int i = 0; i < newExtractActionArr.size(); i++) {
            intentFilter.addAction(newExtractActionArr.get(i));
        }
        mContext.registerReceiver(collectReceiver, intentFilter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub

        if(intent != null){
            if(intent.getBooleanExtra("TopViewService",false)){
//                Log.i("OYSKAR",intent.getStringArrayListExtra("autoCreateList").toString());

                autoCreateList = intent.getStringArrayListExtra("autoCreateList");
//                Log.d("OYSKAR", "------------ bq list -------------");
//                Log.v("OYSKAR", bq.toString());
//                Log.d("OYSKAR", "------------ auto create list -------------");
//                for(int i=0;i<autoCreateList.size();i++){
//                    Log.i("OYSKAR", autoCreateList.get(i));
//                }
//                Log.d("OYSKAR", "------------ figure out  -------------");
                for(int i=0;i<autoCreateList.size();i++){
                    String[] split = autoCreateList.get(i).split("/");
                    ArrayList<String> list = allBroadcastMap.get(split[2]);

//                    if(list != null){
//                        Log.d("OYSKAR", split[2] + "," + list.toString());
//                    }

                    String br = bq.compareRecentBroadcast(list);
                    Util.saveLogToFile(autoCreateList.get(i)+"/"+ br + "\n", "/SSLAB/autocreate.txt");
//                    Log.d("OYSKAR", split[2] + ", reason " + br);
                }
            }
        }

        return START_STICKY;
    }

    /**
     * 설치된 어플리케이션의 패키지 이름을
     * installPackageName 리스트에 넣어주는 작업
     */
    private void getInstallApplicationPackageNameInDevice(){
        List<PackageInfo> packageInfos= getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA);
        installPackageName.clear();
        for(PackageInfo packageInfo : packageInfos) {
            installPackageName.add(packageInfo.packageName);
        }
    }
    /**
     * 설치된 어플리케이션의 Manifest.xml에 접근해서 broadcast의 action을 빼오는 함수
     * 동시에 프로세스 별 어떤 Action이 있는지도 기록하고 있음
     * @param appName
     */
    private void accessApplicationManifest(String appName){
        PackageManager pm = getPackageManager();
        StringBuffer stringBuffer = new StringBuffer();
        String string= null;
        XmlResourceParser xmlPullParser = null;

        ArrayList<String> actionList = new ArrayList<String>();

        try {
            Resources res = pm.getResourcesForApplication(appName);
            AssetManager am = res.getAssets();
            try {
                xmlPullParser= am.openXmlResourceParser("AndroidManifest.xml");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            boolean isReceiver = false;
            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if(xmlPullParser.getName().equals("receiver")){
                        isReceiver = true;
                    }
                    if(xmlPullParser.getName().equals("action") && isReceiver == true){
                        for(int i = 0; i<xmlPullParser.getAttributeCount();i++){
                            extractActionArr.add(xmlPullParser.getAttributeValue(i));
                            actionList.add(xmlPullParser.getAttributeValue(i));
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if(xmlPullParser.getName().equals("receiver")){
                        isReceiver = false;
                    }
                }
                eventType = xmlPullParser.next();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        allBroadcastMap.put(appName, actionList);
    }

    /**
     * 15-10-08
     * 등록된 브로드캐스트 리시버의 리스트를 Action으로 필터링하여 리스트를 불러오는 예제 소스
     * intent 안에 Action을 집어 넣어주면, 이 액션을 리시빙하는 브로드캐스트 리시버를 불러옴
     */
    private void showBroadcastReceiverListWithAction(String action){
        PackageManager packageManager = getPackageManager();

        Intent intent = new Intent(action);

        final List<ResolveInfo> list = packageManager.queryBroadcastReceivers(intent, 0);
        Log.d("LOTTE", "==========================================");
        for (int j = 0; j < list.size(); j++) {
            Log.d("LOTTE", list.get(j).loadLabel(packageManager).toString());
        }
    }
//    void loadTimer(){
//        try {
//            Log.d("TIMER","LOAD TIMER");
//            FileInputStream fileIn = new FileInputStream(Environment.getExternalStorageDirectory() + "/SSLAB/count.ser");
//            ObjectInputStream in = new ObjectInputStream(fileIn);
//            Object obj = in.readObject();
//            countTimer = (Long) obj;
//            in.close();
//            fileIn.close();
//        } catch (Exception e) {
//            try{
//                Log.d("TIMER","SET NEW TIMER");
//                countTimer = 86400000;
//                FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory()+"/SSLAB/countTime.ser");
//                ObjectOutputStream objOut = new ObjectOutputStream(fileout);
//                objOut.writeObject(countTimer);
//                objOut.close();
//                fileout.close();
//
//            }catch(Exception e2){
//                e2.printStackTrace();
//            }
//            e.printStackTrace();
//        }
//    }
//
//    void createTimer(){
//        mCountDown = new CountDownTimer(countTimer,60000) {
//            @Override
//            public void onTick(long millisUntilFinished) {
//                countTimer -= 60000;
//                if(countTimer ==432000000)
//                {
//                    Log.d("TIMER","START KILL");
//
//                    startService(new Intent(mContext,KillingAllProcessService.class));
//                }
//                Log.d("TIMER"," TIME : "+countTimer);
//                try{
//                    FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory()+"/SSLAB/countTime.ser");
//                    ObjectOutputStream objOut = new ObjectOutputStream(fileout);
//                    objOut.writeObject(countTimer);
//                    objOut.close();
//                    fileout.close();
//
//                }catch(Exception e2){
//                    Log.d("TIMER","IN COUNT START()");
//                    e2.printStackTrace();
//                }
//
//            }
//            @Override //86400000
//            public void onFinish() {
//                countTimer = 86400000;
//                startService(new Intent(mContext,AlarmService.class));
//                mCountDown.start();
//            }
//        };
//    }
//
//    void countStart(){
//       mCountDown.start();
//    }
}
