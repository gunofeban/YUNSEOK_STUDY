package sslab.lova.mem_monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by JEFF on 2015-11-09.
 */
public class BeforeKillClass implements Serializable {
    long time;
    ArrayList<String> list;
    ArrayList<String> deadList;
    ArrayList<String> originalList = new ArrayList<String>();
    int originalListSize;
    boolean isFinish;

    public BeforeKillClass(long time, ArrayList<String> list){
        this.time = time;
        this.list = list;
        originalList.addAll(list);
        this.isFinish = false;
        this.originalListSize = originalList.size();
        deadList = new ArrayList<String>();
    }

    public boolean isEmpty(){
        boolean flag;

        if(list.size()>0){
            flag = false;
        }else{
            flag = true;
        }
        return flag;
    }

    public void setList( ArrayList<String> list){
        this.list = list;
    }

    public long getTime(){
        return time;
    }

    public ArrayList<String> getList(){
        return list;
    }

    public boolean getIsFinish(){
        return isFinish;
    }

    public void setIsFinish(boolean isFinish){
        this.isFinish = isFinish;
    }

//    public void setDeadListSize(int size){ this.deadListSize += size; }

    public void addDeadList(ArrayList<String> dList){
        for(int i=0;i<dList.size();i++){
            deadList.add(dList.get(i));
        }
    }

    public ArrayList<String> getDeadList(){
        return deadList;
    }
    public ArrayList<String> getOriginalList(){
        return originalList;
    }

    public int getDeadListSize(){ return deadList.size(); }

}