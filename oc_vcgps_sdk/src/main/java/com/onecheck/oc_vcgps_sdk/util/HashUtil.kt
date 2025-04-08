package com.onecheck.oc_vcgps_sdk.util

import java.security.MessageDigest

object HashUtil {

    /**
     * 입력된 문자열을 SHA-256 해시로 변환하여 반환
     */
    fun sha256(input:String): String{
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString (""){"%02x".format(it)}
    }
}