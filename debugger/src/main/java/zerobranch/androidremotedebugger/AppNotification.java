/*
 * Copyright 2020 Arman Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zerobranch.androidremotedebugger;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

class AppNotification {
    private static final String GROUP_KEY = "android_remote_debugger_group";
    private static final String CHANNEL_ID = "important_channel";
    private static final String CHANNEL_NAME = "Android Remote Debugger";
    private static final int NOTIFICATION_ID = 7265;
    private static AppNotification instance;
    private final Context context;
    private final NotificationManager notificationManager;
    private NotificationChannel channel;

    private AppNotification(Context context) {
        this.context = context;
        notificationManager = getNotificationManager(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(CHANNEL_NAME);
            channel.enableVibration(false);
        }
    }

    static void init(Context context) {
        if (instance == null) {
            instance = new AppNotification(context);
        }
    }

    static void destroy() {
        if (instance != null) {
            cancelAll(instance.context);
        }

        instance = null;
    }

    static void notify(@Nullable String title, @Nullable String description) {
        if (instance == null) return;

        instance.notification(title, description, false);
    }

    static void notifyError(@Nullable String title, @Nullable String description) {
        if (instance == null) return;

        instance.notification(title, description, true);
    }

    static void cancelAll(Context context) {
        if (instance == null) {
            cancel(getNotificationManager(context));
            return;
        }

        cancel(instance.notificationManager);
    }

    private static void cancel(NotificationManager notificationManager) {
        notificationManager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.cancel(GROUP_KEY.hashCode());
        }
    }

    private void notification(@Nullable String title, @Nullable String description, boolean isError) {
        if (title == null && description == null) {
            return;
        }

        NotificationCompat.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(channel);
            }

            builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        } else {
            //noinspection deprecation
            builder = new NotificationCompat.Builder(context);
        }

        builder.setSmallIcon(R.drawable.android_remote_debugger_ic_bug)
                .setContentTitle(title)
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(description))
                .setAutoCancel(true);

        int pendingIntentFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;

        if (isError) {
            Intent repeatConnectionIntent = new Intent(context, NotificationReceiver.class);
            repeatConnectionIntent.setAction(NotificationReceiver.ACTION_REPEAT_CONNECTION);
            PendingIntent repeatConnectionPendingIntent = PendingIntent.getBroadcast(context, 0, repeatConnectionIntent, pendingIntentFlag);

            Intent changePortIntent = new Intent(context, NotificationReceiver.class);
            changePortIntent.setAction(NotificationReceiver.ACTION_CHANGE_PORT);
            PendingIntent changePortPendingIntent = PendingIntent.getBroadcast(context, 0, changePortIntent, pendingIntentFlag);

            builder.addAction(0, "Repeat", repeatConnectionPendingIntent);
            builder.addAction(0, "Change port", changePortPendingIntent);
        } else {
            Intent disconnectIntent = new Intent(context, NotificationReceiver.class);
            disconnectIntent.setAction(NotificationReceiver.ACTION_DISCONNECT);

            PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(context, 0, disconnectIntent, pendingIntentFlag);
            builder.addAction(0, "Disconnect", disconnectPendingIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setGroup(GROUP_KEY);

            NotificationCompat.Builder groupBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.android_remote_debugger_ic_bug)
                    .setGroup(GROUP_KEY)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0L})
                    .setGroupSummary(true);

            notificationManager.notify(NOTIFICATION_ID, builder.build());
            notificationManager.notify(GROUP_KEY.hashCode(), groupBuilder.build());
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private static NotificationManager getNotificationManager(Context context) {
        return ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    }
}
