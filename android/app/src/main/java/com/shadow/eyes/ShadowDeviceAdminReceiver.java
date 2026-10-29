package com.shadow.eyes;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ShadowDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "ShadowAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Shadow Device Administrator granted by librarianist");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.w(TAG, "Shadow Device Administrator revoked");
    }
}

