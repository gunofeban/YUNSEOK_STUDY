package sslab.lova.mem_monitor;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jxl.Workbook;
import jxl.format.Colour;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

/**
 * Created by JEFF on 2015-11-20.
 */
public class CalBootingTimeService extends Service {

    ActivityManager activityManager;
    private Context mContext;
    Boolean firstStart = true;
    List<ActivityManager.RunningAppProcessInfo> curList, prevList, mRunningAppProcessInfo;

    PowerManager pm;
    PowerManager.WakeLock wakeLock;

    long startTime=0;
    long endTime=0;
    int pollingCount = 0;

    private long totalMemory;
    private long availableMemory;
    private String userSpace = "";
    private String kernelSpace = "";

    private int SDK;
    private String ProductModel;
    private String buildVersion;
    private String deviceId;
    private String kernelVersion;


    Map<String, AppInfoClass> befServiceXmlList = new TreeMap<String, AppInfoClass>();
    Map<String, AppInfoClass> befProcessXmlList = new TreeMap<String, AppInfoClass>();

    String[] befServiceKeys;
    String[] befProcessKeys;

    List<AppInfoClass> tpAppList = new ArrayList<AppInfoClass>();
    List<AppInfoClass> downAppList = new ArrayList<AppInfoClass>();
    List<AppInfoClass> systemAppList = new ArrayList<AppInfoClass>();

    Map<String, Integer> killMapList = new TreeMap<String, Integer>();

    String bootTime;

    AppInfoClass installApp;

    /**
     * 엑셀에 데이터 저장하기 위해 사용되는 리스트
     */
    List<AppInfoClass> foreGroundList;
    List<AppInfoClass> visibleList;
    List<AppInfoClass> perceptibleList;
    List<AppInfoClass> HomeList;
    List<AppInfoClass> AServiceList;
    List<AppInfoClass> BServiceList;
    List<AppInfoClass> previousList;

