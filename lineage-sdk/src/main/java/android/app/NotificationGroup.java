package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.UUID;

public class NotificationGroup implements Parcelable {
    private UUID mUuid;
    private String mName;

    public NotificationGroup(UUID uuid, String name) {
        mUuid = uuid;
        mName = name;
    }

    protected NotificationGroup(Parcel in) {
        mUuid = UUID.fromString(in.readString());
        mName = in.readString();
    }

    public UUID getUuid() { return mUuid; }
    public String getName() { return mName; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mUuid.toString());
        dest.writeString(mName);
    }

    public static final Creator<NotificationGroup> CREATOR = new Creator<NotificationGroup>() {
        @Override
        public NotificationGroup createFromParcel(Parcel in) {
            return new NotificationGroup(in);
        }

        @Override
        public NotificationGroup[] newArray(int size) {
            return new NotificationGroup[size];
        }
    };
}
