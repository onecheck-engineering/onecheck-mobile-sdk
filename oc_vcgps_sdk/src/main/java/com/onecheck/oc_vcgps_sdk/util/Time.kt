package com.onecheck.oc_vcgps_sdk.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Time {
    fun getTimeString(): String{
        val now: Long = System.currentTimeMillis()
        val date = Date(now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(date)
    }
}