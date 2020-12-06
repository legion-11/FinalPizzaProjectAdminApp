package com.dmytroandriichuk.finalpizzaprojectadminapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ProximityIntentReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i("TAG", "onReceive: "+intent.getAction());
    }
}