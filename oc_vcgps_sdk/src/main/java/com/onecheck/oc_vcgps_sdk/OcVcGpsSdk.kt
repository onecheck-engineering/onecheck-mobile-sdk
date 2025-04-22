package com.onecheck.oc_vcgps_sdk

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.onecheck.oc_vcgps_sdk.consent.ConsentManager


object OcVcGpsSdk {

    private var smallIconResId: Int? = null
    private val TAG: String = "OcVcGpsSdk"

    private var isServiceRunning = false


    @JvmStatic
    fun startWithConsentCheck(context: Context, iconResId: Int, enableResultStatus: Boolean = false) {
        ConsentManager.requestConsentIfNeeded(context, iconResId, enableResultStatus)
    }

    @JvmStatic
    fun startService(context: Context, IconResId: Int, enableResultStatus: Boolean = false){

        if(isServiceRunning){
            // 이미 서비스 실행중인 상태
            return
        }

        // 권한 체크(Check if all required permissions are granted)
        if(!hasAllRequiredPermissions(context)){
            Log.e(TAG, "[Permission Missing] Required permissions are not granted. Service will not start.")
            return
        }

        setSmallIconResId(IconResId)

        val intent = Intent(context, GpsVcService::class.java).apply {
            putExtra("enableStatus", enableResultStatus)
        }


        // Android 8.0 (API 26) 이상에서는 startForegroundService() 사용(Start foreground service according to Android version)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        setServiceFlag(true)
    }

    @JvmStatic
    fun setServiceFlag(isRunning:Boolean){
        isServiceRunning = isRunning
    }

    // 필수 권한 목록(List of required permissions for the SDK)
    private val REQUIRED_PERMISSIONS = buildList {
        add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        add(android.Manifest.permission.FOREGROUND_SERVICE)
        add(android.Manifest.permission.WAKE_LOCK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14(API 34) 이상에서는 FOREGROUND_SERVICE_LOCATION 권한도 필요
            add(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
    }.toTypedArray()

    // 권한 체크 필수( Check whether all required permissions are granted)
    fun hasAllRequiredPermissions(context: Context): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing permission: $permission")
                return false
            }
        }

        // 백그라운드 위치 권한(Android 10 (API 29) 이상)(항상허용)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Background location permission (ACCESS_BACKGROUND_LOCATION) is not granted.")
            return false
        }


        // 정확한 알람 퍼미션은 별도 체크 (Android 12+)(Additional check for SCHEDULE_EXACT_ALARM (from Android 12))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted (SCHEDULE_EXACT_ALARM)")
                return false
            }
        }

        return true
    }

    @JvmStatic
    fun setSmallIconResId(resId: Int){
        smallIconResId = resId
    }

    @JvmStatic
    fun getSmallIconResId():Int{
        return smallIconResId ?: -1
    }

}