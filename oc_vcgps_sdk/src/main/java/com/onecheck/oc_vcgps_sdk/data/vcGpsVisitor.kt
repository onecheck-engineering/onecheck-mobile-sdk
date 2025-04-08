package com.onecheck.oc_vcgps_sdk.data

data class vcGpsVisitor(
    var visitor_id: Int,
    var places_id: Int,
    var vc_device_id_hash: String, // SHA-256 해시 Android ID
)
