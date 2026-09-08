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
 * "LTE:302-720-30013-9693717". Keys are what location rules store and match on.
 *
 * A cell is only usable if it exposes a full global identity (MCC+MNC plus
 * LAC/CID or TAC/CI). Unregistered neighbor cells often report null MCC/MNC
 * and zeroed ids; those all collapse to the same key and would match at any
 * location, so they are rejected.
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
                if (id.getMccString() == null || id.getMncString() == null) return null;
                if (invalid(id.getCid()) || invalid(id.getLac())) return null;
                if (id.getCid() == 0 && id.getLac() == 0) return null; // unidentified neighbor
                return "GSM:" + id.getMccString() + "-" + id.getMncString()
                        + "-" + id.getLac() + "-" + id.getCid();
            }
            if (info instanceof CellInfoWcdma) {
                CellIdentityWcdma id = ((CellInfoWcdma) info).getCellIdentity();
                if (id.getMccString() == null || id.getMncString() == null) return null;
                if (invalid(id.getCid()) || invalid(id.getLac())) return null;
                if (id.getCid() == 0 && id.getLac() == 0) return null; // unidentified neighbor
                return "WCDMA:" + id.getMccString() + "-" + id.getMncString()
                        + "-" + id.getLac() + "-" + id.getCid();
            }
            if (info instanceof CellInfoLte) {
                CellIdentityLte id = ((CellInfoLte) info).getCellIdentity();
                if (id.getMccString() == null || id.getMncString() == null) return null;
                if (invalid(id.getCi()) || invalid(id.getTac())) return null;
                if (id.getCi() == 0 && id.getTac() == 0) return null; // unidentified neighbor
                return "LTE:" + id.getMccString() + "-" + id.getMncString()
                        + "-" + id.getTac() + "-" + id.getCi();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info instanceof CellInfoNr) {
                CellIdentityNr id = (CellIdentityNr) ((CellInfoNr) info).getCellIdentity();
                if (id.getMccString() == null || id.getMncString() == null) return null;
                if (id.getNci() == Long.MAX_VALUE || id.getNci() == 0) return null;
                return "NR:" + id.getMccString() + "-" + id.getMncString()
                        + "-" + id.getNci();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean invalid(int v) {
        return v == Integer.MAX_VALUE || v < 0; // CellInfo.UNAVAILABLE == Integer.MAX_VALUE
    }

    /** Compact display form for the scan screen, e.g. "LTE 30013-9693717". */
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
