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

package im.vector;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequestCancellation;
import org.matrix.androidsdk.crypto.RoomKeysRequestListener;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.metrics.MetricsListener;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.core.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.gouv.tchap.media.MediaScanManager;
import fr.gouv.tchap.sdk.rest.client.TchapValidityRestClient;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.SplashActivity;
import im.vector.analytics.MetricsListenerProxy;
import im.vector.push.PushManager;
import im.vector.services.EventStreamService;
import im.vector.store.LoginStorage;
import im.vector.util.PreferencesManager;
import im.vector.widgets.WidgetsManager;
import io.realm.Realm;

/**
 * Singleton to control access to the Matrix SDK and providing point of control for MXSessions.
 */
public class Matrix {

    // Additional Matrix error:
    public static final String EXPIRED_ACCOUNT = "ORG_MATRIX_EXPIRED_ACCOUNT";

    // Set to true to enable local file encryption
    private static final boolean CONFIG_ENABLE_LOCAL_FILE_ENCRYPTION = false;

    // the log tag
    private static final String LOG_TAG = Matrix.class.getSimpleName();

    // static instance
    private static Matrix instance = null;

    // the application context
    private final Context mAppContext;

    // login storage
    private final LoginStorage mLoginStorage;

    // list of session
    private List<MXSession> mMXSessions;

    // Push manager
    private final PushManager mPushManager;

    // list of store : some sessions or activities use tmp stores
    // provide an storage to exchange them
    private List<IMXStore> mTmpStores;

    // tell if the client should be logged out
    public boolean mHasBeenDisconnected = false;

    // The potential dialog used to alert an expired account.
    private AlertDialog mExpiredAccountDialog = null;

    // i.e the event has been read from another client
    private static final MXEventListener mLiveEventListener = new MXEventListener() {
        boolean mClearCacheRequired = false;

        @Override
        public void onIgnoredUsersListUpdate() {
            // the application cache will be cleared at next launch if the application is not yet launched
            mClearCacheRequired = true;
        }

        private boolean mRefreshUnreadCounter = false;

        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            mRefreshUnreadCounter |= Event.EVENT_TYPE_MESSAGE.equals(event.getType()) || Event.EVENT_TYPE_RECEIPT.equals(event.getType());

            // TODO update to manage multisessions
            WidgetsManager.getSharedInstance().onLiveEvent(instance.getDefaultSession(), event);
        }

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            // when the client does not use FCM (ie. FDroid),
            // we need to compute the application badge values

            if ((null != instance) && (null != instance.mMXSessions)) {
                if (mClearCacheRequired && !VectorApp.isAppInBackground()) {
                    mClearCacheRequired = false;
                    instance.reloadSessions(VectorApp.getInstance());
                } else if (mRefreshUnreadCounter) {
                    PushManager pushManager = instance.getPushManager();

                    // perform update: if the FCM is not yet available or if FCM registration failed
                    if ((null != pushManager) && (!pushManager.useFcm() || !pushManager.hasRegistrationToken())) {
                        int roomCount = 0;

                        for (MXSession session : instance.mMXSessions) {
                            if (session.isAlive()) {
                                BingRulesManager bingRulesManager = session.getDataHandler().getBingRulesManager();
                                Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

                                for (Room room : rooms) {
                                    if (room.isInvited()) {
                                        roomCount++;
                                    } else {
                                        int notificationCount = room.getNotificationCount();

                                        if (bingRulesManager.isRoomMentionOnly(room.getRoomId())) {
                                            notificationCount = room.getHighlightCount();
                                        }

                                        if (notificationCount > 0) {
                                            roomCount++;
                                        }
                                    }
                                }
                            }
                        }

                        // update the badge counter
                        CommonActivityUtils.updateBadgeCount(instance.mAppContext, roomCount);
                    }
                }

                // TODO find a way to detect which session is synced
                VectorApp.clearSyncingSessions();
            }

            mRefreshUnreadCounter = false;

