package io.demoapp.expensive.util;

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import com.google.common.base.Charsets;

import org.json.JSONException;
import org.json.JSONObject;

public final class IdTokenUtil {

    private static final String TAG = "IdTokenUtil";

    @Nullable
    public static String extractIssuer(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String claimsStr;
        try {
            claimsStr = new String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "Unable to decode claims section of JWT");
            return null;
        }

        JSONObject claims;
        try {
            claims = new JSONObject(claimsStr);
        } catch (JSONException ex) {
            Log.d(TAG, "unable to parse claims JSON", ex);
            return null;
        }

        return claims.optString("iss");
    }
}