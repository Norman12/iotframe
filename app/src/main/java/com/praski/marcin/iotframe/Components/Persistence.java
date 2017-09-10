package com.praski.marcin.iotframe.Components;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by marcin on 08.09.17.
 */

public class Persistence {
    private final static String PREF_KEY = "iotframe_pref";
    private final static String KEY_UUID = "uuid";

    private SharedPreferences sharedPreferences;

    private Persistence() {
    }

    public static Persistence getInstance() {
        return Persistence.InstanceHolder.mPersistence;
    }

    public void initializeStorage(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_KEY, 0);
    }

    public boolean hasUuid() {
        if (sharedPreferences == null) {
            return false;
        }

        return sharedPreferences.contains(KEY_UUID);
    }

    public String getUuid() {
        if (sharedPreferences == null) {
            return "";
        }

        return sharedPreferences.getString(KEY_UUID, null);
    }

    public void setUuid(String role) {
        if (sharedPreferences == null) {
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_UUID, role);
        editor.apply();
    }

    public void deleteUuid() {
        if (sharedPreferences == null) {
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_UUID);
        editor.apply();
    }

    private static class InstanceHolder {
        private static Persistence mPersistence = new Persistence();
    }
}
