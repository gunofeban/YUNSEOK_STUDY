package sslab.lova.mem_monitor;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.os.StrictMode;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    static boolean countEndBool = false;
    TextView logText;
    private ListView m_ListView;
    private ListView m_psListView;
    private ListView m_thirdPartyView;
    private ListView m_executionView;
    private ListView m_restartView;


    private ListViewAdapter m_psAdapter = null;
    private ListViewAdapter m_svcAdapter = null;
    private ListViewAdapter m_downAdapter = null;
    private ListViewAdapter m_thirdPartyAdapter = null;
    private ListViewAdapter m_executionAdapter = null;
    private ListViewAdapter m_restartAdapter = null;

    private int totalMem=0;
    private int totalSvcMem = 0;
    private String svcString;
    private String deviceInfo;
    private int systemApp=0,tpApp=0,downloadApp=0 ;
    private int serviceApplicationMemory=0;
    private int processApplicationMemory=0;

    private String queryAppName;
    private String queryAppPid;

    private int SDK;
    private String ProductModel;
    private String buildVersion;
    private String kernelVersion;
    private long totalMemory;
    private long availableMegs;
    private String catName;
    private boolean checkQueryEnd = false;
    private boolean tpAppBool = false;
    private boolean downAppBool = false;
    private boolean psKillCheck=false;

    private String category = "";
    private String userSpace="";
    private String kernelSpace = "";

    private Map<Integer, String> cachedPidMap = new TreeMap<Integer, String>();
    private Map<Integer, String> svcPidMap = new TreeMap<Integer, String>();

    private ActivityManager activityManager;
    private ProgressDialog progressDialog;
    private ProgressDialog progressDialogQuery;
    boolean queryEnded = false;
    boolean pagerBool = false;

    List<String> ApplicationList = new ArrayList<String>();

    Map<String,AppInfoClass> befServiceXmlList = new TreeMap<String,AppInfoClass>();
    Map<String,AppInfoClass> befProcessXmlList = new TreeMap<String,AppInfoClass>();

    Map<String, AppInfoClass> refCachedMap = new TreeMap<String,AppInfoClass>();
    Map<String, AppInfoClass> refServiceMap = new TreeMap<String,AppInfoClass>();

    Map<String,Integer> aftServiceXmlList = new TreeMap<String,Integer>();
    Map<String,Integer> aftProcessXmlList = new TreeMap<String,Integer>();

    Map<String,Integer> selectedSvcList = new TreeMap<String,Integer>();
    Map<String,Integer> selectedProcList = new TreeMap<String,Integer>();

    String[] befServiceKeys;
    String[] befProcessKeys;

    String[] aftServiceKeys;
    String[] aftProcessKeys;

    String[] selectedSvcKeys;
    String[] selectedProcKeys;

    private int totalTime = 300;
    private int total_min;
    private int total_sec;

    private String changeProgressString = "";

    private Handler progressHandler;

    private CountDownTimer dialogTimer=null;
    private Context mContext;

    private TextView TotalTextView;


    List<String> tpAppList = new ArrayList<String>();

    List<String> downAppList = new ArrayList<String>();

    Map<String,String> downMapList = new HashMap<String,String>();
    Map<String,String> tpMapList = new HashMap<String,String>();

    Map<String,AppInfoClass> cachedCategoryMap = new HashMap<String,AppInfoClass>();
    Map<String,AppInfoClass> serviceCategoryMap = new HashMap<String,AppInfoClass>();

    Map<String,Integer> killMapList = new TreeMap<String,Integer>();
    String[] killMapKey;

    TextView tv_CachedListSummary;
    TextView tv_ServiceListSummary;
    TextView tv_ExecutionListSummary;

    /**
     * Manifest.xml에서 action 뽑아낼 때 사용하는 리스트들
     */
    private ArrayList<String> actionArr = new ArrayList<String>();
    private ArrayList<String> extractActionArr = new ArrayList<String>();
    private ArrayList<String> newExtractActionArr;
    private ArrayList<String> installPackageName = new ArrayList<String>();

    private Map<String,AppInfoClass> RTMap = new HashMap<String, AppInfoClass>();
    private String[] mapKey;

    Button mMailTestBtn;
    private List<ActivityManager.RunningAppProcessInfo> beforeKillingList,afterKillingList;

    //TODO : Slide를 위해 추가된 부분
    private ViewPager mPager;

    //TODO : QueryBOOL
    private boolean forStartQueryBool=false;

    //TODO: Query
    private Button checkQueryBtn;

    //TODO: Installed Application Check
    private boolean installedCheck = false;
    private int installedAppNum = 0;
    private View installedView;
    private boolean loadRTMapAndTimeMapBool=false;

    Button cachedRefreshBtn;
    Button serviceRefreshBtn;
    Button restartRefreshBtn;

    private boolean fiveBool=false,
            sixBool=false,
            sevBool=false,
            eigBool=false,
            ninBool=false,
            hunBool=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy  policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File file = new File(Environment.getExternalStorageDirectory()+"/SSLAB");
        if(!file.exists())
        {
            file.mkdirs();
        }

        mContext = this;

        //TODO : 슬라이더 추가부분
        mPager = (ViewPager)findViewById(R.id.pager);
        // TODO : 슬라이더에 표현될 갯수
        mPager.setOffscreenPageLimit(6);
        mPager.setAdapter(new PagerAdapterClass(getApplicationContext()));

        /**
         * 서비스 시작
         * TopViewService : 어플리케이션 실행시간 로깅 및 실행시킨 어플리케이션 로깅
         * CollectBroadcastMessageService : broadcast message 트레킹
         */

        serviceStart();

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        progressHandler = new Handler();
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle("잠시만 기다려 주세요");
        progressDialog.setMessage("메모리 측정중...");
        progressDialog.show();

        Button startBtn = (Button) findViewById(R.id.startBtn);
        Button sendBtn = (Button)findViewById(R.id.sendBtn);
        Button deleteAll = (Button)findViewById(R.id.deleteBtn);
        deleteAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeDir("SSLAB");
            }
        });
        mMailTestBtn = (Button)findViewById(R.id.mailtest);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final Handler showPrintHandler = new Handler();
        new Thread(){
            public void run(){
                try {
                        Thread.sleep(1000);
                    showPrintHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            runOnUiThread(printListRunnable);
                        }
                    }, 3000);
                }catch(Exception e)
                {

                }
            }
        }.start();
