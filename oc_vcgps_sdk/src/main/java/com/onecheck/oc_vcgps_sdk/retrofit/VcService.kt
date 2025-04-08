package com.onecheck.oc_vcgps_sdk.retrofit

import com.onecheck.oc_vcgps_sdk.data.ConsentPayload
import com.onecheck.oc_vcgps_sdk.data.GpsVisitorResponse
import com.onecheck.oc_vcgps_sdk.data.Places
import com.onecheck.oc_vcgps_sdk.data.vcGpsLog
import com.onecheck.oc_vcgps_sdk.data.vcGpsVisitor
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface VcService {

    @GET("api/vc/get_near_by_place/{latitude}/{longitude}")
    suspend fun getNearByPlace(@Path("latitude") latitude: Double, @Path("longitude") longitude: Double): Response<List<Places>>

    @POST("api/vc/make_vc_gps_log")
    suspend fun makeVcGpsLog(@Body requet: vcGpsLog): Response<Boolean>

    @POST("api/vc/gps_visits")
    suspend fun gpsVisits(@Body payload: vcGpsVisitor): Response<GpsVisitorResponse>

    @POST("api/consent/submitConsent")
    suspend fun submitConsent(@Body payload: ConsentPayload): Response<Boolean>

    @GET("api/consent/checkConsentExist/{deviceIdHash}")
    suspend fun checkConsentExist(@Path("deviceIdHash") deviceIdHash: String): Response<Boolean>
}