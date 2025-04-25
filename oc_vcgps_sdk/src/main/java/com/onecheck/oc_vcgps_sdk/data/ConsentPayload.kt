package com.onecheck.oc_vcgps_sdk.data

data class ConsentPayload(
    var fid: String, // firebase에서 부여되는 fid
    var is_consent_given: String,  // "Y" or "N"
    var consent_time: String,      // ISO 8601 형식 or 서버에서 파싱 가능한 String
    var REG_DATE: String,
)
