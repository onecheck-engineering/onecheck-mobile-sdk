package com.onecheck.oc_vcgps_sdk.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class FusedLocationProvider(private val context: Context) {

    // FusedLocationProviderClient 초기화
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // 최신 위치 정보 저장 (업데이트 된 최종 위치)
    private var currentLocation: Location? = null

    // 위치 업데이트 콜백 (요청 시마다 재정의됨)
    private var locationCallback: LocationCallback? = null

    private val TAG: String = "GpsVcService_Fused"

    /**
     * 위치 권한(FINE 또는 COARSE)이 부여되었는지 확인하는 함수
     */

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED


    /**
     * 위치 업데이트 중지
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }


    /**
     * 단발성으로 최신 위치 정보를 요청합니다.
     * getLastKnownLocation과 달리, 새롭게 위치를 요청하여 가능한 최신의 정확한 위치를 반환합니다.
     *
     * @param onLocationResult 단발성 위치 결과 콜백 함수
     */

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(onLocationResult: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permissions are not granted")
            onLocationResult(null)
            return
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        Log.e(TAG, "getCurrentLocation returned null")
                        onLocationResult(null)
                    } else {
                        var newLocation = location
                        Log.d(TAG, "newLocation.accuracy ${newLocation.accuracy}")

                        // 1. 첫 위치 업데이트 (무조건 반영)
                        if (currentLocation == null) {
                            Log.d(TAG, "First location update received: ${newLocation.latitude}, ${newLocation.longitude}")
                            currentLocation = newLocation
                        } else {
                            // 2. 급격한 위치 점프 감지 (가중치 조정)
                            val weight = calculateDynamicWeight(currentLocation!!, newLocation)
                            //Log.d(TAG, "${weight}")
                            // 3. 가중치 기반 저역 필터 적용
                            newLocation = applyWeightedFilter(currentLocation!!, newLocation, weight)

                            // 4. accuracy 값 기반 보정 추가
                            newLocation = applyAccuracyBasedAdjustment(currentLocation!!, newLocation)

                            currentLocation = newLocation
                        }

                        // 4. 최종 위치 결과 전달
                        onLocationResult(currentLocation)
                    }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "getCurrentLocation SecurityException: $e")
            onLocationResult(null)
        }
    }

    /**
     * 이동 속도 기반 가중치를 계산.
     */
    private fun calculateDynamicWeight(previousLocation: Location, newLocation: Location): Double {
        val distance = previousLocation.distanceTo(newLocation) // m 단위 거리
        val timeDiff = (newLocation.time - previousLocation.time) / 1000.0 // 초 단위 시간 차이

        // 속도 계산 (m/s)
        val speed = if (timeDiff > 0) distance / timeDiff else 0.0

        return when {
            speed > 30 -> 0.1  // 매우 빠름 (비행기, 기차 등) → 강한 보정
            speed > 20 -> 0.2  // 고속 주행
            speed > 15 -> 0.3  // 일반 차량 주행
            speed > 10 -> 0.4  // 도심 내 차량 이동
            speed > 5  -> 0.6  // 자전거, 빠른 도보
            speed > 2  -> 0.8  // 일반적인 도보 이동
            else      -> 0.95  // 실내 이동 (거의 새 위치 반영)
        }
    }

    /**
    * 가중치를 적용하여 새로운 위치를 보정합니다.
    */
    private fun applyWeightedFilter(previousLocation: Location, newLocation: Location, weight: Double): Location {
        val filteredLat = previousLocation.latitude * (1 - weight) + newLocation.latitude * weight
        val filteredLon = previousLocation.longitude * (1 - weight) + newLocation.longitude * weight
        return Location("").apply {
            latitude = filteredLat
            longitude = filteredLon
            time = newLocation.time
            accuracy = newLocation.accuracy
        }
    }

    /**
     * accuracy 값을 기반으로 위도/경도를 직접 조정하는 함수
     */
    private fun applyAccuracyBasedAdjustment(previousLocation: Location, newLocation: Location): Location {

        // 🔥 accuracy 값이 클수록 기존 위치를 더 신뢰하도록 가중치 계산
        val weight = when {
            newLocation.accuracy < 5 -> 1.0  // 5m 이내 → 새로운 위치를 100% 신뢰
            newLocation.accuracy < 10 -> 0.9 // 5~10m → 90% 새로운 위치 반영
            newLocation.accuracy < 20 -> 0.7 // 10~20m → 70% 새로운 위치 반영
            newLocation.accuracy < 50 -> 0.5 // 20~50m → 50% 새로운 위치 반영
            else -> 0.3           // 50m 이상 → 새로운 위치를 거의 반영 안 함
        }

        // 🔥 위도/경도 보정 (accuracy 값을 가중치로 사용하여 기존 위치와 혼합)
        return Location("").apply {
            latitude = previousLocation.latitude * (1 - weight) + newLocation.latitude * weight
            longitude = previousLocation.longitude * (1 - weight) + newLocation.longitude * weight
            accuracy = (previousLocation.accuracy * (1 - weight) + newLocation.accuracy * weight).toFloat()
            time = newLocation.time
        }
    }

}