//        runOnUiThread(printListRunnable);
//        printList();

        //################## Market API QUERY ###################

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_psAdapter.getListData(selectedProcList);
                m_svcAdapter.getListData(selectedSvcList);
                selectedProcKeys = selectedProcList.keySet().toArray(new String[0]);
                selectedSvcKeys = selectedSvcList.keySet().toArray(new String[0]);
//                getExcelFile();
            }
        });


        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_psAdapter.getListData(selectedProcList);
                m_svcAdapter.getListData(selectedSvcList);
                selectedProcKeys = selectedProcList.keySet().toArray(new String[0]);
                selectedSvcKeys = selectedSvcList.keySet().toArray(new String[0]);

//                getExcelFile();

                GMailSender sender = new GMailSender("sslab.dev","sslab5760"); // SUBSTITUTE HERE
                try {
                    sender.sendMail(
                            "[메모리 상태]",   //subject.getText().toString(),
                            "",           //body.getText().toString(),
                            "sslab.dev@gmail.com",          //from.getText().toString(),
                            "sslab.dev@gmail.com",            //to.getText().toString()
                            "data.xls"
                    );
                } catch (Exception e) {
                    Log.e("SendMail", e.getMessage(), e);
                }
            }
        });
    }

    //TODO : 슬라이더 구현을 위한 페이지
    private class PagerAdapterClass extends PagerAdapter {

        private LayoutInflater mInflater;

        public PagerAdapterClass(Context c){
            super();
            Log.d("LOTTE", "hear??");
            mInflater = LayoutInflater.from(c);
        }

        @Override
        public int getCount() {
            return 5;
        }

        @Override
        public Object instantiateItem(View pager, int position) {
            View v = null;
            if(position==0){
                v = mInflater.inflate(R.layout.activity_total, null);
                TotalTextView = (TextView)v.findViewById(R.id.total_deviceInfo);
            }
            else if(position==1){
                v = mInflater.inflate(R.layout.activity_cached, null);
                v.findViewById(R.id.process_text);
                tv_CachedListSummary = (TextView)v.findViewById(R.id.process_summary);
                cachedRefreshBtn = (Button)v.findViewById(R.id.cachedRefresh);
                cachedRefreshBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Thread(){
                            public void run(){
                                runOnUiThread(showProgressBar);
                            }
                        }.start();
                        new Thread(){
                            public void run(){
                                try{
                                    sleep(1000);
                                    runOnUiThread(printListRunnable);
                                }catch(Exception e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
                m_psListView =(ListView) v.findViewById(R.id.process_list);
                m_psAdapter = new ListViewAdapter(v.getContext());
                m_psListView.setAdapter(m_psAdapter);
            }else if(position==2){
                v = mInflater.inflate(R.layout.activity_service, null);
                v.findViewById(R.id.service_text);
                tv_ServiceListSummary = (TextView)v.findViewById(R.id.service_summary);
                serviceRefreshBtn = (Button)v.findViewById(R.id.serviceRefresh);
                serviceRefreshBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Thread(){
                            public void run(){
                                runOnUiThread(showProgressBar);
                            }
                        }.start();
                        new Thread(){
                            public void run(){
                                try{
                                    sleep(1000);
                                    runOnUiThread(printListRunnable);
                                }catch(Exception e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });
                m_ListView = (ListView)v.findViewById(R.id.service_list);
                m_svcAdapter = new ListViewAdapter(v.getContext());
                m_ListView.setAdapter(m_svcAdapter);
            }
            else if(position==3){
                v=mInflater.inflate(R.layout.activity_reborn,null);
                v.findViewById(R.id.restart_text);
                m_restartView = (ListView)v.findViewById(R.id.restart_list);
                m_restartAdapter = new ListViewAdapter(v.getContext());
                m_restartView.setAdapter(m_restartAdapter);
                restartRefreshBtn = (Button)v.findViewById(R.id.restartRefresh);
                restartRefreshBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {new Thread(){
                        public void run(){
                            runOnUiThread(showProgressBar);
                        }
                    }.start();
                        new Thread(){
                            public void run(){
                                try{
                                    sleep(1000);
                                    runOnUiThread(printListRunnable);
                                }catch(Exception e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                    }
                });

            }else{   v=mInflater.inflate(R.layout.activity_execution,null);
                v.findViewById(R.id.execution_text);
                m_executionView = (ListView)v.findViewById(R.id.execution_list);
                m_executionAdapter = new ListViewAdapter(v.getContext());
                m_executionView.setAdapter(m_executionAdapter);
                tv_ExecutionListSummary = (TextView)v.findViewById(R.id.execution_summary);
            }

            ((ViewPager)pager).addView(v,0);

            return v;
        }

        @Override
        public void destroyItem(View pager, int position, Object view) {
            ((ViewPager)pager).removeView((View)view);
        }

        @Override
        public boolean isViewFromObject(View pager, Object obj) {
            return pager == obj;
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {}
        @Override
        public Parcelable saveState() { return null; }
        @Override
        public void startUpdate(ViewGroup arg0){

        }
        @Override
        public void finishUpdate(ViewGroup arg0) {}
    }



    private void loadRTMapAndTimeMap(){
        try {
            FileInputStream fileIn = new FileInputStream(Environment.getExternalStorageDirectory() + "/SSLAB/RTMap.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            RTMap = (Map<String,AppInfoClass>) in.readObject();
            in.close();
            fileIn.close();
            loadRTMapAndTimeMapBool = true;

        } catch (Exception e) {
            loadRTMapAndTimeMapBool = false;

        }
    }

    private void serviceStart(){
        /**
         * TopViewService 실행
         */
        Intent intent = new Intent(mContext,TopViewService.class);
        startService(intent);

        /**
         * CollectBroadcastMessageService 실행
         */
        Intent intent2 = new Intent(mContext,CollectBroadcastMessageService.class);
        startService(intent2);

//        Intent intent3 = new Intent(mContext, AlarmService.class);
//        startService(intent3);
    }



    protected void onResume(){

        super.onResume();
    }

    private class ListViewAdapter extends BaseAdapter {
        private Context m_Context;
        private ArrayList<ListData> mListData = new ArrayList<ListData>();
        final private ArrayList<Integer> checkList = new ArrayList<Integer>();
        public ListViewAdapter(Context mContext) {
            super();
            this.m_Context= mContext;
        }

        public int getCount() {
            return mListData.size();
        }

        public Object getItem(int position) {
            return mListData.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final int checkPosition = position;
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater) m_Context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.list_layout,null);
                holder.mText = (TextView) convertView.findViewById(R.id.text);
                holder.mIcon = (ImageView) convertView.findViewById(R.id.svcImg);

                convertView.setTag(holder);
                // textView.setText(m_List.get(position));
                // imageView.setImageDrawable(m_iconList.get(position));
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            ListData mData = mListData.get(position);
            if (mData.Icon != null) {
                holder.mIcon.setVisibility(View.VISIBLE);
                holder.mIcon.setImageDrawable(mData.Icon);

            } else {
            }

            holder.mIcon.setImageDrawable(mData.Icon);
            holder.mText.setText(mData.mTitle);

            return convertView;
        }
        public void addItemWithCategory(String text1,String text2){
            ListData addInfo =null;
            addInfo = new ListData();
            addInfo.mTitle ="NAME : "+text1+"\n"+"CATEGORY : "+text2;

            mListData.add(addInfo);
        }
        public void addItemWithReborn(String text)
        {
            ListData addInfo=null;
            addInfo = new ListData();
            addInfo.mTitle = text;
            mListData.add(addInfo);
        }
        public void addItemWithExecution(String text,Drawable icon)
        {
            ListData addInfo = null;
            addInfo = new ListData();
            addInfo.mTitle = text;
            addInfo.Icon = icon;

            mListData.add(addInfo);
        }

        public void addItem(String text, Drawable mIcon, int mem, String label) {
            ListData addInfo=null;
            addInfo = new ListData();
            addInfo.mem = mem;
            addInfo.Icon = mIcon;
            addInfo.mTitle = text;
            addInfo.label = label;
            mListData.add(addInfo);
        }

        private class ViewHolder{
            public ImageView mIcon;
            public TextView mText;
        }
        void getListData(Map<String,Integer> inputMap){
            for(int i = 0 ; i<mListData.size(); i++)
            {
                inputMap.put(mListData.get(i).label,mListData.get(i).mem);

            }
        }
        public void clearAll(){
            mListData.clear();
        }


    }
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void initList(){
        cachedPidMap = new TreeMap<Integer, String>();
        svcPidMap = new TreeMap<Integer, String>();
        m_psAdapter.clearAll();
        m_svcAdapter.clearAll();
        m_executionAdapter.clearAll();
        m_restartAdapter.clearAll();

        totalMem = 0;
        totalSvcMem = 0;

    }


    public synchronized void printList(){
        initList();

        SDK = Build.VERSION.SDK_INT;
        ProductModel = Build.BRAND + " / " + Build.MODEL;
        buildVersion = Build.VERSION.RELEASE;
        kernelVersion = System.getProperty("os.version");
        final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        availableMegs=memoryInfo.availMem/1048576L;
        totalMemory =memoryInfo.totalMem/1048576L;

        int cachedProcessCount = 0;
        int serviceCount = 0;
        int executionCount =0;
        int executionMem = 0;
        loadRTMapAndTimeMap();
        final List<ActivityManager.RunningAppProcessInfo> pss = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : pss) {
            String str, str1 = "";
            try {
                FileInputStream fis = new FileInputStream("/proc/" + runningAppProcessInfo.pid + "/oom_adj");
                BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
                while ((str = bufferedReader.readLine()) != null) {
                    str1 += str;
                }
                double oom_adj = Double.parseDouble(str1);
                if (SDK >= 20) {
                    if (oom_adj >= 8) {
                        cachedPidMap.put(runningAppProcessInfo.pid, runningAppProcessInfo.pkgList[0]);
                        cachedProcessCount++;
                    }
                    if (oom_adj == 4 || oom_adj == 7) {
                        svcPidMap.put(runningAppProcessInfo.pid, runningAppProcessInfo.pkgList[0]);
                        serviceCount++;
                    }
                } else {
                    if (oom_adj >= 9) {
                        cachedPidMap.put(runningAppProcessInfo.pid, runningAppProcessInfo.pkgList[0]);
                        cachedProcessCount++;
                    }
                    if (oom_adj == 5 || oom_adj == 8) {
                        svcPidMap.put(runningAppProcessInfo.pid, runningAppProcessInfo.pkgList[0]);
                        serviceCount++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //
        // 서비스 프로세스 검사

        final Collection<Integer> psKeys = cachedPidMap.keySet();
        final Collection<Integer> svcKeys = svcPidMap.keySet();
        Drawable icon;

        String psString="";
        for (int psKey : psKeys) {
            int psPids[] = new int[1];
            psPids[0] = psKey;
            android.os.Debug.MemoryInfo[] psmemoryinfoArray = activityManager.getProcessMemoryInfo(psPids);
            for (android.os.Debug.MemoryInfo pidMemoryInfo : psmemoryinfoArray) {
                String psLabel="";
                String str="";
                double oom_adj;
                try {
                    FileInputStream fis = new FileInputStream("/proc/" + psPids[0] + "/oom_adj");
                    BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
                    str= bufferedReader.readLine();
                    try {
                        icon = getPackageManager().getApplicationIcon(cachedPidMap.get(psPids[0]));
                    }
                    catch(Exception e)
                    {
                        icon = getResources().getDrawable(R.mipmap.ic_launcher);
                    }
                    psLabel = (String)getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cachedPidMap.get(psPids[0]), PackageManager.GET_UNINSTALLED_PACKAGES));
                    oom_adj = Double.parseDouble(str);

                    psString = "";
                    psString += "PID : " + Integer.toString(psPids[0]) + "\n";
                    psString += "NAME : " + psLabel + "\n";
                    psString += "Memory : " + Integer.toString(pidMemoryInfo.getTotalPss()) + "KB";

                    aftProcessXmlList.put(psLabel,pidMemoryInfo.getTotalPss());
                    totalMem += pidMemoryInfo.getTotalPss();
                    if(cachedCategoryMap.containsKey(String.valueOf(psPids[0])))
                    {
                        AppInfoClass mTempClass = cachedCategoryMap.get(String.valueOf(psPids[0]));
                        int mTempPrevMemory = mTempClass.cur_memory;
                        mTempClass.prev_memory = mTempPrevMemory;
                        mTempClass.cur_memory = pidMemoryInfo.getTotalPss();
                        psString = mTempClass.toString();

                    }else {
                        cachedCategoryMap.put(String.valueOf(psPids[0]), new AppInfoClass(icon, psPids[0], psLabel, 0,pidMemoryInfo.getTotalPss(), 0,oom_adj));
                        psString="";
                        psString += "PID : " + Integer.toString(psPids[0]) + "\n";
                        psString += "NAME : " + psLabel + "\n";
                        psString += "Memory : " + Integer.toString(pidMemoryInfo.getTotalPss()) + "KB";
                    }
                    m_psAdapter.addItem(psString,icon,pidMemoryInfo.getTotalPss(),psLabel);
                    m_psAdapter.notifyDataSetChanged();

                } catch (Exception e) {
                    e.printStackTrace();

                }
            }
        }
        Log.d("LOTTE", "SVCKeys " + svcKeys.size());
        for (int svcKey : svcKeys) {
            int svcPids[] = new int[1];
            svcPids[0] = svcKey;
            android.os.Debug.MemoryInfo[] memoryinfoArray = activityManager.getProcessMemoryInfo(svcPids);
            for (android.os.Debug.MemoryInfo pidMemoryInfo : memoryinfoArray) {
                String appLabel="";
                String str="";
                double oom_adj;
                try {
                    FileInputStream fis = new FileInputStream("/proc/" + svcPids[0] + "/oom_adj");
                    BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
                    str= bufferedReader.readLine();
                    try {
                        icon = getPackageManager().getApplicationIcon(svcPidMap.get(svcPids[0]));
                    }catch(Exception e){
                        icon = getResources().getDrawable(R.mipmap.ic_launcher);
                    }
                    appLabel = (String)getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(svcPidMap.get(svcPids[0]), PackageManager.GET_UNINSTALLED_PACKAGES));

                    oom_adj = Double.parseDouble(str);

                    if(serviceCategoryMap.containsKey(String.valueOf(svcPids[0])))
                    {
                        AppInfoClass mTempClass = serviceCategoryMap.get(String.valueOf(svcPids[0]));
                        int mTempPrevMemory = mTempClass.cur_memory;
                        mTempClass.prev_memory = mTempPrevMemory;
                        mTempClass.cur_memory = pidMemoryInfo.getTotalPss();
                        svcString = mTempClass.toString();

                    }else {
                        serviceCategoryMap.put(String.valueOf(svcPids[0]), new AppInfoClass(icon, svcPids[0], appLabel,0 ,pidMemoryInfo.getTotalPss(),0,oom_adj));
                        svcString = "";
                        svcString += "PID : " + Integer.toString(svcPids[0]) + "\n";
                        svcString += "NAME : " + appLabel + "\n";
                        svcString += "Memory : " + Integer.toString(pidMemoryInfo.getTotalPss()) + "KB";
                    }
                    m_svcAdapter.addItem(svcString, icon, pidMemoryInfo.getTotalPss(), appLabel);
                    totalSvcMem += pidMemoryInfo.getTotalPss();
                    m_svcAdapter.notifyDataSetChanged();
                } catch (Exception e) {
                    e.printStackTrace();

                }
            }
        } // add to service list
        aftServiceKeys = aftServiceXmlList.keySet().toArray(new String[0]);

        if(loadRTMapAndTimeMapBool) {
            mapKey = RTMap.keySet().toArray(new String[0]);
            Calendar c = Calendar.getInstance();
            for (int i = 0; i < RTMap.size(); i++) {
                AppInfoClass RTmapInfo = RTMap.get(mapKey[i]);
                Log.d("LOTTE","NAME : " + mapKey[i] + ", 총 실행시간 : " + RTmapInfo.totalTime);
                String listText = "NAME : " + mapKey[i] + "\n총 실행시간 : " + Util.dateToStringHMS(RTmapInfo.totalTime)+ "\n최근 접속시간 :" + Util.dateToStringYMDHMS(RTmapInfo.lastTime)+"\n Memory : "+RTmapInfo.cur_memory+" KB";
                executionMem +=RTmapInfo.cur_memory;
                executionCount++;
                Drawable mIcon;
                try {
                    mIcon = getPackageManager().getApplicationIcon(mapKey[i]);
                } catch (Exception e) {
                    mIcon = getResources().getDrawable(R.mipmap.ic_launcher);
                }
                m_executionAdapter.addItemWithExecution(listText,mIcon);
            }
            m_executionAdapter.notifyDataSetChanged();
        }

        String bootTime = readFile();

        tv_CachedListSummary.setText("갯수: "+ String.valueOf(cachedProcessCount)+", 총 메모리: "+ String.valueOf(totalMem)+"KB");
        tv_ServiceListSummary.setText("갯수: "+ String.valueOf(serviceCount)+", 총 메모리: "+ String.valueOf(totalSvcMem)+"KB");
        tv_ExecutionListSummary.setText("갯수 : " + String.valueOf(executionCount)+ ",총 메모리 : "+ String.valueOf(executionMem)+"KB");
        TotalTextView.setText("MODEL : " + ProductModel + "\n" + "Build Version : " + buildVersion + "\n" + "Kernel Version : " + kernelVersion + "\n" +
                "Total Memory : " + totalMemory + " MB\n Available Memory : " + availableMegs + " MB\n" + "Used Memory : " + (totalMemory - availableMegs) + "MB\n"+"Booting Time : "+bootTime);



        try {
            BufferedReader in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/recreatetime.txt")));
            String s;
            while ((s = in.readLine()) != null) {
                String[] data = s.split("/");
                String perString = data[2].replace("%","");
                int perLong = Integer.parseInt(perString)/10;
                if(Integer.parseInt(data[2].replace("%",""))<50){
                    fiveBool=false;
                    sixBool=false;
                    sevBool=false;
                    eigBool=false;
                    ninBool=false;
                    eigBool=false;
                    hunBool=false;
                }
                switch(perLong)
                {
                    case 5:{
                        if(!fiveBool)
                        {
                            m_restartAdapter.addItemWithReborn("50%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            fiveBool =true;
                        }
                        break;
                    }
                    case 6:{
                        if(!sixBool){
                            m_restartAdapter.addItemWithReborn("60%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            sixBool=true;
                        }
                        break;
                    }
                    case 7:{
                        if(!sevBool){
                            m_restartAdapter.addItemWithReborn("70%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            sevBool = true;
                        }
                        break;
                    }
                    case 8:{
                        if(!eigBool){
                            m_restartAdapter.addItemWithReborn("80%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            eigBool=true;
                        }
                        break;
                    }
                    case 9:{
                        if(!ninBool){
                            m_restartAdapter.addItemWithReborn("90%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            ninBool=true;
                        }
                        break;
                    }
                    case 10:{
                        if(!hunBool){
                            m_restartAdapter.addItemWithReborn("100%"+"\n"+"Start Time : "+data[0]+"\n"+"Re-Start Time : "+data[1]);
                            hunBool=true;
                        }
                        break;
                    }
                }
                m_restartAdapter.notifyDataSetChanged();
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        runOnUiThread(disableDialog);
    }

    public String readFile(){
        BufferedReader in;
        String s;
        try {
            in= new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/bootcompleteTime.txt")));
            s= in.readLine();
            in.close();

            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return "N/A";
        }
    }



    private Runnable printListRunnable = new Runnable() {
        @Override
        public void run() {
            printList();
        }
    };
    private Runnable showProgressBar = new Runnable() {
        @Override
        public void run() {
            progressDialog.setMessage("Refreshing....");
            progressDialog.show();

        }
    };


    private Runnable disableDialog = new Runnable() {
        @Override
        public void run() {
            progressDialog.dismiss();
        }
    };

//파일 & 폴더 삭제

    public static void removeDir(String dirName) {

        String mRootPath = Environment.getExternalStorageDirectory() + File.separator + dirName;
        File file = new File(mRootPath);
        File[] childFileList = file.listFiles();
        for(File childFile : childFileList)
        {
            if(childFile.isDirectory()) {
                removeDir(childFile.getAbsolutePath());    //하위 디렉토리
            }
            else {
                childFile.delete();    //하위 파일
            }
        }
        file.delete();    //root 삭제
    }
}