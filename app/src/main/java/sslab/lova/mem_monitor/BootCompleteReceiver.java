package sslab.lova.mem_monitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by JEFF on 2015-11-07.
 */
public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("LOTTE", "bootcompleteReceiver onReceive");

        context.startService(new Intent(context, CalBootingTimeService.class));
        context.startService(new Intent(context, CollectBroadcastMessageService.class));

        Intent i = new Intent(context, TopViewService.class);
        i.putExtra("isBootCompleteReceiver",true);
        context.startService(i);
    }
}
