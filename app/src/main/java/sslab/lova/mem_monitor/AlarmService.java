package sslab.lova.mem_monitor;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jxl.Workbook;
import jxl.format.Colour;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

/**
 * Created by JEFF on 2015-11-07.
 */
public class AlarmService extends Service {

    private Map<String, AppInfoClass> RTMap = new HashMap<String, AppInfoClass>();

    private String[] mapKey;
    PowerManager pm;
    PowerManager.WakeLock wakeLock;
    private Context mContext;
    private String killingMessage="";
    private String deviceId;
    ArrayList<String> autoCreateList;
    ArrayList<String> unknownList;
    ArrayList<String> foregroundList;
    ArrayList<String> visibleList;
    ArrayList<String> perceptibleList;
    ArrayList<String> AServiceList;
    ArrayList<String> homeList;
    ArrayList<String> previousList;
    ArrayList<String> BServiceList;
    ArrayList<String> cachedList;

    List<AppInfoClass> tpAppList;
    List<AppInfoClass> downAppList;
    List<AppInfoClass> systemAppList;

    ArrayList<String> autoCreateTpAppList;
    ArrayList<String> autoCreateDownAppList;
    ArrayList<String> autoCreateSystemAppList;
    ArrayList<String> autoCreateUnknownAppList;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d("LOTTE","AlarmService onCreate()");
        super.onCreate();

