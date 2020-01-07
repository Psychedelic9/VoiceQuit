package com.bai.psychedelic.voicequit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootBroadcastReceiver";
    private static final String ACTION_BOOT = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_BOOT)) { //开机启动完成后，要做的事情 
            Log.i(TAG, "BootBroadcastReceiver onReceive(), Do thing!");
            Intent intent2 = new Intent(context,MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
            context.startActivity(intent2);
        }
    }

}