    int totalNumOfStoringToMemory=0;
    long totalMemorySizeOfStoreingToMemory=0;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        pm = (PowerManager) getSystemService( Context.POWER_SERVICE );
        wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, "MY TAG" );
        wakeLock.acquire();

        Log.d("LOTTE", "CalBootingTimeSerivce 시작");
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        mContext = this;
        startTime = System.currentTimeMillis();

        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    pollingProcessList();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

        return super.onStartCommand(intent, flags, startId);
    }

    public void pollingProcessList() {
//        pm = (PowerManager) getSystemService( Context.POWER_SERVICE );
//        PowerManager.WakeLock wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, "MY TAG" );
//        wakeLock.acquire();
        while (true) {
            if (firstStart) {
                curList = activityManager.getRunningAppProcesses();
                prevList = activityManager.getRunningAppProcesses();

                firstStart = false;
            } else {
                prevList.clear();
                for (ActivityManager.RunningAppProcessInfo br : curList) {
                    prevList.add(br);
                }

                curList = activityManager.getRunningAppProcesses();
                mRunningAppProcessInfo = activityManager.getRunningAppProcesses();

                for (ActivityManager.RunningAppProcessInfo br : prevList) {
                    for (ActivityManager.RunningAppProcessInfo ar : mRunningAppProcessInfo) {
                        if (br.pid == ar.pid) {
                            mRunningAppProcessInfo.remove(ar);
                            break;
                        }
                    }
                }

                if (mRunningAppProcessInfo.size() != 0) {
                    pollingCount = 0;
                }else{
                    pollingCount++;
                }
            }

            if(pollingCount == 60){
                break;
            }
            Log.d("LOTTE", String.valueOf(pollingCount));
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        endTime = System.currentTimeMillis();
        Log.d("LOTTE", "부팅 완료 " + Util.dateToStringHMS(endTime - startTime));
        Util.saveLogToFileOverride(Util.dateToStringHMS(endTime-startTime) + "\n", "/SSLAB/bootcompleteTime.txt");

        Intent intent = new Intent(mContext,KillingAllProcessService.class);
        intent.putExtra("BootTime",Util.dateToStringHMS(endTime - startTime));

        bootTime = Util.dateToStringHMS(endTime - startTime);
        //여기에 부팅 시작했던 시간 넣었으면 좋겠고

        bootingOperation();

        startService(intent);
        wakeLock.release();
        stopSelf();
    }

    public void bootingOperation(){
        extractInformation();
         Log.e("LOTTE","부팅 시 메모리 정보 보낸다");
        getExcelFile();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Util.sendMail("BootingData.xls",deviceId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        Util.removeDir("SSLAB"); // 부팅되고 나서부터 데이터를 수집하는 것이므로, 부팅이 되면 기존 데이터는 모두 삭제하고 다시 시작.
        /**
         * 시스템앱 - UID<10000, UID>10000, 다운로드 앱을 파일로 저장
         */
        try{
            Util.saveObject(tpAppList, "tpAppList.ser");
            Util.saveObject(systemAppList, "systemAppList.ser");
            Util.saveObject(downAppList, "downAppList.ser");
        } catch (Exception e){
            Log.d("LOTTE","설치된 앱 리스트 저장 에러");
        }


        thread.start();
    }

    public void extractInformation(){
        final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

        activityManager.getMemoryInfo(memoryInfo);
        availableMemory = memoryInfo.availMem / 1048576L;
        totalMemory = memoryInfo.totalMem / 1048576L;


        try {
            String str = "";
            FileInputStream fis = new FileInputStream("/proc/meminfo");
            BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
            while ((str = bufferedReader.readLine()) != null) {
                if (str.indexOf("HighTotal") != -1) {
                    userSpace = str;
                }
                if (str.indexOf("LowTotal") != -1) {
                    kernelSpace = str;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<ApplicationInfo> mAppList = getPackageManager().getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES
                | PackageManager.GET_DISABLED_COMPONENTS);
        for(ApplicationInfo appInfo : mAppList){
            try{
                installApp = new AppInfoClass(appInfo.packageName, (String) getPackageManager().getApplicationLabel(appInfo),appInfo.uid);

                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    downAppList.add(installApp);
                }else if ((appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    downAppList.add(installApp);
                }else {
                    if (appInfo.uid < 10000) {
                        systemAppList.add(installApp);
                    } else {
                        tpAppList.add(installApp);
                    }
                }
            }catch(Exception e){

            }
        }

        SDK = Build.VERSION.SDK_INT;
        ProductModel = Build.BRAND + " / " + Build.MODEL;
        buildVersion = Build.VERSION.RELEASE;
        kernelVersion = System.getProperty("os.version");
        deviceId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);

        final List<ActivityManager.RunningAppProcessInfo> ps = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : ps) {
            if (runningAppProcessInfo.pid != android.os.Process.myPid()) {
                String str = "";
                try {
                    FileInputStream fis = new FileInputStream("/proc/" + runningAppProcessInfo.pid + "/oom_adj");
                    BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(fis)));
                    str = bufferedReader.readLine();
                    double oom_adj = Double.parseDouble(str);
                    int meminfo[] = {runningAppProcessInfo.pid, 0};
                    Debug.MemoryInfo debugMeminfo[] = activityManager.getProcessMemoryInfo(meminfo);
                    for (android.os.Debug.MemoryInfo pidMemoryInfo : debugMeminfo) {
                        if (pidMemoryInfo.getTotalPss() != 0) {
                            Drawable mIcon;
                            String psLabel;
                            if (SDK >= 20) {
                                if (oom_adj >= 0 && oom_adj < 8) {
                                    try {
                                        psLabel = (String) getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(runningAppProcessInfo.pkgList[0], PackageManager.GET_UNINSTALLED_PACKAGES));
                                        mIcon = getPackageManager().getApplicationIcon(runningAppProcessInfo.pkgList[0]);
                                    } catch (Exception e) {
                                        psLabel = "NameNotFound";
                                        mIcon = getResources().getDrawable(R.mipmap.ic_launcher);
                                    }
                                    AppInfoClass mAppInfoClass = new AppInfoClass(mIcon, runningAppProcessInfo.pid, psLabel, 0, pidMemoryInfo.getTotalPss(), runningAppProcessInfo.importance, oom_adj);
                                    befServiceXmlList.put(Integer.toString(runningAppProcessInfo.pid), mAppInfoClass);

                                } else if (oom_adj >= 8) {
                                    try {
                                        psLabel = (String) getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(runningAppProcessInfo.pkgList[0], PackageManager.GET_UNINSTALLED_PACKAGES));
                                        mIcon = getPackageManager().getApplicationIcon(runningAppProcessInfo.pkgList[0]);
                                    } catch (Exception e) {
                                        psLabel = "NameNotFound";
                                        mIcon = getResources().getDrawable(R.mipmap.ic_launcher);
                                    }
                                    AppInfoClass mAppInfoClass = new AppInfoClass(mIcon, runningAppProcessInfo.pid, psLabel, 0, pidMemoryInfo.getTotalPss(), runningAppProcessInfo.importance, oom_adj);
                                    befProcessXmlList.put(Integer.toString(runningAppProcessInfo.pid), mAppInfoClass);
                                }
                                killMapList.put(runningAppProcessInfo.processName, runningAppProcessInfo.pid);
                                /**
                                 * SDK 20 이하에서의 서비스, 프로세스분류
                                 */
                            } else {
                                if (oom_adj >= 0 && oom_adj < 9) {
                                    try {
                                        psLabel = (String) getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(runningAppProcessInfo.pkgList[0], PackageManager.GET_UNINSTALLED_PACKAGES));
                                        mIcon = getPackageManager().getApplicationIcon(runningAppProcessInfo.pkgList[0]);
                                    } catch (Exception e) {
                                        psLabel = "NameNotFound";
                                        mIcon = getResources().getDrawable(R.mipmap.ic_launcher);
                                    }
                                    AppInfoClass mAppInfoClass = new AppInfoClass(mIcon, runningAppProcessInfo.pid, psLabel, 0, pidMemoryInfo.getTotalPss(), runningAppProcessInfo.importance, oom_adj);
                                    befServiceXmlList.put(Integer.toString(runningAppProcessInfo.pid), mAppInfoClass);
                                }
                                if (oom_adj >= 9) {
                                    try {
                                        psLabel = (String) getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(runningAppProcessInfo.pkgList[0], PackageManager.GET_UNINSTALLED_PACKAGES));
                                        mIcon = getPackageManager().getApplicationIcon(runningAppProcessInfo.pkgList[0]);
                                    } catch (Exception e) {
                                        psLabel = "NameNotFound";
                                        mIcon = getResources().getDrawable(R.mipmap.ic_launcher);
                                    }
                                    AppInfoClass mAppInfoClass = new AppInfoClass(mIcon, runningAppProcessInfo.pid, psLabel, 0, pidMemoryInfo.getTotalPss(), runningAppProcessInfo.importance, oom_adj);
                                    befProcessXmlList.put(Integer.toString(runningAppProcessInfo.pid), mAppInfoClass);
                                }
                                killMapList.put(runningAppProcessInfo.processName, runningAppProcessInfo.pid);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } /// process  (Background process)

        befProcessKeys = befProcessXmlList.keySet().toArray(new String[0]);
        befServiceKeys = befServiceXmlList.keySet().toArray(new String[0]);
    }

    void getExcelFile() {
        try {
            int row = 0;

            File xmlFile = new File(Environment.getExternalStorageDirectory() + "/SSLAB/BootingData.xls");
            xmlFile.getParentFile().mkdirs();
            WritableWorkbook workbook = Workbook.createWorkbook(new File(Environment.getExternalStorageDirectory() + "/BootingData.xls"));

            WritableSheet sheet1 = workbook.createSheet("디바이스 메모리 정보", 0);

            jxl.write.WritableCellFormat format = new WritableCellFormat();
            jxl.write.WritableCellFormat classifyFormat = new WritableCellFormat();

            classifyFormat.setBackground(Colour.BLUE_GREY);

            jxl.write.Label label;

            label = new jxl.write.Label(0, row, "안드로이드 버전", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, buildVersion, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "커널 버전", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, kernelVersion, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "디바이스 이름", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, ProductModel, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "디바이스 전체 메모리", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, totalMemory + "MB", format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "커널 영역", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, kernelSpace, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "유저 영역", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, userSpace, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "사용가능한 메모리", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, availableMemory + "MB", format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "디바이스 ID", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, deviceId, format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "부팅시간", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(1, row, bootTime , format);
            sheet1.addCell(label);
            row++;

            row = 0;
            //여기서부터 총합 정보를 넣을 것임
            label = new jxl.write.Label(2, row, "시스탬 앱(UID<10000)개수", classifyFormat);
            sheet1.addCell(label);
            row++;
            label = new jxl.write.Label(2, row, "시스탬 앱(UID>10000)개수", classifyFormat);
            sheet1.addCell(label);
            row++;
            label = new jxl.write.Label(2, row, "다운로드 앱 개수", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "총 설치된 앱 개수", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "부팅 시 메모리에 저장된 앱 요약", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Foreground 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Foreground 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Visible 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Visible 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Perceptible 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Perceptible 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Home 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Home 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Previous 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Previous 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "A service 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "A service 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "B service 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "B service 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "Cached 개수", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "Cached 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(4, row, "A+B 서비스 앱 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(2, row, "메모리에 저장된 앱의 총 개수 ", classifyFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(4, row, "메모리에 저장된 앱의 총 메모리 사용량", classifyFormat);
            sheet1.addCell(label);
            row++;

            row++;

            // 빌트인 설치된 어플리케이션 목록
            label = new jxl.write.Label(0, row, "시스템 앱 ( UID < 10000 )", classifyFormat);
            sheet1.addCell(label);
            row++;
            row = mAppListToExcel(systemAppList, sheet1, format, row);
            insertSummaryInfoToExcel(String.valueOf(systemAppList.size()),sheet1, format, 0,3); //시스템 앱 총 개수를 요약정보에 삽입

            label = new jxl.write.Label(0, row, "시스템 앱 ( UID > 10000 )", classifyFormat);
            sheet1.addCell(label);
            row++;
            row = mAppListToExcel(tpAppList, sheet1, format, row);
            insertSummaryInfoToExcel(String.valueOf(tpAppList.size()),sheet1, format, 1,3); //시스템 앱 총 개수를 요약정보에 삽입

            label = new jxl.write.Label(0, row, "다운로드 앱", classifyFormat);
            sheet1.addCell(label);
            row++;
            row = mAppListToExcel(downAppList, sheet1, format, row);
            insertSummaryInfoToExcel(String.valueOf(downAppList.size()),sheet1, format, 2,3); //다운로드 앱 총 개수를 요약정보에 삽입

            insertSummaryInfoToExcel(String.valueOf(tpAppList.size()+downAppList.size()+systemAppList.size()),sheet1, format, 3,3); //설치된 총 앱 개수를 요약정보에 삽입

            label = new jxl.write.Label(0, row, "서비스 목록", classifyFormat);
            sheet1.addCell(label);
            row++;
            row = mServProcListToXml(befServiceXmlList, sheet1, format, row, befServiceKeys);


            label = new jxl.write.Label(0, row, "캐시드 목록", classifyFormat);
            sheet1.addCell(label);
            row++;
            row = mCachedListToXml(befProcessXmlList, sheet1, format, row);

            // 빌트인 목록 추가
            workbook.write();
            workbook.close();



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 설치된 앱 갯수, 메모리에 올라와있는 앱 개수, 메모리 사이즈 등의 총합을 파일의 바로 앞에서 바로 볼 수 있도록 하기 위해서
     * 총합 정보를 엑셀에 상단에 넣어주는 코드
     */
    void insertSummaryInfoToExcel(String value, WritableSheet sheet, WritableCellFormat cell, int row, int colum){
        jxl.write.Label label;
        try{
            label = new jxl.write.Label(colum, row, value, cell);
            sheet.addCell(label);
        }catch(Exception e){

        }
    }
    int mAppListToExcel(List<AppInfoClass> appList, WritableSheet sheet, WritableCellFormat cell, int row) {
        jxl.write.Label label = null;
        try {
            label = new jxl.write.Label(0, row, "패키지 라벨", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, "패키지 이름", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(2, row, "UID", cell);
            sheet.addCell(label);
            row++;
            for (int i = 0; i < appList.size(); i++) {
                AppInfoClass temp = appList.get(i);
                label = new jxl.write.Label(0, row, temp.getLabel(), cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, temp.getName(), cell);
                sheet.addCell(label);
                label = new jxl.write.Label(2, row, String.valueOf(temp.getUid()), cell);
                sheet.addCell(label);
                row++;
            }

            label = new jxl.write.Label(0, row, "총합", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, Integer.toString(appList.size()) + "개", cell);
            sheet.addCell(label);
            row++;
        } catch (Exception e) {

        }
        row++;
        return row++;
    }

    int mCachedListToXml(Map<String, AppInfoClass> procList, WritableSheet sheet, WritableCellFormat cell, int row) {
        jxl.write.Label label = null;
        int resultMemory = 0;
        try {
            label = new jxl.write.Label(0, row, "이름", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, "사이즈", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(2, row, "Importance", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(3, row, "OOM_ADJ", cell);
            sheet.addCell(label);
            row++;
            String[] keySets = procList.keySet().toArray(new String[0]);
            for (int i = 0; i < keySets.length; i++) {
                String name = keySets[i];
                AppInfoClass aif = procList.get(name);
                int memSize = aif.getMemory();

                label = new jxl.write.Label(0, row, aif.getName(), cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Util.convertKbToMb(memSize)+"MB", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(2, row, Util.importanceToString(aif.importance), cell);
                sheet.addCell(label);
                label = new jxl.write.Label(3, row, Double.toString(aif.oom_adj), cell);
                sheet.addCell(label);
                label = new jxl.write.Label(4, row, String.valueOf(aif.getPid()), cell);
                sheet.addCell(label);
                row++;
                resultMemory += memSize;
            }
            label = new jxl.write.Label(0, row, "총합", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, Integer.toString(procList.size()) + "개", cell);
            sheet.addCell(label);
            row++;

            label = new jxl.write.Label(0, row, "총 메모리", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, Util.convertKbToMb(resultMemory)+"MB", cell);
            sheet.addCell(label);
            row++;
            row++;

            label = new jxl.write.Label(3, 12, String.valueOf(procList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 12, Util.convertKbToMb(resultMemory)+"MB", cell);
            sheet.addCell(label);

            totalNumOfStoringToMemory = totalNumOfStoringToMemory + procList.size();
            totalMemorySizeOfStoreingToMemory = totalMemorySizeOfStoreingToMemory + resultMemory;

            label = new jxl.write.Label(3, 14, String.valueOf(totalNumOfStoringToMemory), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 14, Util.convertKbToMb(totalMemorySizeOfStoreingToMemory)+"MB", cell);
            sheet.addCell(label);

        } catch (Exception e) {

        }
        return row++;
    }

    int mServProcListToXml(Map<String, AppInfoClass> procList, WritableSheet sheet, WritableCellFormat cell, int row, String[] keySets) {
        final jxl.write.WritableCellFormat subFormat = new WritableCellFormat();

        jxl.write.Label label = null;
        int foregroundMemory=0, visibleMemory =0, perceptibleMemory=0, homeMemory =0, AServiceMemory=0, BServiceMemory=0, previousMemory=0;

        int resultMemory = 0;
        AppInfoClass mTempClass;
        try {
            subFormat.setBackground(Colour.BRIGHT_GREEN);
            label = new jxl.write.Label(0, row, "이름", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(1, row, "사이즈", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(2, row, "Importance", cell);
            sheet.addCell(label);
            label = new jxl.write.Label(3, row, "OOM_ADJ", cell);
            sheet.addCell(label);
            row++;

            foreGroundList = new ArrayList<AppInfoClass>();
            visibleList = new ArrayList<AppInfoClass>();
            perceptibleList = new ArrayList<AppInfoClass>();
            HomeList = new ArrayList<AppInfoClass>();
            AServiceList = new ArrayList<AppInfoClass>();
            BServiceList = new ArrayList<AppInfoClass>();
            previousList = new ArrayList<AppInfoClass>();

            if (SDK < 21) {
                for (int i = 0; i < keySets.length; i++) {
                    String name = keySets[i];
                    AppInfoClass aif = procList.get(name);
                    if (aif.oom_adj == 0.0) {
                        foreGroundList.add(aif);
                    } else if (aif.oom_adj == 1.0) {
                        visibleList.add(aif);
                    } else if (aif.oom_adj == 2.0) {
                        perceptibleList.add(aif);
                    } else if (aif.oom_adj == 5.0) {
                        AServiceList.add(aif);
                    } else if (aif.oom_adj == 6.0) {
                        HomeList.add(aif);
                    } else if (aif.oom_adj == 7.0) {
                        previousList.add(aif);
                    } else if (aif.oom_adj == 8.0) {
                        BServiceList.add(aif);
                    } else {

                    }
                }
                label = new jxl.write.Label(0, row, "FOREGROUND", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < foreGroundList.size(); j++) {
                    mTempClass = foreGroundList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    foregroundMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(foreGroundList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "VISIBLE", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < visibleList.size(); j++) {
                    mTempClass = visibleList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    visibleMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(visibleList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "PERCEPTIBLE", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < perceptibleList.size(); j++) {
                    mTempClass = perceptibleList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    perceptibleMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(perceptibleList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "HOME", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < HomeList.size(); j++) {
                    mTempClass = HomeList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    homeMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;

                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(HomeList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "PREVIOUS", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < previousList.size(); j++) {
                    mTempClass = previousList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;

                    previousMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(previousList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "A Service", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < AServiceList.size(); j++) {
                    mTempClass = AServiceList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;

                    AServiceMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(AServiceList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "B Service", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < BServiceList.size(); j++) {
                    mTempClass = BServiceList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;

                    BServiceMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;

                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(BServiceList.size()) + "개", cell);
                sheet.addCell(label);
                row++;
                label = new jxl.write.Label(0, row, "총 메모리", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Util.convertKbToMb(resultMemory)+"MB", cell);
                sheet.addCell(label);
                row++;
                row++;


            } else {
                for (int i = 0; i < keySets.length; i++) {
                    String name = keySets[i];
                    int memSize;
                    AppInfoClass aif = procList.get(name);
                    if (aif.oom_adj == 0.0) {
                        visibleList.add(aif);
                    } else if (aif.oom_adj == 1.0) {
                        perceptibleList.add(aif);
                    } else if (aif.oom_adj == 4.0) {
                        AServiceList.add(aif);
                    } else if (aif.oom_adj == 5.0) {
                        HomeList.add(aif);
                    }else if(aif.oom_adj == 6.0){
                        previousList.add(aif);
                    }else if (aif.oom_adj == 7.0) {
                        BServiceList.add(aif);
                    } else {

                    }
                }
                label = new jxl.write.Label(0, row, "VISIBLE", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < visibleList.size(); j++) {
                    mTempClass = visibleList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    visibleMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(visibleList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "PERCELTABLE", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < perceptibleList.size(); j++) {
                    mTempClass = perceptibleList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    perceptibleMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(perceptibleList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "HOME", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < HomeList.size(); j++) {
                    mTempClass = HomeList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    homeMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;

                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(HomeList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "PREVIOUS", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < previousList.size(); j++) {
                    mTempClass = previousList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;

                    previousMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;
                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(previousList.size()) + "개", cell);
                sheet.addCell(label);
                row++;


                label = new jxl.write.Label(0, row, "A Service", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < AServiceList.size(); j++) {
                    mTempClass = AServiceList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    AServiceMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;

                }

                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(AServiceList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "B Service", subFormat);
                sheet.addCell(label);
                row++;
                for (int j = 0; j < BServiceList.size(); j++) {
                    mTempClass = BServiceList.get(j);
                    label = new jxl.write.Label(0, row, mTempClass.getName(), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(1, row, Util.convertKbToMb(mTempClass.cur_memory)+"MB", cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(2, row, Util.importanceToString(mTempClass.importance), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(3, row, Double.toString(mTempClass.oom_adj), cell);
                    sheet.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(mTempClass.getPid()), cell);
                    sheet.addCell(label);
                    row++;
                    BServiceMemory += mTempClass.cur_memory;
                    resultMemory += mTempClass.cur_memory;

                }
                label = new jxl.write.Label(0, row, "총합", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Integer.toString(BServiceList.size()) + "개", cell);
                sheet.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, "총 메모리", cell);
                sheet.addCell(label);
                label = new jxl.write.Label(1, row, Util.convertKbToMb(resultMemory)+"MB", cell);
                sheet.addCell(label);
                row++;
                row++;
            }

            /**
             * 요약정보 삽입
             */
            label = new jxl.write.Label(3, 5, String.valueOf(foreGroundList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 5, Util.convertKbToMb(foregroundMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 6, String.valueOf(visibleList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 6, Util.convertKbToMb(visibleMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 7, String.valueOf(perceptibleList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 7, Util.convertKbToMb(perceptibleMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 8, String.valueOf(HomeList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 8, Util.convertKbToMb(homeMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 9, String.valueOf(previousList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 9, Util.convertKbToMb(previousMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 10, String.valueOf(AServiceList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 10, Util.convertKbToMb(AServiceMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(3, 11, String.valueOf(BServiceList.size()), cell);
            sheet.addCell(label);
            label = new jxl.write.Label(5, 11, Util.convertKbToMb(BServiceMemory)+"MB", cell);
            sheet.addCell(label);

            label = new jxl.write.Label(5, 13, Util.convertKbToMb(AServiceMemory+BServiceMemory)+"MB", cell);
            sheet.addCell(label);

            totalNumOfStoringToMemory = totalNumOfStoringToMemory + foreGroundList.size() + visibleList.size() + perceptibleList.size() + HomeList.size() + previousList.size() + AServiceList.size() + BServiceList.size();
            totalMemorySizeOfStoreingToMemory = totalMemorySizeOfStoreingToMemory + foregroundMemory + visibleMemory + perceptibleMemory + homeMemory + previousMemory + AServiceMemory + BServiceMemory;

        } catch (Exception e) {

        }
        return row++;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("LOTTE", "CalBootingTimeSerivce 종료");
    }
}
