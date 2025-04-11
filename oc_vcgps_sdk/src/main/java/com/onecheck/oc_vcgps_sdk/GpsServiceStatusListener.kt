package com.onecheck.oc_vcgps_sdk

interface GpsServiceStatusListener {
    fun onStatusChanged(status:String)
}