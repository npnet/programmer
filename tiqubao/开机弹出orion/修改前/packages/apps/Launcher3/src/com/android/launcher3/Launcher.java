/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.app.Service;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.speech.RecognizerIntent;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.OrientationEventListener;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.PagedView.PageSwitchListener;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.mediatek.launcher3.ext.LauncherExtPlugin;

import com.android.launcher3.MTKUnreadLoader;
import com.mediatek.common.widget.IMtkWidget;
import com.mediatek.launcher3.ext.LauncherLog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Default launcher application.
 */
public class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener, PageSwitchListener, LauncherProviderChangeListener,
                   MTKUnreadLoader.UnreadCallbacks                   {
    static final String TAG = "Launcher";
    static final String TAG_SURFACEWIDGET = "MTKWidgetView";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;
    static final boolean DEBUG_STRICT_MODE = false;
    static final boolean DEBUG_RESUME_TIME = false;
    static final boolean DEBUG_DUMP_LOG = false;

    static final boolean ENABLE_DEBUG_INTENTS = false; // allow DebugIntents to run

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    private static final int REQUEST_RECONFIGURE_APPWIDGET = 12;

    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int SCREEN_COUNT = 5;
    static final int DEFAULT_SCREEN = 2;

    private static final String PREFERENCES = "launcher.preferences";
    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]
    static final String FORCE_ENABLE_ROTATION_PROPERTY = "launcher_force_rotate";
    static final String DUMP_STATE_PROPERTY = "launcher_dump_state";
    static final String DISABLE_ALL_APPS_PROPERTY = "launcher_noallapps";

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher3.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_span_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_span_y";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_INFO = "launcher.add_widget_info";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_ID = "launcher.add_widget_id";
    // Type: int[]
    private static final String RUNTIME_STATE_VIEW_IDS = "launcher.view_ids";

    static final String INTRO_SCREEN_DISMISSED = "launcher.intro_screen_dismissed";
    static final String FIRST_RUN_ACTIVITY_DISPLAYED = "launcher.first_run_activity_displayed";

    static final String FIRST_LOAD_COMPLETE = "launcher.first_load_complete";
    static final String ACTION_FIRST_LOAD_COMPLETE =
            "com.android.launcher3.action.FIRST_LOAD_COMPLETE";

    private static final String TOOLBAR_ICON_METADATA_NAME = "com.android.launcher.toolbar_icon";
    private static final String TOOLBAR_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_search_icon";
    private static final String TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_voice_search_icon";

    public static final String SHOW_WEIGHT_WATCHER = "debug.show_mem";
    public static final boolean SHOW_WEIGHT_WATCHER_DEFAULT = false;

    public static final String USER_HAS_MIGRATED = "launcher.user_migrated_from_old_data";

    /// M: [OP09]request to hide application. @{
    private static final int REQUEST_HIDE_APPS = 12;
    private static final String HIDE_PACKAGE_NAME = "com.android.launcher3";
    private static final String HIDE_ACTIVITY_NAME = "com.android.launcher3.HideAppsActivity";

    /// M: whether the apps customize pane is in edit mode, add for OP09.
    private static boolean sIsInEditMode = false;
    //M:[OP09] }@

    //M:[OP09] Market icon start @{
    private Intent mAppMarketIntent = null;
    private static final boolean DISABLE_MARKET_BUTTON = true;
    //M:[OP09] end }@

    /** The different states that Launcher can be in. */
    private enum State { NONE, WORKSPACE, APPS_CUSTOMIZE, APPS_CUSTOMIZE_SPRING_LOADED };
    private State mState = State.WORKSPACE;
    private AnimatorSet mStateAnimation;

    private boolean mIsSafeModeEnabled;

    static final int APPWIDGET_HOST_ID = 1024;
    public static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 300;
    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;
    private static final int ACTIVITY_START_DELAY = 1000;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREEN;

    private HashMap<Integer, Integer> mItemIdToViewId = new HashMap<Integer, Integer>();
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    private static int NEW_APPS_ANIMATION_DELAY = 500;
    private static final int SINGLE_FRAME_DELAY = 16;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private Workspace mWorkspace;
    private View mLauncherView;
    private View mPageIndicators;
    private DragLayer mDragLayer;
    private DragController mDragController;
    private View mWeightWatcher;

    private AppWidgetManagerCompat mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private ItemInfo mPendingAddInfo = new ItemInfo();
    private AppWidgetProviderInfo mPendingAddWidgetInfo;
    private int mPendingAddWidgetId = -1;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    private Hotseat mHotseat;
    private ViewGroup mOverviewPanel;

    private View mAllAppsButton;

    private SearchDropTargetBar mSearchDropTargetBar;
    private AppsCustomizeTabHost mAppsCustomizeTabHost;
    private AppsCustomizePagedView mAppsCustomizeContent;
    private boolean mAutoAdvanceRunning = false;
    private View mQsb;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    private ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<Runnable>();
    private ArrayList<Runnable> mOnResumeCallbacks = new ArrayList<Runnable>();

    private Bundle mSavedInstanceState;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mHasFocus = false;
    private boolean mAttached = false;
    ///M:[OP09]@{
    private boolean mSupportEditAndHideApps = false;
    //}@

    private static LocaleConfiguration sLocaleConfiguration = null;

    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    private View.OnTouchListener mHapticFeedbackTouchListener;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    private HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private final int mRestoreScreenOrientationDelay = 500;

    // External icons saved in case of resource changes, orientation, etc.
    private static Drawable.ConstantState[] sGlobalSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sVoiceSearchIcon = new Drawable.ConstantState[2];

    private Drawable mWorkspaceBackgroundDrawable;

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<Integer>();
    private static final boolean DISABLE_SYNCHRONOUS_BINDING_CURRENT_PAGE = false;

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();
    static Date sDateStamp = new Date();
    static DateFormat sDateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    static long sRunStart = System.currentTimeMillis();
    static final String CORRUPTION_EMAIL_SENT_KEY = "corruptionEmailSent";

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    private static ArrayList<ComponentName> mIntentsOnWorkspaceFromUpgradePath = null;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    private ImageView mFolderIconImageView;
    private Bitmap mFolderIconBitmap;
    private Canvas mFolderIconCanvas;
    private Rect mRectForFolderAnimation = new Rect();

    private boolean mAllAppAnimaterState = false;

    private BubbleTextView mWaitingForResume;

    private Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static PendingAddArguments sPendingAddItem;

    public static boolean sForceEnableRotation = isPropertyEnabled(FORCE_ENABLE_ROTATION_PROPERTY);

    private static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        long screenId;
        int cellX;
        int cellY;
        int appWidgetId;
    }

    private Stats mStats;

    FocusIndicatorView mFocusHandler;

    static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    /// M: Static variable to record whether locale has been changed.
    private static boolean sLocaleChanged = false;

    /// M: Add for launch specified applications in landscape. @{
    private static final int ORIENTATION_0 = 0;
    private static final int ORIENTATION_90 = 90;
    private static final int ORIENTATION_180 = 180;
    private static final int ORIENTATION_270 = 270;

    private OrientationEventListener mOrientationListener;
    private int mLastOrientation = ORIENTATION_0;
    /// @}

    /// M: Add for launcher unread shortcut feature. @{
    static final int MAX_UNREAD_COUNT = 99;

    private boolean mUnreadLoadCompleted = false;
    private boolean mBindingWorkspaceFinished = false;
    private boolean mBindingAppsFinished = false;
    /// @}

    /// M: Save current CellLayout bounds before workspace.changeState(CellLayout will be scaled).
    private Rect mCurrentBounds = new Rect();

    /// M: Used to popup long press widget to add message.
    private Toast mLongPressWidgetToAddToast;

    /// M: Used to force reload when loading workspace
    private boolean mIsLoadingWorkspace;

    /// M: flag to indicate whether the orientation has changed.
    private boolean mOrientationChanged;

    /// M: flag to indicate whether the pages in app customized pane were recreated.
    private boolean mPagesWereRecreated;

    /// M: whether to use the pending apps queue to block all package
    /// add/update/removed events.
    private static boolean sUsePendingAppsQueue = false;

    /// M: list used to store all pending add/update/removed applications.
    private static ArrayList<PendingChangedApplications> sPendingChangedApps =
                            new ArrayList<PendingChangedApplications>();

    /// M: Add for smart book feature. Force re-init dynamic grid if display size is changed.
    private static Point sOldRealSize = new Point();

    /// M: If workspcae no initialized, save last restore workspace screen.
    private int mCurrentWorkSpaceScreen = PagedView.INVALID_RESTORE_PAGE;
    
    /// M: Indicates if already show workspace in onResume/onNewIntent
    /// if yes, don't show workspace when screen off
    private boolean isNewIntentAndAlreadyShowWorkspace = false;

    /// M: Add for unread message feature.
    private MTKUnreadLoader mUnreadLoader = null;
    
    /// M: Disable applist white background for jitter performance issue {@
    public static boolean DISABLE_APPLIST_WHITE_BG = true;
    public static final String PROP_DISABLE_APPLIST_WHITE_BG = "launcher.whitebg.disable";
    // should kill and restart launcher process to re-execute static block if reset properties
    // adb shell setprop launcher.applist.whitebg.disable true/false
    // adb shell stop
    // adb shell start
    static {
        DISABLE_APPLIST_WHITE_BG = android.os.SystemProperties.getBoolean(PROP_DISABLE_APPLIST_WHITE_BG, true);
    }
    /// @}
    
    private static final String OP01_AFFINITY = "com.mediatek.op01.plugin";

	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG_STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        super.onCreate(savedInstanceState);

        /// M: [OP09]Whether Edit and Hide Apps support or not.@{
        mSupportEditAndHideApps = LauncherExtPlugin.getInstance().getWorkspaceExt(
                          this.getApplicationContext()).supportEditAndHideApps();
        //}@

        LauncherAppState.setApplicationContext(getApplicationContext());
        LauncherAppState app = LauncherAppState.getInstance();
        LauncherAppState.getLauncherProvider().setLauncherProviderChangeListener(this);

        /// M: Add for smart book feature. Change DB if UI layout is changed.
        boolean isDatabaseIdChanged = false;
        if (SystemProperties.get("ro.mtk_smartbook_support").equals("1")) {
            int originalDatabaseId = app.getLauncherProvider().getDatabaseId();
            int newDatabaseId = getResources().getInteger(R.integer.database_id);
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "+++++++++[Smartbook or Phone+++++++++"
		    + "\n" + "originalDatabaseId = " + originalDatabaseId
            + "\n" + "newDatabaseId = " + newDatabaseId
		    + "\n" + "+++++++++Smartbook or Phone]+++++++++");
            }
            if (originalDatabaseId != newDatabaseId) {
                isDatabaseIdChanged = true;
                app.updateScreenInfo();
                app.getIconCache().flush();
                Utilities.initStatics(this);
                app.getLauncherProvider().setDatabaseId(newDatabaseId);
            }
        }

        // Determine the dynamic grid properties
        Point smallestSize = new Point();
        Point largestSize = new Point();
        Point realSize = new Point();
        Display display = getWindowManager().getDefaultDisplay();
        display.getCurrentSizeRange(smallestSize, largestSize);
        display.getRealSize(realSize);
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        // Lazy-initialize the dynamic grid
        /// M: Add for smart book feature. Force re-init dynamic grid if database ID is changed.
        DeviceProfile grid = app.initDynamicGrid(this,
                Math.min(smallestSize.x, smallestSize.y),
                Math.min(largestSize.x, largestSize.y),
                realSize.x, realSize.y,
                dm.widthPixels, dm.heightPixels, isDatabaseIdChanged);

        // the LauncherApplication should call this, but in case of Instrumentation it might not be present yet
        mSharedPrefs = getSharedPreferences(LauncherAppState.getSharedPreferencesKey(),
                Context.MODE_PRIVATE);
        mIsSafeModeEnabled = getPackageManager().isSafeMode();
        mModel = app.setLauncher(this);
        
        /**M: added for unread feature, load and bind unread info.@{**/
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "R.bool.config_unreadSupport = "+getResources().getBoolean(R.bool.config_unreadSupport));
        }
        if (getResources().getBoolean(R.bool.config_unreadSupport)) {
            mUnreadLoader = LauncherAppState.getInstance().getLauncehrApplication().getUnreadLoader();
            mUnreadLoader.loadAndInitUnreadShortcuts();
            mUnreadLoader.initialize(this);
        }
        /**@}**/
        mIconCache = app.getIconCache();
        mIconCache.flushInvalidIcons(grid);
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onCreate: savedInstanceState = " + savedInstanceState
                    + ", mModel = " + mModel + ", mIconCache = " + mIconCache + ", this = " + this
                    + ", sLocaleChanged = " + sLocaleChanged + ", realSize = " + realSize);
        }

        mStats = new Stats(this);

        mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);

        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        checkForLocaleChange();
        setContentView(R.layout.launcher);

        setupViews();
        grid.layout(this);

        registerContentObservers();

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            /// M: Add for smart book feature. Reset load state if database changed before.
            if (isDatabaseIdChanged) {
                mModel.resetLoadedState(true, true);
            } else {
                /**M: Added to reset the loader state, to resolve the timing state issue.@{*/
                mModel.resetLoadedState(false, false);
                /**@}**/
            }

            if (DISABLE_SYNCHRONOUS_BINDING_CURRENT_PAGE) {
                // If the user leaves launcher, then we should just load items asynchronously when
                // they return.
                mModel.startLoader(true, PagedView.INVALID_RESTORE_PAGE);
            } else {
                // We only load the page synchronously if the user rotates (or triggers a
                // configuration change) while launcher is in the foreground
                mModel.startLoader(true, mWorkspace.getRestorePage());
            }
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        updateGlobalIcons();

        // On large interfaces, we want the screen to auto-rotate based on the current orientation
        unlockScreenOrientation(true);

        if (shouldShowIntroScreen()) {
            showIntroScreen();
        } else {
            showFirstRunActivity();
            showFirstRunClings();
        }
    }

    @Override
    public void onLauncherProviderChange() { }

    /** To be overriden by subclasses to hint to Launcher that we have custom content */
    protected boolean hasCustomContentToLeft() {
        return false;
    }

    /**
     * To be overridden by subclasses to populate the custom content container and call
     * {@link #addToCustomContentPage}. This will only be invoked if
     * {@link #hasCustomContentToLeft()} is {@code true}.
     */
    protected void populateCustomContentContainer() {
    }

    /**
     * Invoked by subclasses to signal a change to the {@link #addCustomContentToLeft} value to
     * ensure the custom content page is added or removed if necessary.
     */
    protected void invalidateHasCustomContentToLeft() {
        if (mWorkspace == null || mWorkspace.getScreenOrder().isEmpty()) {
            // Not bound yet, wait for bindScreens to be called.
            return;
        }

        if (!mWorkspace.hasCustomContent() && hasCustomContentToLeft()) {
            // Create the custom content page and call the subclass to populate it.
            mWorkspace.createCustomContentContainer();
            populateCustomContentContainer();
        } else if (mWorkspace.hasCustomContent() && !hasCustomContentToLeft()) {
            mWorkspace.removeCustomContentPage();
        }
    }

    private void updateGlobalIcons() {
        boolean searchVisible = false;
        boolean voiceVisible = false;
        // If we have a saved version of these external icons, we load them up immediately
        int coi = getCurrentOrientationIndexForGlobalIcons();
        if (sGlobalSearchIcon[coi] == null || sVoiceSearchIcon[coi] == null) {
            searchVisible = updateGlobalSearchIcon();
            voiceVisible = updateVoiceSearchIcon(searchVisible);
        }
        if (sGlobalSearchIcon[coi] != null) {
             updateGlobalSearchIcon(sGlobalSearchIcon[coi]);
             searchVisible = true;
        }
        if (sVoiceSearchIcon[coi] != null) {
            updateVoiceSearchIcon(sVoiceSearchIcon[coi]);
            voiceVisible = true;
        }
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    private void checkForLocaleChange() {
        if (sLocaleConfiguration == null) {
            new AsyncTask<Void, Void, LocaleConfiguration>() {
                @Override
                protected LocaleConfiguration doInBackground(Void... unused) {
                    LocaleConfiguration localeConfiguration = new LocaleConfiguration();
                    readConfiguration(Launcher.this, localeConfiguration);
                    return localeConfiguration;
                }

                @Override
                protected void onPostExecute(LocaleConfiguration result) {
                    sLocaleConfiguration = result;
                    checkForLocaleChange();  // recursive, but now with a locale configuration
                }
            }.execute();
            return;
        }

        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = sLocaleConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = sLocaleConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = sLocaleConfiguration.mnc;
        final int mnc = configuration.mnc;

        boolean localeChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "checkForLocaleChange: previousLocale = " + previousLocale
                    + ", locale = " + locale + ", previousMcc = " + previousMcc + ", mcc = " + mcc
                    + ", previousMnc = " + previousMnc + ", mnc = " + mnc + ", localeChanged = "
                    + localeChanged + ", this = " + this);
        }

        if (localeChanged) {
            sLocaleConfiguration.locale = locale;
            sLocaleConfiguration.mcc = mcc;
            sLocaleConfiguration.mnc = mnc;

            mIconCache.flush();

            final LocaleConfiguration localeConfiguration = sLocaleConfiguration;
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    writeConfiguration(Launcher.this, localeConfiguration);
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }

    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
            LauncherLog.d(TAG, "FileNotFoundException when read configuration.");
        } catch (IOException e) {
            // Ignore
            LauncherLog.d(TAG, "IOException when read configuration.");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                    LauncherLog.d(TAG, "IOException when close file.");
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
            LauncherLog.d(TAG, "FileNotFoundException when write configuration.");
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                    LauncherLog.d(TAG, "IOException when close file.");
                }
            }
        }
    }

    public Stats getStats() {
        return mStats;
    }

    public LayoutInflater getInflater() {
        return mInflater;
    }

    boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !mModel.isLoadingWorkspace();
    }

    static int getScreen() {
        synchronized (sLock) {
            return sScreen;
        }
    }

    static void setScreen(int screen) {
        synchronized (sLock) {
            sScreen = screen;
        }
    }

    public static int generateViewId() {
        if (Build.VERSION.SDK_INT >= 17) {
            return View.generateViewId();
        } else {
            // of its implementation.
            for (;;) {
                final int result = sNextGeneratedId.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
                if (sNextGeneratedId.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        }
    }

    public int getViewIdForItem(ItemInfo info) {
        // This cast is safe given the > 2B range for int.
        int itemId = (int) info.id;
        if (mItemIdToViewId.containsKey(itemId)) {
            return mItemIdToViewId.get(itemId);
        }
        int viewId = generateViewId();
        mItemIdToViewId.put(itemId, viewId);
        return viewId;
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private long completeAdd(PendingAddArguments args) {
        long screenId = args.screenId;
        if (args.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(args.screenId);
        }

        switch (args.requestCode) {
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(args.intent, args.container, screenId, args.cellX,
                        args.cellY);
                break;
            case REQUEST_CREATE_APPWIDGET:
                /**M:ALPS01808434, To resolve gallery widget can not be 
                 * added to home screen after changing the language.@{*/
                int appWidgetId = args.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                completeAddAppWidget(appWidgetId, args.container, screenId, null, null);
                /**@}**/
                break;
            case REQUEST_RECONFIGURE_APPWIDGET:
                completeRestoreAppWidget(args.appWidgetId);
                break;
        }
        // Before adding this resetAddInfo(), after a shortcut was added to a workspace screen,
        // if you turned the screen off and then back while in All Apps, Launcher would not
        // return to the workspace. Clearing mAddInfo.container here fixes this issue
        resetAddInfo();
        return screenId;
    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        // Reset the startActivity waiting flag
        setWaitingForResult(false);
        final int pendingAddWidgetId = mPendingAddWidgetId;
        mPendingAddWidgetId = -1;

        Runnable exitSpringLoaded = new Runnable() {
            @Override
            public void run() {
                exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED),
                        EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            }
        };

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(appWidgetId, mPendingAddInfo, null,
                        mPendingAddWidgetInfo, ON_ACTIVITY_RESULT_ANIMATION_DELAY);
            }
            return;
        } else if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == RESULT_OK && mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(false);
            }
            return;
        //M:[OP09] handle hide apps @{
        } else if (requestCode == REQUEST_HIDE_APPS) {
            mWaitingForResult = false;
            mAppsCustomizeContent.processAppsStateChanged();
            // update screen after hide app
            mWorkspace.removeExtraEmptyScreenDelayed(true, null,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, true);
            return;
        }
        //M:[OP09]}@

        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        final boolean workspaceLocked = isWorkspaceLocked();

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onActivityResult: requestCode = " + requestCode
                    + ", resultCode = " + resultCode + ", data = " + data
                    + ", mPendingAddInfo = " + mPendingAddInfo);
        }

        // We have special handling for widgets
        /// M: [ALPS01475192] Need to add this app widget item into sPendingAddList if workspace is locked to fix ALPS01382035 and ALPS01432963.
        if (isWidgetDrop && !isWorkspaceLocked()) {
            final int appWidgetId;
            int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    : -1;
            if (widgetId < 0) {
                appWidgetId = pendingAddWidgetId;
            } else {
                appWidgetId = widgetId;
            }

            final int result;
            if (appWidgetId < 0 || resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not " +
                        "returned from the widget configuration activity.");
                result = RESULT_CANCELED;
                completeTwoStageWidgetDrop(result, appWidgetId);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        exitSpringLoadedDragModeDelayed(false, 0, null);
                    }
                };
                if (workspaceLocked) {
                    // No need to remove the empty screen if we're mid-binding, as the
                    // the bind will not add the empty screen.
                    mWorkspace.postDelayed(onComplete, ON_ACTIVITY_RESULT_ANIMATION_DELAY);
                } else {
                    mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                            ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
                }
            } else {
                if (!workspaceLocked) {
                    if (mPendingAddInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        // When the screen id represents an actual screen (as opposed to a rank)
                        // we make sure that the drop page actually exists.
                        mPendingAddInfo.screenId =
                                ensurePendingDropLayoutExists(mPendingAddInfo.screenId);
                    }
                    final CellLayout dropLayout = mWorkspace.getScreenWithId(mPendingAddInfo.screenId);

                    dropLayout.setDropPending(true);
                    final Runnable onComplete = new Runnable() {
                        @Override
                        public void run() {
                            completeTwoStageWidgetDrop(resultCode, appWidgetId);
                            dropLayout.setDropPending(false);
                        }
                    };
                    mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                            ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
                } else {
                    PendingAddArguments args = preparePendingAddArgs(requestCode, data, appWidgetId,
                            mPendingAddInfo);
                    sPendingAddItem = args;
                }
            }
            return;
        }

        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                // Update the widget view.
                PendingAddArguments args = preparePendingAddArgs(requestCode, data,
                        pendingAddWidgetId, mPendingAddInfo);
                if (workspaceLocked) {
                    sPendingAddItem = args;
                } else {
                    completeAdd(args);
                }
            }
            // Leave the widget in the pending state if the user canceled the configure.
            return;
        }

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
			/// M.ALPS01808563, Modify -1 to pendingAddWidgetId;
            final PendingAddArguments args = preparePendingAddArgs(requestCode, data, pendingAddWidgetId,
                    mPendingAddInfo);
            if (isWorkspaceLocked()) {
                sPendingAddItem = args;
            } else {
                completeAdd(args);
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            }
        } else if (resultCode == RESULT_CANCELED) {
            mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                    ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
        }
        mDragLayer.clearAnimatedView();
    }

    private PendingAddArguments preparePendingAddArgs(int requestCode, Intent data, int
            appWidgetId, ItemInfo info) {
        PendingAddArguments args = new PendingAddArguments();
        args.requestCode = requestCode;
        args.intent = data;
        args.container = info.container;
        args.screenId = info.screenId;
        args.cellX = info.cellX;
        args.cellY = info.cellY;
        args.appWidgetId = appWidgetId;
        return args;
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private long ensurePendingDropLayoutExists(long screenId) {
        CellLayout dropLayout =
                (CellLayout) mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreen();
            return mWorkspace.commitExtraEmptyScreen();
        } else {
            return screenId;
        }
    }

    private void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId) {
        CellLayout cellLayout =
                (CellLayout) mWorkspace.getScreenWithId(mPendingAddInfo.screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    mPendingAddWidgetInfo);
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, mPendingAddInfo.container,
                            mPendingAddInfo.screenId, layout, null);
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED),
                            EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(mPendingAddInfo, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else if (onCompleteRunnable != null) {
            // The animated view may be null in the case of a rotation during widget configuration
            onCompleteRunnable.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onStop: this = " + this);
        }

        FirstFrameAnimatorHelper.setIsVisible(false);
        
        /// M. alps01760754, add the part for mms feature.
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onStop: set SharedPreferences for MMS");
        }
        PowerManager powerManager = (PowerManager)getSystemService(Service.POWER_SERVICE);

        if(powerManager.isScreenOn()) {
            if(LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "(Launcher)onStop: PowerManager.isScreenOn() = fasle, don't set SharedPreferences");
            }
            SharedPreferences sp = getSharedPreferences("persist.launcher.top",
            Context.MODE_MULTI_PROCESS | Context.MODE_WORLD_READABLE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("top", false);
            editor.apply();
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "(Launcher)onStop: set SharedPreferences for MMS,end");
            }
        }

        /// M: [OP09]Exit edit mode when leave launcher.
        if (isInEditMode()) {
            exitEditMode();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onStart: this = " + this);
        }

        /// M. alps01760754, add the part for mms feature.
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onStart: set SharedPreferences for MMS");
        }
    
        SharedPreferences sp = getSharedPreferences("persist.launcher.top",
        Context.MODE_MULTI_PROCESS | Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("top", true);
        editor.apply();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onStart: set SharedPreferences for MMS,end");
        }
        
        FirstFrameAnimatorHelper.setIsVisible(true);
    }

    @Override
    protected void onResume() {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
            Log.v(TAG, "Launcher.onResume()");
        }
        super.onResume();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onResume: mRestoring = " + mRestoring
                    + ", mOnResumeNeedsLoad = " + mOnResumeNeedsLoad + ",mOrientationChanged = "
                    + mOrientationChanged + ",mPagesAreRecreated = " + mPagesWereRecreated
                    + ", this = " + this);
        }

        /// M: Call the appropriate callback for the IMtkWidget on the current page when we resume Launcher.
        mWorkspace.onResumeWhenShown(mWorkspace.getCurrentPage());

        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
        } else if (mOnResumeState == State.APPS_CUSTOMIZE) {
            /// M: Show ContentType related page, not always show App page.
            showAllApps(false, mAppsCustomizeContent != null ? mAppsCustomizeContent
                    .getContentType() : AppsCustomizePagedView.ContentType.Applications, false);
        }
        mOnResumeState = State.NONE;

        // Background was set to gradient in onPause(), restore to black if in all apps.
        setWorkspaceBackground(mState == State.WORKSPACE);

        mPaused = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            setWorkspaceLoading(true);
            mModel.startLoader(true, PagedView.INVALID_RESTORE_PAGE);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }
        if (mBindOnResumeCallbacks.size() > 0) {
            // We might have postponed some bind calls until onResume (see waitUntilResume) --
            // execute them here
            long startTimeCallbacks = 0;
            if (DEBUG_RESUME_TIME) {
                startTimeCallbacks = System.currentTimeMillis();
            }

            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setBulkBind(true);
            }
            for (int i = 0; i < mBindOnResumeCallbacks.size(); i++) {
                mBindOnResumeCallbacks.get(i).run();
            }
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setBulkBind(false);
            }
            mBindOnResumeCallbacks.clear();
            if (DEBUG_RESUME_TIME) {
                Log.d(TAG, "Time spent processing callbacks in onResume: " +
                    (System.currentTimeMillis() - startTimeCallbacks));
            }
        }
        if (mOnResumeCallbacks.size() > 0) {
            for (int i = 0; i < mOnResumeCallbacks.size(); i++) {
                mOnResumeCallbacks.get(i).run();
            }
            mOnResumeCallbacks.clear();
        }

        // Reset the pressed state of icons that were locked in the press state while activities
        // were launching
        if (mWaitingForResume != null) {
            // Resets the previous workspace icon press state
            mWaitingForResume.setStayPressed(false);
        }

        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated in the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        getWorkspace().reinflateWidgetsIfNecessary();

        // Process any items that were added while Launcher was away.
        InstallShortcutReceiver.disableAndFlushInstallQueue(this);

        // Update the voice search button proxy
        updateVoiceButtonProxyVisible(false);

        // Again, as with the above scenario, it's possible that one or more of the global icons
        // were updated in the wrong orientation.
        updateGlobalIcons();
        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onResume: " + (System.currentTimeMillis() - startTime));
        }

        if (mWorkspace.getCustomContentCallbacks() != null) {
            // If we are resuming and the custom content is the current page, we call onShow().
            // It is also poassible that onShow will instead be called slightly after first layout
            // if PagedView#setRestorePage was set to the custom content page in onCreate().
            if (mWorkspace.isOnOrMovingToCustomContent()) {
                mWorkspace.getCustomContentCallbacks().onShow(true);
            }
        }
        mWorkspace.updateInteractionForState();
        mWorkspace.onResume();

        PackageInstallerCompat.getInstance(this).onResume();
	
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        InstallShortcutReceiver.enableInstallQueue();
        PackageInstallerCompat.getInstance(this).onPause();

        super.onPause();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onPause: this = " + this);
        }

        /// M: Call the appropriate callback for the IMtkWidget on the current page when we pause Launcher.
        mWorkspace.onPauseWhenShown(mWorkspace.getCurrentPage());
        mPaused = true;
        mDragController.cancelDrag();
        mDragController.resetLastGestureUpTime();

        // We call onHide() aggressively. The custom content callbacks should be able to
        // debounce excess onHide calls.
        if (mWorkspace.getCustomContentCallbacks() != null) {
            mWorkspace.getCustomContentCallbacks().onHide();
        }
    }

    QSBScroller mQsbScroller = new QSBScroller() {
        int scrollY = 0;

        @Override
        public void setScrollY(int scroll) {
            scrollY = scroll;

            if (mWorkspace.isOnOrMovingToCustomContent()) {
                mSearchDropTargetBar.setTranslationY(- scrollY);
                getQsbBar().setTranslationY(-scrollY);
            }
        }
    };

    public void resetQSBScroll() {
        mSearchDropTargetBar.animate().translationY(0).start();
        getQsbBar().animate().translationY(0).start();
    }

    public interface CustomContentCallbacks {
        // Custom content is completely shown. {@code fromResume} indicates whether this was caused
        // by a onResume or by scrolling otherwise.
        public void onShow(boolean fromResume);

        // Custom content is completely hidden
        public void onHide();

        // Custom content scroll progress changed. From 0 (not showing) to 1 (fully showing).
        public void onScrollProgressChanged(float progress);

        // Indicates whether the user is allowed to scroll away from the custom content.
        boolean isScrollingAllowed();
    }

    protected boolean hasSettings() {
        return false;
    }

    protected void startSettings() {
        /// M: [ALPS01233906] Start settings activity when clicking "SETTINGS" button in overview mode.
        Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivitySafely(null, settings, "startSettings");
    }

    public interface QSBScroller {
        public void setScrollY(int scrollY);
    }

    public QSBScroller addToCustomContentPage(View customContent,
            CustomContentCallbacks callbacks, String description) {
        mWorkspace.addToCustomContentPage(customContent, callbacks, description);
        return mQsbScroller;
    }

    // The custom content needs to offset its content to account for the QSB
    public int getTopOffsetForCustomContent() {
        return mWorkspace.getPaddingTop();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onRetainNonConfigurationInstance: mSavedState = "
                    + mSavedState + ", mSavedInstanceState = " + mSavedInstanceState);
        }
        // Flag the loader to stop early before switching
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
        }
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.surrender();
        }
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHasFocus = hasFocus;
    }

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (LauncherLog.DEBUG_KEY) {
            LauncherLog.d(TAG, " onKeyDown: KeyCode = " + keyCode + ", KeyEvent = " + event
                    + ", uniChar = " + uniChar + ", handled = " + handled + ", isKeyNotWhitespace = "
                    + isKeyNotWhitespace);
        }

        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }

        // Eat the long press event so the keyboard doesn't come up.
        if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
            return true;
        }

        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type
     * State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (int i = 0; i < stateValues.length; i++) {
            if (stateValues[i].ordinal() == stateOrdinal) {
                state = stateValues[i];
                break;
            }
        }
        return state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    @SuppressWarnings("unchecked")
    private void restoreState(Bundle savedState) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "restoreState: savedState = " + savedState);
        }

        if (savedState == null) {
            return;
        }

        State state = intToState(savedState.getInt(RUNTIME_STATE, State.WORKSPACE.ordinal()));
        if (state == State.APPS_CUSTOMIZE) {
            mOnResumeState = State.APPS_CUSTOMIZE;
        }

        int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN,
                PagedView.INVALID_RESTORE_PAGE);
        if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
            mWorkspace.setRestorePage(currentScreen);
        }

        /// M: Save last restore workspace screen.
        mCurrentWorkSpaceScreen = currentScreen;

        final long pendingAddContainer = savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final long pendingAddScreen = savedState.getLong(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            mPendingAddInfo.screenId = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mPendingAddInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            mPendingAddInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            mPendingAddWidgetInfo = savedState.getParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO);
            mPendingAddWidgetId = savedState.getInt(RUNTIME_STATE_PENDING_ADD_WIDGET_ID);
            setWaitingForResult(true);
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }

        // Restore the AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String curTab = savedState.getString("apps_customize_currentTab");
            if (curTab != null) {
                mAppsCustomizeTabHost.setContentTypeImmediate(
                        mAppsCustomizeTabHost.getContentTypeForTabTag(curTab));
                mAppsCustomizeContent.loadAssociatedPages(
                        mAppsCustomizeContent.getCurrentPage());
            }

            int currentIndex = savedState.getInt("apps_customize_currentIndex");
            mAppsCustomizeContent.restorePageForIndex(currentIndex);
        }
        mItemIdToViewId = (HashMap<Integer, Integer>)
                savedState.getSerializable(RUNTIME_STATE_VIEW_IDS);
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        mLauncherView = findViewById(R.id.launcher);
        mFocusHandler = (FocusIndicatorView) findViewById(R.id.focus_indicator);
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) mDragLayer.findViewById(R.id.workspace);
        mWorkspace.setPageSwitchListener(this);
        mPageIndicators = mDragLayer.findViewById(R.id.page_indicator);

        mLauncherView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mWorkspaceBackgroundDrawable = getResources().getDrawable(R.drawable.workspace_bg);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mHotseat = (Hotseat) findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setup(this);
            mHotseat.setOnLongClickListener(this);
        }

        mOverviewPanel = (ViewGroup) findViewById(R.id.overview_panel);
        View widgetButton = findViewById(R.id.widget_button);
        widgetButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickAddWidgetButton(arg0);
                }
            }
        });
        widgetButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View wallpaperButton = findViewById(R.id.wallpaper_button);
        wallpaperButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickWallpaperPicker(arg0);
                }
            }
        });
        wallpaperButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View settingsButton = findViewById(R.id.settings_button);
        if (hasSettings()) {
            settingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    if (!mWorkspace.isSwitchingState()) {
                        onClickSettingsButton(arg0);
                    }
                }
            });
            settingsButton.setOnTouchListener(getHapticFeedbackTouchListener());
        } else {
            settingsButton.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                    widgetButton.getLayoutParams();
            lp.gravity = Gravity.END | Gravity.TOP;
            widgetButton.requestLayout();
        }

        mOverviewPanel.setAlpha(0f);

        /// M: [OP09] add editmode and hide app click listener.
        if (mSupportEditAndHideApps) {
            final View[] overviewPanelButtons = new View[] {
                    wallpaperButton, widgetButton, settingsButton, null, null
            };
            LauncherExtPlugin.getInstance().getWorkspaceExt(this.getApplicationContext())
                    .customizeOverviewPanel(mOverviewPanel, overviewPanelButtons);
            final View editAppsButton = overviewPanelButtons[3];
            final View hideAppsButton = overviewPanelButtons[4];

            editAppsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    LauncherLog.d(TAG, "onClick:  v = " + v);
                    enterEditMode();
                    showAllApps(false, AppsCustomizePagedView.ContentType.Applications, true);
                }
            });
            editAppsButton.setOnTouchListener(getHapticFeedbackTouchListener());

            hideAppsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    LauncherLog.d(TAG, "onClick:  arg0 = " + arg0);
                    startHideAppsActivity();
                }
            });
            hideAppsButton.setOnTouchListener(getHapticFeedbackTouchListener());
        }

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(dragController);
        dragController.addDragListener(mWorkspace);

        // Get the search/delete bar
        mSearchDropTargetBar = (SearchDropTargetBar)
                mDragLayer.findViewById(R.id.search_drop_target_bar);

        // Setup AppsCustomize
        mAppsCustomizeTabHost = (AppsCustomizeTabHost) findViewById(R.id.apps_customize_pane);
        mAppsCustomizeContent = (AppsCustomizePagedView)
                mAppsCustomizeTabHost.findViewById(R.id.apps_customize_pane_content);
        mAppsCustomizeContent.setup(this, dragController);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.setup(this, dragController);
        }

        if (getResources().getBoolean(R.bool.debug_memory_enabled)) {
            Log.v(TAG, "adding WeightWatcher");
            mWeightWatcher = new WeightWatcher(this);
            mWeightWatcher.setAlpha(0.5f);
            ((FrameLayout) mLauncherView).addView(mWeightWatcher,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM)
            );

            boolean show = shouldShowWeightWatcher();
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Sets the all apps button. This method is called from {@link Hotseat}.
     */
    public void setAllAppsButton(View allAppsButton) {
        mAllAppsButton = allAppsButton;
    }

    public View getAllAppsButton() {
        return mAllAppsButton;
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) mInflater.inflate(layoutResId, parent, false);
        favorite.applyFromShortcutInfo(info, mIconCache, true);
        favorite.setOnClickListener(this);
        favorite.setOnFocusChangeListener(mFocusHandler);
        return favorite;
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, long screenId, int cellX,
            int cellY) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screenId);

        boolean foundCellSpan = false;

        ShortcutInfo info = mModel.infoFromShortcutIntent(this, data, null);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "completeAddShortcut: info = " + info + ", data = " + data
                    + ", container = " + container + ", screenId = " + screenId + ", cellX = "
                    + cellX + ", cellY = " + cellY + ", layout = " + layout);
        }

        if (info == null) {
            return;
        }
        final View view = createShortcut(info);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null,null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screenId, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screenId, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
        }
    }

    static int[] getSpanForWidget(Context context, ComponentName component, int minWidth,
            int minHeight) {
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(context, component, null);
        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested
        int requiredWidth = minWidth + padding.left + padding.right;
        int requiredHeight = minHeight + padding.top + padding.bottom;
        return CellLayout.rectToCell(requiredWidth, requiredHeight, null);
    }

    static int[] getSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minResizeWidth, info.minResizeHeight);
    }

    static int[] getSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minResizeWidth,
                info.minResizeHeight);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(final int appWidgetId, long container, long screenId,
            AppWidgetHostView hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "completeAddAppWidget: appWidgetId = " + appWidgetId
                    + ", container = " + container + ", screenId = " + screenId);
        }

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = getCellLayout(container, screenId);

        int[] minSpanXY = getMinSpanForWidget(this, appWidgetInfo);
        int[] spanXY = getSpanForWidget(this, appWidgetInfo);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        int[] finalSpan = new int[2];
        boolean foundCellSpan = false;
        if (mPendingAddInfo.cellX >= 0 && mPendingAddInfo.cellY >= 0) {
            cellXY[0] = mPendingAddInfo.cellX;
            cellXY[1] = mPendingAddInfo.cellY;
            spanXY[0] = mPendingAddInfo.spanX;
            spanXY[1] = mPendingAddInfo.spanY;
            foundCellSpan = true;
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(
                    touchXY[0], touchXY[1], minSpanXY[0], minSpanXY[1], spanXY[0],
                    spanXY[1], cellXY, finalSpan);
            spanXY[0] = finalSpan[0];
            spanXY[1] = finalSpan[1];
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, minSpanXY[0], minSpanXY[1]);
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new AsyncTask<Void, Void, Void>() {
                    public Void doInBackground(Void ... args) {
                        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
            }
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId,
                appWidgetInfo.provider);
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];
        launcherInfo.minSpanX = mPendingAddInfo.minSpanX;
        launcherInfo.minSpanY = mPendingAddInfo.minSpanY;
        launcherInfo.user = mAppWidgetManager.getUser(appWidgetInfo);

        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screenId, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            if (hostView == null) {
                // Perform actual inflation because we're live
                launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
                launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            } else {
                // The AppWidgetHostView has already been inflated and instantiated
                launcherInfo.hostView = hostView;
            }

            launcherInfo.hostView.setTag(launcherInfo);
            launcherInfo.hostView.setVisibility(View.VISIBLE);
            launcherInfo.notifyWidgetSizeChanged(this);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screenId, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());

            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
        resetAddInfo();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                LauncherLog.d(TAG, "ACTION_SCREEN_OFF: mPendingAddInfo = " + mPendingAddInfo
                        + ", mAppsCustomizeTabHost = " + mAppsCustomizeTabHost + ", this = " + this);
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateRunning();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsCustomizeTabHost != null && mPendingAddInfo.container == ItemInfo.NO_ID) {
                    showWorkspace(false);
                }

                /// M: [OP09]need to exit edit mode if needed.
                if (isInEditMode()) {
                    exitEditMode();
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateRunning();
            } else if (ENABLE_DEBUG_INTENTS && DebugIntents.DELETE_DATABASE.equals(action)) {
                mModel.resetLoadedState(false, true);
                mModel.startLoader(false, PagedView.INVALID_RESTORE_PAGE,
                        LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE);
            } else if (ENABLE_DEBUG_INTENTS && DebugIntents.MIGRATE_DATABASE.equals(action)) {
                mModel.resetLoadedState(false, true);
                mModel.startLoader(false, PagedView.INVALID_RESTORE_PAGE,
                        LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE
                                | LauncherModel.LOADER_FLAG_MIGRATE_SHORTCUTS);
            } else if (LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                    || LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
                getModel().forceReload();
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onAttachedToWindow.");
        }

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // For handling managed profiles
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED);
        if (ENABLE_DEBUG_INTENTS) {
            filter.addAction(DebugIntents.DELETE_DATABASE);
            filter.addAction(DebugIntents.MIGRATE_DATABASE);
        }
        registerReceiver(mReceiver, filter);
        FirstFrameAnimatorHelper.initializeDrawListener(getWindow().getDecorView());
        setupTransparentSystemBarsForLmp();
        mAttached = true;
        mVisible = true;
    }

    /**
     * Sets up transparent navigation and status bars in LMP.
     * This method is a no-op for other platform versions.
     */
    @TargetApi(19)
    private void setupTransparentSystemBarsForLmp() {
        // TODO(sansid): use the APIs directly when compiling against L sdk.
        // Currently we use reflection to access the flags and the API to set the transparency
        // on the System bars.
        if (Utilities.isLmpOrAbove()) {
            try {
                getWindow().getAttributes().systemUiVisibility |=
                        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                Field drawsSysBackgroundsField = WindowManager.LayoutParams.class.getField(
                        "FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS");
                getWindow().addFlags(drawsSysBackgroundsField.getInt(null));

                Method setStatusBarColorMethod =
                        Window.class.getDeclaredMethod("setStatusBarColor", int.class);
                Method setNavigationBarColorMethod =
                        Window.class.getDeclaredMethod("setNavigationBarColor", int.class);
                setStatusBarColorMethod.invoke(getWindow(), Color.TRANSPARENT);
                setNavigationBarColorMethod.invoke(getWindow(), Color.TRANSPARENT);
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "NoSuchFieldException while setting up transparent bars");
            } catch (NoSuchMethodException ex) {
                Log.w(TAG, "NoSuchMethodException while setting up transparent bars");
            } catch (IllegalAccessException e) {
                Log.w(TAG, "IllegalAccessException while setting up transparent bars");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "IllegalArgumentException while setting up transparent bars");
            } catch (InvocationTargetException e) {
                Log.w(TAG, "InvocationTargetException while setting up transparent bars");
            } finally {}
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onDetachedFromWindow.");
        }

        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateRunning();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateRunning();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            mAppsCustomizeTabHost.onWindowVisible();
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy.
                observer.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                    private boolean mStarted = false;
                    public void onDraw() {
                        if (mStarted) return;
                        mStarted = true;
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);
                        final ViewTreeObserver.OnDrawListener listener = this;
                        mWorkspace.post(new Runnable() {
                                public void run() {
                                    if (mWorkspace != null &&
                                            mWorkspace.getViewTreeObserver() != null) {
                                        mWorkspace.getViewTreeObserver().
                                                removeOnDrawListener(listener);
                                    }
                                }
                            });
                        return;
                    }
                });
            }
            clearTypedText();
        }
    }

    private void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    private void updateRunning() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key: mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (v instanceof Advanceable) {
                       postDelayed(new Runnable() {
                           public void run() {
                               ((Advanceable) v).advance();
                           }
                       }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            }
        }
    };

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addWidgetToAutoAdvanceIfNeeded hostView = " + hostView + ", appWidgetInfo = "
                    + appWidgetInfo);
        }

        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateRunning();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeWidgetToAutoAdvance hostView = " + hostView);
        }

        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateRunning();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "removeAppWidget launcherInfo = " + launcherInfo);
        }

        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
    }

    void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public Workspace getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public ViewGroup getOverviewPanel() {
        return mOverviewPanel;
    }

    public SearchDropTargetBar getSearchBar() {
        return mSearchDropTargetBar;
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    protected SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    public void closeSystemDialogs() {
        getWindow().closeAllPanels();

        // Whatever we were doing is hereby canceled.
        setWaitingForResult(false);
    }
    
    /**
     * M: Pop up message allows to you add only one IMtkWidget for the given AppWidgetInfo.
     *
     * @param info The information of the IMtkWidget.
     */
    void showOnlyOneWidgetMessage(PendingAddWidgetInfo info) {
        try {
            PackageManager pm = getPackageManager();
            String label = pm.getApplicationLabel(pm.getApplicationInfo(info.componentName.getPackageName(), 0)).toString();
            Toast.makeText(this, getString(R.string.one_video_widget, label), Toast.LENGTH_SHORT).show();
        } catch (PackageManager.NameNotFoundException e) {
            LauncherLog.e(TAG, "Got NameNotFounceException when showOnlyOneWidgetMessage.", e);
        }
        // Exit spring loaded mode if necessary after adding the widget.
        exitSpringLoadedDragModeDelayed(false, 0, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
        }
        super.onNewIntent(intent);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onNewIntent: intent = " + intent);
        }

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            final boolean alreadyOnHome = mHasFocus && ((intent.getFlags() &
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                    != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            if (mWorkspace == null) {
                // Can be cases where mWorkspace is null, this prevents a NPE
                LauncherLog.d(TAG, "onNewIntent processIntent run() mWorkspace == null");
                return;
            }
            Folder openFolder = mWorkspace.getOpenFolder();
            // In all these cases, only animate if we're already on home
            mWorkspace.exitWidgetResizeMode();
            if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                    openFolder == null && shouldMoveToDefaultScreenOnHomeIntent()) {
                /// M: Call the appropriate callback for the IMtkWidget on the current page
                /// when press "Home" key move to default screen.
                mWorkspace.moveOutAppWidget(mWorkspace.getCurrentPage());

                mWorkspace.moveToDefaultScreen(true);
            }

            closeFolder();
            exitSpringLoadedDragMode();

            // If we are already on home, then just animate back to the workspace,
            // otherwise, just wait until onResume to set the state back to Workspace
            if (alreadyOnHome) {
                showWorkspace(true);
            } else {
                mOnResumeState = State.WORKSPACE;
            }

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            ///M. ALPS01849521, If it is widget page, don't reset.
            AppsCustomizePagedView.ContentType contentType = mAppsCustomizeContent.getContentType();

            // Reset the apps customize page
            if (!alreadyOnHome && mAppsCustomizeTabHost != null
                && contentType != AppsCustomizePagedView.ContentType.Widgets) {
                mAppsCustomizeTabHost.reset();
            }
            ///M.

            /// M: need to exit edit mode if needed, for op09.
            if (isInEditMode()) {
                exitEditMode();
            }
            onHomeIntent();
        }

        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onNewIntent: " + (System.currentTimeMillis() - startTime));
        }
    }

    /**
     * Override point for subclasses to prevent movement to the default screen when the home
     * button is pressed. Used (for example) in GEL, to prevent movement during a search.
     */
    protected boolean shouldMoveToDefaultScreenOnHomeIntent() {
        return true;
    }

    /**
     * Override point for subclasses to provide custom behaviour for when a home intent is fired.
     */
    protected void onHomeIntent() {
        // Do nothing
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onRestoreInstanceState: state = " + state
                    + ", mSavedInstanceState = " + mSavedInstanceState);
        }

        for (int page: mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mWorkspace.getChildCount() > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN,
                    mWorkspace.getCurrentPageOffsetFromCustomContent());
        } else { /// M: If workspcae no initialized, use saved last restore workspace screen.
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mCurrentWorkSpaceScreen);
        }
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        closeFolder();

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screenId > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putLong(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screenId);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, mPendingAddInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, mPendingAddInfo.spanY);
            outState.putParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO, mPendingAddWidgetInfo);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_WIDGET_ID, mPendingAddWidgetId);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }

        // Save the current AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            AppsCustomizePagedView.ContentType type = mAppsCustomizeContent.getContentType();
            String currentTabTag = mAppsCustomizeTabHost.getTabTagForContentType(type);
            if (currentTabTag != null) {
                outState.putString("apps_customize_currentTab", currentTabTag);
            }
            int currentIndex = mAppsCustomizeContent.getSaveInstanceStateIndex();
            outState.putInt("apps_customize_currentIndex", currentIndex);
        }
        outState.putSerializable(RUNTIME_STATE_VIEW_IDS, mItemIdToViewId);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, " onSaveInstanceState: outState = " + outState);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "(Launcher)onDestroy: this = " + this);
        }

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherAppState app = (LauncherAppState.getInstance());

        // It's possible to receive onDestroy after a new Launcher activity has
        // been created. In this case, don't interfere with the new Launcher.
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
            app.setLauncher(null);
        }

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        // Disconnect any of the callbacks and drawables associated with ItemInfos on the workspace
        // to prevent leaking Launcher activities on orientation change.
        if (mModel != null) {
            mModel.unbindItemInfosAndClearQueuedBindRunnables();
        }

        getContentResolver().unregisterContentObserver(mWidgetObserver);
        unregisterReceiver(mCloseSystemDialogsReceiver);

        mDragLayer.clearAllResizeFrames();
        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllWorkspaceScreens();
        mWorkspace = null;
        mDragController = null;

        PackageInstallerCompat.getInstance(this).onStop();
        LauncherAnimUtils.onDestroyActivity();
        
        /**M: added for unread feature, load and bind unread info.@{**/
        if (getResources().getBoolean(R.bool.config_unreadSupport)) {
            if(mUnreadLoader != null){
                mUnreadLoader.initialize(null);
            }
        }
        /**@}**/

        ///M. alps01963566, before animation start, activity is destory.
        ///So, cancel it here.
        if (mStateAnimation != null) {
            mStateAnimation.setDuration(0);
            mStateAnimation.cancel();
        }
        ///M.
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) {
            setWaitingForResult(true);
        }
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "startSearch.");
        }
        showWorkspace(true);

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }
        Rect sourceBounds = new Rect();
        if (mSearchDropTargetBar != null) {
            sourceBounds = mSearchDropTargetBar.getSearchBarBounds();
        }

        boolean clearTextImmediately = startSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
        if (clearTextImmediately) {
            clearTypedText();
        }
    }

    /**
     * Start a text search.
     *
     * @return {@code true} if the search will start immediately, so any further keypresses
     * will be handled directly by the search UI. {@code false} if {@link Launcher} should continue
     * to buffer keypresses.
     */
    public boolean startSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        startGlobalSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
        return false;
    }

    /**
     * Starts the global search activity. This code is a copied from SearchManager
     */
    private void startGlobalSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        final SearchManager searchManager =
            (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();
        if (globalSearchActivity == null) {
            Log.w(TAG, "No global search activity found.");
            return;
        }
        Intent intent = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(globalSearchActivity);
        // Make sure that we have a Bundle to put source in
        if (appSearchData == null) {
            appSearchData = new Bundle();
        } else {
            appSearchData = new Bundle(appSearchData);
        }
        // Set source to package name of app that starts global search, if not set already.
        if (!appSearchData.containsKey("source")) {
            appSearchData.putString("source", getPackageName());
        }
        intent.putExtra(SearchManager.APP_DATA, appSearchData);
        if (!TextUtils.isEmpty(initialQuery)) {
            intent.putExtra(SearchManager.QUERY, initialQuery);
        }
        if (selectInitialQuery) {
            intent.putExtra(SearchManager.EXTRA_SELECT_QUERY, selectInitialQuery);
        }
        intent.setSourceBounds(sourceBounds);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Global search activity not found: " + globalSearchActivity);
        }
    }

    public boolean isOnCustomContent() {
        return mWorkspace.isOnOrMovingToCustomContent();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (!isOnCustomContent()) {
            // Close any open folders
            closeFolder();
            // Stop resizing any widgets
            mWorkspace.exitWidgetResizeMode();
            if (mState == State.WORKSPACE && !mWorkspace.isInOverviewMode()) {
                // Show the overview mode
                showOverviewMode(true);
            } else {
                showWorkspace(true);
            }
        }
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        boolean isLocked = isWorkspaceLocked();
        mWorkspaceLoading = value;
        if (isLocked != isWorkspaceLocked()) {
            onWorkspaceLockedChanged();
        }
    }

    private void setWaitingForResult(boolean value) {
        boolean isLocked = isWorkspaceLocked();
        mWaitingForResult = value;
        if (isLocked != isWorkspaceLocked()) {
            onWorkspaceLockedChanged();
        }
    }

    protected void onWorkspaceLockedChanged() { }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        mPendingAddInfo.screenId = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.minSpanX = mPendingAddInfo.minSpanY = -1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetImpl(final int appWidgetId, final ItemInfo info,
            final AppWidgetHostView boundWidget, final AppWidgetProviderInfo appWidgetInfo) {
        addAppWidgetImpl(appWidgetId, info, boundWidget, appWidgetInfo, 0);
    }

    void addAppWidgetImpl(final int appWidgetId, final ItemInfo info,
            final AppWidgetHostView boundWidget, final AppWidgetProviderInfo appWidgetInfo, int
            delay) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addAppWidgetImpl: appWidgetId = " + appWidgetId
                    + ", info = " + info + ", boundWidget = " + boundWidget
                    + ", appWidgetInfo = " + appWidgetInfo + ", delay = " + delay);
        }
        if (appWidgetInfo.configure != null) {
            mPendingAddWidgetInfo = appWidgetInfo;
            mPendingAddWidgetId = appWidgetId;
            /**M:ALPS01808434, To resolve gallery widget can not be 
             * added to home screen after changing the language.@{*/
            /// M.ALPS01808563. use the setWaitingForResult API
            setWaitingForResult(true);
            /**@}**/

            // Launch over to configure widget, if needed
            mAppWidgetManager.startConfigActivity(appWidgetInfo, appWidgetId, this,
                    mAppWidgetHost, REQUEST_CREATE_APPWIDGET);

        } else {
            // Otherwise just add it
            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    // Exit spring loaded mode if necessary after adding the widget
                    exitSpringLoadedDragModeDelayed(true, EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT,
                            null);
                }
            };
            completeAddAppWidget(appWidgetId, info.container, info.screenId, boundWidget,
                    appWidgetInfo);
            mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete, delay, false);
        }
    }

    protected void moveToCustomContentScreen(boolean animate) {
        // Close any folders that may be open.
        closeFolder();
        mWorkspace.moveToCustomContentScreen(animate);
    }
    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void processShortcutFromDrop(ComponentName componentName, long container, long screenId,
            int[] cell, int[] loc) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "processShortcutFromDrop componentName = " + componentName + ", container = " + container
                    + ", screenId = " + screenId);
        }

        resetAddInfo();
        mPendingAddInfo.container = container;
        mPendingAddInfo.screenId = screenId;
        mPendingAddInfo.dropPos = loc;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        processShortcut(createShortcutIntent);
    }

    /**
     * Process a widget drop.
     *
     * @param info The PendingAppWidgetInfo of the widget being added.
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, long screenId,
            int[] cell, int[] span, int[] loc) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addAppWidgetFromDrop: info = " + info + ", container = " + container + ", screenId = "
                    + screenId);
        }

        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        mPendingAddInfo.screenId = info.screenId = screenId;
        mPendingAddInfo.dropPos = loc;
        mPendingAddInfo.minSpanX = info.minSpanX;
        mPendingAddInfo.minSpanY = info.minSpanY;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        if (span != null) {
            mPendingAddInfo.spanX = span[0];
            mPendingAddInfo.spanY = span[1];
        }

        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetImpl(appWidgetId, info, hostView, info.info);
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId, info.info, options);
            if (success) {
                addAppWidgetImpl(appWidgetId, info, null, info.info);
            } else {
                mPendingAddWidgetInfo = info.info;
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                mAppWidgetManager.getUser(mPendingAddWidgetInfo)
                    .addToIntent(intent, AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE);
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    void processShortcut(Intent intent) {
        Utilities.startActivityForResultSafely(this, intent, REQUEST_CREATE_SHORTCUT);
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    FolderIcon addFolder(CellLayout layout, long container, final long screenId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screenId, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
            FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo, mIconCache);
        mWorkspace.addInScreen(newFolder, container, screenId, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    protected ComponentName getWallpaperPickerComponent() {
        return new ComponentName(getPackageName(), LauncherWallpaperPickerActivity.class.getName());
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (LauncherLog.DEBUG_KEY) {
            LauncherLog.d(TAG, "dispatchKeyEvent: keyEvent = " + event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (isPropertyEnabled(DUMP_STATE_PROPERTY)) {
                        dumpState();
                        return true;
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Back key pressed, mState = " + mState + ", mOnResumeState = " + mOnResumeState);
        }
        /// M: don't allow respond back key if workspace is in switching state
        if (!mWorkspace.isFinishedSwitchingState()) {
            LauncherLog.d(TAG, "The workspace is in switching state when back key pressed, directly return.");
            return;
        }
        if (isAllAppsVisible()) {
            /// M: [OP09]exit edit mode if the apps customize pane is in edit mode.
            if (isInEditMode()) {
                exitEditMode();
            } else {
                if (mAppsCustomizeContent.getContentType() ==
                        AppsCustomizePagedView.ContentType.Applications) {
                    if (mSupportEditAndHideApps) {
                        closeFolder();
                    }
                    showWorkspace(true);
                } else {
                    showOverviewMode(true);
                }
            }
        } else if (mWorkspace.isInOverviewMode()) {
            mWorkspace.exitOverviewMode(true);
        } else if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else {
                closeFolder();
            }
        } else {
            mWorkspace.exitWidgetResizeMode();

            // Back button is a no-op here, but give at least some feedback for the button press
            mWorkspace.showOutlinesTemporarily();
        }

        /// M. ALPS01908766, reset over sroll when press back key.
        if ((mWorkspace != null) && (mWorkspace.getChildCount() > 0)) {
            View child = mWorkspace.getChildAt(0);
            if (child instanceof CellLayout) {
                ((CellLayout) mWorkspace.getChildAt(0)).setOverScrollAmount(0, false);
                ((CellLayout) mWorkspace.getChildAt(mWorkspace.getChildCount() - 1))
                    .setOverScrollAmount(0, false);
            }
        }
        ///M.
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onAppWidgetReset.");
        }

        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).

        /// M: add systrace to analyze application launche time.
        Trace.traceBegin(Trace.TRACE_TAG_INPUT, "Launcher.onClick");

        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "Click on view " + v);
        }

        if (v.getWindowToken() == null) {
            LauncherLog.d(TAG, "Click on a view with no window token, directly return.");
            return;
        }

        if (!mWorkspace.isFinishedSwitchingState()) {
            LauncherLog.d(TAG, "The workspace is in switching state when clicking on view, directly return.");
            return;
        }

        if (v instanceof Workspace) {
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(true);
            }
            return;
        }

        if (v instanceof CellLayout) {
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(mWorkspace.indexOfChild(v), true);
            }
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            onClickAppShortcut(v);
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                onClickFolderIcon(v);
            }
        } else if (v == mAllAppsButton) {
            onClickAllAppsButton(v);
        } else if (tag instanceof AppInfo && !mAllAppAnimaterState) {
            startAppShortcutOrInfoActivity(v);
        } else if (tag instanceof LauncherAppWidgetInfo) {
            if (v instanceof PendingAppWidgetHostView) {
                onClickPendingWidget((PendingAppWidgetHostView) v);
            }
        }
        /// M: add systrace to analyze application launche time.
        Trace.traceEnd(Trace.TRACE_TAG_INPUT);
    }

    public void onClickPagedViewIcon(View v) {
        startAppShortcutOrInfoActivity(v);
    }

    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    /**
     * Event handler for the app widget view which has not fully restored.
     */
    public void onClickPendingWidget(final PendingAppWidgetHostView v) {
        final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
        if (v.isReadyForClickSetup()) {
            int widgetId = info.appWidgetId;
            AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(widgetId);
            if (appWidgetInfo != null) {
                mPendingAddWidgetInfo = appWidgetInfo;
                mPendingAddInfo.copyFrom(info);
                mPendingAddWidgetId = widgetId;

                AppWidgetManagerCompat.getInstance(this).startConfigActivity(appWidgetInfo,
                        info.appWidgetId, this, mAppWidgetHost, REQUEST_RECONFIGURE_APPWIDGET);
            }
        } else if (info.installProgress < 0) {
            // The install has not been queued
            final String packageName = info.providerName.getPackageName();
            showBrokenAppInstallDialog(packageName,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
                    }
                });
        } else {
            // Download has started.
            final String packageName = info.providerName.getPackageName();
            startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
        }
    }

    /**
     * Event handler for the search button
     *
     * @param v The view that was clicked.
     */
    public void onClickSearchButton(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onClickSearchButton v = " + v);
        }

        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        onSearchRequested();
    }

    /**
     * Event handler for the voice button
     *
     * @param v The view that was clicked.
     */
    public void onClickVoiceButton(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onClickVoiceButton v = " + v);
        }

        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        startVoice();
    }

    public void startVoice() {
        try {
            final SearchManager searchManager =
                    (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            ComponentName activityName = searchManager.getGlobalSearchActivity();
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activityName != null) {
                intent.setPackage(activityName.getPackageName());
            }
            startActivity(null, intent, "onClickVoiceButton");
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySafely(null, intent, "onClickVoiceButton");
        }
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    protected void onClickAllAppsButton(View v) {
        if (LOGD) Log.d(TAG, "onClickAllAppsButton");
        if (isAllAppsVisible()) {
            showWorkspace(true);
        } else {
            showAllApps(true, AppsCustomizePagedView.ContentType.Applications, false);
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "[All apps launch time][Start] onClickAllAppsButton.");
            }
        }    
    }

    private void showBrokenAppInstallDialog(final String packageName,
            DialogInterface.OnClickListener onSearchClickListener) {
        new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault))
            .setTitle(R.string.abandoned_promises_title)
            .setMessage(R.string.abandoned_promise_explanation)
            .setPositiveButton(R.string.abandoned_search, onSearchClickListener)
            .setNeutralButton(R.string.abandoned_clean_this,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        final UserHandleCompat user = UserHandleCompat.myUserHandle();
                        mWorkspace.removeAbandonedPromise(packageName, user);
                    }
                })
            .create().show();
        return;
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link ShortcutInfo}.
     */
    protected void onClickAppShortcut(final View v) {
        if (LOGD) Log.d(TAG, "onClickAppShortcut");
        Object tag = v.getTag();
        if (!(tag instanceof ShortcutInfo)) {
            throw new IllegalArgumentException("Input must be a Shortcut");
        }

        // Open shortcut
        final ShortcutInfo shortcut = (ShortcutInfo) tag;
        final Intent intent = shortcut.intent;

        // Check for special shortcuts
        if (intent.getComponent() != null) {
            final String shortcutClass = intent.getComponent().getClassName();

            if (shortcutClass.equals(MemoryDumpActivity.class.getName())) {
                MemoryDumpActivity.startDump(this);
                return;
            } else if (shortcutClass.equals(ToggleWeightWatcher.class.getName())) {
                toggleShowWeightWatcher();
                return;
            }
        }

        // Check for abandoned promise
        if ((v instanceof BubbleTextView)
                && shortcut.isPromise()
                && !shortcut.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE)) {
            showBrokenAppInstallDialog(
                    shortcut.getTargetComponent().getPackageName(),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startAppShortcutOrInfoActivity(v);
                        }
                    });
            return;
        }

        // Start activities
        startAppShortcutOrInfoActivity(v);
    }

    private void startAppShortcutOrInfoActivity(View v) {
        Object tag = v.getTag();
        final ShortcutInfo shortcut;
        final Intent intent;
        if (tag instanceof ShortcutInfo) {
            shortcut = (ShortcutInfo) tag;
            intent = shortcut.intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

        } else if (tag instanceof AppInfo) {
            shortcut = null;
            intent = ((AppInfo) tag).intent;
        } else {
            throw new IllegalArgumentException("Input must be a Shortcut or AppInfo");
        }

        boolean success = startActivitySafely(v, intent, tag);
        mStats.recordLaunch(intent, shortcut);

        if (success && v instanceof BubbleTextView) {
            mWaitingForResume = (BubbleTextView) v;
            mWaitingForResume.setStayPressed(true);
        }
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link FolderIcon}.
     */
    protected void onClickFolderIcon(View v) {
        if (LOGD) Log.d(TAG, "onClickFolder");
        if (!(v instanceof FolderIcon)){
            throw new IllegalArgumentException("Input must be a FolderIcon");
        }

        FolderIcon folderIcon = (FolderIcon) v;
        final FolderInfo info = folderIcon.getFolderInfo();
        Folder openFolder = mWorkspace.getFolderForTag(info);

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screenId + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon);
                }
            }
        }
    }

    /**
     * Event handler for the (Add) Widgets button that appears after a long press
     * on the home screen.
     */
    protected void onClickAddWidgetButton(View view) {
        if (LOGD) Log.d(TAG, "onClickAddWidgetButton");
        showAllApps(true, AppsCustomizePagedView.ContentType.Widgets, true);
    }

    /**
     * Event handler for the wallpaper picker button that appears after a long press
     * on the home screen.
     */
    protected void onClickWallpaperPicker(View v) {
        if (LOGD) Log.d(TAG, "onClickWallpaperPicker");
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        pickWallpaper.setComponent(getWallpaperPickerComponent());
        startActivityForResult(pickWallpaper, REQUEST_PICK_WALLPAPER);
    }

    /**
     * Event handler for a click on the settings button that appears after a long press
     * on the home screen.
     */
    protected void onClickSettingsButton(View v) {
        if (LOGD) Log.d(TAG, "onClickSettingsButton");
    }

    public void onTouchDownAllAppsButton(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void performHapticFeedbackOnTouchDown(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public View.OnTouchListener getHapticFeedbackTouchListener() {
        if (mHapticFeedbackTouchListener == null) {
            mHapticFeedbackTouchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    return false;
                }
            };
        }
        return mHapticFeedbackTouchListener;
    }

    public void onDragStarted(View view) {}

    /**
     * Called when the user stops interacting with the launcher.
     * This implies that the user is now on the homescreen and is not doing housekeeping.
     */
    protected void onInteractionEnd() {}

    /**
     * Called when the user starts interacting with the launcher.
     * The possible interactions are:
     *  - open all apps
     *  - reorder an app shortcut, or a widget
     *  - open the overview mode.
     * This is a good time to stop doing things that only make sense
     * when the user is on the homescreen and not doing housekeeping.
     */
    protected void onInteractionBegin() {}

    void startApplicationDetailsActivity(ComponentName componentName, UserHandleCompat user) {
        String packageName = componentName.getPackageName();
        try {
            LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
            UserManagerCompat userManager = UserManagerCompat.getInstance(this);
            launcherApps.showAppDetailsForProfile(componentName, user);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have permission to launch settings");
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch settings");
        }
    }

    // returns true if the activity was started
    boolean startApplicationUninstallActivity(ComponentName componentName, int flags,
            UserHandleCompat user) {
        if ((flags & AppInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
            return false;
        } else {

            String packageName = componentName.getPackageName();
            String className = componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            if (user != null) {
                user.addToIntent(intent, Intent.EXTRA_USER);
            }

            Log.e(TAG, "[guo_1]startApplicationUninstallActivity:packageName" + packageName);
            startActivity(intent);
            return true;
        }
    }


    /**
     * M: Start application uninstall activity.
     */
    void startApplicationUninstallActivity(AppInfo appInfo) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "startApplicationUninstallActivity: appInfo = " + appInfo);
        }

        if ((appInfo.flags & AppInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        } else {
            String packageName = appInfo.componentName.getPackageName();
            String className = appInfo.componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    boolean startActivity(View v, Intent intent, Object tag) {
        // M: ALPS02012800 for op01
        if(intent.getComponent() != null && OP01_AFFINITY.equals(intent.getComponent().getPackageName())) {
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            boolean useLaunchAnimation = (v != null) &&
                    !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
            LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
            UserManagerCompat userManager = UserManagerCompat.getInstance(this);

            UserHandleCompat user = null;
            if (intent.hasExtra(AppInfo.EXTRA_PROFILE)) {
                long serialNumber = intent.getLongExtra(AppInfo.EXTRA_PROFILE, -1);
                user = userManager.getUserForSerialNumber(serialNumber);
            }

            Bundle optsBundle = null;
            if (useLaunchAnimation) {
                ActivityOptions opts = Utilities.isLmpOrAbove() ?
                        ActivityOptions.makeCustomAnimation(this, R.anim.task_open_enter, R.anim.no_anim) :
                        ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
                optsBundle = opts.toBundle();
            }

            if (user == null || user.equals(UserHandleCompat.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
            } else {
                // TODO Component can be null when shortcuts are supported for secondary user
                launcherApps.startActivityForProfile(intent.getComponent(), user,
                        intent.getSourceBounds(), optsBundle);
            }
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }

    boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        if (mIsSafeModeEnabled && !Utilities.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyFolderIconToImage(FolderIcon fi) {
        final int width = fi.getMeasuredWidth();
        final int height = fi.getMeasuredHeight();

        // Lazy load ImageView, Bitmap and Canvas
        if (mFolderIconImageView == null) {
            mFolderIconImageView = new ImageView(this);
        }
        if (mFolderIconBitmap == null || mFolderIconBitmap.getWidth() != width ||
                mFolderIconBitmap.getHeight() != height) {
            mFolderIconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mFolderIconCanvas = new Canvas(mFolderIconBitmap);
        }

        DragLayer.LayoutParams lp;
        if (mFolderIconImageView.getLayoutParams() instanceof DragLayer.LayoutParams) {
            lp = (DragLayer.LayoutParams) mFolderIconImageView.getLayoutParams();
        } else {
            lp = new DragLayer.LayoutParams(width, height);
        }

        // The layout from which the folder is being opened may be scaled, adjust the starting
        // view size by this scale factor.
        float scale = mDragLayer.getDescendantRectRelativeToSelf(fi, mRectForFolderAnimation);
        lp.customPosition = true;
        lp.x = mRectForFolderAnimation.left;
        lp.y = mRectForFolderAnimation.top;
        lp.width = (int) (scale * width);
        lp.height = (int) (scale * height);

        mFolderIconCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        fi.draw(mFolderIconCanvas);
        mFolderIconImageView.setImageBitmap(mFolderIconBitmap);
        if (fi.getFolder() != null) {
            mFolderIconImageView.setPivotX(fi.getFolder().getPivotXForIconAnimation());
            mFolderIconImageView.setPivotY(fi.getFolder().getPivotYForIconAnimation());
        }
        // Just in case this image view is still in the drag layer from a previous animation,
        // we remove it and re-add it.
        if (mDragLayer.indexOfChild(mFolderIconImageView) != -1) {
            mDragLayer.removeView(mFolderIconImageView);
        }
        mDragLayer.addView(mFolderIconImageView, lp);
        if (fi.getFolder() != null) {
            fi.getFolder().bringToFront();
        }
    }

    private void growAndFadeOutFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.5f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.5f);

        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        copyFolderIconToImage(fi);
        fi.setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        if (Utilities.isLmpOrAbove()) {
            oa.setInterpolator(new LogDecelerateInterpolator(100, 0));
        }
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(final FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f);

        final CellLayout cl = (CellLayout) fi.getParent().getParent();

        // We remove and re-draw the FolderIcon in-case it has changed
        mDragLayer.removeView(mFolderIconImageView);
        copyFolderIconToImage(fi);
        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    cl.clearFolderLeaveBehind();
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    mDragLayer.removeView(mFolderIconImageView);
                    fi.setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();
        FolderInfo info = folder.mInfo;

        info.opened = true;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget((DropTarget) folder);
        } else {
            Log.w(TAG, "Opening folder (" + folder + ") which already has a parent (" +
                    folder.getParent() + ").");
        }
        folder.animateOpen();
        growAndFadeOutFolderIcon(folderIcon);

        // Notify the accessibility manager that this folder "window" has appeared and occluded
        // the workspace items
        folder.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void closeFolder() {
        Folder folder = mWorkspace != null ? mWorkspace.getOpenFolder() : null;
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = null;
            if (mSupportEditAndHideApps && folder.isPageViewFolder()) {
                fi = folder.getFolderIcon();
            } else {
                fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            }
            LauncherLog.d(TAG, "closeFolder: fi = " + fi);
            shrinkAndFadeInFolderIcon(fi);
        }
        folder.animateClosed();

        // Notify the accessibility manager that this folder "window" has disappeard and no
        // longer occludeds the workspace items
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    public boolean onLongClick(View v) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onLongClick: View = " + v + ", v.getTag() = " + v.getTag()
                    + ", mState = " + mState);
        }

        if (!isDraggingEnabled()) {
            LauncherLog.d(TAG, "onLongClick: isDraggingEnabled() = " + isDraggingEnabled());
            return false;
        }

        if (isWorkspaceLocked()) {
            LauncherLog.d(TAG, "onLongClick: isWorkspaceLocked() mWorkspaceLoading " + mWorkspaceLoading
                    + ", mWaitingForResult = " + mWaitingForResult);
            return false;
        }

        if (mState != State.WORKSPACE) {
            LauncherLog.d(TAG, "onLongClick: mState != State.WORKSPACE: = " + mState);
            return false;
        }

        if (v instanceof Workspace) {
            LauncherLog.d(TAG, "v instanceof Workspace");
            if (!mWorkspace.isInOverviewMode()) {
                if (mWorkspace.enterOverviewMode()) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        CellLayout.CellInfo longClickCellInfo = null;
        View itemUnderLongClick = null;
        if (v.getTag() instanceof ItemInfo) {
            ItemInfo info = (ItemInfo) v.getTag();
            longClickCellInfo = new CellLayout.CellInfo(v, info);;
            itemUnderLongClick = longClickCellInfo.cell;
            resetAddInfo();
        }

        // The hotseat touch handling does not go through Workspace, and we always allow long press
        // on hotseat items.
        final boolean inHotseat = isHotseatLayout(v);
        boolean allowLongPress = inHotseat || mWorkspace.allowLongPress();
        if (allowLongPress && !mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                if (mWorkspace.isInOverviewMode()) {
                    mWorkspace.startReordering(v);
                } else {
                    mWorkspace.enterOverviewMode();
                }
            } else {
                final boolean isAllAppsButton = inHotseat && isAllAppsButtonRank(
                        mHotseat.getOrderInHotseat(
                                longClickCellInfo.cellX,
                                longClickCellInfo.cellY));
                if (!(itemUnderLongClick instanceof Folder || isAllAppsButton)) {
                    /// M: Call the appropriate callback for the IMtkWidget on the current page
                    /// when long click and begin to drag IMtkWidget.
                    mWorkspace.startDragAppWidget(mWorkspace.getCurrentPage());
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    CellLayout getCellLayout(long container, long screenId) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else {
            return (CellLayout) mWorkspace.getScreenWithId(screenId);
        }
    }

    public boolean isAllAppsVisible() {
        return (mState == State.APPS_CUSTOMIZE) || (mOnResumeState == State.APPS_CUSTOMIZE);
    }

    private void setWorkspaceBackground(boolean workspace) {
        mLauncherView.setBackground(/*workspace ?
                mWorkspaceBackgroundDrawable : */null);
    }

    protected void changeWallpaperVisiblity(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
        setWorkspaceBackground(visible);
    }

    private void dispatchOnLauncherTransitionPrepare(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionPrepare(this, animated, toWorkspace);
        }
    }

    private void dispatchOnLauncherTransitionStart(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStart(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 0f);
    }

    private void dispatchOnLauncherTransitionStep(View v, float t) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStep(this, t);
        }
    }

    private void dispatchOnLauncherTransitionEnd(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionEnd(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 1f);
    }

    /**
     * Things to test when changing the following seven functions.
     *   - Home from workspace
     *          - from center screen
     *          - from other screens
     *   - Home from all apps
     *          - from center screen
     *          - from other screens
     *   - Back from all apps
     *          - from center screen
     *          - from other screens
     *   - Launch app from workspace and quit
     *          - with back
     *          - with home
     *   - Launch app from all apps and quit
     *          - with back
     *          - with home
     *   - Go to a screen that's not the default, then all
     *     apps, and launch and app, and go back
     *          - with back
     *          -with home
     *   - On workspace, long press power and go back
     *          - with back
     *          - with home
     *   - On all apps, long press power and go back
     *          - with back
     *          - with home
     *   - On workspace, power off
     *   - On all apps, power off
     *   - Launch an app and turn off the screen while in that app
     *          - Go back with home key
     *          - Go back with back key  TODO: make this not go to workspace
     *          - From all apps
     *          - From workspace
     *   - Enter and exit car mode (becuase it causes an extra configuration changed)
     *          - From all apps
     *          - From the center workspace
     *          - From another workspace
     */

    /**
     * Zoom the camera out from the workspace to reveal 'toView'.
     * Assumes that the view to show is anchored at either the very top or very bottom
     * of the screen.
     */
    private void showAppsCustomizeHelper(final boolean animated, final boolean springLoaded) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "showAppsCustomizeHelper animated = " + animated + ", springLoaded = " + springLoaded);
        }

        AppsCustomizePagedView.ContentType contentType = mAppsCustomizeContent.getContentType();
        showAppsCustomizeHelper(animated, springLoaded, contentType);
    }

    private void showAppsCustomizeHelper(final boolean animated, final boolean springLoaded,
                                         final AppsCustomizePagedView.ContentType contentType) {
        if (mStateAnimation != null) {
            mStateAnimation.setDuration(0);
            mStateAnimation.cancel();
            mStateAnimation = null;
        }

        boolean material = Utilities.isLmpOrAbove();

        final Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomInTime);
        final int fadeDuration = res.getInteger(R.integer.config_appsCustomizeFadeInTime);
        final int revealDuration = res.getInteger(R.integer.config_appsCustomizeRevealTime);
        final int itemsAlphaStagger =
                res.getInteger(R.integer.config_appsCustomizeItemsAlphaStagger);

        final float scale = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mWorkspace;
        final AppsCustomizeTabHost toView = mAppsCustomizeTabHost;

        final ArrayList<View> layerViews = new ArrayList<View>();

        Workspace.State workspaceState = contentType == AppsCustomizePagedView.ContentType.Widgets ?
                Workspace.State.OVERVIEW_HIDDEN : Workspace.State.NORMAL_HIDDEN;
        ///M: fix the bug ALPS01912332, when launcher already be destroyed, we still go here,
        //but because workspace = null, there will be JE.
        if (mWorkspace == null) {
            LauncherLog.d(TAG, "showAppsCustomizeHelper mWorkspace = null");
            return;
        }
        Animator workspaceAnim =
                mWorkspace.getChangeStateAnimation(workspaceState, animated, layerViews);
        if (!LauncherAppState.isDisableAllApps()
                || contentType == AppsCustomizePagedView.ContentType.Widgets) {
            // Set the content type for the all apps/widgets space
            mAppsCustomizeTabHost.setContentTypeImmediate(contentType);
        }

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = getAllAppsButton() != null;

        if (animated && initialized) {
            /// M: do animation before start animation
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            
            mStateAnimation = LauncherAnimUtils.createAnimatorSet();
            final AppsCustomizePagedView content = (AppsCustomizePagedView)
                    toView.findViewById(R.id.apps_customize_pane_content);

            final View page = content.getPageAt(content.getCurrentPage());
            final View revealView = toView.findViewById(R.id.fake_page);

            final float initialPanelAlpha = 1f;

            final boolean isWidgetTray = contentType == AppsCustomizePagedView.ContentType.Widgets;
            if (isWidgetTray) {
                revealView.setBackground(res.getDrawable(R.drawable.quantum_panel_dark));
            } else {
                if(Launcher.DISABLE_APPLIST_WHITE_BG) {
                    revealView.setBackground(null);
                } else {
                    revealView.setBackground(res.getDrawable(R.drawable.quantum_panel));
                }
            }

            // Hide the real page background, and swap in the fake one
            content.setPageBackgroundsVisible(false);
            revealView.setVisibility(View.VISIBLE);
            // We need to hide this view as the animation start will be posted.
            revealView.setAlpha(0);

            int width = revealView.getMeasuredWidth();
            int height = revealView.getMeasuredHeight();
            float revealRadius = (float) Math.sqrt((width * width) / 4 + (height * height) / 4);

            revealView.setTranslationY(0);
            revealView.setTranslationX(0);

            // Get the y delta between the center of the page and the center of the all apps button
            int[] allAppsToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView,
                    getAllAppsButton(), null);

            float alpha = 0;
            float xDrift = 0;
            float yDrift = 0;
            if (material) {
                alpha = isWidgetTray ? 0.3f : 1f;
                yDrift = isWidgetTray ? height / 2 : allAppsToPanelDelta[1];
                xDrift = isWidgetTray ? 0 : allAppsToPanelDelta[0];
            } else {
                yDrift = 2 * height / 3;
                xDrift = 0;
            }
            final float initAlpha = alpha;

            revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            layerViews.add(revealView);
            PropertyValuesHolder panelAlpha = PropertyValuesHolder.ofFloat("alpha", initAlpha, 1f);
            PropertyValuesHolder panelDriftY =
                    PropertyValuesHolder.ofFloat("translationY", yDrift, 0);
            PropertyValuesHolder panelDriftX =
                    PropertyValuesHolder.ofFloat("translationX", xDrift, 0);

            ObjectAnimator panelAlphaAndDrift = ObjectAnimator.ofPropertyValuesHolder(revealView,
                    panelAlpha, panelDriftY, panelDriftX);

            panelAlphaAndDrift.setDuration(revealDuration);
            panelAlphaAndDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));

            mStateAnimation.play(panelAlphaAndDrift);

            if (page != null) {
                page.setVisibility(View.VISIBLE);
                page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                layerViews.add(page);

                ObjectAnimator pageDrift = ObjectAnimator.ofFloat(page, "translationY", yDrift, 0);
                page.setTranslationY(yDrift);
                pageDrift.setDuration(revealDuration);
                pageDrift.setInterpolator(new LogDecelerateInterpolator(100, 0));
                pageDrift.setStartDelay(itemsAlphaStagger);
                mStateAnimation.play(pageDrift);

                page.setAlpha(0f);
                ObjectAnimator itemsAlpha = ObjectAnimator.ofFloat(page, "alpha", 0f, 1f);
                itemsAlpha.setDuration(revealDuration);
                itemsAlpha.setInterpolator(new AccelerateInterpolator(1.5f));
                itemsAlpha.setStartDelay(itemsAlphaStagger);
                mStateAnimation.play(itemsAlpha);
            }

            View pageIndicators = toView.findViewById(R.id.apps_customize_page_indicator);
            pageIndicators.setAlpha(0.01f);
            ObjectAnimator indicatorsAlpha =
                    ObjectAnimator.ofFloat(pageIndicators, "alpha", 1f);
            indicatorsAlpha.setDuration(revealDuration);
            mStateAnimation.play(indicatorsAlpha);

            if (material) {
                final View allApps = getAllAppsButton();
                int allAppsButtonSize = LauncherAppState.getInstance().
                        getDynamicGrid().getDeviceProfile().allAppsButtonVisualSize;
                float startRadius = isWidgetTray ? 0 : allAppsButtonSize / 2;
                /**M: [Changes]If the hardware is accelerated, then allow to use Reveal animator.@{**/
                boolean isHardwareAccelerated = revealView.isHardwareAccelerated();
                if (isHardwareAccelerated) {
                    Animator reveal = ViewAnimationUtils.createCircularReveal(
                            revealView, width / 2, height / 2, startRadius,
                            revealRadius);
                    reveal.setDuration(revealDuration);
                    reveal.setInterpolator(new LogDecelerateInterpolator(100, 0));

                    reveal.addListener(new AnimatorListenerAdapter() {
                        public void onAnimationStart(Animator animation) {
                            if (!isWidgetTray) {
                                allApps.setVisibility(View.INVISIBLE);
                            }
                        }

                        public void onAnimationEnd(Animator animation) {
                            if (!isWidgetTray) {
                                allApps.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                    mStateAnimation.play(reveal);
                } else {
                    if (!isWidgetTray) {
                        allApps.setVisibility(View.VISIBLE);
                    }
                }
                /**@}**/
            }

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mAllAppAnimaterState = false;
                    super.onAnimationCancel(animation);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mAllAppAnimaterState = true;
                    super.onAnimationStart(animation);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, animated, false);
                    dispatchOnLauncherTransitionEnd(toView, animated, false);

                    revealView.setVisibility(View.INVISIBLE);
                    revealView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (page != null) {
                        page.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    content.setPageBackgroundsVisible(true);

                    // Hide the search bar
                    if (mSearchDropTargetBar != null) {
                        mSearchDropTargetBar.hideSearchBar(false);
                    }
                    mAllAppAnimaterState = false;
                }
            });

            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mStateAnimation != stateAnimation)
                        return;
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    revealView.setAlpha(initAlpha);
                    if (Utilities.isLmpOrAbove()) {
                        for (int i = 0; i < layerViews.size(); i++) {
                            View v = layerViews.get(i);
                            if (v != null) {
                                boolean attached = true;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    attached = v.isAttachedToWindow();
                                }
                                if (attached) v.buildLayer();
                            }
                        }
                    }
                    mStateAnimation.start();
                }
            };
            toView.bringToFront();
            toView.setVisibility(View.VISIBLE);
            toView.post(startAnimRunnable);
        } else {
            ///M. ALPS02032987, edit page don't use animation, so we should set the alpha.
            if (mSupportEditAndHideApps) {
                View pageIndicators = toView.findViewById(R.id.apps_customize_page_indicator);
                pageIndicators.setAlpha(1.0f);
            }
            ///M.

            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();

            if (!springLoaded && !LauncherAppState.getInstance().isScreenLarge()) {
                // Hide the search bar
                if (mSearchDropTargetBar != null) {
                    mSearchDropTargetBar.hideSearchBar(false);
                }
            }
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionStart(fromView, animated, false);
            dispatchOnLauncherTransitionEnd(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            dispatchOnLauncherTransitionStart(toView, animated, false);
            dispatchOnLauncherTransitionEnd(toView, animated, false);
        }
    }

    /**
     * Zoom the camera back into the workspace, hiding 'fromView'.
     * This is the opposite of showAppsCustomizeHelper.
     * @param animated If true, the transition will be animated.
     */
    private void hideAppsCustomizeHelper(Workspace.State toState, final boolean animated,
            final boolean springLoaded, final Runnable onCompleteRunnable) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "hideAppsCustomzieHelper toState = " + toState + ", animated = " + animated
                    + ", springLoaded = " + springLoaded);
        }

        if (mStateAnimation != null) {
            mStateAnimation.setDuration(0);
            mStateAnimation.cancel();
            mStateAnimation = null;
        }

        boolean material = Utilities.isLmpOrAbove();
        Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomOutTime);
        final int fadeOutDuration = res.getInteger(R.integer.config_appsCustomizeFadeOutTime);
        final int revealDuration = res.getInteger(R.integer.config_appsCustomizeConcealTime);
        final int itemsAlphaStagger =
                res.getInteger(R.integer.config_appsCustomizeItemsAlphaStagger);

        final float scaleFactor = (float)
                res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mAppsCustomizeTabHost;
        final View toView = mWorkspace;
        Animator workspaceAnim = null;
        final ArrayList<View> layerViews = new ArrayList<View>();

        if (toState == Workspace.State.NORMAL) {
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    toState, animated, layerViews);
        } else if (toState == Workspace.State.SPRING_LOADED ||
                toState == Workspace.State.OVERVIEW) {
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    toState, animated, layerViews);
        }

        // If for some reason our views aren't initialized, don't animate
        boolean initialized = getAllAppsButton() != null;

        if (animated && initialized) {
            mStateAnimation = LauncherAnimUtils.createAnimatorSet();
            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            final AppsCustomizePagedView content = (AppsCustomizePagedView)
                    fromView.findViewById(R.id.apps_customize_pane_content);

            final View page = content.getPageAt(content.getNextPage());

            // We need to hide side pages of the Apps / Widget tray to avoid some ugly edge cases
            int count = content.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = content.getChildAt(i);
                if (child != page) {
                    child.setVisibility(View.INVISIBLE);
                }
            }
            final View revealView = fromView.findViewById(R.id.fake_page);

            // hideAppsCustomizeHelper is called in some cases when it is already hidden
            // don't perform all these no-op animations. In particularly, this was causing
            // the all-apps button to pop in and out.
            if (fromView.getVisibility() == View.VISIBLE) {
                AppsCustomizePagedView.ContentType contentType = content.getContentType();
                final boolean isWidgetTray =
                        contentType == AppsCustomizePagedView.ContentType.Widgets;

                if (isWidgetTray) {
                    revealView.setBackground(res.getDrawable(R.drawable.quantum_panel_dark));
                } else {
                    if(Launcher.DISABLE_APPLIST_WHITE_BG) {
                        revealView.setBackground(null);
                    } else {
                        revealView.setBackground(res.getDrawable(R.drawable.quantum_panel));
                    }
                }

                int width = revealView.getMeasuredWidth();
                int height = revealView.getMeasuredHeight();
                float revealRadius = (float) Math.sqrt((width * width) / 4 + (height * height) / 4);

                // Hide the real page background, and swap in the fake one
                revealView.setVisibility(View.VISIBLE);
                content.setPageBackgroundsVisible(false);

                final View allAppsButton = getAllAppsButton();
                revealView.setTranslationY(0);
                int[] allAppsToPanelDelta = Utilities.getCenterDeltaInScreenSpace(revealView,
                        allAppsButton, null);

                float xDrift = 0;
                float yDrift = 0;
                if (material) {
                    yDrift = isWidgetTray ? height / 2 : allAppsToPanelDelta[1];
                    xDrift = isWidgetTray ? 0 : allAppsToPanelDelta[0];
                } else {
                    yDrift = 5 * height / 4;
                    xDrift = 0;
                }

                revealView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                TimeInterpolator decelerateInterpolator = material ?
                        new LogDecelerateInterpolator(100, 0) :
                        new LogDecelerateInterpolator(30, 0);

                // The vertical motion of the apps panel should be delayed by one frame
                // from the conceal animation in order to give the right feel. We correpsondingly
                // shorten the duration so that the slide and conceal end at the same time.
                ObjectAnimator panelDriftY = LauncherAnimUtils.ofFloat(revealView, "translationY",
                        0, yDrift);
                panelDriftY.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftY.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftY.setInterpolator(decelerateInterpolator);
                mStateAnimation.play(panelDriftY);

                ObjectAnimator panelDriftX = LauncherAnimUtils.ofFloat(revealView, "translationX",
                        0, xDrift);
                panelDriftX.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                panelDriftX.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                panelDriftX.setInterpolator(decelerateInterpolator);
                mStateAnimation.play(panelDriftX);

                if (isWidgetTray || !material) {
                    float finalAlpha = material ? 0.4f : 0f;
                    revealView.setAlpha(1f);
                    ObjectAnimator panelAlpha = LauncherAnimUtils.ofFloat(revealView, "alpha",
                            1f, finalAlpha);
                    panelAlpha.setDuration(revealDuration);
                    panelAlpha.setInterpolator(material ? decelerateInterpolator :
                        new AccelerateInterpolator(1.5f));
                    mStateAnimation.play(panelAlpha);
                }

                if (page != null) {
                    page.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    ObjectAnimator pageDrift = LauncherAnimUtils.ofFloat(page, "translationY",
                            0, yDrift);
                    page.setTranslationY(0);
                    pageDrift.setDuration(revealDuration - SINGLE_FRAME_DELAY);
                    pageDrift.setInterpolator(decelerateInterpolator);
                    pageDrift.setStartDelay(itemsAlphaStagger + SINGLE_FRAME_DELAY);
                    mStateAnimation.play(pageDrift);

                    page.setAlpha(1f);
                    ObjectAnimator itemsAlpha = LauncherAnimUtils.ofFloat(page, "alpha", 1f, 0f);
                    itemsAlpha.setDuration(100);
                    itemsAlpha.setInterpolator(decelerateInterpolator);
                    mStateAnimation.play(itemsAlpha);
                }

                View pageIndicators = fromView.findViewById(R.id.apps_customize_page_indicator);
                pageIndicators.setAlpha(1f);
                ObjectAnimator indicatorsAlpha =
                        LauncherAnimUtils.ofFloat(pageIndicators, "alpha", 0f);
                indicatorsAlpha.setDuration(revealDuration);
                indicatorsAlpha.setInterpolator(new DecelerateInterpolator(1.5f));
                mStateAnimation.play(indicatorsAlpha);

                width = revealView.getMeasuredWidth();

                if (material) {
                    if (!isWidgetTray) {
                        allAppsButton.setVisibility(View.INVISIBLE);
                    }
                    int allAppsButtonSize = LauncherAppState.getInstance().
                            getDynamicGrid().getDeviceProfile().allAppsButtonVisualSize;
                    float finalRadius = isWidgetTray ? 0 : allAppsButtonSize / 2;
                    /**M: [Changes]If the hardware is accelerated, then allow to use Reveal animator.@{**/
                    boolean isHardwareAccelerated = revealView.isHardwareAccelerated();
                    if (isHardwareAccelerated) {
                        Animator reveal = LauncherAnimUtils
                                .createCircularReveal(revealView, width / 2,
                                        height / 2, revealRadius, finalRadius);
                        reveal.setInterpolator(new LogDecelerateInterpolator(
                                100, 0));
                        reveal.setDuration(revealDuration);
                        reveal.setStartDelay(itemsAlphaStagger);

                        reveal.addListener(new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator animation) {
                                revealView.setVisibility(View.INVISIBLE);
                                if (!isWidgetTray) {
                                    allAppsButton.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                        mStateAnimation.play(reveal);
                    }else{
                        revealView.setVisibility(View.INVISIBLE);
                        if (!isWidgetTray) {
                            allAppsButton.setVisibility(View.VISIBLE);
                        }
                    }
                    /**@}**/
                }

                dispatchOnLauncherTransitionPrepare(fromView, animated, true);
                dispatchOnLauncherTransitionPrepare(toView, animated, true);
                mAppsCustomizeContent.stopScrolling();
            }

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mAllAppAnimaterState = false;
                    super.onAnimationCancel(animation);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mAllAppAnimaterState = true;
                    super.onAnimationStart(animation);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, animated, true);
                    dispatchOnLauncherTransitionEnd(toView, animated, true);
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }

                    revealView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (page != null) {
                        page.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    content.setPageBackgroundsVisible(true);
                    // Unhide side pages
                    int count = content.getChildCount();
                    for (int i = 0; i < count; i++) {
                        View child = content.getChildAt(i);
                        child.setVisibility(View.VISIBLE);
                    }

                    // Reset page transforms
                    if (page != null) {
                        page.setTranslationX(0);
                        page.setTranslationY(0);
                        page.setAlpha(1);
                    }
                    content.setCurrentPage(content.getNextPage());

                    mAppsCustomizeContent.updateCurrentPageScroll();
                    mAllAppAnimaterState = false;
                }
            });

            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mStateAnimation != stateAnimation)
                        return;
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);

                    if (Utilities.isLmpOrAbove()) {
                        for (int i = 0; i < layerViews.size(); i++) {
                            View v = layerViews.get(i);
                            if (v != null) {
                                boolean attached = true;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    attached = v.isAttachedToWindow();
                                }
                                if (attached) v.buildLayer();
                            }
                        }
                    }
                    mStateAnimation.start();
                }
            };
            fromView.post(startAnimRunnable);
        } else {
            fromView.setVisibility(View.GONE);
            /// M: sometimes the hotseat's alpha will be 0 {@
            if(mHotseat != null) {
                Log.d(TAG, "mHotseat.setAlpha(1.0f);");
                mHotseat.setAlpha(1.0f);
            }
            /// @}
            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionEnd(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            dispatchOnLauncherTransitionEnd(toView, animated, true);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "onTrimMemory: level = " + level);
        }

        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            mAppsCustomizeTabHost.onTrimMemory();
        }
    }

    protected void showWorkspace(boolean animated) {
        showWorkspace(animated, null);
    }

    protected void showWorkspace() {
        showWorkspace(true);
    }

    void showWorkspace(boolean animated, Runnable onCompleteRunnable) {
        Trace.traceBegin(Trace.TRACE_TAG_INPUT, "showWorkspace");
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "showWorkspace: animated = " + animated + ", mState = " + mState);
        }

        ///M. ALPS01990478, add this back.
        if (mWorkspace.isInOverviewMode()) {
            mWorkspace.exitOverviewMode(animated);
        }
        ///M

        if (mState != State.WORKSPACE || mWorkspace.getState() != Workspace.State.NORMAL) {
            boolean wasInSpringLoadedMode = (mState != State.WORKSPACE);
            mWorkspace.setVisibility(View.VISIBLE);
            hideAppsCustomizeHelper(Workspace.State.NORMAL, animated, false, onCompleteRunnable);

            // Show the search bar (only animate if we were showing the drop target bar in spring
            // loaded mode)
            if (mSearchDropTargetBar != null) {
                mSearchDropTargetBar.showSearchBar(animated && wasInSpringLoadedMode);
            }

            // Set focus to the AppsCustomize button
            if (mAllAppsButton != null) {
                mAllAppsButton.requestFocus();
            }
        }

        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;
        /**M: [ALPS01557436]Added to update the background while switched to workspace.@{**/
        setWorkspaceBackground(mState == State.WORKSPACE);
        /**@}**/

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateRunning();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);

        onWorkspaceShown(animated);
        Trace.traceEnd(Trace.TRACE_TAG_INPUT);
    }

    void showOverviewMode(boolean animated) {
        mWorkspace.setVisibility(View.VISIBLE);
        hideAppsCustomizeHelper(Workspace.State.OVERVIEW, animated, false, null);
        mState = State.WORKSPACE;
        /**M: [ALPS01557436]Added to update the background while switched to workspace.@{**/
        setWorkspaceBackground(mState == State.WORKSPACE);
        /**@}**/
        onWorkspaceShown(animated);
    }

    public void onWorkspaceShown(boolean animated) {
    }

    void showAllApps(boolean animated, AppsCustomizePagedView.ContentType contentType,
                     boolean resetPageToZero) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "showAllApps: animated = " + animated + ", mState = " + mState
                    + ", mCurrentBounds = " + mCurrentBounds);
        }
        if (mState != State.WORKSPACE) return;

        /// M: Call the appropriate callback for the IMtkWidget on the current page when enter all apps list.
        mWorkspace.startCovered(mWorkspace.getCurrentPage());

        if (resetPageToZero) {
            mAppsCustomizeTabHost.reset();
        }
        showAppsCustomizeHelper(animated, false, contentType);
        mAppsCustomizeTabHost.post(new Runnable() {
            @Override
            public void run() {
                // We post this in-case the all apps view isn't yet constructed.
                mAppsCustomizeTabHost.requestFocus();
            }
        });

        // Change the state *after* we've called all the transition code
        mState = State.APPS_CUSTOMIZE;
        /**M: [ALPS01557436]Added to update the background while switched to workspace.@{**/
        setWorkspaceBackground(mState == State.WORKSPACE);
        /**@}**/

        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateRunning();
        closeFolder();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void enterSpringLoadedDragMode() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "enterSpringLoadedDragMode mState = " + mState + ", mOnResumeState = " + mOnResumeState);
        }

        if (isAllAppsVisible()) {
            hideAppsCustomizeHelper(Workspace.State.SPRING_LOADED, true, true, null);
            mState = State.APPS_CUSTOMIZE_SPRING_LOADED;
        }
    }

    void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, int delay,
            final Runnable onCompleteRunnable) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "exitSpringLoadedDragModeDelayed successfulDrop = " + successfulDrop + ", delay = "
                    + delay + ", mState = " + mState);
        }

        if (mState != State.APPS_CUSTOMIZE_SPRING_LOADED) return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mAppsCustomizeTabHost.setVisibility(View.GONE);
                    showWorkspace(true, onCompleteRunnable);
                } else {
                    exitSpringLoadedDragMode();
                }
            }
        }, delay);
    }

    void exitSpringLoadedDragMode() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "exitSpringLoadedDragMode mState = " + mState);
        }

        if (mState == State.APPS_CUSTOMIZE_SPRING_LOADED) {
            final boolean animated = true;
            final boolean springLoaded = true;
            showAppsCustomizeHelper(animated, springLoaded);
            mState = State.APPS_CUSTOMIZE;
        }
        // Otherwise, we are not in spring loaded mode, so don't do anything.
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    /**
     * Hides the hotseat area.
     */
    void hideHotseat(boolean animated) {
        if (!LauncherAppState.getInstance().isScreenLarge()) {
            if (animated) {
                if (mHotseat.getAlpha() != 0f) {
                    int duration = 0;
                    if (mSearchDropTargetBar != null) {
                        duration = mSearchDropTargetBar.getTransitionOutDuration();
                    }
                    mHotseat.animate().alpha(0f).setDuration(duration);
                }
            } else {
                mHotseat.setAlpha(0f);
            }
        }
    }

    /**
     * Add an item from all apps or customize onto the given workspace screen.
     * If layout is null, add to the current screen.
     */
    void addExternalItemToScreen(ItemInfo itemInfo, final CellLayout layout) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "addExternalItemToScreen itemInfo = " + itemInfo + ", layout = " + layout);
        }

        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
        }
    }

    /** Maps the current orientation to an index for referencing orientation correct global icons */
    private int getCurrentOrientationIndexForGlobalIcons() {
        // default - 0, landscape - 1
        switch (getResources().getConfiguration().orientation) {
        case Configuration.ORIENTATION_LANDSCAPE:
            return 1;
        default:
            return 0;
        }
    }

    private Drawable getExternalPackageToolbarIcon(ComponentName activityName, String resourceName) {
        try {
            PackageManager packageManager = getPackageManager();
            // Look for the toolbar icon specified in the activity meta-data
            Bundle metaData = packageManager.getActivityInfo(
                    activityName, PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                int iconResId = metaData.getInt(resourceName);
                if (iconResId != 0) {
                    Resources res = packageManager.getResourcesForActivity(activityName);
                    return res.getDrawable(iconResId);
                }
            }
        } catch (NameNotFoundException e) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon; " + activityName.flattenToShortString() +
                    " not found", e);
        } catch (Resources.NotFoundException nfe) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon from " + activityName.flattenToShortString(),
                    nfe);
        }
        return null;
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateTextButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);
        Resources r = getResources();
        int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
        int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);

        TextView button = (TextView) findViewById(buttonId);
        // If we were unable to find the icon via the meta-data, use a generic one
        if (toolbarIcon == null) {
            toolbarIcon = r.getDrawable(fallbackDrawableId);
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return null;
        } else {
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return toolbarIcon.getConstantState();
        }
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        ImageView button = (ImageView) findViewById(buttonId);
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);

        if (button != null) {
            // If we were unable to find the icon via the meta-data, use a
            // generic one
            if (toolbarIcon == null) {
                button.setImageResource(fallbackDrawableId);
            } else {
                button.setImageDrawable(toolbarIcon);
            }
        }

        return toolbarIcon != null ? toolbarIcon.getConstantState() : null;

    }

    private void updateTextButtonWithDrawable(int buttonId, Drawable d) {
        TextView button = (TextView) findViewById(buttonId);
        button.setCompoundDrawables(d, null, null, null);
    }

    private void updateButtonWithDrawable(int buttonId, Drawable.ConstantState d) {
        ImageView button = (ImageView) findViewById(buttonId);
        button.setImageDrawable(d.newDrawable(getResources()));
    }

    private void invalidatePressedFocusedStates(View container, View button) {
        if (container instanceof HolographicLinearLayout) {
            HolographicLinearLayout layout = (HolographicLinearLayout) container;
            layout.invalidatePressedFocusedStates();
        } else if (button instanceof HolographicImageView) {
            HolographicImageView view = (HolographicImageView) button;
            view.invalidatePressedFocusedStates();
        }
    }

    public View getQsbBar() {
        if (mQsb == null) {
            mQsb = mInflater.inflate(R.layout.qsb, mSearchDropTargetBar, false);
            mSearchDropTargetBar.addView(mQsb);
        }
        return mQsb;
    }

    protected boolean updateGlobalSearchIcon() {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final ImageView searchButton = (ImageView) findViewById(R.id.search_button);
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName activityName = searchManager.getGlobalSearchActivity();
        if (activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                    TOOLBAR_SEARCH_ICON_METADATA_NAME);
            if (sGlobalSearchIcon[coi] == null) {
                sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }

            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.VISIBLE);
            searchButton.setVisibility(View.VISIBLE);
            invalidatePressedFocusedStates(searchButtonContainer, searchButton);
            return true;
        } else {
            // We disable both search and voice search when there is no global search provider
            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.GONE);
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            if (searchButton != null) searchButton.setVisibility(View.GONE);
            if (voiceButton != null) voiceButton.setVisibility(View.GONE);
            updateVoiceButtonProxyVisible(false);
            return false;
        }
    }

    protected void updateGlobalSearchIcon(Drawable.ConstantState d) {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final View searchButton = (ImageView) findViewById(R.id.search_button);
        updateButtonWithDrawable(R.id.search_button, d);
        invalidatePressedFocusedStates(searchButtonContainer, searchButton);
    }

    protected boolean updateVoiceSearchIcon(boolean searchVisible) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        // We only show/update the voice search icon if the search icon is enabled as well
        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();

        ComponentName activityName = null;
        if (globalSearchActivity != null) {
            // Check if the global search activity handles voice search
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setPackage(globalSearchActivity.getPackageName());
            activityName = intent.resolveActivity(getPackageManager());
        }

        if (activityName == null) {
            // Fallback: check if an activity other than the global search activity
            // resolves this
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            activityName = intent.resolveActivity(getPackageManager());
        }
        if (searchVisible && activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                    TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME);
            if (sVoiceSearchIcon[coi] == null) {
                sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.VISIBLE);
            voiceButton.setVisibility(View.VISIBLE);
            updateVoiceButtonProxyVisible(false);
            invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
            return true;
        } else {
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            if (voiceButton != null) voiceButton.setVisibility(View.GONE);
            /// M: [ALPS01257663] Correct usage of updateVoiceButtonProxyVisible().
            updateVoiceButtonProxyVisible(true);
            return false;
        }
    }

    protected void updateVoiceSearchIcon(Drawable.ConstantState d) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        updateButtonWithDrawable(R.id.voice_button, d);
        invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
    }

    public void updateVoiceButtonProxyVisible(boolean forceDisableVoiceButtonProxy) {
        final View voiceButtonProxy = findViewById(R.id.voice_button_proxy);
        if (voiceButtonProxy != null) {
            boolean visible = !forceDisableVoiceButtonProxy &&
                    mWorkspace.shouldVoiceButtonProxyBeVisible();
            voiceButtonProxy.setVisibility(visible ? View.VISIBLE : View.GONE);
            voiceButtonProxy.bringToFront();
        }
    }

    /**
     * This is an overrid eot disable the voice button proxy.  If disabled is true, then the voice button proxy
     * will be hidden regardless of what shouldVoiceButtonProxyBeVisible() returns.
     */
    public void disableVoiceButtonProxy(boolean disabled) {
        updateVoiceButtonProxyVisible(disabled);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        if (mState == State.APPS_CUSTOMIZE) {
            text.add(mAppsCustomizeTabHost.getContentTag());
        } else {
            text.add(getString(R.string.all_apps_home_button_label));
        }
        return result;
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "Close system dialogs: intent = " + intent);
            }
            closeSystemDialogs();
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to run the passed Runnable
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    private boolean waitUntilResume(Runnable run, boolean deletePreviousRunnables) {
        if (mPaused) {
            Log.i(TAG, "Deferring update until onResume");
            if (deletePreviousRunnables) {
                while (mBindOnResumeCallbacks.remove(run)) {
                }
            }
            mBindOnResumeCallbacks.add(run);
            return true;
        } else {
            return false;
        }
    }

    private boolean waitUntilResume(Runnable run) {
        return waitUntilResume(run, false);
    }

    public void addOnResumeCallback(Runnable run) {
        mOnResumeCallbacks.add(run);
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            LauncherLog.i(TAG, "setLoadOnResume: this = " + this);
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return SCREEN_COUNT / 2;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        setWorkspaceLoading(true);
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "startBinding: this = " + this);
        }

        // If we're starting binding all over again, clear any bind calls we'd postponed in
        // the past (see waitUntilResume) -- we don't need them since we're starting binding
        // from scratch again
        mBindOnResumeCallbacks.clear();

        // Clear the workspace because it's going to be rebound
        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();

        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "startBinding: mIsLoadingWorkspace = " + mIsLoadingWorkspace);
        }
    }

    @Override
    public void bindScreens(ArrayList<Long> orderedScreenIds) {
        bindAddScreens(orderedScreenIds);

        // If there are no screens, we need to have an empty screen
        if (orderedScreenIds.size() == 0) {
            mWorkspace.addExtraEmptyScreen();
        }

        // Create the custom content page (this call updates mDefaultScreen which calls
        // setCurrentPage() so ensure that all pages are added before calling this).
        if (hasCustomContentToLeft()) {
            mWorkspace.createCustomContentContainer();
            populateCustomContentContainer();
        }
    }

    @Override
    public void bindAddScreens(ArrayList<Long> orderedScreenIds) {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - bindAddScreens()", true);
        Launcher.addDumpLog(TAG, "11683562 -   orderedScreenIds: " +
                TextUtils.join(", ", orderedScreenIds), true);
        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(orderedScreenIds.get(i));
        }
    }

    private boolean shouldShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, SHOW_WEIGHT_WATCHER_DEFAULT);

        return show;
    }

    private void toggleShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, true);

        show = !show;

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(SHOW_WEIGHT_WATCHER, show);
        editor.commit();

        if (mWeightWatcher != null) {
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void bindAppsAdded(final ArrayList<Long> newScreens,
                              final ArrayList<ItemInfo> addNotAnimated,
                              final ArrayList<ItemInfo> addAnimated,
                              final ArrayList<AppInfo> addedApps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsAdded(newScreens, addNotAnimated, addAnimated, addedApps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Add the new screens
        if (newScreens != null) {
            bindAddScreens(newScreens);
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, 0,
                    addNotAnimated.size(), false);
        }
        if (addAnimated != null && !addAnimated.isEmpty()) {
            bindItems(addAnimated, 0,
                    addAnimated.size(), true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen(false, false);

        if (!LauncherAppState.isDisableAllApps() &&
                addedApps != null && mAppsCustomizeContent != null) {
            /// M: [OP09]delay to handle apps added event if app list is in edit mode.@{
            if (mSupportEditAndHideApps && (sUsePendingAppsQueue || !mBindingAppsFinished
                || !mAppsCustomizeContent.isDataReady())) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "bindAppsAdded: sUsePendingAppsQueue = "
                        + sUsePendingAppsQueue
                        + ", mBindingAppsFinished = " + mBindingAppsFinished
                        + ", mAppsCustomizeContent.isDataReady() = "
                        + mAppsCustomizeContent.isDataReady());
                }
                sPendingChangedApps.add(new PendingChangedApplications(addedApps,
                        PendingChangedApplications.TYPE_ADDED));
            } else {
                mAppsCustomizeContent.addApps(addedApps);
            }
            //}@

        }
        
        /** M: If unread information load completed, we need to update information in app list.@{**/
        if (mUnreadLoadCompleted) {
            AppsCustomizePagedView.updateUnreadNumInAppInfo(addedApps);
        }
        /**@}**/
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(final ArrayList<ItemInfo> shortcuts, final int start, final int end,
                          final boolean forceAnimateIcons) {
        Runnable r = new Runnable() {
            public void run() {
                bindItems(shortcuts, start, end, forceAnimateIcons);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        final AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        final Collection<Animator> bounceAnims = new ArrayList<Animator>();
        final boolean animateIcons = forceAnimateIcons && canRunNewAppsAnimation();
        Workspace workspace = mWorkspace;
        long newShortcutsScreenId = -1;
        for (int i = start; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);
            if (LauncherLog.DEBUG) {
                LauncherLog.d(TAG, "bindItems: start = " + start + ", end = " + end
                        + "item = " + item + ", this = " + this);
            }

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    View shortcut = createShortcut(info);

                    /*
                     * TODO: FIX collision case
                     */
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
                        if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
                            View v = cl.getChildAt(item.cellX, item.cellY);
                            Object tag = v.getTag();
                            String desc = "Collision while binding workspace item: " + item
                                    + ". Collides with " + tag;
                            if (LauncherAppState.isDogfoodBuild()) {
                                throw (new RuntimeException(desc));
                            } else {
                                Log.d(TAG, desc);
                            }
                        }
                    }

                    workspace.addInScreenFromBind(shortcut, item.container, item.screenId, item.cellX,
                            item.cellY, 1, 1);
                    if (animateIcons) {
                        // Animate all the applications up now
                        shortcut.setAlpha(0f);
                        shortcut.setScaleX(0f);
                        shortcut.setScaleY(0f);
                        bounceAnims.add(createNewAppBounceAnimation(shortcut, i));
                        newShortcutsScreenId = item.screenId;
                    }
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    workspace.addInScreenFromBind(newFolder, item.container, item.screenId, item.cellX,
                            item.cellY, 1, 1);
                    break;
                default:
                    throw new RuntimeException("Invalid Item Type");
            }
        }

        if (animateIcons) {
            // Animate to the correct page
            if (newShortcutsScreenId > -1) {
                long currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
                final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newShortcutsScreenId);
                final Runnable startBounceAnimRunnable = new Runnable() {
                    public void run() {
                        anim.playTogether(bounceAnims);
                        anim.start();
                    }
                };
                if (newShortcutsScreenId != currentScreenId) {
                    // We post the animation slightly delayed to prevent slowdowns
                    // when we are loading right after we return to launcher.
                    mWorkspace.postDelayed(new Runnable() {
                        public void run() {
                            if (mWorkspace != null) {
                                mWorkspace.snapToPage(newScreenIndex);
                                mWorkspace.postDelayed(startBounceAnimRunnable,
                                        NEW_APPS_ANIMATION_DELAY);
                            }
                        }
                    }, NEW_APPS_PAGE_MOVE_DELAY);
                } else {
                    mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
                }
            }
        }
        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(final HashMap<Long, FolderInfo> folders) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindFolders: this = " + this);
        }

        Runnable r = new Runnable() {
            public void run() {
                bindFolders(folders);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        sFolders.clear();
        sFolders.putAll(folders);
    }

    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(final LauncherAppWidgetInfo item) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppWidget(item);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: " + item);
        }
        final Workspace workspace = mWorkspace;

        AppWidgetProviderInfo appWidgetInfo;
        if (((item.restoreStatus & LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0) &&
                ((item.restoreStatus & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID) != 0)) {

            appWidgetInfo = mModel.findAppWidgetProviderInfoWithComponent(this, item.providerName);
            if (appWidgetInfo == null) {
                if (DEBUG_WIDGETS) {
                    Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId
                            + " belongs to component " + item.providerName
                            + ", as the povider is null");
                }
                LauncherModel.deleteItemFromDatabase(this, item);
                return;
            }
            // Note: This assumes that the id remap broadcast is received before this step.
            // If that is not the case, the id remap will be ignored and user may see the
            // click to setup view.
            PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(appWidgetInfo, null, null);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options =
                    AppsCustomizePagedView.getDefaultOptionsForWidget(this, pendingInfo);

            int newWidgetId = mAppWidgetHost.allocateAppWidgetId();
            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    newWidgetId, appWidgetInfo, options);

            // TODO consider showing a permission dialog when the widget is clicked.
            if (!success) {
                mAppWidgetHost.deleteAppWidgetId(newWidgetId);
                if (DEBUG_WIDGETS) {
                    Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId
                            + " belongs to component " + item.providerName
                            + ", as the launcher is unable to bing a new widget id");
                }
                LauncherModel.deleteItemFromDatabase(this, item);
                return;
            }

            item.appWidgetId = newWidgetId;

            // If the widget has a configure activity, it is still needs to set it up, otherwise
            // the widget is ready to go.
            item.restoreStatus = (appWidgetInfo.configure == null)
                    ? LauncherAppWidgetInfo.RESTORE_COMPLETED
                    : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;

            LauncherModel.updateItemInDatabase(this, item);
        }

        int appWidgetId = 0;;
        if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            appWidgetId = item.appWidgetId;
            appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
            if (DEBUG_WIDGETS) {
                Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId + " belongs to component " + appWidgetInfo.provider);
            }

            item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
        } else {
            appWidgetInfo = null;
            PendingAppWidgetHostView view = new PendingAppWidgetHostView(this, item);
            view.updateIcon(mIconCache);
            item.hostView = view;
            item.hostView.updateAppWidget(null);
            item.hostView.setOnClickListener(this);
        }

        item.hostView.setTag(item);
        item.onBindAppWidget(this);
        /// M: Call the appropriate callback for the IMtkWidget will be bound to workspace.
        mWorkspace.setAppWidgetIdAndScreen(item.hostView, mWorkspace.getCurrentPage(), appWidgetId);

        workspace.addInScreen(item.hostView, item.container, item.screenId, item.cellX,
                item.cellY, item.spanX, item.spanY, false);
        addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);

        workspace.requestLayout();

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
    }

    /**
     * Restores a pending widget.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeRestoreAppWidget(final int appWidgetId) {
        LauncherAppWidgetHostView view = mWorkspace.getWidgetForAppWidgetId(appWidgetId);
        if ((view == null) || !(view instanceof PendingAppWidgetHostView)) {
            Log.e(TAG, "Widget update called, when the widget no longer exists.");
            return;
        }

        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
        info.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;

        mWorkspace.reinflateWidgetsIfNecessary();
        LauncherModel.updateItemInDatabase(this, info);
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems(final boolean upgradePath) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "finishBindingItems: mSavedState = " + mSavedState + ", mSavedInstanceState = "
                    + mSavedInstanceState + ", this = " + this);
        }

        Runnable r = new Runnable() {
            public void run() {
                finishBindingItems(upgradePath);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }
            mSavedState = null;
        }

        mWorkspace.restoreInstanceStateForRemainingPages();

        setWorkspaceLoading(false);
        sendLoadingCompleteBroadcastIfNecessary();

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        if (sPendingAddItem != null) {
            final long screenId = completeAdd(sPendingAddItem);

            // TODO: this moves the user to the page where the pending item was added. Ideally,
            // the screen would be guaranteed to exist after bind, and the page would be set through
            // the workspace restore process.
            mWorkspace.post(new Runnable() {
                @Override
                public void run() {
                    mWorkspace.snapToScreenId(screenId);
                }
            });
            sPendingAddItem = null;
        }
        /** M: If unread information load completed, we need to bind it to workspace.@{**/
        if (mUnreadLoadCompleted) {
            bindWorkspaceUnreadInfo();
        }
        mBindingWorkspaceFinished = true;
        /**@}**/
        if (upgradePath) {
            mWorkspace.getUniqueComponents(true, null);
            mIntentsOnWorkspaceFromUpgradePath = mWorkspace.getUniqueComponents(true, null);
        }
        PackageInstallerCompat.getInstance(this).onFinishBind();
        mModel.recheckRestoredItems(this);

        /// M. ALPS01833637, remove the empty screen.
        mWorkspace.removeExtraEmptyScreenDelayed(true, null,
                    10, false);
        /// M.

        ///M. ALPS01960480. for specially case.
        restoreOverviewMode();
        ///M.
    }

    private void sendLoadingCompleteBroadcastIfNecessary() {
        if (!mSharedPrefs.getBoolean(FIRST_LOAD_COMPLETE, false)) {
            String permission =
                    getResources().getString(R.string.receive_first_load_broadcast_permission);
            Intent intent = new Intent(ACTION_FIRST_LOAD_COMPLETE);
            sendBroadcast(intent, permission);
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putBoolean(FIRST_LOAD_COMPLETE, true);
            editor.apply();
        }
    }

    public boolean isAllAppsButtonRank(int rank) {
        if (mHotseat != null) {
            return mHotseat.isAllAppsButtonRank(rank);
        }
        return false;
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = LauncherAnimUtils.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("alpha", 1f),
                PropertyValuesHolder.ofFloat("scaleX", 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f));
        bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new SmoothPagedView.OvershootInterpolator());
        return bounceAnim;
    }

    public boolean useVerticalBarLayout() {
        return LauncherAppState.getInstance().getDynamicGrid().
                getDeviceProfile().isVerticalBarLayout();
    }

    protected Rect getSearchBarBounds() {
        return LauncherAppState.getInstance().getDynamicGrid().
                getDeviceProfile().getSearchBarBounds();
    }

    @Override
    public void bindSearchablesChanged() {
        boolean searchVisible = updateGlobalSearchIcon();
        boolean voiceVisible = updateVoiceSearchIcon(searchVisible);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<AppInfo> apps) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindAllApplications: apps = " + apps);
        }

        if (LauncherAppState.isDisableAllApps()) {
            if (mIntentsOnWorkspaceFromUpgradePath != null) {
                if (LauncherModel.UPGRADE_USE_MORE_APPS_FOLDER) {
                    getHotseat().addAllAppsFolder(mIconCache, apps,
                            mIntentsOnWorkspaceFromUpgradePath, Launcher.this, mWorkspace);
                }
                mIntentsOnWorkspaceFromUpgradePath = null;
            }
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.onPackagesUpdated(
                        LauncherModel.getSortedWidgetsAndShortcuts(this));
            }
        } else {
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setApps(apps);
                mAppsCustomizeContent.onPackagesUpdated(
                        LauncherModel.getSortedWidgetsAndShortcuts(this));
             /// M: [OP09] Edit and Hide app icons.
            if (mSupportEditAndHideApps) {
                mBindingAppsFinished = true;
                if (LauncherLog.DEBUG) {
                        LauncherLog.d(TAG, "bindAllApplications: finished!");
                    }
                //TODO
                    /*if (mAppsCustomizeContent.isDataReady()) {
                        LauncherLog.d(TAG, "bindAllApplications: Data ready,
                        flush pending apps queue!");
                        flushPendingAppsQueue(mAppsCustomizeContent);
                    }*/
                }
            }
        }
        
        /** M: If unread information load completed, we need to update information in app list.@{**/
        if (mUnreadLoadCompleted) {
            AppsCustomizePagedView.updateUnreadNumInAppInfo(apps);
        }
        /**@}**/
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(final ArrayList<AppInfo> apps) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindAppsUpdated: apps = " + apps);
        }

        Runnable r = new Runnable() {
            public void run() {
                bindAppsUpdated(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mWorkspace != null) {
            mWorkspace.updateShortcutsAndWidgets(apps);
        }

        if (!LauncherAppState.isDisableAllApps() &&
                mAppsCustomizeContent != null) {
            /// M: [OP09]delay to handle apps update event if app list is in edit mode
            if (mSupportEditAndHideApps && (sUsePendingAppsQueue || !mBindingAppsFinished
                  || !mAppsCustomizeContent.isDataReady())) {
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "bindAppsUpdated: sUsePendingAppsQueue = "
                        + sUsePendingAppsQueue
                        + ", mBindingAppsFinished = " + mBindingAppsFinished
                        + ", mAppsCustomizeContent.isDataReady() = "
                        + mAppsCustomizeContent.isDataReady());
                }
                sPendingChangedApps.add(new PendingChangedApplications(apps,
                        PendingChangedApplications.TYPE_UPDATED));
            } else {
                mAppsCustomizeContent.updateApps(apps);
            }
        }
    }

    /**
     * Packages were restored
     */
    public void bindAppsRestored(final ArrayList<AppInfo> apps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsRestored(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mWorkspace != null) {
            mWorkspace.updateShortcutsAndWidgets(apps);
        }

        /** M: If unread information load completed, we need to update information in app list.@{**/
        if (mUnreadLoadCompleted) {
            AppsCustomizePagedView.updateUnreadNumInAppInfo(apps);
        }
        /**@}**/
    }

    /**
     * Update the state of a package, typically related to install state.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void updatePackageState(ArrayList<PackageInstallInfo> installInfo) {
        if (mWorkspace != null) {
            mWorkspace.updatePackageState(installInfo);
        }
    }

    /**
     * Update the label and icon of all the icons in a package
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void updatePackageBadge(String packageName) {
        if (mWorkspace != null) {
            mWorkspace.updatePackageBadge(packageName, UserHandleCompat.myUserHandle());
        }
    }

    /**
     * A package was uninstalled.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace, where as
     * package-removal should clear all items by package name.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    /// M: [ALPS01273634] Do not remove shortcut from workspace when receiving ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.
    public void bindComponentsRemoved(final ArrayList<String> packageNames,
            final ArrayList<AppInfo> appInfos, final UserHandleCompat user) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindComponentsRemoved: packageNames = " + packageNames +
                               ", appInfos = " + appInfos);
        }

        Runnable r = new Runnable() {
            public void run() {
                bindComponentsRemoved(packageNames, appInfos, user);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (!packageNames.isEmpty()) {
            mWorkspace.removeItemsByPackageName(packageNames, user);
            if (mSupportEditAndHideApps) {
                mAppsCustomizeContent.removeItemsInFolderByPackageName(packageNames, user);
            }
        }
        if (!appInfos.isEmpty()) {
            mWorkspace.removeItemsByApplicationInfo(appInfos, user);
            if (mSupportEditAndHideApps) {
                mAppsCustomizeContent.removeItemsInFolderByApplicationInfo(appInfos, user);
            }
        }

        // Notify the drag controller
        mDragController.onAppsRemoved(packageNames, appInfos);

        // Update AllApps
        if (!LauncherAppState.isDisableAllApps() &&
                mAppsCustomizeContent != null) {
            mAppsCustomizeContent.removeApps(appInfos);
        }
    }

    /**
     * A number of packages were updated.
     */
    private ArrayList<Object> mWidgetsAndShortcuts;
    private Runnable mBindPackagesUpdatedRunnable = new Runnable() {
            public void run() {
                bindPackagesUpdated(mWidgetsAndShortcuts);
                mWidgetsAndShortcuts = null;
            }
        };
    public void bindPackagesUpdated(final ArrayList<Object> widgetsAndShortcuts) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindPackagesUpdated: sUsePendingAppsQueue = " + sUsePendingAppsQueue);
        }

        if (waitUntilResume(mBindPackagesUpdatedRunnable, true)) {
            mWidgetsAndShortcuts = widgetsAndShortcuts;
            return;
        }

        // Update the widgets pane
        if (mAppsCustomizeContent != null && !sUsePendingAppsQueue) { /// M: delay to update package if app list is in edit mode, for op09.
            mAppsCustomizeContent.onPackagesUpdated(widgetsAndShortcuts);
        }
    }

    private int mapConfigurationOriActivityInfoOri(int configOri) {
        final Display d = getWindowManager().getDefaultDisplay();
        int naturalOri = Configuration.ORIENTATION_LANDSCAPE;
        switch (d.getRotation()) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
            // We are currently in the same basic orientation as the natural orientation
            naturalOri = configOri;
            break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
            // We are currently in the other basic orientation to the natural orientation
            naturalOri = (configOri == Configuration.ORIENTATION_LANDSCAPE) ?
                    Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            break;
        }

        int[] oriMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOri == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }
        return oriMap[(d.getRotation() + indexOffset) % 4];
    }

    public boolean isRotationEnabled() {
        boolean enableRotation = sForceEnableRotation ||
                getResources().getBoolean(R.bool.allow_rotation);
        return enableRotation;
    }
    public void lockScreenOrientation() {
        if (isRotationEnabled()) {
            setRequestedOrientation(mapConfigurationOriActivityInfoOri(getResources()
                    .getConfiguration().orientation));
        }
    }
    public void unlockScreenOrientation(boolean immediate) {
        if (isRotationEnabled()) {
            if (immediate) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                }, mRestoreScreenOrientationDelay);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
    }

    /**
     * Called when the SearchBar hint should be changed.
     *
     * @param hint the hint to be displayed in the search bar.
     */
    protected void onSearchBarHintChanged(String hint) {

    }

    protected boolean isLauncherPreinstalled() {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(getComponentName().getPackageName(), 0);
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            } else {
                return false;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method indicates whether or not we should suggest default wallpaper dimensions
     * when our wallpaper cropper was not yet used to set a wallpaper.
     */
    protected boolean overrideWallpaperDimensions() {
        return true;
    }

    protected boolean shouldClingFocusHotseatApp() {
        return false;
    }
    protected String getFirstRunClingSearchBarHint() {
        return "";
    }
    protected String getFirstRunCustomContentHint() {
        return "";
    }
    protected int getFirstRunFocusedHotseatAppDrawableId() {
        return -1;
    }
    protected ComponentName getFirstRunFocusedHotseatAppComponentName() {
        return null;
    }
    protected int getFirstRunFocusedHotseatAppRank() {
        return -1;
    }
    protected String getFirstRunFocusedHotseatAppBubbleTitle() {
        return "";
    }
    protected String getFirstRunFocusedHotseatAppBubbleDescription() {
        return "";
    }

    /**
     * To be overridden by subclasses to indicate that there is an activity to launch
     * before showing the standard launcher experience.
     */
    protected boolean hasFirstRunActivity() {
        return false;
    }

    /**
     * To be overridden by subclasses to launch any first run activity
     */
    protected Intent getFirstRunActivity() {
        return null;
    }

    private boolean shouldRunFirstRunActivity() {
        return !ActivityManager.isRunningInTestHarness() &&
                !mSharedPrefs.getBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, false);
    }

    protected boolean hasRunFirstRunActivity() {
        return mSharedPrefs.getBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, false);
    }

    public boolean showFirstRunActivity() {
        if (shouldRunFirstRunActivity() &&
                hasFirstRunActivity()) {
            Intent firstRunIntent = getFirstRunActivity();
            if (firstRunIntent != null) {
                startActivity(firstRunIntent);
                markFirstRunActivityShown();
                return true;
            }
        }
        return false;
    }

    private void markFirstRunActivityShown() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(FIRST_RUN_ACTIVITY_DISPLAYED, true);
        editor.apply();
    }

    /**
     * To be overridden by subclasses to indicate that there is an in-activity full-screen intro
     * screen that must be displayed and dismissed.
     */
    protected boolean hasDismissableIntroScreen() {
        return false;
    }

    /**
     * Full screen intro screen to be shown and dismissed before the launcher can be used.
     */
    protected View getIntroScreen() {
        return null;
    }

    /**
     * To be overriden by subclasses to indicate whether the in-activity intro screen has been
     * dismissed. This method is ignored if #hasDismissableIntroScreen returns false.
     */
    private boolean shouldShowIntroScreen() {
        return hasDismissableIntroScreen() &&
                !mSharedPrefs.getBoolean(INTRO_SCREEN_DISMISSED, false);
    }

    protected void showIntroScreen() {
        View introScreen = getIntroScreen();
        changeWallpaperVisiblity(false);
        if (introScreen != null) {
            mDragLayer.showOverlayView(introScreen);
        }
    }

    public void dismissIntroScreen() {
        markIntroScreenDismissed();
        if (showFirstRunActivity()) {
            // We delay hiding the intro view until the first run activity is showing. This
            // avoids a blip.
            mWorkspace.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDragLayer.dismissOverlayView();
                    showFirstRunClings();
                }
            }, ACTIVITY_START_DELAY);
        } else {
            mDragLayer.dismissOverlayView();
            showFirstRunClings();
        }
        changeWallpaperVisiblity(true);
    }

    private void markIntroScreenDismissed() {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(INTRO_SCREEN_DISMISSED, true);
        editor.apply();
    }

    private void showFirstRunClings() {
        // The two first run cling paths are mutually exclusive, if the launcher is preinstalled
        // on the device, then we always show the first run cling experience (or if there is no
        // launcher2). Otherwise, we prompt the user upon started for migration
        LauncherClings launcherClings = new LauncherClings(this);
        if (launcherClings.shouldShowFirstRunOrMigrationClings()) {
            if (mModel.canMigrateFromOldLauncherDb(this)) {
                launcherClings.showMigrationCling();
            } else {
                launcherClings.showLongPressCling(true);
            }
        }
    }

    void showWorkspaceSearchAndHotseat() {
        if (mWorkspace != null) mWorkspace.setAlpha(1f);
        if (mHotseat != null) mHotseat.setAlpha(1f);
        if (mPageIndicators != null) mPageIndicators.setAlpha(1f);
        if (mSearchDropTargetBar != null) mSearchDropTargetBar.showSearchBar(false);
    }

    void hideWorkspaceSearchAndHotseat() {
        if (mWorkspace != null) mWorkspace.setAlpha(0f);
        if (mHotseat != null) mHotseat.setAlpha(0f);
        if (mPageIndicators != null) mPageIndicators.setAlpha(0f);
        if (mSearchDropTargetBar != null) mSearchDropTargetBar.hideSearchBar(false);
    }

    public ItemInfo createAppDragInfo(Intent appLaunchIntent) {
        // Called from search suggestion, not supported in other profiles.
        final UserHandleCompat myUser = UserHandleCompat.myUserHandle();
        LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(this);
        LauncherActivityInfoCompat activityInfo = launcherApps.resolveActivity(appLaunchIntent,
                myUser);
        if (activityInfo == null) {
            return null;
        }
        return new AppInfo(this, activityInfo, myUser, mIconCache, null);
    }

    public ItemInfo createShortcutDragInfo(Intent shortcutIntent, CharSequence caption,
            Bitmap icon) {
        // Called from search suggestion, not supported in other profiles.
        return createShortcutDragInfo(shortcutIntent, caption, icon,
                UserHandleCompat.myUserHandle());
    }

    public ItemInfo createShortcutDragInfo(Intent shortcutIntent, CharSequence caption,
            Bitmap icon, UserHandleCompat user) {
        UserManagerCompat userManager = UserManagerCompat.getInstance(this);
        CharSequence contentDescription = userManager.getBadgedLabelForUser(caption, user);
        return new ShortcutInfo(shortcutIntent, caption, contentDescription, icon, user);
    }

    protected void moveWorkspaceToDefaultScreen() {
        mWorkspace.moveToDefaultScreen(false);
    }

    public void startDrag(View dragView, ItemInfo dragInfo, DragSource source) {
        dragView.setTag(dragInfo);
        mWorkspace.onExternalDragStartedWithItem(dragView);
        mWorkspace.beginExternalDragShared(dragView, source);
    }

    @Override
    public void onPageSwitch(View newPage, int newPageIndex) {
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher3 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.dumpState();
        }
        Log.d(TAG, "END launcher3 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        synchronized (sDumpLogs) {
            writer.println(" ");
            writer.println("Debug logs: ");
            for (int i = 0; i < sDumpLogs.size(); i++) {
                writer.println("  " + sDumpLogs.get(i));
            }
        }
    }

    public static void dumpDebugLogsToConsole() {
        if (DEBUG_DUMP_LOG) {
            synchronized (sDumpLogs) {
                Log.d(TAG, "");
                Log.d(TAG, "*********************");
                Log.d(TAG, "Launcher debug logs: ");
                for (int i = 0; i < sDumpLogs.size(); i++) {
                    Log.d(TAG, "  " + sDumpLogs.get(i));
                }
                Log.d(TAG, "*********************");
                Log.d(TAG, "");
            }
        }
    }

    public static void addDumpLog(String tag, String log, boolean debugLog) {
        addDumpLog(tag, log, null, debugLog);
    }

    public static void addDumpLog(String tag, String log, Exception e, boolean debugLog) {
        if (debugLog) {
            if (e != null) {
                Log.d(tag, log, e);
            } else {
                Log.d(tag, log);
            }
        }
        if (DEBUG_DUMP_LOG) {
            sDateStamp.setTime(System.currentTimeMillis());
            synchronized (sDumpLogs) {
                sDumpLogs.add(sDateFormat.format(sDateStamp) + ": " + tag + ", " + log
                    + (e == null ? "" : (", Exception: " + e)));
            }
        }
    }

    public void dumpLogsToLocalData() {
        if (DEBUG_DUMP_LOG) {
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    boolean success = false;
                    sDateStamp.setTime(sRunStart);
                    String FILENAME = sDateStamp.getMonth() + "-"
                            + sDateStamp.getDay() + "_"
                            + sDateStamp.getHours() + "-"
                            + sDateStamp.getMinutes() + "_"
                            + sDateStamp.getSeconds() + ".txt";

                    FileOutputStream fos = null;
                    File outFile = null;
                    try {
                        outFile = new File(getFilesDir(), FILENAME);
                        outFile.createNewFile();
                        fos = new FileOutputStream(outFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (fos != null) {
                        PrintWriter writer = new PrintWriter(fos);

                        writer.println(" ");
                        writer.println("Debug logs: ");
                        synchronized (sDumpLogs) {
                            for (int i = 0; i < sDumpLogs.size(); i++) {
                                writer.println("  " + sDumpLogs.get(i));
                            }
                        }
                        writer.close();
                    }
                    try {
                        if (fos != null) {
                            fos.close();
                            success = true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        }
    }

    /**
     * M: Get current CellLayout bounds.
     *
     * @return mCurrentBounds.
     */
    Rect getCurrentBounds() {
        return mCurrentBounds;
    }

    /**
     * M: Show long press widget to add message, avoid duplication of message.
     */
    public void showLongPressWidgetToAddMessage() {
        if (mLongPressWidgetToAddToast == null) {
            mLongPressWidgetToAddToast = Toast.makeText(getApplicationContext(), R.string.long_press_widget_to_add,
                    Toast.LENGTH_SHORT);
        } else {
            mLongPressWidgetToAddToast.setText(R.string.long_press_widget_to_add);
            mLongPressWidgetToAddToast.setDuration(Toast.LENGTH_SHORT);
        }
        mLongPressWidgetToAddToast.show();
    }

    /**
     * M: Cancel long press widget to add message when press back key.
     */
    private void cancelLongPressWidgetToAddMessage() {
        if (mLongPressWidgetToAddToast != null) {
            mLongPressWidgetToAddToast.cancel();
        }
    }

    /**
     * M: Set orientation changed flag, this would make the apps customized pane
     * recreate views in certain condition.
     */
    public void notifyOrientationChanged() {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "notifyOrientationChanged: mOrientationChanged = "
                    + mOrientationChanged + ", mPaused = " + mPaused);
        }
        mOrientationChanged = true;
    }

    /**
     * M: Tell Launcher that the pages in app customized pane were recreated.
     */
    void notifyPagesWereRecreated() {
        mPagesWereRecreated = true;
    }

    /**
     * M: Reset re-sync apps pages flags.
     */
    private void resetReSyncFlags() {
        mOrientationChanged = false;
        mPagesWereRecreated = false;
    }

    /**
     * M: Volunteer free memory when system low memory.
     */
    private void volunteerFreeMemory() {
        mAppsCustomizeTabHost.onTrimMemory();

        /// M: Free more memory than AOSP
        mIconCache.flush();
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            Trace.traceBegin(Trace.TRACE_TAG_INPUT, "Launcher.dispatchTouchEvent:ACTION_DOWN");
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            Trace.traceBegin(Trace.TRACE_TAG_INPUT, "Launcher.dispatchTouchEvent:ACTION_UP");
        }
        Trace.traceEnd(Trace.TRACE_TAG_INPUT);
        return super.dispatchTouchEvent(ev);
    }
    
    /**M: Added for unread message feature.@{**/
    
    /**
     * M: Bind component unread information in workspace and all apps list.
     *
     * @param component the component name of the app.
     * @param unreadNum the number of the unread message.
     */
    public void bindComponentUnreadChanged(final ComponentName component, final int unreadNum) {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "bindComponentUnreadChanged: component = " + component
                    + ", unreadNum = " + unreadNum + ", this = " + this);
        }
        // Post to message queue to avoid possible ANR.
        mHandler.post(new Runnable() {
            public void run() {
                final long start = System.currentTimeMillis();
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindComponentUnreadChanged begin: component = " + component
                            + ", unreadNum = " + unreadNum + ", start = " + start);
                }
                if (mWorkspace != null) {
                    mWorkspace.updateComponentUnreadChanged(component, unreadNum);
                }

                if (mAppsCustomizeContent != null) {
                    mAppsCustomizeContent.updateAppsUnreadChanged(component, unreadNum);
                }
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindComponentUnreadChanged end: current time = "
                            + System.currentTimeMillis() + ", time used = "
                            + (System.currentTimeMillis() - start));
                }
            }
        });
    }

   /**
     * M: Bind shortcuts unread number if binding process has finished.
     */
    public void bindUnreadInfoIfNeeded() {
        if (LauncherLog.DEBUG_UNREAD) {
            LauncherLog.d(TAG, "bindUnreadInfoIfNeeded: mBindingWorkspaceFinished = "
                    + mBindingWorkspaceFinished + ", thread = " + Thread.currentThread());
        }
        if (mBindingWorkspaceFinished) {
            bindWorkspaceUnreadInfo();
        }

        if (mBindingAppsFinished) {
            bindAppsUnreadInfo();
        }
        mUnreadLoadCompleted = true;
    }

    /**
     * M: Bind unread number to shortcuts with data in MTKUnreadLoader.
     */
    private void bindWorkspaceUnreadInfo() {
        mHandler.post(new Runnable() {
            public void run() {
                final long start = System.currentTimeMillis();
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindWorkspaceUnreadInfo begin: start = " + start);
                }
                if (mWorkspace != null) {
                    mWorkspace.updateShortcutsAndFoldersUnread();
                }
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindWorkspaceUnreadInfo end: current time = "
                            + System.currentTimeMillis() + ",time used = "
                            + (System.currentTimeMillis() - start));
                }
            }
        });
    }

    /**
     * M: Bind unread number to shortcuts with data in MTKUnreadLoader.
     */
    private void bindAppsUnreadInfo() {
        mHandler.post(new Runnable() {
            public void run() {
                final long start = System.currentTimeMillis();
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindAppsUnreadInfo begin: start = " + start);
                }
                if (mAppsCustomizeContent != null) {
                    mAppsCustomizeContent.updateAppsUnread();
                }
                if (LauncherLog.DEBUG_PERFORMANCE) {
                    LauncherLog.d(TAG, "bindAppsUnreadInfo end: current time = "
                            + System.currentTimeMillis() + ",time used = "
                            + (System.currentTimeMillis() - start));
                }
            }
        });
    }
    
    /**@}**/

    ///M. ALPS01888456. when receive  configuration change, cancel drag.
    public void cancelDrag() {
        if (mWorkspace != null) {
            mWorkspace.cancelDrag();
        }
    }
    ///M.

    ///M. ALPS01960480.  for specially case.
    public boolean isReordering() {
        if (mWorkspace != null) {
            return mWorkspace.isReordering(false);
        }
        return false;
    }

    public void restoreOverviewMode() {
        if (mWorkspace != null) {
            LauncherLog.i(TAG, "restoreOverviewMode,mWorkspace.isInOverviewMode():"
                + mWorkspace.isInOverviewMode());
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(false);

                if (mWorkspace.mDragView != null) {
                    mWorkspace.mDragView.setAlpha(0.0f);
                }

                mWorkspace.enterOverviewMode();
            }
        }
    }

    //M:[OP09]Add for edit/hide apps. Start@{
    /**
     * M: Make the apps customize pane enter edit mode, user can rearrange the
     * application icons in this mode, add for OP09 start.
     */
    public void enterEditMode() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "enterEditMode: mIsInEditMode = " + sIsInEditMode);
        }

        sIsInEditMode = true;
        //TODO
        //final View marketButton = findViewById(R.id.market_button);
        //marketButton.setVisibility(View.GONE);

        mAppsCustomizeTabHost.enterEditMode();
    }

    /**
     * M: Make the apps customize pane exit edit mode.
     */
    public void exitEditMode() {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "exitEditMode: mIsInEditMode = " + sIsInEditMode);
        }

        sIsInEditMode = false;
        updateAppMarketIcon();
        mAppsCustomizeTabHost.exitEditMode();
    }

    /**
     * M: Whether the apps customize pane is in edit mode.
     *
     * @return whether in edit mode or not
     */
    public static boolean isInEditMode() {
        return sIsInEditMode;
    }

    AppsCustomizePagedView getPagedView() {
        return mAppsCustomizeContent;
    }
    /**
     * M: Update view visibility and icon resource of the given view.
     *
     * @param viewId
     * @param drawableResId
     * @param visible
     */
    private void updateVisibilityAndIconResource(final int viewId, final int drawableResId,
            final boolean visible) {
        final TextView textButton = (TextView) findViewById(viewId);
        if (visible) {
            final Resources r = getResources();
            final int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
            final int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);

            final Drawable iconDrawable = r.getDrawable(drawableResId);
            iconDrawable.setBounds(0, 0, w, h);
            textButton.setVisibility(View.VISIBLE);
            textButton.setCompoundDrawables(iconDrawable, null, null, null);
        } else {
            textButton.setVisibility(View.GONE);
        }
    }

    /**
     * M: Hanlde up button click event.
     *
     * @param v the click view
     */
    public void onClickHomeAndUpButton(View v) {
        exitEditMode();
    }

    /**
     * M: Click delete button will uinstall the app in edit mode.
     *
     * @param v the click view
     */
    public void onClickDeleteButton(View v) {
        if (v.getTag() instanceof AppInfo) {
            final AppInfo info = (AppInfo) ((BubbleTextView) v).getTag();
            startApplicationUninstallActivity(info);
        } else if (v.getTag() instanceof ShortcutInfo) {
            final ShortcutInfo info = (ShortcutInfo) ((BubbleTextView) v).getTag();
            startApplicationUninstallActivity(info.makeAppInfo());
        }
    }

    /**
     * M: Start hideAppsActivity.
     */
    private void startHideAppsActivity() {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setClassName(HIDE_PACKAGE_NAME, HIDE_ACTIVITY_NAME);
        startActivityForResultSafely(intent, REQUEST_HIDE_APPS);
    }

    /**
     * M: Enable pending apps queue to block all package add/change/removed
     * events to protect the right order in apps customize paged view, this
     * would be called when entering edit mode.
     */
    static void enablePendingAppsQueue() {
        sUsePendingAppsQueue = true;
    }

    /**
     * M: Disable pending queue and flush pending queue to handle all pending
     * package add/change/removed events.
     *
     * @param appsCustomizePagedView
     */
    static void disableAndFlushPendingAppsQueue(AppsCustomizePagedView appsCustomizePagedView) {
        sUsePendingAppsQueue = false;
        flushPendingAppsQueue(appsCustomizePagedView);
    }

    /**
     * M: Flush pending queue and handle all pending package add/change/removed
     * events.
     *
     * @param appsCustomizePagedView
     */
    static void flushPendingAppsQueue(AppsCustomizePagedView appsCustomizePagedView) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "flushPendingAppsQueue: numbers = " + sPendingChangedApps.size());
        }
        Iterator<PendingChangedApplications> iter = sPendingChangedApps.iterator();
        // TODO: maybe we can optimize this to avoid some applications
        // installed/uninstall/changed many times during user in edit mode.
        final boolean listEmpty = sPendingChangedApps.isEmpty();
        while (iter.hasNext()) {
            processPendingChangedApplications(appsCustomizePagedView, iter.next());
            iter.remove();
        }

        if (!listEmpty) {
            appsCustomizePagedView.processPendingPost();
            appsCustomizePagedView.onPackagesUpdated(
                LauncherModel.getSortedWidgetsAndShortcuts(
                       LauncherAppState.getInstance().getContext()));
        }
    }

    /**
     * M: Process pending changes application list, these apps are changed
     * during editing all apps list.
     */
    private static void processPendingChangedApplications(
            AppsCustomizePagedView appsCustomizePagedView, PendingChangedApplications pendingApps) {
        if (LauncherLog.DEBUG_EDIT) {
            LauncherLog.d(TAG, "processPendingChangedApplications: type = " + pendingApps.mType
                    + ",apps = " + pendingApps.mAppInfos);
        }

        switch (pendingApps.mType) {
            case PendingChangedApplications.TYPE_ADDED:
                appsCustomizePagedView.processPendingAddApps(pendingApps.mAppInfos);
                break;
            case PendingChangedApplications.TYPE_UPDATED:
                appsCustomizePagedView.processPendingUpdateApps(pendingApps.mAppInfos);
                break;
            case PendingChangedApplications.TYPE_REMOVED:
                appsCustomizePagedView.processPendingRemoveApps(pendingApps.mRemovedPackages);
                break;
            default:
                break;
        }
    }

    /**
     * M: Class used to record pending add/change/removed applications.
     */
    private static class PendingChangedApplications {
        public static final int TYPE_ADDED = 0;
        public static final int TYPE_UPDATED = 1;
        public static final int TYPE_REMOVED = 2;

        ArrayList<String> mRemovedPackages;
        ArrayList<AppInfo> mAppInfos;
        int mType;

        public PendingChangedApplications(ArrayList<AppInfo> apps, int t) {
            mAppInfos = apps;
            mType = t;
        }
    }

     //TODO: updateAppMarket Icon
     /** M:Sets the app market icon.
      */
    private void updateAppMarketIcon() {
        if (!DISABLE_MARKET_BUTTON) {
            //final View marketButton = findViewById(R.id.market_button);
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
            // Find the app market activity by resolving an intent.
            // (If multiple app markets are installed, it will return the ResolverActivity.)
            ComponentName activityName = intent.resolveActivity(getPackageManager());
            if (activityName != null) {
                int coi = getCurrentOrientationIndexForGlobalIcons();
                mAppMarketIntent = intent;
                /*sAppMarketIcon[coi] = updateTextButtonWithIconFromExternalActivity(
                        R.id.market_button, activityName, R.drawable.ic_launcher_market_holo,
                        TOOLBAR_ICON_METADATA_NAME);*/
                //marketButton.setVisibility(View.VISIBLE);
            } else {
                // We should hide and disable the view so that we don't try and
                //restore the visibility  of it when we swap between
                //drag & normal states from IconDropTarget subclasses.
                //marketButton.setVisibility(View.GONE);
                //marketButton.setEnabled(false);
            }
        }
    }

    private void updateAppMarketIcon(Drawable.ConstantState d) {
        if (!DISABLE_MARKET_BUTTON) {
            // Ensure that the new drawable we are creating has the approprate toolbar icon bounds
            Resources r = getResources();
            Drawable marketIconDrawable = d.newDrawable(r);
            int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
            int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);
            marketIconDrawable.setBounds(0, 0, w, h);

            //updateTextButtonWithDrawable(R.id.market_button, marketIconDrawable);
        }
    }

    /**
     * M: Start Activity For Result Safely.
     */
    void startActivityForResultSafely(Intent intent, int requestCode) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "startActivityForResultSafely: intent = " + intent
                    + ", requestCode = " + requestCode);
        }

        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    @Override
    public void bindAllItems(ArrayList<AppInfo> allApps, ArrayList<AppInfo> apps,
            ArrayList<FolderInfo> folders) {
        if (LauncherLog.DEBUG) {
            LauncherLog.d(TAG, "bindAllItems: "
                    + "\n allApps = " + allApps
                    + "\n apps = " + apps
                    + "\n folders = " + folders);
        }

        if (!LauncherAppState.isDisableAllApps()) {
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setItems(allApps, apps, folders);
                mAppsCustomizeContent.onPackagesUpdated(
                        LauncherModel.getSortedWidgetsAndShortcuts(this));
                mBindingAppsFinished = true;
                if (LauncherLog.DEBUG) {
                    LauncherLog.d(TAG, "bindAllApplications: finished!");
                }
            }
        }

        /** M: If unread information load completed, we need to update information in app list.@{**/
        if (mUnreadLoadCompleted) {
            AppsCustomizePagedView.updateUnreadNumInAppInfo(allApps);
        }
        /**@}**/
    }

    //M:[OP09]End }@

}

interface LauncherTransitionable {
    View getContent();
    void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStep(Launcher l, float t);
    void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace);
}

interface DebugIntents {
    static final String DELETE_DATABASE = "com.android.launcher3.action.DELETE_DATABASE";
    static final String MIGRATE_DATABASE = "com.android.launcher3.action.MIGRATE_DATABASE";
}
