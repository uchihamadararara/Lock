package com.applock.guardian.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.applock.guardian.activities.LockScreenActivity;
import com.applock.guardian.utils.AppPrefs;

public class ScreenStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        AppPrefs prefs = new AppPrefs(context);

        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            if (prefs.isLockScreenEnabled()) {
                Intent lockIntent = new Intent(context, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(lockIntent);
            }
        }
    }
}