            Log.d(LOG_TAG, "onLiveEventsChunkProcessed ");
            EventStreamService.checkDisplayedNotifications();
        }
    };

    // constructor
    private Matrix(Context appContext) {
        instance = this;

        mAppContext = appContext.getApplicationContext();
        mLoginStorage = new LoginStorage(mAppContext);
        mMXSessions = new ArrayList<>();
        mTmpStores = new ArrayList<>();

        mPushManager = new PushManager(mAppContext);
    }

    /**
     * Retrieve the static instance.
     * Create it if it does not exist yet.
     *
     * @param appContext the application context
     * @return the shared instance
     */
    public synchronized static Matrix getInstance(Context appContext) {
        if (instance == null && null != appContext) {
            instance = new Matrix(appContext);

            // Update the configuration error codes.
            // EXPIRED_ACCOUNT: the account validity expired, the user should have received an email to renew his validity.
            MatrixError.mConfigurationErrorCodes.add(EXPIRED_ACCOUNT);

            // Add a RoomSummary listener to override the method used to handle the last message of the rooms.
            RoomSummary.setRoomSummaryListener(new RoomSummary.RoomSummaryListener() {
                @Override
                public boolean isSupportedEvent(Event event) {
                    // Consider first the default implementation
                    // Note: display name and avatar change are already ignored by this implementation.
                    boolean isSupported = RoomSummary.isSupportedEventDefaultImplementation(event);

                    // If the event is seen as supported by the default implementation,
                    // Check whether the user wants to hide the join and leave events
                    if (isSupported && !PreferencesManager.showJoinLeaveMessages(appContext)) {
                        String type = event.getType();
                        if (TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_MEMBER, type)) {
                            RoomMember roomMember = JsonUtils.toRoomMember(event.getContent());
                            String membership = roomMember.membership;

                            if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {
                                // Check whether this is an actual leave or join event
                                // (in this case the membership has changed compare to the prev-content).
                                EventContent prevEventContent = event.getPrevContent();
                                if ((null != prevEventContent)) {
                                    String prevMembership = prevEventContent.membership;
                                    isSupported = TextUtils.equals(prevMembership, membership);
                                } else {
                                    isSupported = false;
                                }
                            }
                        }
                    }
                    return isSupported;
                }
            });
        }
        return instance;
    }

    /**
     * @return the loginstorage
     */
    public LoginStorage getLoginStorage() {
        return mLoginStorage;
    }

    /**
     * @return the application name
     */
    public static String getApplicationName() {
        return instance.mAppContext.getApplicationInfo().loadLabel(instance.mAppContext.getPackageManager()).toString();
    }

    /**
     * Provides the application version
     *
     * @param longformat     true to append the build time
     * @param useBuildNumber true to replace the git version by the build number
     * @return the application version.
     */
    public String getVersion(boolean longformat, boolean useBuildNumber) {
        String versionName = "";
        String flavor = "";

        try {
            PackageInfo pInfo = mAppContext.getPackageManager().getPackageInfo(mAppContext.getPackageName(), 0);
            versionName = pInfo.versionName;

            flavor = BuildConfig.SHORT_FLAVOR_DESCRIPTION;

            if (!TextUtils.isEmpty(flavor)) {
                flavor += "-";
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## versionName() : failed " + e.getMessage(), e);
        }

        String gitVersion = mAppContext.getString(R.string.git_revision);
        String buildNumber = mAppContext.getString(R.string.build_number);

        if ((useBuildNumber) && !TextUtils.equals(buildNumber, "0")) {
            gitVersion = "b" + buildNumber;
            longformat = false;
        }

        if (longformat) {
            String date = mAppContext.getString(R.string.git_revision_date);
            versionName += " (" + flavor + gitVersion + "-" + date + ")";
        } else {
            versionName += " (" + flavor + gitVersion + ")";
        }

        return versionName;
    }

    /**
     * Static method top the MXSession list
     *
     * @param context the application content
     * @return the sessions list
     */
    public static List<MXSession> getMXSessions(Context context) {
        if ((null != context) && (null != instance)) {
            return instance.getSessions();
        } else {
            return null;
        }
    }

    /**
     * @return The list of sessions
     */
    public List<MXSession> getSessions() {
        List<MXSession> sessions = new ArrayList<>();

        synchronized (LOG_TAG) {
            if (null != mMXSessions) {
                sessions = new ArrayList<>(mMXSessions);
            }
        }

        return sessions;
    }

    /**
     * Retrieve the default session if one exists.
     * <p>
     * The default session may be user-configured, or it may be the last session the user was using.
     *
     * @return The default session or null.
     */
    public synchronized MXSession getDefaultSession() {
        List<MXSession> sessions = getSessions();

        if (sessions.size() > 0) {
            return sessions.get(0);
        }

        List<HomeServerConnectionConfig> hsConfigList = mLoginStorage.getCredentialsList();

        // any account ?
        if ((hsConfigList == null) || (hsConfigList.size() == 0)) {
            return null;
        }

        boolean appDidCrash = VectorApp.getInstance().didAppCrash();

        Set<String> matrixIds = new HashSet<>();
        sessions = new ArrayList<>();

        for (HomeServerConnectionConfig config : hsConfigList) {
            // avoid duplicated accounts.
            // null userId has been reported by GA
            if (config.getCredentials() != null && !TextUtils.isEmpty(config.getCredentials().userId) && !matrixIds.contains(config.getCredentials().userId)) {
                MXSession session = createSession(config);

                // if the application crashed
                if (appDidCrash) {
                    // clear the session data
                    session.clear(VectorApp.getInstance());
                    // and open it again
                    session = createSession(config);
                }

                sessions.add(session);
                matrixIds.add(config.getCredentials().userId);
            }
        }

        synchronized (LOG_TAG) {
            mMXSessions = sessions;
        }

        if (0 == sessions.size()) {
            return null;
        }

        return sessions.get(0);
    }

    /**
     * Static method to return a MXSession from an account Id.
     *
     * @param matrixId the matrix id
     * @return the MXSession.
     */
    public static MXSession getMXSession(Context context, String matrixId) {
        return Matrix.getInstance(context.getApplicationContext()).getSession(matrixId);
    }

    /**
     * Retrieve a session from an user Id.
     * The application should be able to manage multi session.
     *
     * @param matrixId the matrix id
     * @return the MXsession if it exists.
     */
    public synchronized MXSession getSession(String matrixId) {
        if (null != matrixId) {
            List<MXSession> sessions;

            synchronized (this) {
                sessions = getSessions();
            }

            for (MXSession session : sessions) {
                Credentials credentials = session.getCredentials();

                if ((null != credentials) && (credentials.userId.equals(matrixId))) {
                    return session;
                }
            }
        }

        return getDefaultSession();
    }

    /**
     * Add an error listener to each sessions
     *
     * @param activity the activity.
     */
    public static void setSessionErrorListener(Activity activity) {
        if ((null != instance) && (null != activity)) {
            Collection<MXSession> sessions = getMXSessions(activity);

            for (MXSession session : sessions) {
                if (session.isAlive()) {
                    session.setFailureCallback(new ErrorListener(session, activity));
                }
            }
        }
    }

    /**
     * Remove the sessions error listener to each
     */
    public static void removeSessionErrorListener(Activity activity) {
        if ((null != instance) && (null != activity)) {
            Collection<MXSession> sessions = getMXSessions(activity);

            for (MXSession session : sessions) {
                if (session.isAlive()) {
                    session.setFailureCallback(null);
                }
            }
        }
    }

    /**
     * Return the used media caches.
     * This class can inherited to customized it.
     *
     * @return the mediaCache.
     */
    public MXMediaCache getMediaCache() {
        if (getSessions().size() > 0) {
            return getSessions().get(0).getMediaCache();
        }
        return null;
    }

    /**
     * Return the used latestMessages caches.
     * This class can inherited to customized it.
     *
     * @return the latest messages cache.
     */
    public MXLatestChatMessageCache getDefaultLatestChatMessageCache() {
        if (getSessions().size() > 0) {
            return getSessions().get(0).getLatestChatMessageCache();
        }
        return null;
    }

    /**
     * @return true if the matrix client instance defines a valid session
     */
    public static boolean hasValidSessions() {
        if (null == instance) {
            Log.e(LOG_TAG, "hasValidSessions : has no instance");
            return false;
        }

        boolean res;

        synchronized (LOG_TAG) {
            res = (null != instance.mMXSessions) && (instance.mMXSessions.size() > 0);

            if (!res) {
                Log.e(LOG_TAG, "hasValidSessions : has no session");
            } else {
                for (MXSession session : instance.mMXSessions) {
                    // some GA issues reported that the data handler can be null
                    // so assume the application should be restarted
                    res &= session.isAlive() && (null != session.getDataHandler());
                }

                if (!res) {
                    Log.e(LOG_TAG, "hasValidSessions : one sesssion has no valid data handler");
                }
            }
        }

        return res;
    }

    //==============================================================================================================
    // Session management
    //==============================================================================================================

    /**
     * Deactivate a session.
     *
     * @param context       the context.
     * @param session       the session to deactivate.
     * @param userPassword  the user password
     * @param eraseUserData true to also erase all the user data
     * @param aCallback     the success and failure callback
     */
    public void deactivateSession(final Context context,
                                  final MXSession session,
                                  final String userPassword,
                                  final boolean eraseUserData,
                                  final @NonNull ApiCallback<Void> aCallback) {
        Log.d(LOG_TAG, "## deactivateSession() " + session.getMyUserId());

        session.deactivateAccount(context, userPassword, eraseUserData, new SimpleApiCallback<Void>(aCallback) {
            @Override
            public void onSuccess(Void info) {
                mLoginStorage.removeCredentials(session.getHomeServerConfig());

                session.getDataHandler().removeListener(mLiveEventListener);

                VectorApp.removeSyncingSession(session);

                synchronized (LOG_TAG) {
                    mMXSessions.remove(session);
                }

                aCallback.onSuccess(info);
            }
        });
    }

    /**
     * Clear a session.
     *
     * @param context          the context.
     * @param session          the session to clear.
     * @param clearCredentials true to clear the credentials.
     */
    public synchronized void clearSession(final Context context,
                                          final MXSession session,
                                          final boolean clearCredentials,
                                          final ApiCallback<Void> aCallback) {
        if (!session.isAlive()) {
            Log.e(LOG_TAG, "## clearSession() " + session.getMyUserId() + " is already released");
            return;
        }

        Log.d(LOG_TAG, "## clearSession() " + session.getMyUserId() + " clearCredentials " + clearCredentials);

        if (clearCredentials) {
            mLoginStorage.removeCredentials(session.getHomeServerConfig());
        }

        session.getDataHandler().removeListener(mLiveEventListener);

        // Clear media scan database
        // TODO The media scan database clear should be handled during the media cache clear when the MediaScanManager will be moved into the sdk.
        Realm realm = Realm.getDefaultInstance();
        MediaScanManager mediaScanManager = new MediaScanManager(session.getMediaScanRestClient(), realm);
        mediaScanManager.clearAntiVirusScanResults();
        realm.close();

        ApiCallback<Void> callback = new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                VectorApp.removeSyncingSession(session);

                synchronized (LOG_TAG) {
                    mMXSessions.remove(session);
                }

                if (null != aCallback) {
                    aCallback.onSuccess(null);
                }
            }
        };

        if (clearCredentials) {
            session.logout(context, callback);
        } else {
            session.clear(context, callback);
        }
    }

    /**
     * Clear any existing session.
     *
     * @param context          the context.
     * @param clearCredentials true to clear the credentials.
     */
    public synchronized void clearSessions(Context context, boolean clearCredentials, ApiCallback<Void> callback) {
        List<MXSession> sessions;

        synchronized (LOG_TAG) {
            sessions = new ArrayList<>(mMXSessions);
        }

        clearSessions(context, sessions.iterator(), clearCredentials, callback);
    }

    /**
     * Internal routine to clear the sessions data
     *
     * @param context          the context
     * @param iterator         the sessions iterator
     * @param clearCredentials true to clear the credentials.
     * @param callback         the asynchronous callback
     */
    private synchronized void clearSessions(final Context context,
                                            final Iterator<MXSession> iterator,
                                            final boolean clearCredentials,
                                            final ApiCallback<Void> callback) {
        if (!iterator.hasNext()) {
            if (null != callback) {
                callback.onSuccess(null);
            }
            return;
        }

        clearSession(context, iterator.next(), clearCredentials, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                clearSessions(context, iterator, clearCredentials, callback);
            }
        });

    }

    /**
     * Set a default session.
     *
     * @param session The session to store as the default session.
     */
    public synchronized void addSession(MXSession session) {
        mLoginStorage.addCredentials(session.getHomeServerConfig());
        synchronized (LOG_TAG) {
            mMXSessions.add(session);
        }
    }

    /**
     * Creates an MXSession from some credentials.
     *
     * @param hsConfig The HomeserverConnectionConfig to create a session from.
     * @return The session.
     */
    public MXSession createSession(HomeServerConnectionConfig hsConfig) {
        return createSession(mAppContext, hsConfig);
    }

    /**
     * Creates an MXSession from some credentials.
     *
     * @param context  the context.
     * @param hsConfig The HomeserverConnectionConfig to create a session from.
     * @return The session.
     */
    private MXSession createSession(final Context context, HomeServerConnectionConfig hsConfig) {
        MXFileStore store;

        final MetricsListener metricsListener = new MetricsListenerProxy(VectorApp.getInstance().getAnalytics());
        final Credentials credentials = hsConfig.getCredentials();

        /*if (true) {*/
        store = new MXFileStore(hsConfig, CONFIG_ENABLE_LOCAL_FILE_ENCRYPTION, context);
        store.setMetricsListener(metricsListener);

        /*} else {
            store = new MXMemoryStore(hsConfig.getCredentials(), context);
        }*/

        final MXDataHandler dataHandler = new MXDataHandler(store, credentials);
        store.setDataHandler(dataHandler);
        dataHandler.setLazyLoadingEnabled(PreferencesManager.useLazyLoading(context));

        final MXSession session = new MXSession.Builder(hsConfig, dataHandler, context)
                .withPushServerUrl(context.getString(R.string.push_server_url))
                .withMetricsListener(metricsListener)
                .withFileEncryption(CONFIG_ENABLE_LOCAL_FILE_ENCRYPTION)
                .build();

        // Turn on the anti-virus server
        session.getContentManager().configureAntiVirusScanner(true);

        dataHandler.setMetricsListener(metricsListener);
        dataHandler.setRequestNetworkErrorListener(new MXDataHandler.RequestNetworkErrorListener() {

            @Override
            public void onConfigurationError(String matrixErrorCode) {
                Log.e(LOG_TAG, "## createSession() : onConfigurationError " + matrixErrorCode);

                if (TextUtils.equals(matrixErrorCode, MatrixError.UNKNOWN_TOKEN)) {
                    if (null != VectorApp.getCurrentActivity()) {
                        Log.e(LOG_TAG, "## createSession() : onTokenCorrupted");
                        CommonActivityUtils.logout(VectorApp.getCurrentActivity());
                    }
                } else if (TextUtils.equals(matrixErrorCode, EXPIRED_ACCOUNT)) {
                    instance.suspendTchapOnExpiredAccount(VectorApp.getInstance());
                }
            }

            @Override
            public void onSSLCertificateError(UnrecognizedCertificateException unrecCertEx) {
                if (null != VectorApp.getCurrentActivity()) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                    Log.d(LOG_TAG, "## createSession() : Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());

                    UnrecognizedCertHandler.show(session.getHomeServerConfig(), fingerprint, true, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            LoginStorage loginStorage = Matrix.getInstance(VectorApp.getInstance().getApplicationContext()).getLoginStorage();
                            loginStorage.replaceCredentials(session.getHomeServerConfig());
                        }

                        @Override
                        public void onIgnore() {
                            // nothing to do
                        }

                        @Override
                        public void onReject() {
                            Log.d(LOG_TAG, "Found fingerprint: reject fingerprint");
                            CommonActivityUtils.logout(VectorApp.getCurrentActivity(), Arrays.asList(session), true, null);
                        }
                    });
                }

            }
        });

        // if a device id is defined, enable the encryption
        if (!TextUtils.isEmpty(credentials.deviceId)) {
            session.enableCryptoWhenStarting();
        }

        dataHandler.addListener(mLiveEventListener);
        dataHandler.addListener(VectorApp.getInstance().getDecryptionFailureTracker());

        session.setUseDataSaveMode(PreferencesManager.useDataSaveMode(context));

        dataHandler.addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                if (null != session.getCrypto()) {
                    session.getCrypto().addRoomKeysRequestListener(new RoomKeysRequestListener() {
                        @Override
                        public void onRoomKeyRequest(IncomingRoomKeyRequest request) {
                            KeyRequestHandler.getSharedInstance().handleKeyRequest(request);
                        }

                        @Override
                        public void onRoomKeyRequestCancellation(IncomingRoomKeyRequestCancellation request) {
                            KeyRequestHandler.getSharedInstance().handleKeyRequestCancellation(request);
                        }
                    });
                }
            }
        });

        return session;
    }

    /**
     * Reload the matrix sessions.
     * The session caches are cleared before being reloaded.
     * Any opened activity is closed and the application switches to the splash screen.
     *
     * @param context the context
     */
    public void reloadSessions(final Context context) {
        Log.e(LOG_TAG, "## reloadSessions");

        CommonActivityUtils.logout(context, getMXSessions(context), false, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                synchronized (LOG_TAG) {
                    // build a new sessions list
                    List<HomeServerConnectionConfig> configs = mLoginStorage.getCredentialsList();

                    for (HomeServerConnectionConfig config : configs) {
                        MXSession session = createSession(config);
                        mMXSessions.add(session);
                    }
                }

                // clear FCM token before launching the splash screen
                Matrix.getInstance(context).getPushManager().clearFcmData(new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(final Void anything) {
                        launchSplashScreen(context);
                    }
                });
            }
        });
    }

    /**
     * Launch the splash screen to reload the session.
     */
    private void launchSplashScreen(final Context context) {
        Intent intent = new Intent(context.getApplicationContext(), SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.getApplicationContext().startActivity(intent);

        if (null != VectorApp.getCurrentActivity()) {
            VectorApp.getCurrentActivity().finish();
        }
    }

    /**
     * Suspend the Tchap application use when the account is expired.
     * The session caches are cleared, and any opened activity is closed.
     * The credentials are kept and reused when the account validity is renewed.
     *
     * @param context the context
     */
    private void suspendTchapOnExpiredAccount(final Context context) {
        Log.i(LOG_TAG, "## suspendTchapOnExpiredAccount");

        // Check whether the application is actually running and available.
        // Check "isAlive" flag to know whether a logout is in progress on the current session.
        final MXSession currentSession = getDefaultSession();
        final Activity currentActivity = VectorApp.getCurrentActivity();
        if (currentSession == null || !currentSession.isAlive() || currentActivity == null) {
            Log.w(LOG_TAG, "## suspendTchapOnExpiredAccount: ignored (app busy)");
            return;
        }

        // If a dialog already exists, check whether it is displayed on the current activity.
        // Dismiss it if this is not the case.
        if (mExpiredAccountDialog != null && mExpiredAccountDialog.getOwnerActivity() != currentActivity)
        {
            Log.i(LOG_TAG, "## suspendTchapOnExpiredAccount: dismiss existing dialog");
            mExpiredAccountDialog.dismiss();
        }

        // Check whether the expired account is not already handled.
        if (mExpiredAccountDialog == null) {
            CommonActivityUtils.logout(context, getMXSessions(context), false, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    synchronized (LOG_TAG) {
                        // build a new sessions list
                        List<HomeServerConnectionConfig> configs = mLoginStorage.getCredentialsList();

                        for (HomeServerConnectionConfig config : configs) {
                            MXSession session = createSession(config);
                            mMXSessions.add(session);
                        }
                    }

                    // Clear FCM token before prompting the user about his account validity
                    Matrix.getInstance(context).getPushManager().clearFcmData(new SimpleApiCallback<Void>() {
                        @Override
                        public void onSuccess(final Void anything) {
                            displayExpiredAccountDialog(context);
                        }
                    });
                }
            });
        } else {
            Log.w(LOG_TAG, "## suspendTchapOnExpiredAccount: ignored (already handled)");
        }
    }

    /**
     * Prompt the user to renew his account validity by checking his emails, or requesting a renewal email.
     *
     * @param context the context
     */
    private void displayExpiredAccountDialog(final Context context) {
        final Activity currentActivity = VectorApp.getCurrentActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);

        mExpiredAccountDialog = builder
                .setMessage(R.string.tchap_expired_account_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.tchap_expired_account_resume_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        launchSplashScreen(context);
                    }
                })
                .setNeutralButton(R.string.tchap_request_renewal_email_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TchapValidityRestClient validityRestClient = new TchapValidityRestClient(getDefaultSession().getHomeServerConfig());
                        validityRestClient.requestRenewalEmail(new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                // Just log the information
                                Log.i(LOG_TAG, "a renewal email has been requested");
                                onRequestedRenewalEmail(context);
                            }

                            private void onError(final String errorMessage) {
                                Log.e(LOG_TAG, "request for a renewal email failed " + errorMessage);
                                displayExpiredAccountDialog(context);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onError(e.getLocalizedMessage());
                            }
                        });
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mExpiredAccountDialog = null;
                    }
                })
                .show();

        mExpiredAccountDialog.setOwnerActivity(currentActivity);
    }

    /**
     * Prompt the user to renew his account validity by checking his emails.
     *
     * @param context the context
     */
    private void onRequestedRenewalEmail(final Context context) {
        final Activity currentActivity = VectorApp.getCurrentActivity();
        final AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);

        mExpiredAccountDialog = builder
                .setMessage(R.string.tchap_expired_account_on_new_sent_email_msg)
                .setPositiveButton(R.string.tchap_expired_account_resume_button, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mExpiredAccountDialog = null;

                        // Relaunch the app
                        launchSplashScreen(context);
                    }
                })
                .show();

        mExpiredAccountDialog.setOwnerActivity(currentActivity);
    }

    /**
     * Handle the case where the application destroys the activity on which the expired account
     * dialog is displayed.
     *
     *  @param activity the destroyed activity
     */
    public void dismissExpiredAccountDialogIfAnyOnActivityDestroyed(Activity activity) {
        if (mExpiredAccountDialog != null) {
            if (mExpiredAccountDialog.getOwnerActivity() == activity) {
                mExpiredAccountDialog.dismiss();
            }
        }
    }

    /**
     * @return the push manager
     */
    public PushManager getPushManager() {
        return mPushManager;
    }

    //==============================================================================================================
    // Push rules management
    //==============================================================================================================

    /**
     * Refresh the sessions push rules.
     */
    public void refreshPushRules() {
        List<MXSession> sessions;

        synchronized (this) {
            sessions = getSessions();
        }

        for (MXSession session : sessions) {
            if (null != session.getDataHandler()) {
                session.getDataHandler().refreshPushRules();
            }
        }
    }

    //==============================================================================================================
    // Network events manager
    //==============================================================================================================

    /**
     * Add a network event listener.
     *
     * @param networkEventListener the event listener to add
     */
    public void addNetworkEventListener(final IMXNetworkEventListener networkEventListener) {
        if ((null != getDefaultSession()) && (null != networkEventListener)) {
            getDefaultSession().getNetworkConnectivityReceiver().addEventListener(networkEventListener);
        }
    }

    /**
     * Remove a network event listener.
     *
     * @param networkEventListener the event listener to remove
     */
    public void removeNetworkEventListener(final IMXNetworkEventListener networkEventListener) {
        if ((null != getDefaultSession()) && (null != networkEventListener)) {
            getDefaultSession().getNetworkConnectivityReceiver().removeEventListener(networkEventListener);
        }
    }

    /**
     * @return true if the device is connected to a data network
     */
    public boolean isConnected() {
        if (null != getDefaultSession()) {
            return getDefaultSession().getNetworkConnectivityReceiver().isConnected();
        }

        return true;
    }

    //==============================================================================================================
    // Tmp stores list management
    //==============================================================================================================

    /**
     * Add a tmp IMXStore in the currently used stores list
     *
     * @param store the store
     * @return the store index
     */
    public int addTmpStore(IMXStore store) {
        // sanity check
        if (null != store) {
            int pos = mTmpStores.indexOf(store);

            if (pos < 0) {
                mTmpStores.add(store);
                pos = mTmpStores.indexOf(store);
            }

            return pos;
        }

        return -1;
    }

    /**
     * Return a tmp store.
     *
     * @param storeIndex the store index.
     * @return the store
     */
    public IMXStore getTmpStore(int storeIndex) {
        if ((0 <= storeIndex) && (storeIndex < mTmpStores.size())) {
            return mTmpStores.get(storeIndex);
        }

        return null;
    }

    /**
     * Clear the tmp stores list.
     */
    public void clearTmpStoresList() {
        mTmpStores = new ArrayList<>();
    }
}
