/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.systemui.statusbar;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;
import static com.android.systemui.statusbar.phone.StatusBar.DEBUG_MEDIA_FAKE_ARTWORK;
import static com.android.systemui.statusbar.phone.StatusBar.ENABLE_LOCKSCREEN_WALLPAPER;
import static com.android.systemui.statusbar.phone.StatusBar.SHOW_LOCKSCREEN_MEDIA_ARTWORK;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.WallpaperColors;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationStats;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.palette.graphics.Palette;

import com.android.internal.statusbar.NotificationVisibility;
import com.android.internal.util.crdroid.ImageHelper;
import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.media.MediaData;
import com.android.systemui.media.MediaDataManager;
import com.android.systemui.media.MediaFeatureFlag;
import com.android.systemui.media.SmartspaceMediaData;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.dagger.StatusBarModule;
import com.android.systemui.statusbar.notification.NotificationEntryListener;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.notifcollection.DismissedByUserStats;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.ScrimState;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.Utils;
import com.android.systemui.util.concurrency.DelayableExecutor;

import lineageos.providers.LineageSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dagger.Lazy;

/**
 * Handles tasks and state related to media notifications. For example, there is a 'current' media
 * notification, which this class keeps track of.
 */
public class NotificationMediaManager implements Dumpable, TunerService.Tunable , MediaDataManager.Listener {
    private static final String TAG = "NotificationMediaManager";
    public static final boolean DEBUG_MEDIA = false;

    private static final String LOCKSCREEN_MEDIA_METADATA =
            "lineagesecure:" + LineageSettings.Secure.LOCKSCREEN_MEDIA_METADATA;
    private static final String LOCKSCREEN_ALBUMART_FILTER =
            "system:" + Settings.System.LOCKSCREEN_ALBUMART_FILTER;
    private static final String LOCKSCREEN_MEDIA_BLUR =
            "system:" + Settings.System.LOCKSCREEN_MEDIA_BLUR;

    private static final String PREF_CUSTOM_COLOR = "monet_engine_custom_color";
    private static final String PREF_COLOR_OVERRIDE = "monet_engine_color_override";

    private static final String NOWPLAYING_SERVICE = "com.google.android.as";
    private final StatusBarStateController mStatusBarStateController
            = Dependency.get(StatusBarStateController.class);
    private final SysuiColorExtractor mColorExtractor = Dependency.get(SysuiColorExtractor.class);
    private final KeyguardStateController mKeyguardStateController = Dependency.get(
            KeyguardStateController.class);
    private final KeyguardBypassController mKeyguardBypassController;
    private static final HashSet<Integer> PAUSED_MEDIA_STATES = new HashSet<>();
    static {
        PAUSED_MEDIA_STATES.add(PlaybackState.STATE_NONE);
        PAUSED_MEDIA_STATES.add(PlaybackState.STATE_STOPPED);
        PAUSED_MEDIA_STATES.add(PlaybackState.STATE_PAUSED);
        PAUSED_MEDIA_STATES.add(PlaybackState.STATE_ERROR);
        PAUSED_MEDIA_STATES.add(PlaybackState.STATE_CONNECTING);
    }

    private final NotificationEntryManager mEntryManager;
    private final MediaDataManager mMediaDataManager;
    private final NotifPipeline mNotifPipeline;
    private final NotifCollection mNotifCollection;
    private final boolean mUsingNotifPipeline;
    private final boolean mIsMediaInQS;

    @Nullable
    private Lazy<NotificationShadeWindowController> mNotificationShadeWindowController;

    @Nullable
    private BiometricUnlockController mBiometricUnlockController;
    @Nullable
    private ScrimController mScrimController;
    @Nullable
    private LockscreenWallpaper mLockscreenWallpaper;

    private final DelayableExecutor mMainExecutor;

    private final Context mContext;
    private final ArrayList<MediaListener> mMediaListeners;
    private final Lazy<Optional<StatusBar>> mStatusBarOptionalLazy;
    private final MediaArtworkProcessor mMediaArtworkProcessor;
    private final Set<AsyncTask<?, ?, ?>> mProcessArtworkTasks = new ArraySet<>();
    private float mLockscreenMediaBlur;

    protected NotificationPresenter mPresenter;
    private MediaController mMediaController;
    private String mMediaNotificationKey;
    private MediaMetadata mMediaMetadata;

    private String mNowPlayingNotificationKey;
    private String mNowPlayingTrack;

    private BackDropView mBackdrop;
    private ImageView mBackdropFront;
    private ImageView mBackdropBack;

    private boolean mShowMediaMetadata;
    private int mAlbumArtFilter;
    
    private ContentResolver resolver;
    private boolean customColor;
    private boolean customColorIsSet;
    private boolean artworkColorUpdated;
    private boolean accentChanged = false;
    private boolean noChangeInPlaybackState;
    private int customColorTracker = 0;
    private int colorOverride;
    private int artwork_color;
    private Palette palette;
    private long current_medatada_update_millis;
    private long previous_medatada_update_millis = 0;

