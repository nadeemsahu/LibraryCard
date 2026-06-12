package com.piotrekwitkowski.libraryhce;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class ProfileManager {
    private static final String PREF_NAME = "LibraryNFC_Profiles_Encrypted";
    private static final String KEY_PROFILES = "profiles_list";
    private static final String KEY_ACTIVE = "active_profile_name";

    public static class CardProfile {
        public String name;
        public String libraryId;
        public String payloadHex;
        public String aesKeyHex;

        public CardProfile(String name, String libraryId, String payloadHex, String aesKeyHex) {
            this.name = name;
            this.libraryId = libraryId;
            this.payloadHex = payloadHex;
            this.aesKeyHex = aesKeyHex;
        }

        @Override
        public String toString() {
            return name; // Needed for simple Spinner adapter mapping
        }
    }

    private static SharedPreferences getSecurePrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            android.util.Log.e("ProfileManager", "Failed to create EncryptedSharedPreferences: " + e.getMessage());
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static List<CardProfile> getProfiles(Context context) {
        SharedPreferences prefs = getSecurePrefs(context);
        String jsonStr = prefs.getString(KEY_PROFILES, null);
        List<CardProfile> list = new ArrayList<>();
        boolean modified = false;

        if (jsonStr != null) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.getString("name");
                    String libraryId = obj.getString("libraryId");

                    list.add(new CardProfile(
                            name,
                            libraryId,
                            obj.getString("payloadHex"),
                            obj.getString("aesKeyHex")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (modified) {
            saveProfilesInternal(context, list);
        }
        return list;
    }

    private static void saveProfilesInternal(Context context, List<CardProfile> list) {
        SharedPreferences prefs = getSecurePrefs(context);
        try {
            JSONArray arr = new JSONArray();
            for (CardProfile p : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", p.name);
                obj.put("libraryId", p.libraryId);
                obj.put("payloadHex", p.payloadHex);
                obj.put("aesKeyHex", p.aesKeyHex);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CardProfile getActiveProfile(Context context) {
        SharedPreferences prefs = getSecurePrefs(context);
        String activeName = prefs.getString(KEY_ACTIVE, null);
        List<CardProfile> list = getProfiles(context);


        if (activeName != null) {
            for (CardProfile p : list) {
                if (p.name.equalsIgnoreCase(activeName)) {
                    return p;
                }
            }
        }
        // Fallback
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public static void setActiveProfile(Context context, String name) {
        SharedPreferences prefs = getSecurePrefs(context);
        if (name == null) {
            prefs.edit().remove(KEY_ACTIVE).apply();
        } else {
            prefs.edit().putString(KEY_ACTIVE, name).apply();
        }
    }

    public static boolean addProfile(Context context, CardProfile profile) {
        List<CardProfile> list = getProfiles(context);
        // Prevent duplicate names
        for (CardProfile p : list) {
            if (p.name.equalsIgnoreCase(profile.name)) {
                return false;
            }
        }
        list.add(profile);
        saveProfilesInternal(context, list);
        setActiveProfile(context, profile.name);
        return true;
    }

    public static void deleteProfile(Context context, String name) {
        List<CardProfile> list = getProfiles(context);
        CardProfile toRemove = null;
        for (CardProfile p : list) {
            if (p.name.equalsIgnoreCase(name)) {
                toRemove = p;
                break;
            }
        }
        if (toRemove != null) {
            list.remove(toRemove);
            saveProfilesInternal(context, list);
            
            // If we deleted the active profile, reset active
            SharedPreferences prefs = getSecurePrefs(context);
            String activeName = prefs.getString(KEY_ACTIVE, null);
            if (activeName != null && activeName.equalsIgnoreCase(name)) {
                if (!list.isEmpty()) {
                    setActiveProfile(context, list.get(0).name);
                } else {
                    setActiveProfile(context, null);
                }
            }
        }
    }

    public static void resetAll(Context context) {
        SharedPreferences prefs = getSecurePrefs(context);
        prefs.edit().clear().apply();
    }
}
