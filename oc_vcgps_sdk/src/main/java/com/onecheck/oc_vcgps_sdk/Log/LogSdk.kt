package com.onecheck.oc_vcgps_sdk.Log

import com.onecheck.oc_vcgps_sdk.data.LogSend
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi

object LogSdk {
    fun d(fid: String, tag: String, message: String) = log(fid, "DEBUG", tag, message)
    fun i(fid: String, tag: String, message: String) = log(fid,"INFO", tag, message)
    fun e(fid: String, tag: String, message: String) = log(fid,"ERROR", tag, message)
    fun w(fid: String, tag: String, message: String) = log(fid,"WARNNING", tag, message)

    private fun log(fid:String, level: String, tag: String, message: String){
        val logSend = LogSend(
            fid = fid,
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