package com.onecheck.oc_vcgps_sdk.data

import android.util.Log
import com.onecheck.oc_vcgps_sdk.Log.LogSdk

data class Places(
    val id: Int,
    val place_id: String,
    val place_name: String,
    val business_status: String,
    val formatted_address: String,
    val icon_url: String,
    val icon_background_color: String,
    val plus_code_compound: String,
    val plus_code_global: String,
    val geometry_lat: Double,
    val geometry_lng: Double,
    val offset_north_lat: Double?,
    val offset_south_lat: Double?,
    val offset_east_lng: Double?,
    val offset_west_lng: Double?,
    val attributions_url: String,
    val distance: Float
)

fun pickBestPlace(
    fid: String,
    userLat: Double,
    userLng: Double,
    userAccuracy: Double,
    places: List<Places>
): Places? {
    val TAG = "Places.pickBestPlace"
    // 1) 우선 Outlier / BoundingBox / 반경 체크로 "매장 진입" 여부 필터링
    val filtered = places.filter { place ->
        // (1) Bounding Box가 있다면, 해당 박스 안에 들어왔는지?
        if (place.offset_north_lat != null && place.offset_south_lat != null &&
            place.offset_east_lng != null && place.offset_west_lng != null
        ) {
            val inside = isInsideBoundingBox(
                userLat, userLng,
                place.offset_north_lat, place.offset_south_lat,
                place.offset_east_lng, place.offset_west_lng
            )
            if (!inside) {
                // Bounding Box 밖
                LogSdk.d(fid, TAG, "Store ${place.place_name} is outside BoundingBox → excluded")
                return@filter false
            }
        } else {
            // (2) BoundingBox가 없다면, distance + accuracy 활용
            // 예: "place.distance <= userAccuracy + 30" 정도
            if (place.distance > userAccuracy + 30) {
                LogSdk.d(fid, TAG, "Store ${place.place_name} (distance: ${place.distance}) exceeds accuracy ($userAccuracy) + 30m → excluded")
                return@filter false
            }
        }

        // (3) 여기까지 통과하면 후보로 인정
        true
    }

    if (filtered.isEmpty()) {
        return null
    }

    // 2) 남은 후보 중 "가장 가까운" 매장 선택
    //    (혹은 더 정교한 로직: distance + accuracy 가중치, 최근 방문 히스토리 등)
    val sorted = filtered.sortedBy { it.distance }
    val best = sorted.first()

    // 예: distance 차가 매우 근소하면, 두 번째 후보와 비교해볼 수도 있음.
    if (sorted.size > 1) {
        val second = sorted[1]
        if (second.distance - best.distance < 5) {
            // 5m 이하 차이라면? -> 추가 판단
            // 예: "이전 방문 매장"이 best면 유지, 아니면 second도 가능
            LogSdk.d(fid, TAG, "Distance between best (${best.place_name}) and second (${second.place_name}) is less than 5m")
        }
    }
    return best
}

// Bounding Box 체크
fun isInsideBoundingBox(
    lat: Double,
    lng: Double,
    northLat: Double,
    southLat: Double,
    eastLng: Double,
    westLng: Double
): Boolean {
    val isLatInside = lat in southLat..northLat
    val isLngInside = lng in westLng..eastLng
    return isLatInside && isLngInside
}
