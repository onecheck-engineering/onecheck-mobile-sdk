package com.onecheck.oc_vcgps_sdk.consent

import android.content.Context
import android.util.Log
import com.onecheck.oc_vcgps_sdk.Log.LogSdk
import com.onecheck.oc_vcgps_sdk.OcVcGpsSdk
import com.onecheck.oc_vcgps_sdk.data.ConsentPayload
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi
import com.onecheck.oc_vcgps_sdk.util.DeviceInfo
import com.onecheck.oc_vcgps_sdk.util.Time

object ConsentManager {

    private const val PREF_NAME = "oc_consent_pref"
    private const val KEY_CONSENT_GIVEN = "consent_given"
    private const val KEY_CONSENT_SYNCED = "consent_synced"
    private const val TAG = "ConsentManager"

    fun hasConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSENT_GIVEN, false)
    }

    fun isConsentSynced(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONSENT_SYNCED, false)
    }

    fun setConsent(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, value)
            .putBoolean(KEY_CONSENT_SYNCED, false)
            .apply()
    }

    fun markConsentSynced(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CONSENT_SYNCED, true).apply()
    }

    fun setConsentSyncedOnly(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .putBoolean(KEY_CONSENT_SYNCED, true)
            .apply()
    }

    fun requestConsentIfNeeded(context: Context, iconResId: Int) {
        if (hasConsent(context)) {
            LogSdk.d(TAG, "Consent already confirmed locally")

            checkConsentFromServer(context) { exists ->
                if (exists) {
                    LogSdk.d(TAG, "Consent already exists on server - syncing local state only")
                    markConsentSynced(context)
                } else {
                    LogSdk.d(TAG, "No consent found on server - attempting to send")
                    sendConsentToServer(context)
                }

                OcVcGpsSdk.startService(context, iconResId)
            }


        } else {
            // 로컬에 동의 기록 없으면 서버에서 확인
            checkConsentFromServer(context) { existsOnServer ->
                if (existsOnServer) {
                    LogSdk.d(TAG, "Consent found on server - applying to local state")
                    setConsentSyncedOnly(context)
                    OcVcGpsSdk.startService(context, iconResId)
                } else {
                    ConsentDialogUtil.showConsentDialog(context) {
                        setConsent(context, true)
                        sendConsentToServer(context){
                            markConsentSynced(context)
                            OcVcGpsSdk.startService(context, iconResId)
                        }
                    }
                }
            }
        }
    }

    private fun sendConsentToServer(context: Context, onComplete: () -> Unit = {}) {
        val deviceInfo = DeviceInfo(context)
        val payload = ConsentPayload(
            vc_device_id_hash = deviceInfo.getHashedDeviceId(),
            is_consent_given = "Y",
            consent_time = Time.getTimeString(),
            sdk_version = deviceInfo.getAppVersion(),
            app_package_nm = deviceInfo.getPackageName(),
            REG_DATE = Time.getTimeString()
        )

        RetrofitConnection.makeApiCall(
            call = {VcApi.service.submitConsent(payload)},
            onSuccess = { success ->
                LogSdk.d(TAG, "Consent successfully sent to server")
                onComplete()
            },
            onFailure = { error ->
                LogSdk.d(TAG, "sendConsentToServer - server error")
                LogSdk.d(TAG, "${error.printStackTrace()}")
            }
        )
    }

    private fun checkConsentFromServer(context: Context, callback: (Boolean) -> Unit) {
        val hash = DeviceInfo(context).getHashedDeviceId()
        RetrofitConnection.makeApiCall(
            call = {VcApi.service.checkConsentExist(hash)},
            onSuccess = { isExist ->
                callback(isExist ?: false)
            },
            onFailure = { error ->
                LogSdk.e(TAG, "checkConsentFromServer - error while checking consent status")
                LogSdk.e(TAG, "${error.printStackTrace()}")
                callback(false)
            }
        )
    }
}