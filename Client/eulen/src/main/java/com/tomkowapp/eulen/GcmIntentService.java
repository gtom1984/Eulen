package com.tomkowapp.eulen;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

// Google Cloud Messaging intent handling

public class GcmIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        SharedPreferences prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            try {
                // Post notification of received message.
                String message = extras.getString(CONST.MESSAGE);
                if (message != null) {
                    switch (message) {
                        case CONST.SYNC:
                            sendNotification(getString(R.string.notification_gcm));
                            prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);
                            prefs.edit().putLong(CONST.PREFS_SYNCTIME, 0).apply(); //zero out last sync timestamp to force message sync upon resume of open of main Eulen task
                            break;
                        case CONST.PASSWORD:
                            sendNotification(getString(R.string.gcm_bad_password));
                            break;
                        case CONST.FULL:
                            sendNotification(getString(R.string.message_inbox_full_server));
                            break;
                    }

                    Intent passIntent = new Intent(main.class.getName());
                    passIntent.putExtra(CONST.MESSAGE, message);
                    getApplicationContext().sendBroadcast(passIntent);
                }
            } catch (Exception ex) {
                sendNotification(getString(R.string.notification_gcm_error));
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    // Notification bar and Android Wear alerts
    private void sendNotification(String msg) {
        SharedPreferences prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

        // stop if user has disabled notifications
        if(!prefs.getBoolean(CONST.PREFS_NOTIFICATIONS, true)) {
            return;
        }

        NotificationManager mNotificationManager;

        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender()
                        .setContentIcon(R.drawable.ic_launcher)
                        .setHintHideIcon(true)
                        .setBackground(BitmapFactory.decodeResource(getResources(),
                                R.drawable.wearbg));

        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(this, main.class);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(getString(R.string.app_name))
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setContentIntent(contentIntent)
                        .extend(wearableExtender)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setContentText(msg);

        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}
