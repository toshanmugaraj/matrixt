/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.core.Log;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.vector.Matrix;
import im.vector.R;
import im.vector.push.PushManager;
import im.vector.repositories.ServerUrlsRepository;
import im.vector.ui.themes.ThemeUtils;

public class PreferencesManager {

    private static final String LOG_TAG = PreferencesManager.class.getSimpleName();

    public static final String VERSION_BUILD = "VERSION_BUILD";

    public static final String SETTINGS_MESSAGES_SENT_BY_BOT_PREFERENCE_KEY = "SETTINGS_MESSAGES_SENT_BY_BOT_PREFERENCE_KEY_2";
    public static final String SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY = "SETTINGS_CHANGE_PASSWORD_PREFERENCE_KEY";
    public static final String SETTINGS_VERSION_PREFERENCE_KEY = "SETTINGS_VERSION_PREFERENCE_KEY";
    public static final String SETTINGS_OLM_VERSION_PREFERENCE_KEY = "SETTINGS_OLM_VERSION_PREFERENCE_KEY";
    public static final String SETTINGS_LOGGED_IN_PREFERENCE_KEY = "SETTINGS_LOGGED_IN_PREFERENCE_KEY";
    public static final String SETTINGS_HOME_SERVER_PREFERENCE_KEY = "SETTINGS_HOME_SERVER_PREFERENCE_KEY";
    public static final String SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY = "SETTINGS_IDENTITY_SERVER_PREFERENCE_KEY";
    public static final String SETTINGS_APP_TERM_CONDITIONS_PREFERENCE_KEY = "SETTINGS_APP_TERM_CONDITIONS_PREFERENCE_KEY";
    public static final String SETTINGS_PRIVACY_POLICY_PREFERENCE_KEY = "SETTINGS_PRIVACY_POLICY_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATION_PRIVACY_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_PRIVACY_PREFERENCE_KEY";
    public static final String SETTINGS_THIRD_PARTY_NOTICES_PREFERENCE_KEY = "SETTINGS_THIRD_PARTY_NOTICES_PREFERENCE_KEY";
    public static final String SETTINGS_COPYRIGHT_PREFERENCE_KEY = "SETTINGS_COPYRIGHT_PREFERENCE_KEY";
    public static final String SETTINGS_CLEAR_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_CACHE_PREFERENCE_KEY";
    public static final String SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY = "SETTINGS_CLEAR_MEDIA_CACHE_PREFERENCE_KEY";
    public static final String SETTINGS_USER_SETTINGS_PREFERENCE_KEY = "SETTINGS_USER_SETTINGS_PREFERENCE_KEY";
    public static final String SETTINGS_CONTACT_PREFERENCE_KEYS = "SETTINGS_CONTACT_PREFERENCE_KEYS";
    public static final String SETTINGS_NOTIFICATIONS_TARGETS_PREFERENCE_KEY = "SETTINGS_NOTIFICATIONS_TARGETS_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATIONS_TARGET_DIVIDER_PREFERENCE_KEY = "SETTINGS_NOTIFICATIONS_TARGET_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_IGNORED_USERS_PREFERENCE_KEY = "SETTINGS_IGNORED_USERS_PREFERENCE_KEY";
    public static final String SETTINGS_IGNORE_USERS_DIVIDER_PREFERENCE_KEY = "SETTINGS_IGNORE_USERS_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY";
    public static final String SETTINGS_BACKGROUND_SYNC_DIVIDER_PREFERENCE_KEY = "SETTINGS_BACKGROUND_SYNC_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_LABS_PREFERENCE_KEY = "SETTINGS_LABS_PREFERENCE_KEY";
    public static final String SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY = "SETTINGS_CRYPTOGRAPHY_PREFERENCE_KEY";
    public static final String SETTINGS_CRYPTOGRAPHY_DIVIDER_PREFERENCE_KEY = "SETTINGS_CRYPTOGRAPHY_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_DEVICES_LIST_PREFERENCE_KEY = "SETTINGS_DEVICES_LIST_PREFERENCE_KEY";
    public static final String SETTINGS_DEVICES_DIVIDER_PREFERENCE_KEY = "SETTINGS_DEVICES_DIVIDER_PREFERENCE_KEY";
    public static final String SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY = "SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY";
    public static final String SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_IS_ACTIVE_PREFERENCE_KEY
            = "SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_IS_ACTIVE_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_NAME_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_ID_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_EXPORT_E2E_ROOM_KEYS_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_IMPORT_E2E_ROOM_KEYS_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_NEVER_SENT_TO_PREFERENCE_KEY";
    public static final String SETTINGS_ENCRYPTION_INFORMATION_DEVICE_KEY_PREFERENCE_KEY = "SETTINGS_ENCRYPTION_INFORMATION_DEVICE_KEY_PREFERENCE_KEY";

