/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.support.annotation.NonNull;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.SearchIndexableRaw;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.drawer.SettingsDrawerActivity;
import com.mediatek.settings.deviceinfo.StorageSettingsExts;
import android.os.SystemProperties;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * Panel showing both internal storage (both built-in storage and private
 * volumes) and removable storage (public volumes).
 */
public class StorageSettings extends SettingsPreferenceFragment implements Indexable {
    static final String TAG = "StorageSettings";

    private static final String TAG_VOLUME_UNMOUNTED = "volume_unmounted";
    private static final String TAG_DISK_INIT = "disk_init";
    private static boolean sHasOpened;

    static final int COLOR_PUBLIC = Color.parseColor("#ff9e9e9e");

    static final int[] COLOR_PRIVATE = new int[] {
            Color.parseColor("#ff26a69a"),
            Color.parseColor("#ffab47bc"),
            Color.parseColor("#fff2a600"),
            Color.parseColor("#ffec407a"),
            Color.parseColor("#ffc0ca33"),
    };

    private StorageManager mStorageManager;

    private PreferenceCategory mInternalCategory;
    private PreferenceCategory mExternalCategory;

    private StorageSummaryPreference mInternalSummary;
    private static long sTotalInternalStorage;

    /// M: Add for default write disk.
    private StorageSettingsExts mCustomizationCategory;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DEVICEINFO_STORAGE;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_uri_storage;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();

        mStorageManager = context.getSystemService(StorageManager.class);
        /// M: ALPS03292381 repeated register
        //mStorageManager.registerListener(mStorageListener);

        if (sTotalInternalStorage <= 0) {
            sTotalInternalStorage = mStorageManager.getPrimaryStorageSize();
        }

        addPreferencesFromResource(R.xml.device_info_storage);

        mInternalCategory = (PreferenceCategory) findPreference("storage_internal");
        mExternalCategory = (PreferenceCategory) findPreference("storage_external");

        mInternalSummary = new StorageSummaryPreference(getPrefContext());

