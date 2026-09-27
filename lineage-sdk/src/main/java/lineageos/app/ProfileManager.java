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
import android.os.RemoteException;
import android.util.Log;
import java.util.UUID;

public class ProfileManager {
    private static final String TAG = "ProfileManager";
    private Context mContext;
    private static IProfileManager sService;

    public static final String INTENT_ACTION_PROFILE_SELECTED =
            "lineageos.platform.intent.action.PROFILE_SELECTED";
    public static final String INTENT_ACTION_PROFILE_UPDATED =
            "lineageos.platform.intent.action.PROFILE_UPDATED";
    public static final String EXTRA_PROFILE_NAME = "name";
    public static final String EXTRA_PROFILE_UUID = "uuid";

    public ProfileManager(Context context) {
        mContext = context;
    }

    static public IProfileManager getService() throws RemoteException {
        if (sService == null) {
            Log.w(TAG, "ProfileManager service not available");
        }
        return sService;
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
                return service.setActiveProfile(profileUuid);
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
                return service.getProfile(uuid);
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

    public void addProfile(Profile profile) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                service.addProfile(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error adding profile", e);
        }
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

    public void removeProfile(Profile profile) {
        try {
            IProfileManager service = getService();
            if (service != null) {
                service.removeProfile(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error removing profile", e);
        }
    }
}
