package com.jordon.sdkbuild

import android.content.Context
import android.content.Intent
import android.os.Build

object SdkBuildTest {
    fun start(context: Context){
        val intent = Intent(context, SdkBuildService::class.java)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}