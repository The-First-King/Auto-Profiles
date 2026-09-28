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

import android.os.Parcel;
import android.os.Parcelable;
import java.util.UUID;

public class Profile implements Parcelable {
    private UUID mUuid;
    private String mName;

    public Profile(String name, UUID uuid) {
        mName = name;
        mUuid = uuid;
    }

    protected Profile(Parcel in) {
        long mostSigBits = in.readLong();
        long leastSigBits = in.readLong();
        mUuid = new UUID(mostSigBits, leastSigBits);
        mName = in.readString();
    }

    public UUID getUuid() {
        return mUuid;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mUuid.getMostSignificantBits());
        dest.writeLong(mUuid.getLeastSignificantBits());
        dest.writeString(mName);
    }

    public static final Creator<Profile> CREATOR = new Creator<Profile>() {
        @Override
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };
}