        setHasOptionsMenu(true);
        /// M: Add for default write disk. @}
        mCustomizationCategory = new StorageSettingsExts(getActivity(),
              getPreferenceScreen(), mStorageManager);
        mCustomizationCategory.initCustomizationCategory();
        /// @}
    }

    private final StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (isInteresting(vol)) {
                refresh();
            }
        }

        @Override
        public void onDiskDestroyed(DiskInfo disk) {
            refresh();
        }
    };

    private static boolean isInteresting(VolumeInfo vol) {
        switch(vol.getType()) {
            case VolumeInfo.TYPE_PRIVATE:
            case VolumeInfo.TYPE_PUBLIC:
                return true;
            default:
                return false;
        }
    }

    private static long getTotalSize(VolumeInfo info) {
 {
            final File path = info.getPath();
            if (path == null) {
                // Should not happen, caller should have checked.
                Log.e(TAG, "info's path is null on getTotalSize(): " + info);
                return 0;
            }
            return path.getTotalSpace();
        }
    }

    private synchronized void refresh() {
        final Context context = getPrefContext();

        getPreferenceScreen().removeAll();
        mInternalCategory.removeAll();
        mExternalCategory.removeAll();

        /// M: Add for default write disk.
        mCustomizationCategory.updateCustomizationCategory();

        mInternalCategory.addPreference(mInternalSummary);

        int privateCount = 0;
        long privateUsedBytes = 0;
        long privateTotalBytes = 0;

        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        Collections.sort(volumes, VolumeInfo.getDescriptionComparator());

        for (VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                //final long volumeTotalBytes = PrivateStorageInfo.getTotalSize(vol,
                //        sTotalInternalStorage);
               // final long volumeTotalBytes = getTotalSize(vol);
		float JTY_ROM_MULTIPLE = 1;
			//longliang add if
		if(VolumeInfo.ID_PRIVATE_INTERNAL.equals(vol.getId())){
		      JTY_ROM_MULTIPLE = Float.parseFloat(SystemProperties.get("ro.jty.fat.expandNum", "1"));
                 }
		final long volumeTotalBytes = (long)(getTotalSize(vol)*JTY_ROM_MULTIPLE);
                final int color = COLOR_PRIVATE[privateCount++ % COLOR_PRIVATE.length];
                mInternalCategory.addPreference(
                        new StorageVolumePreference(context, vol, color, volumeTotalBytes));
                if (vol.isMountedReadable()) {
                    final File path = vol.getPath();
                    privateUsedBytes += (getTotalSize(vol) - path.getFreeSpace());
                    privateTotalBytes += volumeTotalBytes;
                }
            } else if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                mExternalCategory.addPreference(
                        new StorageVolumePreference(context, vol, COLOR_PUBLIC, 0));
            }
        }

        // Show missing private volumes
        final List<VolumeRecord> recs = mStorageManager.getVolumeRecords();
        for (VolumeRecord rec : recs) {
            if (rec.getType() == VolumeInfo.TYPE_PRIVATE
                    && mStorageManager.findVolumeByUuid(rec.getFsUuid()) == null) {
                // TODO: add actual storage type to record
                final Drawable icon = context.getDrawable(R.drawable.ic_sim_sd);
                icon.mutate();
                icon.setTint(COLOR_PUBLIC);

                final Preference pref = new Preference(context);
                pref.setKey(rec.getFsUuid());
                pref.setTitle(rec.getNickname());
                pref.setSummary(com.android.internal.R.string.ext_media_status_missing);
                pref.setIcon(icon);
                mInternalCategory.addPreference(pref);
            }
        }

        // Show unsupported disks to give a chance to init
        final List<DiskInfo> disks = mStorageManager.getDisks();
        for (DiskInfo disk : disks) {
            if (disk.volumeCount == 0 && disk.size > 0) {
                final Preference pref = new Preference(context);
                pref.setKey(disk.getId());
                pref.setTitle(disk.getDescription());
                pref.setSummary(com.android.internal.R.string.ext_media_status_unsupported);
                pref.setIcon(R.drawable.ic_sim_sd);
                mExternalCategory.addPreference(pref);
            }
        }

        final BytesResult result = Formatter.formatBytes(getResources(), privateUsedBytes, 0);
        mInternalSummary.setTitle(TextUtils.expandTemplate(getText(R.string.storage_size_large),
                result.value, result.units));
        mInternalSummary.setSummary(getString(R.string.storage_volume_used_total,
                Formatter.formatFileSize(context, privateTotalBytes)));
        if (mInternalCategory.getPreferenceCount() > 0) {
            getPreferenceScreen().addPreference(mInternalCategory);
        }
        if (mExternalCategory.getPreferenceCount() > 0) {
            getPreferenceScreen().addPreference(mExternalCategory);
        }

        if (mInternalCategory.getPreferenceCount() == 2
                && mExternalCategory.getPreferenceCount() == 0 && !sHasOpened) {
            // Only showing primary internal storage, so just shortcut
            final Bundle args = new Bundle();
            args.putString(VolumeInfo.EXTRA_VOLUME_ID, VolumeInfo.ID_PRIVATE_INTERNAL);
            Intent intent = Utils.onBuildStartFragmentIntent(getActivity(),
                    StorageDashboardFragment.class.getName(), args, null,
                    R.string.storage_settings, null, false, getMetricsCategory());
            intent.putExtra(SettingsDrawerActivity.EXTRA_SHOW_MENU, true);
            getActivity().startActivity(intent);
            /// M: ALPS03292605 Fix storage abnormal behavior after plug out SD card.
            sHasOpened = true;
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mStorageManager.registerListener(mStorageListener);
        sHasOpened = false;
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mStorageManager.unregisterListener(mStorageListener);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference pref) {
        final String key = pref.getKey();
        if (pref instanceof StorageVolumePreference) {
            // Picked a normal volume
            final VolumeInfo vol = mStorageManager.findVolumeById(key);

            if (vol == null) {
                return false;
            }

            if (vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                VolumeUnmountedFragment.show(this, vol.getId());
                return true;
            } else if (vol.getState() == VolumeInfo.STATE_UNMOUNTABLE) {
                DiskInitFragment.show(this, R.string.storage_dialog_unmountable, vol.getDiskId());
                return true;
            }

            if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                final Bundle args = new Bundle();
                args.putString(VolumeInfo.EXTRA_VOLUME_ID, vol.getId());

                if (VolumeInfo.ID_PRIVATE_INTERNAL.equals(vol.getId())) {
                    startFragment(this, StorageDashboardFragment.class.getCanonicalName(),
                            R.string.storage_settings, 0, args);
                } else {
                    // TODO: Go to the StorageDashboardFragment once it fully handles all of the
                    //       SD card cases and other private internal storage cases.
                    PrivateVolumeSettings.setVolumeSize(args, PrivateStorageInfo.getTotalSize(vol,
                            sTotalInternalStorage));
                    startFragment(this, PrivateVolumeSettings.class.getCanonicalName(),
                            -1, 0, args);
                }

                return true;

            } else if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                if (vol.isMountedReadable()) {
                    startActivity(vol.buildBrowseIntent());
                    return true;
                } else {
                    final Bundle args = new Bundle();
                    args.putString(VolumeInfo.EXTRA_VOLUME_ID, vol.getId());
                    startFragment(this, PublicVolumeSettings.class.getCanonicalName(),
                            -1, 0, args);
                    return true;
                }
            }

        } else if (key.startsWith("disk:")) {
            // Picked an unsupported disk
            DiskInitFragment.show(this, R.string.storage_dialog_unsupported, key);
            return true;

        } else {
            /// M: Add for default write disk. @{
            if (key.startsWith(StorageSettingsExts.KEY_WRITE_DISK_ITEM)) {
                return false;
            }
            /// @}
            // Picked a missing private volume
            final Bundle args = new Bundle();
            args.putString(VolumeRecord.EXTRA_FS_UUID, key);
            startFragment(this, PrivateVolumeForget.class.getCanonicalName(),
                    R.string.storage_menu_forget, 0, args);
            return true;
        }

        return false;
    }

    public static class MountTask extends AsyncTask<Void, Void, Exception> {
        private final Context mContext;
        private final StorageManager mStorageManager;
        private final String mVolumeId;
        private final String mDescription;

        public MountTask(Context context, VolumeInfo volume) {
            mContext = context.getApplicationContext();
            mStorageManager = mContext.getSystemService(StorageManager.class);
            mVolumeId = volume.getId();
            mDescription = mStorageManager.getBestVolumeDescription(volume);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                mStorageManager.mount(mVolumeId);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e == null) {
                Toast.makeText(mContext, mContext.getString(R.string.storage_mount_success,
                        mDescription), Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Failed to mount " + mVolumeId, e);
                Toast.makeText(mContext, mContext.getString(R.string.storage_mount_failure,
                        mDescription), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class UnmountTask extends AsyncTask<Void, Void, Exception> {
        private final Context mContext;
        private final StorageManager mStorageManager;
        private final String mVolumeId;
        private final String mDescription;

        public UnmountTask(Context context, VolumeInfo volume) {
            mContext = context.getApplicationContext();
            mStorageManager = mContext.getSystemService(StorageManager.class);
            mVolumeId = volume.getId();
            mDescription = mStorageManager.getBestVolumeDescription(volume);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                mStorageManager.unmount(mVolumeId);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception e) {
            if (e == null) {
                Toast.makeText(mContext, mContext.getString(R.string.storage_unmount_success,
                        mDescription), Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Failed to unmount " + mVolumeId, e);
                Toast.makeText(mContext, mContext.getString(R.string.storage_unmount_failure,
                        mDescription), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class VolumeUnmountedFragment extends InstrumentedDialogFragment {
        public static void show(Fragment parent, String volumeId) {
            final Bundle args = new Bundle();
            args.putString(VolumeInfo.EXTRA_VOLUME_ID, volumeId);

            final VolumeUnmountedFragment dialog = new VolumeUnmountedFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_VOLUME_UNMOUNTED);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.DIALOG_VOLUME_UNMOUNT;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final StorageManager sm = context.getSystemService(StorageManager.class);

            final String volumeId = getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID);
            final VolumeInfo vol = sm.findVolumeById(volumeId);

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(TextUtils.expandTemplate(
                    getText(R.string.storage_dialog_unmounted), vol.getDisk().getDescription()));

            builder.setPositiveButton(R.string.storage_menu_mount,
                    new DialogInterface.OnClickListener() {
                /**
                 * Check if an {@link RestrictedLockUtils#sendShowAdminSupportDetailsIntent admin
                 * details intent} should be shown for the restriction and show it.
                 *
                 * @param restriction The restriction to check
                 * @return {@code true} iff a intent was shown.
                 */
                private boolean wasAdminSupportIntentShown(@NonNull String restriction) {
                    EnforcedAdmin admin = RestrictedLockUtils.checkIfRestrictionEnforced(
                            getActivity(), restriction, UserHandle.myUserId());
                    boolean hasBaseUserRestriction = RestrictedLockUtils.hasBaseUserRestriction(
                            getActivity(), restriction, UserHandle.myUserId());
                    if (admin != null && !hasBaseUserRestriction) {
                        RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getActivity(), admin);
                        return true;
                    }

                    return false;
                }

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (wasAdminSupportIntentShown(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)) {
                        return;
                    }

                    if (vol.disk != null && vol.disk.isUsb() &&
                            wasAdminSupportIntentShown(UserManager.DISALLOW_USB_FILE_TRANSFER)) {
                        return;
                    }

                    new MountTask(context, vol).execute();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);

            return builder.create();
        }
    }

    public static class DiskInitFragment extends InstrumentedDialogFragment {
        @Override
        public int getMetricsCategory() {
            return MetricsEvent.DIALOG_VOLUME_INIT;
        }

        public static void show(Fragment parent, int resId, String diskId) {
            final Bundle args = new Bundle();
            args.putInt(Intent.EXTRA_TEXT, resId);
            args.putString(DiskInfo.EXTRA_DISK_ID, diskId);

            final DiskInitFragment dialog = new DiskInitFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_DISK_INIT);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final StorageManager sm = context.getSystemService(StorageManager.class);

            final int resId = getArguments().getInt(Intent.EXTRA_TEXT);
            final String diskId = getArguments().getString(DiskInfo.EXTRA_DISK_ID);
            final DiskInfo disk = sm.findDiskById(diskId);

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(TextUtils.expandTemplate(getText(resId), disk.getDescription()));

            builder.setPositiveButton(R.string.storage_menu_set_up,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final Intent intent = new Intent(context, StorageWizardInit.class);
                    intent.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(R.string.cancel, null);

            return builder.create();
        }
    }

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {
        private final Context mContext;
        private final SummaryLoader mLoader;
        private final StorageManagerVolumeProvider mStorageManagerVolumeProvider;

        private SummaryProvider(Context context, SummaryLoader loader) {
            mContext = context;
            mLoader = loader;
            final StorageManager storageManager = mContext.getSystemService(StorageManager.class);
            mStorageManagerVolumeProvider = new StorageManagerVolumeProvider(storageManager);
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                updateSummary();
            }
        }

        private void updateSummary() {
            // TODO: Register listener.
            final NumberFormat percentageFormat = NumberFormat.getPercentInstance();
            final PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(
                    mStorageManagerVolumeProvider);
            float JTY_ROM_MULTIPLE = Float.parseFloat(SystemProperties.get("ro.jty.fat.expandNum", "1"));
            double privateUsedBytes = info.totalBytes - info.freeBytes;
            mLoader.setSummary(this, mContext.getString(R.string.storage_summary,
                    percentageFormat.format(privateUsedBytes / (long)(info.totalBytes*JTY_ROM_MULTIPLE)),
                    Formatter.formatFileSize(mContext, (long)(info.freeBytes*JTY_ROM_MULTIPLE))));
        }
    }


    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                                                                   SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };

    /**
     * Enable indexing of searchable data
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider() {
            @Override
            public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                final List<SearchIndexableRaw> result = new ArrayList<SearchIndexableRaw>();

                SearchIndexableRaw data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.storage_settings);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.internal_storage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                final StorageManager storage = context.getSystemService(StorageManager.class);
                final List<VolumeInfo> vols = storage.getVolumes();
                for (VolumeInfo vol : vols) {
                    if (isInteresting(vol)) {
                        data.title = storage.getBestVolumeDescription(vol);
                        data.screenTitle = context.getString(R.string.storage_settings);
                        result.add(data);
                    }
                }

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_size);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_available);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_apps_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_dcim_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_music_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_downloads_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_media_cache_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                data = new SearchIndexableRaw(context);
                data.title = context.getString(R.string.memory_media_misc_usage);
                data.screenTitle = context.getString(R.string.storage_settings);
                result.add(data);

                return result;
            }
        };
}
