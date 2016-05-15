package sslab.lova.mem_monitor;

/**
 * Created by JEFF on 2015-11-06.
 */


import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import jxl.format.Colour;
import jxl.write.WritableCellFormat;

public class Util {

    public static int getAdjByPid(int pid) throws Exception {
        File proc_pid_directory = new File("/proc/" + String.valueOf(pid));
        if (!proc_pid_directory.exists()) {
            throw new Exception("/proc/" + pid + " doesn't exist");
        }

        File oom_adj_file = new File(proc_pid_directory, "oom_adj");

        String ajdString = "0";
        BufferedReader brReceived = null;
        try {
            brReceived = new BufferedReader(new FileReader(oom_adj_file));
            String receivedLine;

            if ((receivedLine = brReceived.readLine()) != null)
                ajdString = receivedLine;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                brReceived.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return Integer.parseInt(ajdString);
    }

    public static Long getUidRxBytes(int uid) {
        File dir = new File("/proc/uid_stat/");
        String[] children = dir.list();

        if (!Arrays.asList(children).contains(String.valueOf(uid)))
            return 0L;

        File uidFileDir = new File("/proc/uid_stat/" + String.valueOf(uid));
        File uidActualFileReceived = new File(uidFileDir, "tcp_rcv");
//		File uidActualFileSent = new File(uidFileDir,"tcp_snd");

        String textReceived = "0";
//		String textSent = "0";

        try {
            BufferedReader brReceived = new BufferedReader(new FileReader(uidActualFileReceived));
//			BufferedReader brSent = new BufferedReader(new FileReader(uidActualFileSent));
            String receivedLine;

            if ((receivedLine = brReceived.readLine()) != null)
                textReceived = receivedLine;
//			if ((sentLine = brSent.readLine()) != null)
//				textSent = sentLine;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Long.valueOf(textReceived).longValue();
    }

    public static Long getUidTxBytes(int uid) {
        File dir = new File("/proc/uid_stat/");
        String[] children = dir.list();

        if (!Arrays.asList(children).contains(String.valueOf(uid)))
            return 0L;

        File uidFileDir = new File("/proc/uid_stat/" + String.valueOf(uid));
        File uidActualFileSended = new File(uidFileDir, "tcp_snd");

        String textSended = "0";

        try {
            BufferedReader brSended = new BufferedReader(new FileReader(uidActualFileSended));
            String sendedLine;

            if ((sendedLine = brSended.readLine()) != null)
                textSended = sendedLine;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Long.valueOf(textSended).longValue();
    }

    /**
     * <key, value> 쌍으로 <uid, RxBytes>를 가지는 SparseIntArray aka HashMap을 리턴함
     *
     * @return 모든 프로세스가 네트워크 수신이 안됬다면 null 리턴, 하나 이상이라면 인스턴스 리턴
     */
    public static SparseIntArray getRxBytesAsMap() {
        SparseIntArray sparseIntArray = new SparseIntArray();

        File dir = new File("/proc/uid_stat/");
        String[] children = dir.list();

        if (children.length == 0)
            return null;

        for (String uid : children) {
            File uidFileDir = new File("/proc/uid_stat/" + uid);
            File uidActualFileReceived = new File(uidFileDir, "tcp_rcv");

            String textReceived = "0";

            try {
                BufferedReader brReceived = new BufferedReader(new FileReader(uidActualFileReceived));
                String receivedLine;
                String sentLine;

                if ((receivedLine = brReceived.readLine()) != null) {
                    textReceived = receivedLine;
                    int rxBytes = new Integer(textReceived);
                    sparseIntArray.put(new Integer(uid), rxBytes == 0 ? -1 : rxBytes);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sparseIntArray;
    }

    /**
     * prev와 cur는 각각 <key, value> 쌍으로 <uid, RxBytes>를 가지고 있다.
     *
     * @param prev
     * @param cur
     * @param threshold 허용할 오차 범위 (단위 %)
     * @param file_size 파일 사이즈 (단위 bytes)
     * @return threshold 이내의 오차를 가지며, 파일 사이즈에 제일 근접한 uid를 리턴한다. 실패시 0 리턴
     */
    public static int findUid(SparseIntArray prev, SparseIntArray cur, int threshold, long fileSize) {
        int curUid = 0;
        int uid = 0;
        int minGap = 0x7FFFFFFF;    // 인트 최대값
        int curRxBytes = 0;
        int prevRxBytes = 0;
        double tolerance = (double) (fileSize * threshold) * 0.01d;        // 오차율

        for (int i = 0; i < cur.size(); i++) {
            curUid = cur.keyAt(i);
            curRxBytes = cur.valueAt(i);
            prevRxBytes = prev.get(curUid);

            if (prevRxBytes == 0)
                continue;
            else if (prevRxBytes == -1)
                prevRxBytes = 0;

            int prevCurGap = curRxBytes - prevRxBytes;

//			Log.d("curUid : " + curUid + ", Gap : " + prevCurGap);

            if (fileSize <= prevCurGap &&                    // fileSize <= prevCurGap <= (fileSize + tolerance)
                    prevCurGap <= (fileSize + (fileSize * tolerance))) {
                if (prevCurGap < minGap) {
                    minGap = prevCurGap;
                    uid = curUid;
                }
            }
        }

        return uid;
    }

    /**
     * prev와 cur는 각각 <key, value> 쌍으로 <uid, RxBytes>를 가지고 있다.
     *
     * @param prev
     * @param cur
     * @param threshold 허용할 오차 범위 (단위 %)
     * @param file_size 파일 사이즈 (단위 bytes)
     * @return threshold 이내의 오차를 가지며, 파일 사이즈에 제일 근접한 uid를 리턴한다. 실패시 0 리턴
     */
    public static int findUid(Context context, SparseIntArray prev, SparseIntArray cur, int threshold, long fileSize) {
        int curUid = 0;
        int uid = 0;
        int minGap = 0x7FFFFFFF;    // 인트 최대값
        int curRxBytes = 0;
        int prevRxBytes = 0;
        double tolerance = (double) (fileSize * threshold) * 0.01d;        // 오차율

        for (int i = 0; i < cur.size(); i++) {
            curUid = cur.keyAt(i);
            curRxBytes = cur.valueAt(i);
            prevRxBytes = prev.get(curUid);

            if (prevRxBytes == 0)
                continue;
            else if (prevRxBytes == -1)
                prevRxBytes = 0;

            int prevCurGap = curRxBytes - prevRxBytes;

//			Log.d(Util.findPackageNameByUid(context, curUid) + " curUid : " + curUid + ", Gap : " + prevCurGap + ", fileSize : " + fileSize);

            if (fileSize <= prevCurGap &&                    // fileSize <= prevCurGap <= (fileSize + tolerance)
                    prevCurGap <= (fileSize + (fileSize * tolerance))) {
                if (prevCurGap < minGap) {
                    minGap = prevCurGap;
                    uid = curUid;
                }
            }
        }

        return uid;
    }

    public static String findPackageNameByUid(Context context, int uid) {
        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> list = pm.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : list) {
            if (applicationInfo.uid == uid)
                return String.valueOf(applicationInfo.packageName);
        }
        return null;
    }

    public static String findPackageLabelByUid(Context context, int uid) {
        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> list = pm.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : list) {
            if (applicationInfo.uid == uid)
                return String.valueOf(applicationInfo.loadLabel(pm));
        }
        return null;
    }

    public static ApplicationInfo findApplicationInfoByUid(Context context, int uid) {
        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> list = pm.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : list) {
            if (applicationInfo.uid == uid)
                return applicationInfo;
        }
        return null;
    }

    public static int findUidByPackageName(Context context, String packageName) {
        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> list = pm.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : list) {
            if (applicationInfo.packageName.compareTo(packageName) == 0)
                return applicationInfo.uid;
        }
        return 0;
    }

    public static int findPidByPackageName(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> list = am.getRunningAppProcesses();
//        Log.e("******* findPidByPackageName ******");
        for (RunningAppProcessInfo applicationInfo : list) {
//        	Log.v(applicationInfo.processName);
            if (applicationInfo.processName.compareTo(packageName) == 0)
                return applicationInfo.pid;
        }
        return 0;
    }

    /**
     * 파일을 저장하는데 붙여넣기로 저장한다. 파일이 없으면 파일도 생성해준다
     *
     * @param string   : 저장할 파일 내용
     * @param fileName : 저장할 파일 이름
     */
    public static void saveLogToFile(String string, String fileName) {
        String dirPath = Environment.getExternalStorageDirectory().toString();
        String filePath = dirPath + fileName;
        File file = new File(dirPath+"/SSLAB");
        if (!file.exists()) {
            file.mkdir();
        }

        try {
            BufferedWriter bfw = new BufferedWriter(new FileWriter(filePath, true));
            bfw.write(string);
            bfw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveLogToFileOverride(String string, String fileName) {
        String dirPath = Environment.getExternalStorageDirectory().toString();
        String filePath = dirPath +fileName;
        File file = new File(dirPath+"/SSLAB");
        if (!file.exists()) {
            file.mkdir();
        }
        File saveFile = new File(filePath);
        try {
            BufferedWriter bfw = new BufferedWriter(new FileWriter(filePath, false));
            bfw.write(string);
            bfw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * System.currentTimeMilles() 를 인자로 받아
     * Date로 변경 후 yyyy.MM.dd HH:mm:ss 포멧으로 변경 후 String 리턴
     * @param time 시간
     * @return
     */
    public static String dateToStringYMDHMS(long time){
        Date date = new Date(time);
        DateFormat sdFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        return sdFormat.format(date);
    }

    public static String dateToStringHMS(long time){
//        Date date = new Date(time);
//        DateFormat sdFormat = new SimpleDateFormat("HH:mm:ss");
        long convert = time/1000;
        long hour = convert/3600;
        long min = (convert%3600)/60;
        long sec = (convert%3600)%60;

        return hour+":"+min+":"+sec;
    }



    /**
     * RunningAppProcessInfo에는 프로세스의 importance가 들어가있음
     * 이 importance는 프로세스가 현재 어떤 상태인지를 나타내는 변수
     * 이 값을 스트링으로 변환하기 위한 함수
     * @param importance
     * @return
     */
    public static String importanceToString(int importance){
        String string = "" ;
        if(importance == RunningAppProcessInfo.IMPORTANCE_BACKGROUND){
            string="BACKGROUND";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_EMPTY){
            string="EMPTY";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND){
            string="FOREGROUND";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_GONE){
            string="GONE";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE){
            string="PERCEPTIBLE";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_SERVICE){
            string="SERVICE";
        }else if(importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE){
            string="VISIBLE";
        }else{
            string="UNKNOWN";
        }
        return string;
    }

    /**
     * 메일을 보낸다
     * @param fileName 파일 이름
     */
    public static void sendMail(String fileName, String deviceId){
        GMailSender sender = new GMailSender("sslab.dev","sslab5760"); // SUBSTITUTE HERE
        try {
            sender.sendMail(
                    "[2]["+deviceId+"] 부팅데이터",   //subject.getText().toString(),
                    "",           //body.getText().toString(),
                    "sslab.dev@gmail.com",          //from.getText().toString(),
                    "sslab.dev@gmail.com",            //to.getText().toString()
                    fileName
            );
        } catch (Exception e) {
            Log.e("SendMail", e.getMessage(), e);
        }
    }

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
                if(!childFile.getName().equals("RTMap.ser") && !childFile.getName().equals("killingcount.ser") && !childFile.getName().equals("bootcompleteTime.txt")
                        && !childFile.getName().equals("tpAppList.ser") && !childFile.getName().equals("downAppList.ser") && !childFile.getName().equals("systemAppList.ser")){
                    childFile.delete();    //하위 파일
                }
            }
        }
    }

    /**
     * kb를 mb로 바꿔주는 메소드
     * String 변환해서 넘겨줌
     * @param num kb 단위의 사이즈
     * @return
     */
    public static String convertKbToMb(double num){
        return String.valueOf(Math.round((num/1024.0)*100d)/100d);
    }

    /**
     * adj값에 따라서 현재 프로세스가 어떤 상태를 가지는지 리턴하는 메소드
     * @param adj adj 값
     * @return adj에 상응하는 스트링을 리턴, adj 5 --> Service A
     */
    public static String convertAdjToCorrespondingString(int adj) {

        String adjString="Unknown";

        int SDK = Build.VERSION.SDK_INT;
        try {
            if (SDK < 21) {

                if (adj < 0) {
                    if (adj == -100) {
                        adjString = "Unknown";
                    } else {
                        adjString = "System";
                    }
                } else if (adj == 0.0) {
                    adjString = "Foreground";
                } else if (adj == 1.0) {
                    adjString = "Visible";
                } else if (adj == 2.0) {
                    adjString = "Perceptible";
                } else if (adj == 5.0) {
                    adjString = "A Service";
                } else if (adj == 6.0) {
                    adjString = "Home";
                } else if (adj == 7.0) {
                    adjString = "Previous";
                } else if (adj == 8.0) {
                    adjString = "B Service";
                } else {
                    adjString = "Cached";
                }
            } else {
                if (adj < 0) {
                    if (adj == -100) {
                        adjString = "Unknown";
                    } else {
                        adjString = "System";
                    }
                } else if (adj == 0.0) {
                    adjString = "Visible";
                } else if (adj == 1.0) {
                    adjString = "Perceptible";
                } else if (adj == 4.0) {
                    adjString = "A Service";
                } else if (adj == 5.0) {
                    adjString = "Home";
                } else if (adj == 6.0) {
                    adjString = "Previous";
                } else if (adj == 7.0) {
                    adjString = "B Service";
                } else {
                    adjString = "Cached";
                }
            }


        } catch (Exception e) {

        }

        return adjString;
    }

    /**
     * 중복된 데이터가 들어있는 리스트를, 중복되지 않은 데이터를 가진 리스트로 바꿔주는 메소드
     * 단 ArrayList는 String 이여야 한다.
     * @param list
     * @return
     */
    public static ArrayList deleteDuplicationList(ArrayList list){
        ArrayList <String> uniqueItems = new ArrayList<String>(new HashSet<String>(list));
        return uniqueItems;
    }

    /**
     * 객체를 파일에 저장하는 함수
     * @param obj 저장하고 싶은 객체
     * @param fileName 저장하고 싶은 파일 명
     */
    public static void saveObject(Object obj, String fileName)
    {
        try{
            FileOutputStream fileout = new FileOutputStream(Environment.getExternalStorageDirectory()+"/SSLAB/"+fileName);
            ObjectOutputStream objOut = new ObjectOutputStream(fileout);
            objOut.writeObject(obj);
            objOut.close();
            fileout.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * /**
     * 객체를 파일에 저장하는 함수
     * @param fileName 저장하고 싶은 파일 명
     * @return 객체 리턴
     */
    public static Object loadObject(String fileName){
        Object obj =null;
        try{
            FileInputStream fileIn =  new FileInputStream(Environment.getExternalStorageDirectory()+"/SSLAB/"+fileName);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            obj = in.readObject();
            in.close();
            fileIn.close();


        }catch(Exception e){
            e.printStackTrace();
        }

        return obj;
    }
}
