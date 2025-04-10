package com.onecheck.oc_vcgps_sdk.Log

import com.onecheck.oc_vcgps_sdk.data.LogSend
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi

object LogSdk {
    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)

    private fun log(level: String, tag: String, message: String){
        val logSend = LogSend(
            level = level,
            tag = tag,
            message = message
        )

        RetrofitConnection.makeApiCall(
            call = {VcApi.logService.LogSender(logSend)},
            onSuccess = {},
            onFailure = {}
        )
    }
}