    // user
    public static final String SETTINGS_DISPLAY_NAME_PREFERENCE_KEY = "SETTINGS_DISPLAY_NAME_PREFERENCE_KEY";
    public static final String SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY = "SETTINGS_PROFILE_PICTURE_PREFERENCE_KEY";

    // contacts
    public static final String SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY = "SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY";

    // interface
    public static final String SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY = "SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY";
    public static final String SETTINGS_INTERFACE_TEXT_SIZE_KEY = "SETTINGS_INTERFACE_TEXT_SIZE_KEY";
    public static final String SETTINGS_SHOW_URL_PREVIEW_KEY = "SETTINGS_SHOW_URL_PREVIEW_KEY";
    private static final String SETTINGS_SEND_TYPING_NOTIF_KEY = "SETTINGS_SEND_TYPING_NOTIF_KEY";
    private static final String SETTINGS_ENABLE_MARKDOWN_KEY = "SETTINGS_ENABLE_MARKDOWN_KEY";
    private static final String SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY = "SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY";
    private static final String SETTINGS_12_24_TIMESTAMPS_KEY = "SETTINGS_12_24_TIMESTAMPS_KEY";
    private static final String SETTINGS_SHOW_READ_RECEIPTS_KEY = "SETTINGS_SHOW_READ_RECEIPTS_KEY";
    public static final String SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY = "SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY";
    private static final String SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY = "SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY";
    private static final String SETTINGS_VIBRATE_ON_MENTION_KEY = "SETTINGS_VIBRATE_ON_MENTION_KEY";
    private static final String SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY = "SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY";

    // home
    private static final String SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY = "SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY";
    private static final String SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY = "SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY";

    // flair
    public static final String SETTINGS_GROUPS_FLAIR_KEY = "SETTINGS_GROUPS_FLAIR_KEY";

    // notifications
    public static final String SETTINGS_NOTIFICATIONS_KEY = "SETTINGS_NOTIFICATIONS_KEY";
    public static final String SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY = "SETTINGS_ENABLE_ALL_NOTIF_PREFERENCE_KEY";
    public static final String SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY = "SETTINGS_ENABLE_THIS_DEVICE_PREFERENCE_KEY";
    public static final String SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY = "SETTINGS_TURN_SCREEN_ON_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY";
    public static final String SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY = "SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY";
    public static final String SETTINGS_CONTAINING_MY_DISPLAY_NAME_PREFERENCE_KEY = "SETTINGS_CONTAINING_MY_DISPLAY_NAME_PREFERENCE_KEY_2";
    public static final String SETTINGS_CONTAINING_MY_USER_NAME_PREFERENCE_KEY = "SETTINGS_CONTAINING_MY_USER_NAME_PREFERENCE_KEY_2";
    public static final String SETTINGS_MESSAGES_IN_ONE_TO_ONE_PREFERENCE_KEY = "SETTINGS_MESSAGES_IN_ONE_TO_ONE_PREFERENCE_KEY_2";
    public static final String SETTINGS_MESSAGES_IN_GROUP_CHAT_PREFERENCE_KEY = "SETTINGS_MESSAGES_IN_GROUP_CHAT_PREFERENCE_KEY_2";
    public static final String SETTINGS_INVITED_TO_ROOM_PREFERENCE_KEY = "SETTINGS_INVITED_TO_ROOM_PREFERENCE_KEY_2";
    public static final String SETTINGS_CALL_INVITATIONS_PREFERENCE_KEY = "SETTINGS_CALL_INVITATIONS_PREFERENCE_KEY_2";

