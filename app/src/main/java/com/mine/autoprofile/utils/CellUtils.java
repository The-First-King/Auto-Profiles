package com.mine.autoprofile.utils;

import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds stable identity keys for the cell towers currently in range, across
 * radio technologies (GSM / WCDMA / LTE / NR). A key looks like
 * "LTE:262-02-12345-6789012". Keys are what location rules store and match on.
 */
public final class CellUtils {

    private CellUtils() {}

    /** Unique keys for all identifiable cells in the list (serving + neighbors). */
    public static Set<String> cellKeys(List<CellInfo> cellInfos) {
        Set<String> keys = new LinkedHashSet<>();
        if (cellInfos == null) return keys;
        for (CellInfo info : cellInfos) {
            String key = keyOf(info);
            if (key != null) keys.add(key);
        }
        return keys;
    }

    private static String keyOf(CellInfo info) {
        try {
            if (info instanceof CellInfoGsm) {
                CellIdentityGsm id = ((CellInfoGsm) info).getCellIdentity();
                if (invalid(id.getCid()) || invalid(id.getLac())) return null;
                return "GSM:" + mcc(id.getMccString()) + "-" + mcc(id.getMncString())
                        + "-" + id.getLac() + "-" + id.getCid();
            }
            if (info instanceof CellInfoWcdma) {
                CellIdentityWcdma id = ((CellInfoWcdma) info).getCellIdentity();
                if (invalid(id.getCid()) || invalid(id.getLac())) return null;
                return "WCDMA:" + mcc(id.getMccString()) + "-" + mcc(id.getMncString())
                        + "-" + id.getLac() + "-" + id.getCid();
            }
            if (info instanceof CellInfoLte) {
                CellIdentityLte id = ((CellInfoLte) info).getCellIdentity();
                if (invalid(id.getCi()) || invalid(id.getTac())) return null;
                return "LTE:" + mcc(id.getMccString()) + "-" + mcc(id.getMncString())
                        + "-" + id.getTac() + "-" + id.getCi();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info instanceof CellInfoNr) {
                CellIdentityNr id = (CellIdentityNr) ((CellInfoNr) info).getCellIdentity();
                if (id.getNci() == Long.MAX_VALUE) return null;
                return "NR:" + mcc(id.getMccString()) + "-" + mcc(id.getMncString())
                        + "-" + id.getNci();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean invalid(int v) {
        return v == Integer.MAX_VALUE || v < 0; // CellInfo.UNAVAILABLE == Integer.MAX_VALUE
    }

    private static String mcc(String v) {
        return v == null ? "?" : v;
    }

    /** Compact display form for the scan screen, e.g. "LTE 12345-6789012". */
    public static String shortLabel(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) return key;
        String tech = key.substring(0, colon);
        String[] parts = key.substring(colon + 1).split("-");
        if (parts.length >= 4) return tech + " " + parts[2] + "-" + parts[3];
        if (parts.length == 3) return tech + " " + parts[2];
        return key;
    }

    // ---- trigger value (de)serialization: {"v":1,"cells":["LTE:...", ...]} ----

    public static String toTriggerValue(Set<String> cells) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            JSONArray arr = new JSONArray();
            for (String c : cells) arr.put(c);
            o.put("cells", arr);
            return o.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return the stored cell keys, or an empty set if the value is unusable. */
    public static Set<String> fromTriggerValue(String value) {
        Set<String> out = new LinkedHashSet<>();
        try {
            JSONArray arr = new JSONObject(value).optJSONArray("cells");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
            }
        } catch (Exception ignored) { }
        return out;
    }
}
