package com.onecheck.oc_vcgps_sdk.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val context: Context) {

    //SHA-256으로 해시 처리된 디바이스 식별자 전환
    fun getHashedDeviceId(): String{
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        return HashUtil.sha256(androidId)
    }

    // 기기 모델 정보 (예: SM-G991N)
    fun getDeviceModel(): String {
        return Build.MODEL ?: "unknown"
    }

    // OS 버전 (예: 13, 14)
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE ?: "unknown"
    }

    // 앱 패키지명 (예: com.onecheck.oc_vpgps_sdk)
    fun getPackageName(): String {
        return context.packageName
    }

    // 앱 버전 정보 (예: 1.0.3)
    fun getAppVersion(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

}