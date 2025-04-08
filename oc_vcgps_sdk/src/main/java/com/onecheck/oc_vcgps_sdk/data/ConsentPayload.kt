package com.onecheck.oc_vcgps_sdk.data

data class ConsentPayload(
    var vc_device_id_hash: String, // SHA-256 해시 Android ID
    var is_consent_given: String,  // "Y" or "N"
    var consent_time: String,      // ISO 8601 형식 or 서버에서 파싱 가능한 String
    var sdk_version: String,
    var app_package_nm: String,
    var REG_DATE: String,
)
