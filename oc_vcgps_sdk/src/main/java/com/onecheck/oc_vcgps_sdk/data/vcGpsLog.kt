package com.onecheck.oc_vcgps_sdk.data

import java.util.Date

data class vcGpsLog(
    var id: Int?,
    var place_name: String,
    var distance: Double,
    var geometry_lat: Double,
    var geometry_lng: Double,
    var time_stamp: Date?
)
