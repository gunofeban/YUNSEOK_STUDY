package sslab.lova.mem_monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by JEFF on 2015-11-06.
 */
public class KeepServicesAliveReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("LOTTE", "KeepServicesAliveReceiver onReceive");
        context.startService(new Intent(context, CollectBroadcastMessageService.class));
        context.startService(new Intent(context, TopViewService.class));
    }
}
