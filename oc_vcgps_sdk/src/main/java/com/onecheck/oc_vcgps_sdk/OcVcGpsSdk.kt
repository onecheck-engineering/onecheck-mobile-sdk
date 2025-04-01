package com.onecheck.oc_vcgps_sdk

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object OcVcGpsSdk {

    private var smallIconResId: Int? = null
    private val TAG:String = "OcVcGpsSdk"

    fun startService(context: Context, IconResId: Int){
        smallIconResId = IconResId

        // 권한 체크(Check if all required permissions are granted)
        if(!hasAllRequiredPermissions(context)){
            Log.e(TAG, "[Permission Missing] Required permissions are not granted. Service will not start.")
            return
        }

        val intent = Intent(context, GpsVcService::class.java).apply {
            putExtra("smallIconResId", IconResId)
        }

        // Android 8.0 (API 26) 이상에서는 startForegroundService() 사용(Start foreground service according to Android version)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // 필수 권한 목록(List of required permissions for the SDK)
    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.WAKE_LOCK
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + android.Manifest.permission.POST_NOTIFICATIONS
        } else it
    }

    // 권한 체크 필수( Check whether all required permissions are granted)
    private fun hasAllRequiredPermissions(context: Context): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing permission: $permission")
                return false
            }
        }

        // 정확한 알람 퍼미션은 별도 체크 (Android 12+)(Additional check for SCHEDULE_EXACT_ALARM (from Android 12))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("OcVcGpsSdk", "Exact alarm permission not granted (SCHEDULE_EXACT_ALARM)")
                return false
            }
        }

        return true
    }


}