    // background sync
    public static final String SETTINGS_START_ON_BOOT_PREFERENCE_KEY = "SETTINGS_START_ON_BOOT_PREFERENCE_KEY";
    public static final String SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY = "SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY";
    public static final String SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY = "SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY";
    public static final String SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY = "SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY";

    // Calls
    public static final String SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY = "SETTINGS_CALL_RINGTONE_USE_RIOT_PREFERENCE_KEY";
    public static final String SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY = "SETTINGS_CALL_RINGTONE_URI_PREFERENCE_KEY";

    // labs
    public static final String SETTINGS_LAZY_LOADING_PREFERENCE_KEY = "SETTINGS_LAZY_LOADING_PREFERENCE_KEY";
    public static final String SETTINGS_USER_REFUSED_LAZY_LOADING_PREFERENCE_KEY = "SETTINGS_USER_REFUSED_LAZY_LOADING_PREFERENCE_KEY";
    public static final String SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY = "SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY";
    private static final String SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY = "SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY";

    private static final String SETTINGS_USE_NATIVE_CAMERA_PREFERENCE_KEY = "SETTINGS_USE_NATIVE_CAMERA_PREFERENCE_KEY";

    private static final String SETTINGS_ENABLE_SEND_VOICE_FEATURE_PREFERENCE_KEY = "SETTINGS_ENABLE_SEND_VOICE_FEATURE_PREFERENCE_KEY";

    // analytics
    public static final String SETTINGS_USE_ANALYTICS_KEY = "SETTINGS_USE_ANALYTICS_KEY";
    public static final String SETTINGS_USE_RAGE_SHAKE_KEY = "SETTINGS_USE_RAGE_SHAKE_KEY";

    // other
    public static final String SETTINGS_MEDIA_SAVING_PERIOD_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_KEY";
    private static final String SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY = "SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY";
    private static final String DID_ASK_TO_IGNORE_BATTERY_OPTIMIZATIONS_KEY = "DID_ASK_TO_IGNORE_BATTERY_OPTIMIZATIONS_KEY";
    private static final String DID_ASK_TO_USE_ANALYTICS_TRACKING_KEY = "DID_ASK_TO_USE_ANALYTICS_TRACKING_KEY";
    public static final String SETTINGS_DEACTIVATE_ACCOUNT_KEY = "SETTINGS_DEACTIVATE_ACCOUNT_KEY";
    private static final String SETTINGS_DISPLAY_ALL_EVENTS_KEY = "SETTINGS_DISPLAY_ALL_EVENTS_KEY";

    public static final String SETTINGS_DETECT_ACCESSIBILITY_SERVICE_KEY = "SETTINGS_DETECT_ACCESSIBILITY_SERVICE_KEY";
    public static final String SETTINGS_DETECT_NOTIFICATION_LISTENER_KEY = "SETTINGS_DETECT_NOTIFICATION_LISTENER_KEY";

    public static final String SETTINGS_HIDE_FROM_USERS_DIRECTORY_KEY = "SETTINGS_HIDE_FROM_USERS_DIRECTORY_KEY";

    private static final int MEDIA_SAVING_3_DAYS = 0;
    private static final int MEDIA_SAVING_1_WEEK = 1;
    private static final int MEDIA_SAVING_1_MONTH = 2;
    private static final int MEDIA_SAVING_FOREVER = 3;

