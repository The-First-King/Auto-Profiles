/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package lineageos.app;

import android.content.Context;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.UUID;

public class ProfileManager {
    private static final String TAG = "ProfileManager";

    /** Matches LineageContextConstants.LINEAGE_PROFILE_SERVICE from the real SDK. */
    private static final String LINEAGE_PROFILE_SERVICE = "profile";

    private static ProfileManager sInstance;
    private static IProfileManager sService;

    private Context mContext;

    public static final String INTENT_ACTION_PROFILE_SELECTED =
            "lineageos.platform.intent.action.PROFILE_SELECTED";
    public static final String INTENT_ACTION_PROFILE_UPDATED =
            "lineageos.platform.intent.action.PROFILE_UPDATED";
    public static final String EXTRA_PROFILE_NAME = "name";
    public static final String EXTRA_PROFILE_UUID = "uuid";

    private ProfileManager(Context context) {
        mContext = context;
    }

    /**
     * Singleton accessor expected by callers using the historical LineageOS SDK API shape.
     */
    public static ProfileManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProfileManager(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * android.os.ServiceManager is a hidden, non-SDK class: it exists on every device's
     * boot classpath but is stripped from the public SDK stub jar we compile against.
     * We reach it through reflection instead of a normal import.
     */
    private static IBinder getBinderFromServiceManager(String name) {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            return (IBinder) getServiceMethod.invoke(null, name);
        } catch (Exception e) {
            Log.e(TAG, "Reflection failed to reach android.os.ServiceManager", e);
            return null;
        }
    }

    static public IProfileManager getService() {
        if (sService != null) {
            return sService;
        }
        IBinder binder = getBinderFromServiceManager(LINEAGE_PROFILE_SERVICE);
        if (binder == null) {
            Log.w(TAG, "ProfileManager system service not found on this device");
            return null;
        }
        sService = IProfileManager.Stub.asInterface(binder);
        return sService;
    }

    public boolean isProfilesEnabled() {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.isEnabled();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error checking isProfilesEnabled", e);
        }
        return false;
    }

    public Profile getActiveProfile() {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.getActiveProfile();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting active profile", e);
        }
        return null;
    }

    public boolean setActiveProfile(UUID profileUuid) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.setActiveProfile(new ParcelUuid(profileUuid));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error setting active profile", e);
        }
        return false;
    }

    public Profile getProfile(UUID uuid) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.getProfile(new ParcelUuid(uuid));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting profile", e);
        }
        return null;
    }

    public Profile[] getProfiles() {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.getProfiles();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting profiles", e);
        }
        return new Profile[0];
    }

    public boolean addProfile(Profile profile) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.addProfile(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error adding profile", e);
        }
        return false;
    }

    public void updateProfile(Profile profile) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                service.updateProfile(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating profile", e);
        }
    }

    public boolean removeProfile(Profile profile) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                return service.removeProfile(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error removing profile", e);
        }
        return false;
    }
}
