package com.onecheck.oc_vcgps_sdk.retrofit

import com.onecheck.oc_vcgps_sdk.data.LogSend
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LogService {
    @POST("api/sdk/sendLog")
    suspend fun LogSender(@Body log:LogSend): Response<Unit>
}