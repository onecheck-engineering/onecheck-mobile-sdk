package com.onecheck.oc_vcgps_sdk.retrofit

object VcApi {
    val service: VcService by lazy {
        RetrofitConnection.getInstance().create(VcService::class.java)
    }
}