    // some preferences keys must be kept after a logout
    private static final List<String> mKeysToKeepAfterLogout = Arrays.asList(
            SETTINGS_SEND_TYPING_NOTIF_KEY,
            SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY,
            SETTINGS_12_24_TIMESTAMPS_KEY,
            SETTINGS_SHOW_READ_RECEIPTS_KEY,
            SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY,
            SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY,
            SETTINGS_MEDIA_SAVING_PERIOD_KEY,
            SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY,
            SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY,

            SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY,
            SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY,
            // Do not keep SETTINGS_LAZY_LOADING_PREFERENCE_KEY because the user may log in on a server which does not support lazy loading
            SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY,
            SETTINGS_START_ON_BOOT_PREFERENCE_KEY,
            SETTINGS_INTERFACE_TEXT_SIZE_KEY,
            SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY,
            SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY,
            SETTINGS_NOTIFICATION_RINGTONE_SELECTION_PREFERENCE_KEY,

            SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY,
            SETTINGS_ROOM_SETTINGS_LABS_END_TO_END_PREFERENCE_KEY,
            SETTINGS_CONTACTS_PHONEBOOK_COUNTRY_PREFERENCE_KEY,
            SETTINGS_INTERFACE_LANGUAGE_PREFERENCE_KEY,
            SETTINGS_BACKGROUND_SYNC_PREFERENCE_KEY,
            SETTINGS_ENABLE_BACKGROUND_SYNC_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_TIMEOUT_PREFERENCE_KEY,
            SETTINGS_SET_SYNC_DELAY_PREFERENCE_KEY,

            SETTINGS_USE_RAGE_SHAKE_KEY
    );

    /**
     * Clear the preferences.
     *
     * @param context the context
     */
    public static void clearPreferences(Context context) {
        Set<String> keysToKeep = new HashSet<>(mKeysToKeepAfterLogout);

        // home server urls
        keysToKeep.add(ServerUrlsRepository.HOME_SERVER_URL_PREF);
        keysToKeep.add(ServerUrlsRepository.IDENTITY_SERVER_URL_PREF);

        // theme
        keysToKeep.add(ThemeUtils.APPLICATION_THEME_KEY);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();

        // get all the existing keys
        Set<String> keys = preferences.getAll().keySet();
        // remove the one to keep

        keys.removeAll(keysToKeep);

        for (String key : keys) {
            editor.remove(key);
        }

        editor.apply();
    }

