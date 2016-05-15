package sslab.lova.mem_monitor;

import android.graphics.drawable.Drawable;

import java.io.Serializable;

/**
 * Created by JEFF on 2015-11-13.
 */
public class AppInfoClass implements Serializable {

    int pid;
    String name;
    String label;
    Drawable icon;
    int prev_memory;
    int cur_memory;
    long lastTime;
    long totalTime;
    int importance;
    double oom_adj;
    int uid;
    int exeCount =0;

    public AppInfoClass(Drawable icon, int pid, String name, int prev_memory,int cur_memory, int importance, double oom_adj){
        this.icon = icon;
        this.pid = pid;
        this.name = name;
        this.prev_memory = prev_memory;
        this.cur_memory = cur_memory;
        this.importance = importance;
        this.oom_adj = oom_adj;
    }
    public AppInfoClass(Drawable icon, int pid, String name, int memory,double oom_adj){
        this.icon = icon;
        this.pid = pid;
        this.name = name;
        this.prev_memory = memory;
        this.oom_adj = oom_adj;
    }

    public AppInfoClass(int pid, String name, int memory){
        this.pid = pid;
        this.name = name;
        this.prev_memory = memory;
    }

    public AppInfoClass(String packageName, String label, long lastTime, long totalTime,int cur_memory){
        this.name = packageName;
        this.label = label;
        this.lastTime = lastTime;
        this.totalTime = totalTime;
        this.cur_memory = cur_memory;
        this.exeCount = 1;
    } // For Execution

    public AppInfoClass(String name, String label, int uid){
        this.name = name;
        this.label = label;
        this.uid = uid;
    }


    public Drawable getIcon(){
        return icon;
    }

    public int getPid(){
        return pid;
    }

    public String getName(){
        return name;
    }

    public int getMemory(){

        return cur_memory;

    }

    public String getLabel(){ return label;}

    public int getUid() { return uid; }


    public String toString(){
        if(prev_memory!=0)
        {
            int changedMem =cur_memory-prev_memory;
            String returnString="";
            if(changedMem>=0)
            {
                returnString="+" + String.valueOf(changedMem);
            }
            else{
                returnString = ""+ String.valueOf(changedMem);
            }
            return "PID : " + String.valueOf(pid) + "\n" + "NAME : " + name + "\n" + "MEMORY : " + String.valueOf(prev_memory)+"KB ->"+ String.valueOf(cur_memory) +"KB ("+returnString+")";


        }
        else {
            return "PID : " + String.valueOf(pid) + "\n" + "NAME : " + name + "\n" + "MEMORY : " + String.valueOf(cur_memory) +"KB";
        }
    }
}