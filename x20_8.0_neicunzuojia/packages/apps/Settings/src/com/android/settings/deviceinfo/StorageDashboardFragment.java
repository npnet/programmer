/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Loader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.SearchIndexableResource;
import android.support.annotation.VisibleForTesting;
import android.util.SparseArray;
import android.view.View;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.applications.PackageManagerWrapperImpl;
import com.android.settings.applications.UserManagerWrapper;
import com.android.settings.applications.UserManagerWrapperImpl;
import com.android.settings.core.PreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.storage.AutomaticStorageManagementSwitchPreferenceController;
import com.android.settings.deviceinfo.storage.SecondaryUserController;
import com.android.settings.deviceinfo.storage.StorageAsyncLoader;
import com.android.settings.deviceinfo.storage.StorageItemPreferenceController;
import com.android.settings.deviceinfo.storage.StorageSummaryDonutPreferenceController;
import com.android.settings.deviceinfo.storage.UserIconLoader;
import com.android.settings.deviceinfo.storage.VolumeSizesLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import android.os.SystemProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StorageDashboardFragment extends DashboardFragment
    implements LoaderManager.LoaderCallbacks<SparseArray<StorageAsyncLoader.AppsStorageResult>> {
    private static final String TAG = "StorageDashboardFrag";
    private static final int STORAGE_JOB_ID = 0;
    private static final int ICON_JOB_ID = 1;
    private static final int VOLUME_SIZE_JOB_ID = 2;
    private static final int OPTIONS_MENU_MIGRATE_DATA = 100;

    private VolumeInfo mVolume;
    private PrivateStorageInfo mStorageInfo;
    private SparseArray<StorageAsyncLoader.AppsStorageResult> mAppsResult;

    private StorageSummaryDonutPreferenceController mSummaryController;
    private StorageItemPreferenceController mPreferenceController;
    private PrivateVolumeOptionMenuController mOptionMenuController;
    private List<PreferenceController> mSecondaryUsers;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Initialize the storage sizes that we can quickly calc.
        final Activity activity = getActivity();
        StorageManager sm = activity.getSystemService(StorageManager.class);
        mVolume = Utils.maybeInitializeVolume(sm, getArguments());
        if (mVolume == null) {
            activity.finish();
            return;
        }

        initializeOptionsMenu(activity);
    }

    @VisibleForTesting
    void initializeOptionsMenu(Activity activity) {
        mOptionMenuController = new PrivateVolumeOptionMenuController(
                activity, mVolume, new PackageManagerWrapperImpl(activity.getPackageManager()));
        getLifecycle().addObserver(mOptionMenuController);
        setHasOptionsMenu(true);
        activity.invalidateOptionsMenu();
    }

    @Override
    public void onViewCreated(View v, Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        setLoading(true, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(STORAGE_JOB_ID, Bundle.EMPTY, this);
        getLoaderManager().initLoader(ICON_JOB_ID, Bundle.EMPTY, new IconLoaderCallbacks());
        getLoaderManager().initLoader(VOLUME_SIZE_JOB_ID, Bundle.EMPTY, new VolumeSizeCallbacks());
    }

    private void onReceivedSizes() {
        if (mStorageInfo == null || mAppsResult == null) {
            return;
        }
		float JTY_ROM_MULTIPLE = 1;
			//longliang add if
		if(VolumeInfo.ID_PRIVATE_INTERNAL.equals(mVolume.getId())){
		      JTY_ROM_MULTIPLE = Float.parseFloat(SystemProperties.get("ro.jty.fat.expandNum", "1"));
                 }
        long privateUsedBytes = mStorageInfo.totalBytes - mStorageInfo.freeBytes;
        mSummaryController.updateBytes(privateUsedBytes, (long)(mVolume.getPath().getTotalSpace() * JTY_ROM_MULTIPLE));
        mPreferenceController.setVolume(mVolume);
        mPreferenceController.setUsedSize(privateUsedBytes);
        mPreferenceController.setTotalSize((long)(mVolume.getPath().getTotalSpace() * JTY_ROM_MULTIPLE));
        for (int i = 0, size = mSecondaryUsers.size(); i < size; i++) {
            PreferenceController controller = mSecondaryUsers.get(i);
            if (controller instanceof SecondaryUserController) {
                SecondaryUserController userController = (SecondaryUserController) controller;
                userController.setTotalSize((long)(mVolume.getPath().getTotalSpace() * JTY_ROM_MULTIPLE));
            }
        }

        mPreferenceController.onLoadFinished(mAppsResult, UserHandle.myUserId());
        updateSecondaryUserControllers(mSecondaryUsers, mAppsResult);

        // setLoading always causes a flicker, so let's avoid doing it.
        if (getView().findViewById(R.id.loading_container).getVisibility() == View.VISIBLE) {
            setLoading(false, true);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.SETTINGS_STORAGE_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.storage_dashboard_fragment;
    }

    @Override
    protected List<PreferenceController> getPreferenceControllers(Context context) {
        final List<PreferenceController> controllers = new ArrayList<>();
        mSummaryController = new StorageSummaryDonutPreferenceController(context);
        controllers.add(mSummaryController);

        StorageManager sm = context.getSystemService(StorageManager.class);
        mPreferenceController = new StorageItemPreferenceController(context, this,
                mVolume, new StorageManagerVolumeProvider(sm));
        controllers.add(mPreferenceController);

        UserManagerWrapper userManager =
                new UserManagerWrapperImpl(context.getSystemService(UserManager.class));
        mSecondaryUsers = SecondaryUserController.getSecondaryUserControllers(context, userManager);
        controllers.addAll(mSecondaryUsers);

        final AutomaticStorageManagementSwitchPreferenceController asmController =
                new AutomaticStorageManagementSwitchPreferenceController(
                        context, mMetricsFeatureProvider, getFragmentManager());
        getLifecycle().addObserver(asmController);
        controllers.add(asmController);
        return controllers;
    }

    @VisibleForTesting
    protected void setVolume(VolumeInfo info) {
        mVolume = info;
    }

    /**
     * Updates the secondary user controller sizes.
     */
    private void updateSecondaryUserControllers(List<PreferenceController> controllers,
            SparseArray<StorageAsyncLoader.AppsStorageResult> stats) {
        for (int i = 0, size = controllers.size(); i < size; i++) {
            PreferenceController controller = controllers.get(i);
            if (controller instanceof StorageAsyncLoader.ResultHandler) {
                StorageAsyncLoader.ResultHandler userController =
                        (StorageAsyncLoader.ResultHandler) controller;
                userController.handleResult(stats);
            }
        }
    }

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.storage_dashboard_fragment;
                    return Arrays.asList(sir);
                }

                @Override
                public List<PreferenceController> getPreferenceControllers(Context context) {
                    final StorageManager sm = context.getSystemService(StorageManager.class);
                    final UserManagerWrapper userManager =
                            new UserManagerWrapperImpl(context.getSystemService(UserManager.class));
                    final List<PreferenceController> controllers = new ArrayList<>();
                    controllers.add(new StorageSummaryDonutPreferenceController(context));
                    controllers.add(new StorageItemPreferenceController(context, null /* host */,
                            null /* volume */, new StorageManagerVolumeProvider(sm)));
                    controllers.addAll(SecondaryUserController.getSecondaryUserControllers(
                            context, userManager));
                    return controllers;
                }

            };

    @Override
    public Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> onCreateLoader(int id,
            Bundle args) {
        Context context = getContext();
        return new StorageAsyncLoader(context,
                new UserManagerWrapperImpl(context.getSystemService(UserManager.class)),
                mVolume.fsUuid,
                new StorageStatsSource(context),
                new PackageManagerWrapperImpl(context.getPackageManager()));
    }

    @Override
    public void onLoadFinished(Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> loader,
            SparseArray<StorageAsyncLoader.AppsStorageResult> data) {
        mAppsResult = data;
        onReceivedSizes();
    }

    @Override
    public void onLoaderReset(Loader<SparseArray<StorageAsyncLoader.AppsStorageResult>> loader) {
    }

    /**
     * IconLoaderCallbacks exists because StorageDashboardFragment already implements
     * LoaderCallbacks for a different type.
     */
    public final class IconLoaderCallbacks
            implements LoaderManager.LoaderCallbacks<SparseArray<Drawable>> {
        @Override
        public Loader<SparseArray<Drawable>> onCreateLoader(int id, Bundle args) {
            return new UserIconLoader(
                    getContext(),
                    () -> UserIconLoader.loadUserIconsWithContext(getContext()));
        }

        @Override
        public void onLoadFinished(
                Loader<SparseArray<Drawable>> loader, SparseArray<Drawable> data) {
            mSecondaryUsers
                    .stream()
                    .filter(controller -> controller instanceof UserIconLoader.UserIconHandler)
                    .forEach(
                            controller ->
                                    ((UserIconLoader.UserIconHandler) controller)
                                            .handleUserIcons(data));
        }

        @Override
        public void onLoaderReset(Loader<SparseArray<Drawable>> loader) {}
    }

    public final class VolumeSizeCallbacks
            implements LoaderManager.LoaderCallbacks<PrivateStorageInfo> {
        @Override
        public Loader<PrivateStorageInfo> onCreateLoader(int id, Bundle args) {
            Context context = getContext();
            StorageManager sm = context.getSystemService(StorageManager.class);
            StorageManagerVolumeProvider smvp = new StorageManagerVolumeProvider(sm);
            final StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
            return new VolumeSizesLoader(context, smvp, stats, mVolume);
        }

        @Override
        public void onLoaderReset(Loader<PrivateStorageInfo> loader) {}

        @Override
        public void onLoadFinished(
                Loader<PrivateStorageInfo> loader, PrivateStorageInfo privateStorageInfo) {
            if (privateStorageInfo == null) {
                getActivity().finish();
                return;
            }

            mStorageInfo = privateStorageInfo;
            onReceivedSizes();
        }
    }
}
