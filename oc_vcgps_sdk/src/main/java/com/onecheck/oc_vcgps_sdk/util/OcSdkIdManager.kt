package com.onecheck.oc_vcgps_sdk.util

import android.content.Context

object OcSdkIdManager {

    private const val PREF_NAME = "oc_sdk_id_pref"
    private const val KEY_FID = "key_firebase_installation_id"

    fun saveFid(context: Context, fid: String) {
        if(fid != getFid(context)){
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FID, fid)
                .apply()
        }
    }

    fun getFid(context: Context): String? {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FID, null)
    }
}
