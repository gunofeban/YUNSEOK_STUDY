package sslab.lova.mem_monitor;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by JEFF on 2015-11-10.
 */
public class BroadcastQueue implements Serializable {
    final int listSize = 20;
    ArrayList<String> list;

    public BroadcastQueue(){
        list = new ArrayList<String>();
    }

    public void insertRecord(String action){
        if(list.size() == listSize){
            dequeue();
            enqueue(action);
        }else{
            enqueue(action);
        }
    }

    public void enqueue(String action){
        list.add(action);
    }

    public void dequeue(){
        list.remove(0);
    }

    public String toString(){
        return list.toString();
    }

    public String compareRecentBroadcast(ArrayList<String> brList){
        String br = "null";
        boolean flag = false;

        if(brList != null){
            for(int i = list.size()-1;i>=0;i--){
                for(int j=0;j<brList.size();j++){
                    if(list.get(i).equals(brList.get(j))){
                        br = list.get(i);
                        flag = true;
                        break;
                    }
                }

                if(flag == true){
                    break;
                }
            }
        }else{
            Log.d("OYSKAR", "brList null????");
        }
        return br;
    }
}