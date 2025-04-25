package com.onecheck.oc_vcgps_sdk.consent

import android.content.Context
import com.onecheck.oc_vcgps_sdk.Log.LogSdk
import com.onecheck.oc_vcgps_sdk.OcVcGpsSdk
import com.onecheck.oc_vcgps_sdk.data.ConsentPayload
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi
import com.onecheck.oc_vcgps_sdk.util.OcSdkIdManager
import com.onecheck.oc_vcgps_sdk.util.Time

object ConsentManager {

    private const val PREF_NAME = "oc_consent_pref"
    private const val KEY_CONSENT_GIVEN = "consent_given"
    private const val TAG = "ConsentManager"

    fun hasConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false)
    }

    fun setConsent(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, value)
            .apply()
    }
    fun requestConsentIfNeeded(context: Context, iconResId: Int, fid :String, enableResultStatus: Boolean = false) {

        if (hasConsent(context)) {
            LogSdk.d(fid, TAG, "Consent already confirmed locally")
            OcVcGpsSdk.startService(context, iconResId, fid, enableResultStatus)
        } else {
            ConsentDialogUtil.showConsentDialog(context) {
                setConsent(context, true)
                OcVcGpsSdk.startService(context, iconResId, fid, enableResultStatus)
            }
        }
    }
}
