package com.onecheck.oc_vcgps_sdk.util

import java.text.SimpleDateFormat
import java.util.Date

object Time {
    fun getTimeString(): String{
        val now: Long = System.currentTimeMillis()
        val date = Date(now)
        val dateFormat = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
        return dateFormat.format(date)
    }
}