        pm = (PowerManager) getSystemService( Context.POWER_SERVICE );
        wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, "MY TAG" );
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("LOTTE","AlarmService onDestroy");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mContext = this;
        killingMessage = intent.getStringExtra("killingMessage");
        Log.d("LOTTE","AlarmService killingMessage : " + killingMessage );

        makeExcelOfServiceInfo();
        startTopViewService();
        Log.i("LOTTE", "AlarmService 호출!!");

        deviceId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GMailSender sender = new GMailSender("sslab.dev", "sslab5760"); // SUBSTITUTE HERE
                try {
                    sender.sendMail(
                            "[2]["+deviceId+"]" + killingMessage,   //subject.getText().toString(),
                            "메일 본문입니다..~~ ",           //body.getText().toString(),
                            "sslab.dev@gmail.com",          //from.getText().toString(),
                            "sslab.dev@gmail.com",            //to.getText().toString()
                            "/SSLAB/CollectServiceData.xls"
                    );
                    Util.removeDir("SSLAB");
                } catch (Exception e) {
                    Log.e("SendMail", e.getMessage(), e);
                }
            }
        });

        thread.start();

        wakeLock.release();
        stopSelf();

        return START_NOT_STICKY;
    }
    public void startTopViewService(){
        Intent intent = new Intent(mContext,TopViewService.class);
        intent.putExtra("isAlarmService",true);
        startService(intent);
    }
    public void classifyAutoCreateAndInstalledApp(){
        autoCreateTpAppList = new ArrayList<String>();
        autoCreateSystemAppList = new ArrayList<String>();
        autoCreateDownAppList = new ArrayList<String>();
        autoCreateUnknownAppList = new ArrayList<String>();

        try{
            tpAppList = (List<AppInfoClass>)Util.loadObject("tpAppList.ser");
            systemAppList = (List<AppInfoClass>)Util.loadObject("systemAppList.ser");
            downAppList = (List<AppInfoClass>)Util.loadObject("downAppList.ser");
        }catch(Exception e){
            Log.e("LOTTE","classifyAutoCreateAndInstalledApp 에러");
        }

        for(int i=0;i<autoCreateList.size();i++){
            boolean flag = false;
            for(int j = 0 ; j<tpAppList.size() ; j++){
                if(autoCreateList.get(i).equals(tpAppList.get(j).getLabel())){
                    flag = true;
                    autoCreateTpAppList.add(autoCreateList.get(i));
                    tpAppList.remove(j);
                    break;
                }
            }

            if(!flag){
                for(int j = 0 ; j<systemAppList.size() ; j++){
                    if(autoCreateList.get(i).equals(systemAppList.get(j).getLabel())){
                        flag = true;
                        autoCreateSystemAppList.add(autoCreateList.get(i));
                        systemAppList.remove(j);
                        break;
                    }
                }
            }

            if(!flag){
                for(int j = 0 ; j<downAppList.size() ; j++){
                    if(autoCreateList.get(i).equals(downAppList.get(j).getLabel())){
                        flag = true;
                        autoCreateDownAppList.add(autoCreateList.get(i));
                        downAppList.remove(j);
                        break;
                    }
                }
            }
            if(!flag){
                autoCreateUnknownAppList.add(autoCreateList.get(i));
            }
        }
    }
    /**
     * TopViewService에서 모은 3개의 정보 (자동 실행된 어플리케이션, 재복구 걸리는 시간, 앱 사용시간) 과
     * CollectBroadcastMessageService 에서 모은 1개의 정보 (브로드캐스트 메시지 발생 )
     * 을 각 시트별로 만들어서 엑셀 파일을 만드는 함수
     * Sheet1 : 자동 실행된 어플리케이션 <시간, 이름, importance>
     * Sheet2 : 앱 실행 시간  <이름, 총 사용 시간, 최근 사용 시간>
     * Sheet3 : 재복구 시 걸리는 시간 <모든 앱을 죽인 시간, 재복구가 완료된 시점의 시간, 걸린 시간>
     * Sheet4 : 브로드캐스트 메시지 로깅 <시간, action>
     */
    private void makeExcelOfServiceInfo() {
        BufferedReader in;
        String s;
        /**
         * [16-04-18 오후 8시] 구현해야 하는 것
         * 1. 자동 실행된 앱 개수가 얼마인지 총합 확인해야 할 것이고
         * 2.
         */
        try {
            int row = 0, column = 0;
            int saveRow=0;
            int summaryClassifyLocation = 0;
            int summaryDataLocation = 0;

            File xmlFile = new File(Environment.getExternalStorageDirectory() + "/SSLAB/CollectServiceData.xls");
            xmlFile.getParentFile().mkdirs();
            WritableWorkbook workbook = Workbook.createWorkbook(new File(Environment.getExternalStorageDirectory() + "/SSLAB/CollectServiceData.xls"));

            jxl.write.WritableCellFormat classifyFormat = new WritableCellFormat();
            classifyFormat.setBackground(Colour.AQUA);

            jxl.write.WritableCellFormat summaryFormat = new WritableCellFormat();
            summaryFormat.setBackground(Colour.YELLOW);

            jxl.write.WritableCellFormat format = new WritableCellFormat();
            jxl.write.Label label = null;

            //-------------------------------------------------------------------------------
            try {
                int autoCreateCount = 0;

                autoCreateList = new ArrayList<String>();
                unknownList = new ArrayList<String>();
                foregroundList = new ArrayList<String>();
                visibleList = new ArrayList<String>();
                perceptibleList= new ArrayList<String>();
                AServiceList = new ArrayList<String>();
                homeList = new ArrayList<String>();
                previousList = new ArrayList<String>();
                BServiceList = new ArrayList<String>();
                cachedList = new ArrayList<String>();

                WritableSheet sheet1 = workbook.createSheet("자동 실행된 어플리케이션", 0);

                label = new jxl.write.Label(0, row, "시간", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(1, row, "앱 이름", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(2, row, "패키지 이름 ", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(3, row, "IMPORTANCE", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(4, row, "Adj", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(5, row, "Adj 분류", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(6, row, "사용가능한 메모리 사이즈", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(7, row, "앱 메모리 차지량", classifyFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(8, row, "발생한 Broadcast action", classifyFormat);
                sheet1.addCell(label);

                row++;

                in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/autocreate.txt")));

                while ((s = in.readLine()) != null) {
                    autoCreateCount++;
                    String[] data = s.split("/");

                    label = new jxl.write.Label(0, row, data[0], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(1, row, data[1], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(2, row, data[2], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(3, row, data[3], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(4, row, data[4], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(5, row, data[5], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(6, row, data[6], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(7, row, data[7], format);
                    sheet1.addCell(label);
                    label = new jxl.write.Label(8, row, data[8], format);
                    sheet1.addCell(label);

                    /**
                     * summary를 위해 각 리스트에 데이터 삽입
                     */
                    autoCreateList.add(data[1]); // 전체 리스트에 삽입
                    if (data[5].equals("Unknown")){
                        unknownList.add(data[1]);
                    } else if (data[5].equals("Foreground")){
                        foregroundList.add(data[1]);
                    } else if (data[5].equals("Visible")){
                        visibleList.add(data[1]);
                    } else if (data[5].equals("Perceptible")){
                        perceptibleList.add(data[1]);
                    } else if (data[5].equals("A Service")){
                        AServiceList.add(data[1]);
                    } else if (data[5].equals("Home")){
                        homeList.add(data[1]);
                    } else if (data[5].equals("Previous")){
                        previousList.add(data[1]);
                    } else if (data[5].equals("B Service")){
                        BServiceList.add(data[1]);
                    } else if (data[5].equals("Cached")){
                        cachedList.add(data[1]);
                    }

                    row++;
                }
                in.close();
                /**
                 * 요약정보 작성 가로를 10부터 시작하면 된다.
                 * 작성 방식은 분류 ("총 자동실행 된 앱의 개수" 등)를 먼저 적고, 바로 그 다음줄에 값을 적는 방식으로 작성한다.
                 */

                row = 1;
                autoCreateList = Util.deleteDuplicationList(autoCreateList);
                label = new jxl.write.Label(10, row, "총 자동실행된 앱의 개 수 ", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(11, row, String.valueOf(autoCreateList.size()), format);
                sheet1.addCell(label);
                label = new jxl.write.Label(12, row, "총 자동실행된 앱의 이름(중복제거) ", summaryFormat);
                sheet1.addCell(label);
                for(int i = 0; i<autoCreateList.size();i++){
                    label = new jxl.write.Label(13, row, autoCreateList.get(i), format);
                    sheet1.addCell(label);
                    row++;
                }
                row++;
                saveRow = row;

                /**
                 * 부팅 시 설치된 앱 목록 얻어온거 불러오고, 분류
                 */
                classifyAutoCreateAndInstalledApp();

                row = 1;
                label = new jxl.write.Label(14, row, "총 자동실행된 앱의 시스템,다운로드 앱 분류", summaryFormat);
                sheet1.addCell(label);

                row = 1;
                label = new jxl.write.Label(15, row, "시스템 앱 (UID < 10000) 개수", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(16, row, String.valueOf(autoCreateSystemAppList.size()), format);
                sheet1.addCell(label);

                row++;
                label = new jxl.write.Label(15, row, "시스템 앱 (UID < 10000) 앱 목록", summaryFormat);
                sheet1.addCell(label);
                for(int i=0;i<autoCreateSystemAppList.size();i++){
                    label = new jxl.write.Label(16, row, autoCreateSystemAppList.get(i), format);
                    sheet1.addCell(label);
                    row++;
                }

                row = 1;
                label = new jxl.write.Label(17, row, "시스템 앱 (UID > 10000) 개수", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(18, row, String.valueOf(autoCreateTpAppList.size()), format);
                sheet1.addCell(label);

                row++;
                label = new jxl.write.Label(17, row, "시스템 앱 (UID > 10000) 앱 목록", summaryFormat);
                sheet1.addCell(label);
                for(int i=0;i<autoCreateTpAppList.size();i++){
                    label = new jxl.write.Label(18, row, autoCreateTpAppList.get(i), format);
                    sheet1.addCell(label);
                    row++;
                }

                row = 1;
                label = new jxl.write.Label(19, row, "다운로드 앱 개수", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(20, row, String.valueOf(autoCreateDownAppList.size()), format);
                sheet1.addCell(label);

                row++;
                label = new jxl.write.Label(19, row, "다운로드 앱 목록", summaryFormat);
                sheet1.addCell(label);
                for(int i=0;i<autoCreateDownAppList.size();i++){
                    label = new jxl.write.Label(20, row, autoCreateDownAppList.get(i), format);
                    sheet1.addCell(label);
                    row++;
                }

                row = 1;
                label = new jxl.write.Label(21, row, "판독불가 앱 개수", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(22, row, String.valueOf(autoCreateUnknownAppList.size()), format);
                sheet1.addCell(label);

                row++;
                label = new jxl.write.Label(21, row, "판독불가 앱 목록", summaryFormat);
                sheet1.addCell(label);
                for(int i=0;i<autoCreateUnknownAppList.size();i++){
                    label = new jxl.write.Label(22, row, autoCreateUnknownAppList.get(i), format);
                    sheet1.addCell(label);
                    row++;
                }

                label = new jxl.write.Label(10, 2, "총 자동실행 된 횟수 ", summaryFormat);
                sheet1.addCell(label);
                label = new jxl.write.Label(11, 2, String.valueOf(autoCreateCount), format);
                sheet1.addCell(label);
                row++;

                row = saveRow;
                /**
                 * 1. Foreground 요약
                 */
                summaryClassifyLocation = 10;
                summaryDataLocation = 11;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Foreground 자동실행 된 횟수 ", "Foreground 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,foregroundList);

                /**
                 * 2. visible 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Visible 자동실행 된 횟수 ", "Visible 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,visibleList);

                /**
                 * 3. perceptible 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Perceptible 자동실행 된 횟수 ", "Perceptible 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,perceptibleList);
                /**
                 * 4. A Service 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"A Service 자동실행 된 횟수 ", "A Service 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,AServiceList);
                /**
                 * 5. home 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Home 자동실행 된 횟수 ", "Home 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,homeList);
                /**
                 * 6. previous 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Previous 자동실행 된 횟수 ", "Previous 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,previousList);
                /**
                 * 7. B Service 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"B Service 자동실행 된 횟수 ", "B Service 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,BServiceList);
                /**
                 * 8. cached 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Cached 자동실행 된 횟수 ", "Cached 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,cachedList);
                /**
                 * 9. unknown 요약
                 */
                summaryClassifyLocation += 4;
                summaryDataLocation += 4;
                makeSummaryInformationAboutAdjStateInSheet1(sheet1,"Unknown 자동실행 된 횟수 ", "Unknwon 자동실행 된 앱의 이름 ",row,summaryClassifyLocation,summaryDataLocation,summaryFormat,format,unknownList);

            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }
            //-------------------------------------------------------------------------------
            try {
                WritableSheet sheet2 = workbook.createSheet("앱 실행 시간", 1);
                row = 0;
                label = new jxl.write.Label(0, row, "앱 이름", classifyFormat);
                sheet2.addCell(label);
                label = new jxl.write.Label(1, row, "패키지 이름", classifyFormat);
                sheet2.addCell(label);
                label = new jxl.write.Label(2, row, "총 사용 시간", classifyFormat);
                sheet2.addCell(label);
                label = new jxl.write.Label(3, row, "최근 접근 시간", classifyFormat);
                sheet2.addCell(label);
                label = new jxl.write.Label(4, row, "총 접근 수 ", classifyFormat);
                sheet2.addCell(label);
                label = new jxl.write.Label(5, row, "메모리 사용량", classifyFormat);
                sheet2.addCell(label);

                row++;

                loadRTMap();
                mapKey = RTMap.keySet().toArray(new String[0]);

                for (int i = 0; i < RTMap.size(); i++) {
                    AppInfoClass RTmapInfo = RTMap.get(mapKey[i]);

                    label = new jxl.write.Label(0, row, RTmapInfo.getLabel(), format);
                    sheet2.addCell(label);
                    label = new jxl.write.Label(1, row, mapKey[i], format);
                    sheet2.addCell(label);
                    label = new jxl.write.Label(2, row, Util.dateToStringHMS(RTmapInfo.totalTime), format);
                    sheet2.addCell(label);
                    label = new jxl.write.Label(3, row, Util.dateToStringYMDHMS(RTmapInfo.lastTime), format);
                    sheet2.addCell(label);
                    label = new jxl.write.Label(4, row, String.valueOf(RTmapInfo.exeCount), format);
                    sheet2.addCell(label);
                    label = new jxl.write.Label(5, row, Util.convertKbToMb(RTmapInfo.cur_memory) + " MB", format);
                    sheet2.addCell(label);
                    row++;

                }
            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }
            //-------------------------------------------------------------------------------
            try {
                WritableSheet sheet3 = workbook.createSheet("모든 앱을 죽이고 다시 살아나는데 걸리는 시간", 2);
                row = 0;
                label = new jxl.write.Label(0, row, "앱을 죽인 시간 ", classifyFormat);
                sheet3.addCell(label);
                label = new jxl.write.Label(1, row, "다시 살아나는 시간", classifyFormat);
                sheet3.addCell(label);
                label = new jxl.write.Label(2, row, "퍼센트", classifyFormat);
                sheet3.addCell(label);
                label = new jxl.write.Label(3, row, "앱이름", classifyFormat);
                sheet3.addCell(label);
                label = new jxl.write.Label(4, row, "걸린시간", classifyFormat);
                sheet3.addCell(label);
                label = new jxl.write.Label(5, row, "다시살아난 앱이 차지하는 메모리 사이즈", classifyFormat);
                sheet3.addCell(label);

                row++;
                in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/recreatetime.txt")));

                while ((s = in.readLine()) != null) {
                    String[] data = s.split("/");

                    label = new jxl.write.Label(0, row, data[0], format);
                    sheet3.addCell(label);
                    label = new jxl.write.Label(1, row, data[1], format);
                    sheet3.addCell(label);
                    label = new jxl.write.Label(2, row, data[2], format);
                    sheet3.addCell(label);
                    label = new jxl.write.Label(3, row, data[3], format);
                    sheet3.addCell(label);
                    label = new jxl.write.Label(4, row, Util.dateToStringHMS(Long.parseLong(data[4])*1000), format); //HMS가 1000이 1초를 기준으로 되어있기 때문임.
                    sheet3.addCell(label);
                    label = new jxl.write.Label(5, row, data[5], format);
                    sheet3.addCell(label);
                    row++;
                }
                in.close();
            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }
            //-------------------------------------------------------------------------------
            try {
                WritableSheet sheet4 = workbook.createSheet("브로드캐스트 메시지 로깅", 5);
                row = 0;
                label = new jxl.write.Label(0, row, "시간", classifyFormat);
                sheet4.addCell(label);
                label = new jxl.write.Label(1, row, "메시지 Action ", classifyFormat);
                sheet4.addCell(label);

                row++;
                in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/broadcastmessage.txt")));

                while ((s = in.readLine()) != null) {
                    String[] data = s.split("/");

                    label = new jxl.write.Label(0, row, data[0], format);
                    sheet4.addCell(label);
                    label = new jxl.write.Label(1, row, data[1], format);
                    sheet4.addCell(label);
                    row++;
                }
                in.close();
            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }


            try {    //-------------------------------------------------------------------------------
                int countOfLmk = 0;
                WritableSheet sheet5 = workbook.createSheet("lmk", 3);
                row = 0;
                label = new jxl.write.Label(0, row, "시간", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(1, row, "level", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(2, row, "lmk를 발생시킨 앱", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(3, row, "사용가능 메모리 사이즈", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(4, row, "죽은 앱", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(5, row, "죽은 앱 개수", classifyFormat);
                sheet5.addCell(label);
                label = new jxl.write.Label(6, row, "총 lmk 수", summaryFormat);
                sheet5.addCell(label);
                row++;
                in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/lmk.txt")));

                while ((s = in.readLine()) != null) {
                    countOfLmk++;
                    String[] data = s.split("/");
                    label = new jxl.write.Label(0, row, data[0], format);
                    sheet5.addCell(label);
                    label = new jxl.write.Label(1, row, data[1], format);
                    sheet5.addCell(label);
                    label = new jxl.write.Label(2, row, data[2], format);
                    sheet5.addCell(label);
                    label = new jxl.write.Label(3, row, data[3], format);
                    sheet5.addCell(label);

                    if(!data[4].equals("NULL")){
                        String[] deadProcess = data[4].split("&");

                        label = new jxl.write.Label(5, row, String.valueOf(deadProcess.length), format);
                        sheet5.addCell(label);

                        for(int i=0; i<deadProcess.length ; i++){
                            label = new jxl.write.Label(4, row, deadProcess[i], format);
                            sheet5.addCell(label);
                            row++;
                        }
                    }else{
                        label = new jxl.write.Label(4, row, data[4], format);
                        sheet5.addCell(label);
                        label = new jxl.write.Label(5, row, "0", format);
                        sheet5.addCell(label);
                        row++;
                    }
                    row++;
                }
                row = 1;
                label = new jxl.write.Label(6, row, String.valueOf(countOfLmk), format);
                sheet5.addCell(label);
                in.close();
            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }

            try {    //-------------------------------------------------------------------------------
                WritableSheet sheet6 = workbook.createSheet("빌드번호", 6);
                row = 0;
                label = new jxl.write.Label(0, row, "빌드번호", classifyFormat);
                sheet6.addCell(label);
                label = new jxl.write.Label(1, row, "테스트방식", classifyFormat);
                sheet6.addCell(label);
                row++;

                label = new jxl.write.Label(0, row, Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID), format);
                sheet6.addCell(label);
                label = new jxl.write.Label(1, row, killingMessage, format);
                sheet6.addCell(label);

            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }
            try {    //-------------------------------------------------------------------------------
                WritableSheet sheet7 = workbook.createSheet("터치 발생 시점", 4);
                row = 0;
                label = new jxl.write.Label(0, row, "터치 발생 시간", classifyFormat);
                sheet7.addCell(label);
                label = new jxl.write.Label(1, row, "터치 시 실행중이였던 앱의 이름", classifyFormat);
                sheet7.addCell(label);
                label = new jxl.write.Label(2, row, "터치 시 실행중이였던 앱의 패키지 이름", classifyFormat);
                sheet7.addCell(label);
                row++;

                in = new BufferedReader(new FileReader(new File(Environment.getExternalStorageDirectory() + "/SSLAB/touchtext.txt")));
//                Log.d("TOUCHTEST","whywhy???");
                while ((s = in.readLine()) != null) {
//                    Log.d("TOUCHTEST",s);
                    String[] data = s.split("&");
                    label = new jxl.write.Label(0, row, data[0], format);
                    sheet7.addCell(label);
                    label = new jxl.write.Label(1, row, data[1], format);
                    sheet7.addCell(label);
                    label = new jxl.write.Label(2, row, data[2], format);
                    sheet7.addCell(label);

                    row++;
                }

            } catch (Exception e2) {
                Log.d("ALARM", "EXCEPTION\n" + e2.getMessage());
                e2.printStackTrace();
            }
            // 빌트인 목록 추가
            workbook.write();
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadRTMap() {
        try {
            FileInputStream fileIn = new FileInputStream(Environment.getExternalStorageDirectory() + "/SSLAB/RTMap.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            RTMap = (Map<String, AppInfoClass>) in.readObject();
            in.close();
            fileIn.close();
        } catch (Exception e) {

        }
    }

    private void makeSummaryInformationAboutAdjStateInSheet1(WritableSheet sheet1, String autoCreateSizeString, String autoCreateNameString, int row, int summaryClassifyLocation,
                                                             int summaryDataLocation,jxl.write.WritableCellFormat summaryFormat, jxl.write.WritableCellFormat format,ArrayList<String> list){
        jxl.write.Label label = null;
        int initialRowValue = row;
        try{

            label = new jxl.write.Label(summaryClassifyLocation, row, autoCreateSizeString , summaryFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(summaryDataLocation, row, String.valueOf(list.size()), format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(summaryClassifyLocation, row, autoCreateNameString, summaryFormat);
            sheet1.addCell(label);
            for (int i = 0; i < list.size(); i++) {
                label = new jxl.write.Label(summaryDataLocation, row, list.get(i), format);
                sheet1.addCell(label);
                row++;
            }
            summaryClassifyLocation += 2;
            summaryDataLocation += 2;

            row = initialRowValue;

            list = Util.deleteDuplicationList(list);
            label = new jxl.write.Label(summaryClassifyLocation, row, autoCreateSizeString +"(중복제거)", summaryFormat);
            sheet1.addCell(label);
            label = new jxl.write.Label(summaryDataLocation, row, String.valueOf(list.size()), format);
            sheet1.addCell(label);
            row++;

            label = new jxl.write.Label(summaryClassifyLocation, row, autoCreateNameString+"(중복제거)", summaryFormat);
            sheet1.addCell(label);
            for (int i = 0; i < list.size(); i++) {
                label = new jxl.write.Label(summaryDataLocation, row, list.get(i), format);
                sheet1.addCell(label);
                row++;
            }
        }catch(Exception e){

        }
    }
}