    /**
     * Tells if we want to check at each start up if some notification listener(s) is active
     *
     */
    public static boolean detectNotificationListener(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DETECT_NOTIFICATION_LISTENER_KEY, true);
    }

    public static void putDetectNotificationListener(Context context, boolean asked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_DETECT_NOTIFICATION_LISTENER_KEY, asked)
                .apply();
    }

    /**
     * Tells if we want to check at each start up if an accessibility service is active
     *
     */
    public static boolean detectAccessibilityService(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DETECT_ACCESSIBILITY_SERVICE_KEY, true);
    }

    public static void putDetectAccessibilityService(Context context, boolean asked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_DETECT_ACCESSIBILITY_SERVICE_KEY, asked)
                .apply();
    }

    /**
     * Tells if we have already asked the user to disable battery optimisations on android >= M devices.
     *
     * @param context the context
     * @return true if it was already requested
     */
    public static boolean didAskUserToIgnoreBatteryOptimizations(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DID_ASK_TO_IGNORE_BATTERY_OPTIMIZATIONS_KEY, false);
    }

    /**
     * Mark as requested the question to disable battery optimisations.
     *
     * @param context the context
     */
    public static void setDidAskUserToIgnoreBatteryOptimizations(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(DID_ASK_TO_IGNORE_BATTERY_OPTIMIZATIONS_KEY, true)
                .apply();
    }

    /**
     * Enable again the question to disable battery optimisations.
     *
     * @param context the context
     */
    public static void resetDidAskUserToIgnoreBatteryOptimizations(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(DID_ASK_TO_IGNORE_BATTERY_OPTIMIZATIONS_KEY, false)
                .apply();
    }

    /**
     * Tells if the timestamp must be displayed in 12h format
     *
     * @param context the context
     * @return true if the time must be displayed in 12h format
     */
    public static boolean displayTimeIn12hFormat(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_12_24_TIMESTAMPS_KEY, false);
    }

    /**
     * Tells if the join and leave membership events should be shown in the messages list.
     *
     * @param context the context
     * @return true if the join and leave membership events should be shown in the messages list
     */
    public static boolean showJoinLeaveMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_SHOW_JOIN_LEAVE_MESSAGES_KEY, false);
    }

    /**
     * Tells if the avatar and display name events should be shown in the messages list.
     *
     * @param context the context
     * @return true true if the avatar and display name events should be shown in the messages list.
     */
    public static boolean showAvatarDisplayNameChangeMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_SHOW_AVATAR_DISPLAY_NAME_CHANGES_MESSAGES_KEY, false);
    }

    /**
     * Tells if the send voice feature is enabled.
     *
     * @param context the context
     * @return true if the send voice feature is enabled.
     */
    public static boolean isSendVoiceFeatureEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_ENABLE_SEND_VOICE_FEATURE_PREFERENCE_KEY, false);
    }

    /**
     * Update the notification ringtone
     *
     * @param context the context
     * @param uri     the new notification ringtone, or null for no RingTone
     */
    public static void setNotificationRingTone(Context context, @Nullable Uri uri) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();

        String value = "";

        if (null != uri) {
            value = uri.toString();

            if (value.startsWith("file://")) {
                // it should never happen
                // else android.os.FileUriExposedException will be triggered.
                // see https://github.com/vector-im/riot-android/issues/1725
                return;
            }
        }

        editor.putString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, value);
        editor.apply();
    }

    /**
     * Provides the selected notification ring tone
     *
     * @param context the context
     * @return the selected ring tone or null for no RingTone
     */
    @Nullable
    public static Uri getNotificationRingTone(Context context) {
        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(SETTINGS_NOTIFICATION_RINGTONE_PREFERENCE_KEY, null);

        // the user selects "None"
        if (TextUtils.equals(url, "")) {
            return null;
        }

        Uri uri = null;

        // https://github.com/vector-im/riot-android/issues/1725
        if ((null != url) && !url.startsWith("file://")) {
            try {
                uri = Uri.parse(url);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getNotificationRingTone() : Uri.parse failed", e);
            }
        }

        if (null == uri) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        Log.d(LOG_TAG, "## getNotificationRingTone() returns " + uri);
        return uri;
    }

    /**
     * Provide the notification ringtone filename
     *
     * @param context the context
     * @return the filename or null if "None" is selected
     */
    @Nullable
    public static String getNotificationRingToneName(Context context) {
        Uri toneUri = getNotificationRingTone(context);

        if (null == toneUri) {
            return null;
        }

        String name = null;

        Cursor cursor = null;

        try {
            String[] proj = {MediaStore.Audio.Media.DATA};
            cursor = context.getContentResolver().query(toneUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            cursor.moveToFirst();

            File file = new File(cursor.getString(column_index));
            name = file.getName();

            if (name.contains(".")) {
                name = name.substring(0, name.lastIndexOf("."));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getNotificationRingToneName() failed() : " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return name;
    }

    /**
     * Enable or disable the lazy loading
     *
     * @param context  the context
     * @param newValue true to enable lazy loading, false to disable it
     */
    public static void setUseLazyLoading(Context context, boolean newValue) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_LAZY_LOADING_PREFERENCE_KEY, newValue)
                .apply();
    }

    /**
     * Tells if the lazy loading is enabled
     *
     * @param context the context
     * @return true if the lazy loading of room members is enabled
     */
    public static boolean useLazyLoading(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_LAZY_LOADING_PREFERENCE_KEY, false);
    }

    /**
     * User explicitly refuses the lazy loading.
     *
     * @param context the context
     */
    public static void setUserRefuseLazyLoading(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_USER_REFUSED_LAZY_LOADING_PREFERENCE_KEY, true)
                .apply();
    }

    /**
     * Tells if the user has explicitly refused the lazy loading
     *
     * @param context the context
     * @return true if the user has explicitly refuse the lazy loading of room members
     */
    public static boolean hasUserRefusedLazyLoading(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USER_REFUSED_LAZY_LOADING_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the data save mode is enabled
     *
     * @param context the context
     * @return true if the data save mode is enabled
     */
    public static boolean useDataSaveMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DATA_SAVE_MODE_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the conf calls must be done with Jitsi.
     *
     * @param context the context
     * @return true if the conference call must be done with jitsi.
     */
    public static boolean useJitsiConfCall(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USE_JITSI_CONF_PREFERENCE_KEY, false);
    }

    /**
     * Tells if the application is started on boot
     *
     * @param context the context
     * @return true if the application must be started on boot (defaultValue = true)
     */
    public static boolean autoStartOnBoot(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, true);
    }

    /**
     * Tells if the application is started on boot
     *
     * @param context the context
     * @param value   true to start the application on boot
     */
    public static void setAutoStartOnBoot(Context context, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_START_ON_BOOT_PREFERENCE_KEY, value)
                .apply();
    }

    /**
     * Provides the selected saving period.
     *
     * @param context the context
     * @return the selected period
     */
    public static int getSelectedMediasSavingPeriod(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, MEDIA_SAVING_1_WEEK);
    }

    /**
     * Updates the selected saving period.
     *
     * @param context the context
     * @param index   the selected period index
     */
    public static void setSelectedMediasSavingPeriod(Context context, int index) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(SETTINGS_MEDIA_SAVING_PERIOD_SELECTED_KEY, index)
                .apply();
    }

    /**
     * Provides the minimum last access time to keep a media file.
     *
     * @param context the context
     * @return the min last access time (in seconds)
     */
    public static long getMinMediasLastAccessTime(Context context) {
        int selection = getSelectedMediasSavingPeriod(context);

        switch (selection) {
            case MEDIA_SAVING_3_DAYS:
                return (System.currentTimeMillis() / 1000) - (3 * 24 * 60 * 60);
            case MEDIA_SAVING_1_WEEK:
                return (System.currentTimeMillis() / 1000) - (7 * 24 * 60 * 60);
            case MEDIA_SAVING_1_MONTH:
                return (System.currentTimeMillis() / 1000) - (30 * 24 * 60 * 60);
            case MEDIA_SAVING_FOREVER:
                return 0;
        }

        return 0;
    }

    /**
     * Provides the selected saving period.
     *
     * @param context the context
     * @return the selected period
     */
    public static String getSelectedMediasSavingPeriodString(Context context) {
        int selection = getSelectedMediasSavingPeriod(context);

        switch (selection) {
            case MEDIA_SAVING_3_DAYS:
                return context.getString(R.string.media_saving_period_3_days);
            case MEDIA_SAVING_1_WEEK:
                return context.getString(R.string.media_saving_period_1_week);
            case MEDIA_SAVING_1_MONTH:
                return context.getString(R.string.media_saving_period_1_month);
            case MEDIA_SAVING_FOREVER:
                return context.getString(R.string.media_saving_period_forever);
        }
        return "?";
    }

    /**
     * Fix some migration issues
     */
    public static void fixMigrationIssues(Context context) {
        // Notification Privacy: the REDUCED privacy mode is removed in Tchap.
        final PushManager pushManager = Matrix.getInstance(context).getPushManager();
        PushManager.NotificationPrivacy notificationPrivacy = pushManager.getNotificationPrivacy();
        if (notificationPrivacy == PushManager.NotificationPrivacy.REDUCED) {
            // We fallback to the LOW_DETAIL mode by default
            pushManager.setNotificationPrivacy(PushManager.NotificationPrivacy.LOW_DETAIL, null);
            // and reset the "IgnoreBatteryOptimization" to invite again the user to use the NORMAL
            // mode on VectorHomeActivity resume.
            PreferencesManager.resetDidAskUserToIgnoreBatteryOptimizations(context);
        }
    }

    /**
     * Tells if the markdown is enabled
     *
     * @param context the context
     * @return true if the markdown is enabled
     */
    public static boolean isMarkdownEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_ENABLE_MARKDOWN_KEY, true);
    }

    /**
     * Update the markdown enable status.
     *
     * @param context   the context
     * @param isEnabled true to enable the markdown
     */
    public static void setMarkdownEnabled(Context context, boolean isEnabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_ENABLE_MARKDOWN_KEY, isEnabled)
                .apply();
    }

    /**
     * Tells if the read receipts should be shown
     *
     * @param context the context
     * @return true if the read receipts should be shown
     */
    public static boolean showReadReceipts(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_SHOW_READ_RECEIPTS_KEY, true);
    }

    /**
     * Tells if the message timestamps must be always shown
     *
     * @param context the context
     * @return true if the message timestamps must be always shown
     */
    public static boolean alwaysShowTimeStamps(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_ALWAYS_SHOW_TIMESTAMPS_KEY, false);
    }

    /**
     * Tells if the typing notifications should be sent
     *
     * @param context the context
     * @return true to send the typing notifs
     */
    public static boolean sendTypingNotifs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_SEND_TYPING_NOTIF_KEY, true);
    }

    /**
     * Tells of the missing notifications rooms must be displayed at left (home screen)
     *
     * @param context the context
     * @return true to move the missed notifications to the left side
     */
    public static boolean pinMissedNotifications(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_PIN_MISSED_NOTIFICATIONS_PREFERENCE_KEY, true);
    }

    /**
     * Tells of the unread rooms must be displayed at left (home screen)
     *
     * @param context the context
     * @return true to move the unread room to the left side
     */
    public static boolean pinUnreadMessages(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_PIN_UNREAD_MESSAGES_PREFERENCE_KEY, true);
    }

    /**
     * Tells if the phone must vibrate when mentioning
     *
     * @param context the context
     * @return true
     */
    public static boolean vibrateWhenMentioning(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_VIBRATE_ON_MENTION_KEY, false);
    }

    /**
     * Tells if a dialog has been displayed to ask to use the analytics tracking (piwik, matomo, etc.).
     *
     * @param context the context
     * @return true if a dialog has been displayed to ask to use the analytics tracking
     */
    public static boolean didAskToUseAnalytics(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DID_ASK_TO_USE_ANALYTICS_TRACKING_KEY, false);
    }

    /**
     * To call if the user has been asked for analytics tracking.
     *
     * @param context the context
     */
    public static void setDidAskToUseAnalytics(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(DID_ASK_TO_USE_ANALYTICS_TRACKING_KEY, true)
                .apply();
    }

    /**
     * Tells if the analytics tracking is authorized (piwik, matomo, etc.).
     *
     * @param context the context
     * @return true if the analytics tracking is authorized
     */
    public static boolean useAnalytics(Context context) {
        // Disable Analytics tracking until Tchap defines its own instance
        // Ignore the current value if any
        // return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USE_ANALYTICS_KEY, false);
        return false;
    }

    /**
     * Enable or disable the analytics tracking.
     *
     * @param context      the context
     * @param useAnalytics true to enable the analytics tracking
     */
    public static void setUseAnalytics(Context context, boolean useAnalytics) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_USE_ANALYTICS_KEY, useAnalytics)
                .apply();
    }

    /**
     * Tells if media should be previewed before sending
     *
     * @param context the context
     * @return true to preview media
     */
    public static boolean previewMediaWhenSending(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_PREVIEW_MEDIA_BEFORE_SENDING_KEY, false);
    }

    /**
     * Tells if the rage shake is used.
     *
     * @param context the context
     * @return true if the rage shake is used
     */
    public static boolean useRageshake(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_USE_RAGE_SHAKE_KEY, true);
    }

    /**
     * Update the rage shake  status.
     *
     * @param context   the context
     * @param isEnabled true to enable the rage shake
     */
    public static void setUseRageshake(Context context, boolean isEnabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SETTINGS_USE_RAGE_SHAKE_KEY, isEnabled)
                .apply();
    }

    /**
     * Tells if all the events must be displayed ie even the redacted events.
     *
     * @param context the context
     * @return true to display all the events even the redacted ones.
     */
    public static boolean displayAllEvents(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTINGS_DISPLAY_ALL_EVENTS_KEY, false);
    }
}