    private final MediaController.Callback mMediaListener = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            super.onPlaybackStateChanged(state);
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: onPlaybackStateChanged: " + state);
            }
            if (state != null) {
                if (!isPlaybackActive(state.getState())) {
                    clearCurrentMediaNotification();
                }
                findAndUpdateMediaNotifications();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: onMetadataChanged: " + metadata);
            }
            mMediaArtworkProcessor.clearCache();
            mMediaMetadata = metadata;
            dispatchUpdateMediaMetaData(true /* changed */, true /* allowAnimation */);
        }
    };

    /**
     * Injected constructor. See {@link StatusBarModule}.
     */
    public NotificationMediaManager(
            Context context,
            Lazy<Optional<StatusBar>> statusBarOptionalLazy,
            Lazy<NotificationShadeWindowController> notificationShadeWindowController,
            NotificationEntryManager notificationEntryManager,
            MediaArtworkProcessor mediaArtworkProcessor,
            KeyguardBypassController keyguardBypassController,
            NotifPipeline notifPipeline,
            NotifCollection notifCollection,
            FeatureFlags featureFlags,
            @Main DelayableExecutor mainExecutor,
            MediaDataManager mediaDataManager,
            DumpManager dumpManager,
            MediaFeatureFlag mediaFeatureFlag) {
        mContext = context;
        mMediaArtworkProcessor = mediaArtworkProcessor;
        mKeyguardBypassController = keyguardBypassController;
        mMediaListeners = new ArrayList<>();
        // TODO: use KeyguardStateController#isOccluded to remove this dependency
        mStatusBarOptionalLazy = statusBarOptionalLazy;
        mNotificationShadeWindowController = notificationShadeWindowController;
        mEntryManager = notificationEntryManager;
        mMainExecutor = mainExecutor;
        mMediaDataManager = mediaDataManager;
        mNotifPipeline = notifPipeline;
        mNotifCollection = notifCollection;
        mMediaDataManager.addListener(this);
        mIsMediaInQS = mediaFeatureFlag.getEnabled();

        if (!featureFlags.isNewNotifPipelineRenderingEnabled()) {
            setupNEM();
            mUsingNotifPipeline = false;
        } else {
            setupNotifPipeline();
            mUsingNotifPipeline = true;
        }

        dumpManager.registerDumpable(this);

        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, LOCKSCREEN_MEDIA_METADATA);
        tunerService.addTunable(this, LOCKSCREEN_ALBUMART_FILTER);

        resolver = mContext.getContentResolver();
        Log.d("DroidFreak32", "Class Init");
        customColorTracker =  Settings.Secure.getIntForUser(mContext.getContentResolver(), PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT);

        // Log.d("DroidFreak32", "Currently customColorTracker is " + customColorTracker);

        customColorIsSet = (Settings.Secure.getIntForUser(mContext.getContentResolver(), PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT) != 0);
        Log.d("DroidFreak32", "Currently customColorIsSet is " + customColorIsSet);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_MEDIA_METADATA:
                mShowMediaMetadata =
                        TunerService.parseIntegerSwitch(newValue, true);
                dispatchUpdateMediaMetaData(false /* changed */, true /* allowAnimation */);
                break;
            case LOCKSCREEN_ALBUMART_FILTER:
                mAlbumArtFilter =
                        TunerService.parseInteger(newValue, 0);
                dispatchUpdateMediaMetaData(false /* changed */, true /* allowAnimation */);
                break;
            default:
                break;
        }
    }

    private void setupNotifPipeline() {
        Log.d("DroidFreak32", "Inside setupNotifPipeline()");
        mNotifPipeline.addCollectionListener(new NotifCollectionListener() {
            @Override
            public void onEntryAdded(@NonNull NotificationEntry entry) {
                mMediaDataManager.onNotificationAdded(entry.getKey(), entry.getSbn());
            }

            @Override
            public void onEntryUpdated(NotificationEntry entry) {
                mMediaDataManager.onNotificationAdded(entry.getKey(), entry.getSbn());
            }

            @Override
            public void onEntryBind(NotificationEntry entry, StatusBarNotification sbn) {
                findAndUpdateMediaNotifications();
                if (!mIsMediaInQS) {
                    checkMediaNotificationColor(entry);
                }
            }

            @Override
            public void onEntryRemoved(@NonNull NotificationEntry entry, int reason) {
                removeEntry(entry);
            }

            @Override
            public void onEntryCleanUp(@NonNull NotificationEntry entry) {
                removeEntry(entry);
            }
        });

        mMediaDataManager.addListener(new MediaDataManager.Listener() {
            @Override
            public void onMediaDataLoaded(@NonNull String key,
                    @Nullable String oldKey, @NonNull MediaData data, boolean immediately,
                    int receivedSmartspaceCardLatency, boolean isSsReactivated) {

                        Log.d("DroidFreak32", "Early onMediaDataLoaded()");
            }

            @Override
            public void onSmartspaceMediaDataLoaded(@NonNull String key,
                    @NonNull SmartspaceMediaData data, boolean shouldPrioritize) {
            }

            @Override
            public void onMediaDataRemoved(@NonNull String key) {
                Log.d("DroidFreak32", "Early onMediaDataRemoved()");
                mNotifPipeline.getAllNotifs()
                        .stream()
                        .filter(entry -> Objects.equals(entry.getKey(), key))
                        .findAny()
                        .ifPresent(entry -> {
                            // TODO(b/160713608): "removing" this notification won't happen and
                            //  won't send the 'deleteIntent' if the notification is ongoing.
                            mNotifCollection.dismissNotification(entry,
                                    getDismissedByUserStats(entry));
                        });
            }

            @Override
            public void onSmartspaceMediaDataRemoved(@NonNull String key, boolean immediately) {}
        });
    }

    private void setupNEM() {
        mEntryManager.addNotificationEntryListener(new NotificationEntryListener() {

            @Override
            public void onPendingEntryAdded(NotificationEntry entry) {
                mMediaDataManager.onNotificationAdded(entry.getKey(), entry.getSbn());
            }

            @Override
            public void onPreEntryUpdated(NotificationEntry entry) {
                mMediaDataManager.onNotificationAdded(entry.getKey(), entry.getSbn());
            }

            @Override
            public void onEntryInflated(NotificationEntry entry) {
                findAndUpdateMediaNotifications();
            }

            @Override
            public void onEntryReinflated(NotificationEntry entry) {
                findAndUpdateMediaNotifications();
            }

            @Override
            public void onEntryRemoved(
                    @NonNull NotificationEntry entry,
                    @Nullable NotificationVisibility visibility,
                    boolean removedByUser,
                    int reason) {
                removeEntry(entry);
            }

            // these get called from NotificationEntryManager.onAsyncInflationFinished
            // so we are sure the final media notification albumart and colors elaboration
            // has been completed by the system
            @Override
            public void onNotificationAdded(
                    NotificationEntry entry) {
                if (!mIsMediaInQS) {
                    checkMediaNotificationColor(entry);
                }
            }
        });

        // Pending entries are never inflated, and will never generate a call to onEntryRemoved().
        // This can happen when notifications are added and canceled before inflation. Add this
        // separate listener for cleanup, since media inflation occurs onPendingEntryAdded().
        mEntryManager.addCollectionListener(new NotifCollectionListener() {
            @Override
            public void onEntryCleanUp(@NonNull NotificationEntry entry) {
                removeEntry(entry);
            }
        });

        mMediaDataManager.addListener(new MediaDataManager.Listener() {
            @Override
            public void onMediaDataLoaded(@NonNull String key,
                    @Nullable String oldKey, @NonNull MediaData data, boolean immediately,
                    int receivedSmartspaceCardLatency, boolean isSsReactivated) {
                        Log.d("DroidFreak32", "setupNEM()'s onMediaDataLoaded()");
            }

            @Override
            public void onSmartspaceMediaDataLoaded(@NonNull String key,
                    @NonNull SmartspaceMediaData data, boolean shouldPrioritize) {

            }

            @Override
            public void onMediaDataRemoved(@NonNull String key) {
                NotificationEntry entry = mEntryManager.getPendingOrActiveNotif(key);
                if (entry != null) {
                    // TODO(b/160713608): "removing" this notification won't happen and
                    //  won't send the 'deleteIntent' if the notification is ongoing.
                    mEntryManager.performRemoveNotification(entry.getSbn(),
                            getDismissedByUserStats(entry),
                            NotificationListenerService.REASON_CANCEL);
                }
            }

            @Override
            public void onSmartspaceMediaDataRemoved(@NonNull String key, boolean immediately) {}
        });
    }

    private DismissedByUserStats getDismissedByUserStats(NotificationEntry entry) {
        final int activeNotificationsCount;
        if (mUsingNotifPipeline) {
            activeNotificationsCount = mNotifPipeline.getShadeListCount();
        } else {
            activeNotificationsCount = mEntryManager.getActiveNotificationsCount();
        }
        return new DismissedByUserStats(
                NotificationStats.DISMISSAL_SHADE, // Add DISMISSAL_MEDIA?
                NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                NotificationVisibility.obtain(
                        entry.getKey(),
                        entry.getRanking().getRank(),
                        activeNotificationsCount,
                        /* visible= */ true,
                        NotificationLogger.getNotificationLocation(entry)));
    }

    private void removeEntry(NotificationEntry entry) {
        Log.d("DroidFreak32", "Entering removeEntry");
        onNotificationRemoved(entry.getKey());
        mMediaDataManager.onNotificationRemoved(entry.getKey());
        Log.d("DroidFreak32", "Exiting removeEntry");
    }

    /**
     * Check if a state should be considered actively playing
     * @param state a PlaybackState
     * @return true if playing
     */
    public static boolean isPlayingState(int state) {
        return !PAUSED_MEDIA_STATES.contains(state);
    }

    public void setUpWithPresenter(NotificationPresenter presenter) {
        mPresenter = presenter;
    }

    public void onNotificationRemoved(String key) {
        if (key.equals(mMediaNotificationKey)) {
            clearCurrentMediaNotification();
            dispatchUpdateMediaMetaData(true /* changed */, true /* allowEnterAnimation */);
        }
        if (key.equals(mNowPlayingNotificationKey)) {
            mNowPlayingNotificationKey = null;
            dispatchUpdateMediaMetaData(true /* changed */, true /* allowEnterAnimation */);
        }
    }

    private void checkMediaNotificationColor(NotificationEntry entry) {
        if (entry.getSbn().getKey().equals(mMediaNotificationKey)) {
            ArrayList<MediaListener> callbacks = new ArrayList<>(mMediaListeners);
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).setMediaNotificationColor(
                        entry.getSbn().getNotification().isColorized(),
                        entry.getRow().getCurrentBackgroundTint());
            }
        }
    }

    @NonNull
    static private Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    // @Override
    // public void onMediaDataLoaded(String key, String oldKey, MediaData data) {

    @Override
    public void onMediaDataLoaded(@NonNull String key,
            @Nullable String oldKey, @NonNull MediaData data, boolean immediately,
            int receivedSmartspaceCardLatency, boolean isSsReactivated) {
        
        Log.d("DroidFreak32", "inside onMediaDataLoaded():");
        /* for future reference, now this static call is also available:
        MediaDataManagerKt.isMediaNotification(sbn)*/
        // TODO: mIsMediaInQS check should be useless here, if so we can remove it
        if (mIsMediaInQS || key.equals(mMediaNotificationKey)) {
            Bitmap artwork = getBitmapFromDrawable(data.getArtwork().loadDrawable(mContext));
            palette = Palette.from(artwork).generate();
            artwork_color = palette.getVibrantColor(data.getBackgroundColor());
            Log.d("DroidFreak32", "onMediaDataLoaded(): Retreived Vibrant color from Album Art: " + artwork_color);
            artworkColorUpdated = true;
            ArrayList<MediaListener> callbacks = new ArrayList<>(mMediaListeners);
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).setMediaNotificationColor(
                        true/*colorized*/,
                        artwork_color);
            }
        }
    // customColorTracker += 1;
    // Log.d("DroidFreak32", "After onMediaDataLoaded customColorTracker is " + customColorTracker);
    }

    @Override
    public void onMediaDataRemoved(@NonNull String key) {
        Log.d("DroidFreak32", "onMediaDataRemoved(): TODO: Maybe try resetting monet here?");
        // Rescan The Monet setting choice if all media is cleared from notifications
        if (PlaybackState.STATE_PLAYING != getMediaControllerPlaybackState(mMediaController)){
            customColorIsSet = (Settings.Secure.getIntForUser(mContext.getContentResolver(), PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT) != 0);
            Log.d("DroidFreak32", "Updated customColorIsSet to " + customColorIsSet);
            // customColorTracker += 1;
            // Log.d("DroidFreak32", "After onMediaDataRemoved customColorTracker is " + customColorTracker);
        }
    }


    
    @Override
    public void onSmartspaceMediaDataLoaded(@NonNull String key,
            @NonNull SmartspaceMediaData data, boolean shouldPrioritize) {
    }

    @Override
    public void onSmartspaceMediaDataRemoved(@NonNull String key, boolean immediately) {}

    public String getMediaNotificationKey() {
        return mMediaNotificationKey;
    }

    public MediaMetadata getMediaMetadata() {
        return mMediaMetadata;
    }

    public Icon getMediaIcon() {
        if (mMediaNotificationKey == null) {
            return null;
        }
        if (mUsingNotifPipeline) {
            // TODO(b/169655596): Either add O(1) lookup, or cache this icon?
            return mNotifPipeline.getAllNotifs().stream()
                .filter(entry -> Objects.equals(entry.getKey(), mMediaNotificationKey))
                .findAny()
                .map(entry -> entry.getIcons().getShelfIcon())
                .map(StatusBarIconView::getSourceIcon)
                .orElse(null);
        } else {
            synchronized (mEntryManager) {
                NotificationEntry entry = mEntryManager
                    .getActiveNotificationUnfiltered(mMediaNotificationKey);
                if (entry == null || entry.getIcons().getShelfIcon() == null) {
                    return null;
                }

                return entry.getIcons().getShelfIcon().getSourceIcon();
            }
        }
    }

    public void addCallback(MediaListener callback) {
        mMediaListeners.add(callback);
        callback.onPrimaryMetadataOrStateChanged(mMediaMetadata,
                getMediaControllerPlaybackState(mMediaController));
    }

    public void removeCallback(MediaListener callback) {
        mMediaListeners.remove(callback);
    }

    public void findAndUpdateMediaNotifications() {
        boolean metaDataChanged;
        if (mUsingNotifPipeline) {
            // TODO(b/169655907): get the semi-filtered notifications for current user
            Collection<NotificationEntry> allNotifications = mNotifPipeline.getAllNotifs();
            metaDataChanged = findPlayingMediaNotification(allNotifications);
        } else {
            synchronized (mEntryManager) {
                Collection<NotificationEntry> allNotifications = mEntryManager.getAllNotifs();
                metaDataChanged = findPlayingMediaNotification(allNotifications);
            }

            if (metaDataChanged) {
                mEntryManager.updateNotifications("NotificationMediaManager - metaDataChanged");
            }

        }
        dispatchUpdateMediaMetaData(metaDataChanged, true /* allowEnterAnimation */);
    }

    
    /**
     * Find a notification and media controller associated with the playing media session, and
     * update this manager's internal state.
     * @return whether the current MediaMetadata changed (and needs to be announced to listeners).
     */
    private boolean findPlayingMediaNotification(
            @NonNull Collection<NotificationEntry> allNotifications) {
    // customColorTracker += 1;
    // // Log.d("DroidFreak32", "before findPlayingMediaNotification customColorTracker is " + customColorTracker);
        boolean metaDataChanged = false;
        // Promote the media notification with a controller in 'playing' state, if any.
        NotificationEntry mediaNotification = null;
        MediaController controller = null;
        for (NotificationEntry entry : allNotifications) {
            if (entry.getSbn().getPackageName().toLowerCase().equals(NOWPLAYING_SERVICE)) {
                mNowPlayingNotificationKey = entry.getSbn().getKey();
                String notificationText = null;
                final String title = entry.getSbn().getNotification()
                        .extras.getString(Notification.EXTRA_TITLE);
                if (!TextUtils.isEmpty(title)) {
                    mNowPlayingTrack = title;
                }
                break;
            }
        }
        for (NotificationEntry entry : allNotifications) {
            Notification notif = entry.getSbn().getNotification();
            if (notif.isMediaNotification()) {
                final MediaSession.Token token =
                        entry.getSbn().getNotification().extras.getParcelable(
                                Notification.EXTRA_MEDIA_SESSION);
                if (token != null) {
                    MediaController aController = new MediaController(mContext, token);
                    if (PlaybackState.STATE_PLAYING
                            == getMediaControllerPlaybackState(aController)) {
                        if (DEBUG_MEDIA) {
                            Log.v(TAG, "DEBUG_MEDIA: found mediastyle controller matching "
                                    + entry.getSbn().getKey());
                        }
                        mediaNotification = entry;
                        controller = aController;
                        break;
                    }
                }
            }
        }

        if (controller != null && !sameSessions(mMediaController, controller)) {
            // We have a new media session
            clearCurrentMediaNotificationSession();
            mMediaController = controller;
            mMediaController.registerCallback(mMediaListener);
            mMediaMetadata = mMediaController.getMetadata();
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: insert listener, found new controller: "
                        + mMediaController + ", receive metadata: " + mMediaMetadata);
            }

            metaDataChanged = true;
        }

        if (mediaNotification != null
                && !mediaNotification.getSbn().getKey().equals(mMediaNotificationKey)) {
            mMediaNotificationKey = mediaNotification.getSbn().getKey();
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: Found new media notification: key="
                        + mMediaNotificationKey);
            }
        }

        return metaDataChanged;
    }

    public String getNowPlayingTrack() {
        if (mNowPlayingNotificationKey == null) {
            mNowPlayingTrack = null;
        }
        return mNowPlayingTrack;
    }

    public void clearCurrentMediaNotification() {
        mMediaNotificationKey = null;
        clearCurrentMediaNotificationSession();
    }

    private void dispatchUpdateMediaMetaData(boolean changed, boolean allowEnterAnimation) {
        if (mPresenter != null) {
            mPresenter.updateMediaMetaData(changed, allowEnterAnimation);
        }
        @PlaybackState.State int state = getMediaControllerPlaybackState(mMediaController);
        ArrayList<MediaListener> callbacks = new ArrayList<>(mMediaListeners);
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onPrimaryMetadataOrStateChanged(mMediaMetadata, state);
        }
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw, @NonNull String[] args) {
        pw.print("    mMediaNotificationKey=");
        pw.println(mMediaNotificationKey);
        pw.print("    mMediaController=");
        pw.print(mMediaController);
        if (mMediaController != null) {
            pw.print(" state=" + mMediaController.getPlaybackState());
        }
        pw.println();
        pw.print("    mMediaMetadata=");
        pw.print(mMediaMetadata);
        if (mMediaMetadata != null) {
            pw.print(" title=" + mMediaMetadata.getText(MediaMetadata.METADATA_KEY_TITLE));
        }
        pw.println();
    }

    private boolean isPlaybackActive(int state) {
        return state != PlaybackState.STATE_STOPPED && state != PlaybackState.STATE_ERROR
                && state != PlaybackState.STATE_NONE;
    }

    private boolean sameSessions(MediaController a, MediaController b) {
        if (a == b) {
            return true;
        }
        if (a == null) {
            return false;
        }
        return a.controlsSameSession(b);
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    private void clearCurrentMediaNotificationSession() {
        mMediaArtworkProcessor.clearCache();
        mMediaMetadata = null;
        if (mMediaController != null) {
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: Disconnecting from old controller: "
                        + mMediaController.getPackageName());
            }
            mMediaController.unregisterCallback(mMediaListener);
        }
        mMediaController = null;
    }

    /**
     * Refresh or remove lockscreen artwork from media metadata or the lockscreen wallpaper.
     */
    public void updateMediaMetaData(boolean metaDataChanged, boolean allowEnterAnimation) {
        Log.d("DroidFreak32", "Entering updateMediaMetaData");
        Log.d("DroidFreak32", "updateMediaMetaData: Current State" + getMediaControllerPlaybackState(mMediaController));

        // Prevent few apps Spamming the state
        current_medatada_update_millis = System.currentTimeMillis();
        if (current_medatada_update_millis - previous_medatada_update_millis < 1000){
            noChangeInPlaybackState = true;
            previous_medatada_update_millis = current_medatada_update_millis;
            Log.d("DroidFreak32", "noChangeInPlaybackState");
        } else {
            noChangeInPlaybackState = false;
            previous_medatada_update_millis = current_medatada_update_millis;
            Log.d("DroidFreak32", "YES ChangeInPlaybackState");
        }

        Trace.beginSection("StatusBar#updateMediaMetaData");
        if (!SHOW_LOCKSCREEN_MEDIA_ARTWORK) {
            Trace.endSection();
            return;
        }

        if (mBackdrop == null) {
            Trace.endSection();
            return; // called too early
        }

        boolean wakeAndUnlock = mBiometricUnlockController != null
            && mBiometricUnlockController.isWakeAndUnlock();
        if (mKeyguardStateController.isLaunchTransitionFadingAway() || wakeAndUnlock) {
            mBackdrop.setVisibility(View.INVISIBLE);
            Trace.endSection();
            return;
        }

        MediaMetadata mediaMetadata = getMediaMetadata();

        if (DEBUG_MEDIA) {
            Log.v(TAG, "DEBUG_MEDIA: updating album art for notification "
                    + getMediaNotificationKey()
                    + " metadata=" + mediaMetadata
                    + " metaDataChanged=" + metaDataChanged
                    + " state=" + mStatusBarStateController.getState());
        }

        Bitmap artworkBitmap = null;
        if (mediaMetadata != null && !mKeyguardBypassController.getBypassEnabled()) {
            artworkBitmap = mediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (artworkBitmap == null) {
                artworkBitmap = mediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            }
        }

        // Process artwork on a background thread and send the resulting bitmap to
        // finishUpdateMediaMetaData.
        if (metaDataChanged) {
            for (AsyncTask<?, ?, ?> task : mProcessArtworkTasks) {
                task.cancel(true);
            }
            mProcessArtworkTasks.clear();
        }
        if (artworkBitmap != null) {
            // mProcessArtworkTasks.add(new ProcessArtworkTask(this, metaDataChanged,
            //         allowEnterAnimation).execute(artworkBitmap));
            finishUpdateMediaMetaData(metaDataChanged, allowEnterAnimation, artworkBitmap);
            Log.d("DroidFreak32", "updateMediaMetaData Processing artwork" + artworkBitmap);
        } else {
            finishUpdateMediaMetaData(metaDataChanged, allowEnterAnimation, null);
            Log.d("DroidFreak32", "artwork is NULL");
        }

        Trace.endSection();
        Log.d("DroidFreak32", "Leaving updateMediaMetaData");
    }

    private void finishUpdateMediaMetaData(boolean metaDataChanged, boolean allowEnterAnimation,
            @Nullable Bitmap bmp) {
        Drawable artworkDrawable = null;
        mLockscreenMediaBlur = (float) Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MEDIA_BLUR, 25,
                UserHandle.USER_CURRENT);
    try {
        Log.d("DroidFreak32", "finishUpdateMediaMetaData " + bmp);
        // set media artwork as lockscreen wallpaper if player is playing
        if (bmp != null && (mShowMediaMetadata || !ENABLE_LOCKSCREEN_WALLPAPER) &&
                PlaybackState.STATE_PLAYING == getMediaControllerPlaybackState(mMediaController)) {

            customColor = (Settings.Secure.getIntForUser(resolver, PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT) != 0);
            Log.d("DroidFreak32", "Current Custom color override option is " + customColor);
            colorOverride = Settings.Secure.getIntForUser(resolver, PREF_COLOR_OVERRIDE, -1, UserHandle.USER_CURRENT);
            Log.d("DroidFreak32", "Current Custom color override is " + colorOverride);
            // artwork_color = colorOverride;

            switch (mAlbumArtFilter) {
                case 0:
                default:
                    artworkDrawable = new BitmapDrawable(mBackdropBack.getResources(), bmp);
                    break;
                case 1:
                    artworkDrawable = new BitmapDrawable(mBackdropBack.getResources(),
                        ImageHelper.toGrayscale(bmp));
                    break;
                case 2:
                    Drawable aw = new BitmapDrawable(mBackdropBack.getResources(), bmp);
                    artworkDrawable = new BitmapDrawable(ImageHelper.getColoredBitmap(aw,
                        mContext.getResources().getColor(R.color.accent_device_default_light)));
                    break;
                case 3:
                    artworkDrawable = new BitmapDrawable(mBackdropBack.getResources(),
                        ImageHelper.getBlurredImage(mContext, bmp, mLockscreenMediaBlur));
                    break;
                case 4:
                    artworkDrawable = new BitmapDrawable(mBackdropBack.getResources(),
                        ImageHelper.getGrayscaleBlurredImage(mContext, bmp, mLockscreenMediaBlur));
                    break;
            }
            
            if ((!customColorIsSet) && (colorOverride != artwork_color)) {
                // Retrieve artwork color from Pulse code
                Log.d("DroidFreak32", "Temporarily switching to custom color " + customColor);
                Settings.Secure.putIntForUser(resolver, PREF_CUSTOM_COLOR, 1, UserHandle.USER_CURRENT);
                Log.d("DroidFreak32", "Trying to set Monet color" + colorOverride);
                // artwork_color = palette.getVibrantColor(colorOverride);
                Log.d("DroidFreak32", "Retrived artwork_color " + artwork_color);
                Settings.Secure.putIntForUser(resolver, PREF_COLOR_OVERRIDE, artwork_color, UserHandle.USER_CURRENT);
                Log.d("DroidFreak32", "Done set Monet color");
                accentChanged = true;
            }
        }
        boolean hasMediaArtwork = artworkDrawable != null;
        boolean allowWhenShade = false;
        // if no media artwork, show normal lockscreen wallpaper
        if (ENABLE_LOCKSCREEN_WALLPAPER && artworkDrawable == null) {
            Bitmap lockWallpaper =
                    mLockscreenWallpaper != null ? mLockscreenWallpaper.getBitmap() : null;
            if (lockWallpaper != null) {
                artworkDrawable = new LockscreenWallpaper.WallpaperDrawable(
                        mBackdropBack.getResources(), lockWallpaper);
                // We're in the SHADE mode on the SIM screen - yet we still need to show
                // the lockscreen wallpaper in that mode.
                allowWhenShade = mStatusBarStateController.getState() == KEYGUARD;
            }
            if (!(customColorIsSet) && accentChanged) {
                Settings.Secure.putIntForUser(resolver, PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT);
                Settings.Secure.putIntForUser(resolver, PREF_COLOR_OVERRIDE, 0, UserHandle.USER_CURRENT);
                Log.d("DroidFreak32", "Reverted temporary color override");
                accentChanged = false;
            }
        }

        NotificationShadeWindowController windowController =
                mNotificationShadeWindowController.get();
        boolean hideBecauseOccluded =
                mStatusBarOptionalLazy.get().map(StatusBar::isOccluded).orElse(false);

        final boolean hasArtwork = artworkDrawable != null;
        mColorExtractor.setHasMediaArtwork(hasMediaArtwork);
        if (mScrimController != null) {
            mScrimController.setHasBackdrop(hasArtwork);
        }

        if ((hasArtwork || DEBUG_MEDIA_FAKE_ARTWORK)
                && (mStatusBarStateController.getState() != StatusBarState.SHADE || allowWhenShade)
                &&  mBiometricUnlockController != null && mBiometricUnlockController.getMode()
                        != BiometricUnlockController.MODE_WAKE_AND_UNLOCK_PULSING
                && !hideBecauseOccluded) {
            // time to show some art!
            if (mBackdrop.getVisibility() != View.VISIBLE) {
                mBackdrop.setVisibility(View.VISIBLE);
                if (allowEnterAnimation) {
                    mBackdrop.setAlpha(0);
                    mBackdrop.animate().alpha(1f);
                } else {
                    mBackdrop.animate().cancel();
                    mBackdrop.setAlpha(1f);
                }
                if (windowController != null) {
                    windowController.setBackdropShowing(true);
                }
                metaDataChanged = true;
                if (DEBUG_MEDIA) {
                    Log.v(TAG, "DEBUG_MEDIA: Fading in album artwork");
                }
            }
            if (metaDataChanged) {
                if (mBackdropBack.getDrawable() != null) {
                    Drawable drawable =
                            mBackdropBack.getDrawable().getConstantState()
                                    .newDrawable(mBackdropFront.getResources()).mutate();
                    mBackdropFront.setImageDrawable(drawable);
                    mBackdropFront.setAlpha(1f);
                    mBackdropFront.setVisibility(View.VISIBLE);
                } else {
                    mBackdropFront.setVisibility(View.INVISIBLE);
                }

                if (DEBUG_MEDIA_FAKE_ARTWORK) {
                    final int c = 0xFF000000 | (int)(Math.random() * 0xFFFFFF);
                    Log.v(TAG, String.format("DEBUG_MEDIA: setting new color: 0x%08x", c));
                    mBackdropBack.setBackgroundColor(0xFFFFFFFF);
                    mBackdropBack.setImageDrawable(new ColorDrawable(c));
                } else {
                    mBackdropBack.setImageDrawable(artworkDrawable);
                }

                if (mBackdropFront.getVisibility() == View.VISIBLE) {
                    if (DEBUG_MEDIA) {
                        Log.v(TAG, "DEBUG_MEDIA: Crossfading album artwork from "
                                + mBackdropFront.getDrawable()
                                + " to "
                                + mBackdropBack.getDrawable());
                    }
                    mBackdropFront.animate()
                            .setDuration(250)
                            .alpha(0f).withEndAction(mHideBackdropFront);
                }
            }
        } else {
            // need to hide the album art, either because we are unlocked, on AOD
            // or because the metadata isn't there to support it
            if (mBackdrop.getVisibility() != View.GONE) {
                if (DEBUG_MEDIA) {
                    Log.v(TAG, "DEBUG_MEDIA: Fading out album artwork");
                }
                boolean cannotAnimateDoze = mStatusBarStateController.isDozing()
                        && !ScrimState.AOD.getAnimateChange();
                boolean needsBypassFading = mKeyguardStateController.isBypassFadingAnimation();
                if (((mBiometricUnlockController != null && mBiometricUnlockController.getMode()
                        == BiometricUnlockController.MODE_WAKE_AND_UNLOCK_PULSING
                                || cannotAnimateDoze) && !needsBypassFading)
                        || hideBecauseOccluded) {

                    // We are unlocking directly - no animation!
                    mBackdrop.setVisibility(View.GONE);
                    mBackdropBack.setImageDrawable(null);
                    if (windowController != null) {
                        windowController.setBackdropShowing(false);
                    }
                } else {
                    if (windowController != null) {
                        windowController.setBackdropShowing(false);
                    }
                    mBackdrop.animate()
                            .alpha(0)
                            .setInterpolator(Interpolators.ACCELERATE_DECELERATE)
                            .setDuration(300)
                            .setStartDelay(0)
                            .withEndAction(() -> {
                                mBackdrop.setVisibility(View.GONE);
                                mBackdropFront.animate().cancel();
                                mBackdropBack.setImageDrawable(null);
                                mMainExecutor.execute(mHideBackdropFront);
                            });
                    if (mKeyguardStateController.isKeyguardFadingAway()) {
                        mBackdrop.animate()
                                .setDuration(
                                        mKeyguardStateController.getShortenedFadingAwayDuration())
                                .setStartDelay(
                                        mKeyguardStateController.getKeyguardFadingAwayDelay())
                                .setInterpolator(Interpolators.LINEAR)
                                .start();
                    }
                }
            }
        }
    } catch (Exception e) {
        Log.e("DroidFreak32", "Testing Monet change from WP failed with " + e.toString());
    }
    }

    public void setup(BackDropView backdrop, ImageView backdropFront, ImageView backdropBack,
            ScrimController scrimController, LockscreenWallpaper lockscreenWallpaper) {
        mBackdrop = backdrop;
        mBackdropFront = backdropFront;
        mBackdropBack = backdropBack;
        mScrimController = scrimController;
        mLockscreenWallpaper = lockscreenWallpaper;
        Log.d("DroidFreak32", "Inside setup()");
    }

    public void setBiometricUnlockController(BiometricUnlockController biometricUnlockController) {
        mBiometricUnlockController = biometricUnlockController;
    }

    /**
     * Hide the album artwork that is fading out and release its bitmap.
     */
    protected final Runnable mHideBackdropFront = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_MEDIA) {
                Log.v(TAG, "DEBUG_MEDIA: removing fade layer");
            }
            mBackdropFront.setVisibility(View.INVISIBLE);
            mBackdropFront.animate().cancel();
            mBackdropFront.setImageDrawable(null);
        }
    };

    private Bitmap processArtwork(Bitmap artwork) {
        return mMediaArtworkProcessor.processArtwork(mContext, artwork);
    }

    @MainThread
    private void removeTask(AsyncTask<?, ?, ?> task) {
        mProcessArtworkTasks.remove(task);
    }

    /**
     * {@link AsyncTask} to prepare album art for use as backdrop on lock screen.
     */
    private static final class ProcessArtworkTask extends AsyncTask<Bitmap, Void, Bitmap> {

        private final WeakReference<NotificationMediaManager> mManagerRef;
        private final boolean mMetaDataChanged;
        private final boolean mAllowEnterAnimation;

        ProcessArtworkTask(NotificationMediaManager manager, boolean changed,
                boolean allowAnimation) {
            mManagerRef = new WeakReference<>(manager);
            mMetaDataChanged = changed;
            mAllowEnterAnimation = allowAnimation;
            Log.d("DroidFreak32", "ProcessArtworkTask constructor with " + mMetaDataChanged + " and " + mAllowEnterAnimation);
        }

        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            NotificationMediaManager manager = mManagerRef.get();
            Log.d("DroidFreak32", "ProcessArtworkTask doInBackground with blength " + bitmaps.length);
            if (manager == null || bitmaps.length == 0 || isCancelled()) {
                return null;
            }
            return manager.processArtwork(bitmaps[0]);
        }

        @Override
        protected void onPostExecute(@Nullable Bitmap result) {
            NotificationMediaManager manager = mManagerRef.get();
            Log.d("DroidFreak32", "onPostExecute doInBackground with result " + result);
            if (manager != null && !isCancelled()) {
                manager.removeTask(this);
                manager.finishUpdateMediaMetaData(mMetaDataChanged, mAllowEnterAnimation, result);
            }
        }

        @Override
        protected void onCancelled(Bitmap result) {
             Log.d("DroidFreak32", " onCancelled " + result);
            if (result != null) {
                result.recycle();
            }
            NotificationMediaManager manager = mManagerRef.get();
            if (manager != null) {
                manager.removeTask(this);
            }
        }
    }

    public interface MediaListener {
        /**
         * Called whenever there's new metadata or playback state.
         * @param metadata Current metadata.
         * @param state Current playback state
         * @see PlaybackState.State
         */
        default void onPrimaryMetadataOrStateChanged(MediaMetadata metadata,
                @PlaybackState.State int state) {}

        default void setMediaNotificationColor(boolean colorizedMedia, int color) {};
    }
}
