package com.onecheck.oc_vcgps_sdk

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onecheck.oc_vcgps_sdk.data.Places
import com.onecheck.oc_vcgps_sdk.data.pickBestPlace
import com.onecheck.oc_vcgps_sdk.data.vcGpsLog
import com.onecheck.oc_vcgps_sdk.data.vcGpsVisitor
import com.onecheck.oc_vcgps_sdk.gps.FusedLocationProvider
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi
import com.onecheck.oc_vcgps_sdk.util.DeviceInfo

class GpsVcService : Service() {

    private val TAG: String = "GpsVcService"
    private val SERVICE_NOTI_ID = 1001
    private var dozeReceiver: DozeReceiver? = null
    private var notification: Notification? = null

    // GPS 관련 객체
    private lateinit var fusedLocationProvider: FusedLocationProvider

    // 스캔 주기(60초)
    private val SCAN_INTERVAL_MS = 10 * 6000L

    // Wake Lock
    private lateinit var wakeLock: PowerManager.WakeLock

    // AlarmManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent
    
    // 디바이스 정보
    private lateinit var deviceInfo: DeviceInfo

    // 앱 아이콘 Notification에서 활용
    private var iconResId: Int? = null

    // Cache Visitor
    private var cacheVisitorId = 0

    private var cacheVisitorPlaceId = 0
    // 3번 이상 매칭 실패시 매장 나간걸로 간주
    private var visitFailCount = 0
    private val MAX_FAIL_COUNT = 3

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[OnCreate] GpsVcService Start")

        // 위치 제공자 초기화
        fusedLocationProvider = FusedLocationProvider(this)

        // 디바이스 정보 Util
        deviceInfo = DeviceInfo(this)

        // Wake Lock 초기화
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsVcService::PartialWakeLock")

        // AlarmManager 초기화
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, GpsVcService::class.java)
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[onStartCommand] onStartCommand")

        // 시스템 재시작으로 null 인텐트 들어온 경우, 가장 먼저 체크!
        if (intent == null) {
            Log.w(TAG, "[onStartCommand] Service restarted by system with null intent")
            return START_STICKY
        }

        // 최초 실행일 때만 값 설정
        if (iconResId == null) {
            val incomingIcon = intent?.getIntExtra("smallIconResId", -1) ?: -1

            if (incomingIcon == -1) {
                Log.e(TAG, "[onStartCommand] Missing smallIconResId. Stopping service.")
                stop()
                return START_NOT_STICKY
            }

            iconResId = incomingIcon
        }

        if (!wakeLock.isHeld) {
            wakeLock.acquire(SCAN_INTERVAL_MS)
        }

        setDozeReceiver()

        if (notification == null) {
            notification = iconResId?.let {createPermanentNotification("ocVcGps Service Open", iconResId!!)}
        }
        startForeground(SERVICE_NOTI_ID, notification!!)

        vcGpsProcess()
        return START_STICKY
    }

    private fun vcGpsProcess(){
        fusedLocationProvider.requestCurrentLocation { location ->
            if (location == null) {
                Log.e(TAG, "[vcGpsProcess] Failed to get location.")
                handleVisitFail()
                return@requestCurrentLocation
            }

            val latitude = location.latitude
            val longitude = location.longitude
            val accuracy = location.accuracy

            Log.d(TAG, "[vcGpsProcess] Current location: $latitude, $longitude (Accuracy: $accuracy)")

            RetrofitConnection.makeApiCall(
                call = {VcApi.service.getNearByPlace(latitude, longitude)},
                onSuccess = {nearByPlaces ->
                    if (nearByPlaces.isNullOrEmpty()) {
                        Log.d(TAG, "[vcGpsProcess] No nearby stores found.")
                        handleVisitFail()
                        return@makeApiCall
                    }

                    val bestPlace = pickBestPlace(latitude, longitude, accuracy.toDouble(), nearByPlaces)
                    if (bestPlace == null) {
                        Log.d(TAG, "[vcGpsProcess] No suitable store matched.")
                        handleVisitFail()
                        return@makeApiCall
                    }

                    Log.d(TAG, "[vcGpsProcess] Store matched: ${bestPlace.place_name}, Distance: ${bestPlace.distance}")

                    if(cacheVisitorPlaceId != 0 && cacheVisitorPlaceId != bestPlace.id){
                        Log.d(TAG, "[vcGpsProcess] Store changed detected! $cacheVisitorPlaceId → ${bestPlace.id}")
                        // 이전 방문 종료 처리 (클라이언트 측 ID 초기화)
                        cacheVisitorId = 0
                        visitFailCount = 0
                    }

                    sendGpsVisitLog(latitude, longitude, bestPlace)
                    sendGpsVisitor(bestPlace.id)
                },
                onFailure = {
                    Log.e(TAG, "[vcGpsProcess] Failed to request store information")
                    handleVisitFail()
                }
            )

        }

    }

    private fun setDozeReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (dozeReceiver == null) {
                dozeReceiver = DozeReceiver()
                val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                registerReceiver(dozeReceiver, filter)
                Log.d(TAG, "[setDozeReceiver] DozeReceiver registered")
            }
        } else {
            Log.d(TAG, "[setDozeReceiver] ozeReceiver only available from API 23 - Skipping registration")
        }
    }

    private fun unsetDozeReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dozeReceiver?.let {
                try {
                    unregisterReceiver(it)
                    dozeReceiver = null
                    Log.d(TAG, "[unsetDozeReceiver] DozeReceiver unregistered")
                } catch (e: Exception) {
                    Log.e(TAG, "[unsetDozeReceiver] Error while unregistering DozeReceiver\\n$e")
                }
            }
        }
    }

    fun stop() {
        unsetDozeReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        cancelAlarmManager()
        stopSelf()
        //sound.releaseMediaPlayer()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called = App was swiped or killed")

        // 여기선 따로 setupAlarmManager() 호출 X
        // 이미 주기적으로 예약되어 있기 때문

        // stop() 호출도 생략 (onDestroy에서 정리하니까)
    }

    override fun onDestroy() {
        Log.d(TAG, "GpsVcService onDestroy - Releasing resources")

        // 위치 업데이트 중지 (만약 requestLocationUpdates 사용했다면 필요함)
        fusedLocationProvider.stopLocationUpdates()

        unsetDozeReceiver()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    private fun createPermanentNotification(message: String, iconResId: Int): Notification {
        val channelId = "onecheck_vcgps_service"
        val channelName = "onecheck Background GPS"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notification 채널은 SDK 26 이상일 때만 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Used for background GPS tracking"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val intent = Intent(this, GpsVcService::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // SDK 26 이하는 channelId 무시하므로 문제 없음
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(message)
            .setStyle(NotificationCompat.InboxStyle())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun setupAlarmManager() {
        val triggerAtMillis = SystemClock.elapsedRealtime() + SCAN_INTERVAL_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23 이상 → Doze 모드에서도 정확하게 알람 울림
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            Log.d(TAG, "[setupAlarmManager] Alarm set: using setExactAndAllowWhileIdle()용")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19~22 → 정확한 알람 설정 (Doze 미지원)
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            Log.d(TAG, "[setupAlarmManager] Alarm set: using setExact()")
        } else {
            // API 19 미만 → 정확하지 않은 일반 알람 사용
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            Log.d(TAG, "[setupAlarmManager] Alarm set: using set()")
        }
    }

    private fun cancelAlarmManager() {
        if (::alarmManager.isInitialized) {
            alarmManager.cancel(alarmIntent)
        }
    }


    inner class DozeReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                intent.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (powerManager.isDeviceIdleMode) {
                    Log.d(TAG, "[DozeReceiver] Device is in Doze mode")
                } else {
                    Log.d(TAG, "[DozeReceiver] Device exited Doze mode")
                }
            }
        }
    }

    private fun handleVisitFail() {
        visitFailCount++
        Log.d(TAG, "[handleVisitFail] Visit match failed count: $visitFailCount")
        if (visitFailCount >= MAX_FAIL_COUNT) {
            Log.d(TAG, "[handleVisitFail] 3 consecutive failures → ending visit")
            Log.d(TAG, "[handleVisitFail] Visitor #$cacheVisitorId ended")
            cacheVisitorId = 0
            cacheVisitorPlaceId = 0
            visitFailCount = 0
        }
        setupAlarmManager()
    }

    private fun sendGpsVisitLog(lat: Double, lng: Double, bestPlace: Places){
        val gpsLog = vcGpsLog(
            id = null,
            place_name = bestPlace.place_name ?: "Unknown",
            distance = bestPlace.distance?.toDouble() ?: 0.0,
            geometry_lat = lat,
            geometry_lng = lng,
            time_stamp = null
        )

        RetrofitConnection.makeApiCall(
            call = { VcApi.service.makeVcGpsLog(gpsLog) },
            onSuccess = { Log.d(TAG, "[sendGpsVisitLog] GPS log saved") },
            onFailure = { Log.e(TAG, "[Failed to save GPS log] Failed to save GPS log") }
        )
    }

    private fun sendGpsVisitor(placesId: Int) {
        val request = vcGpsVisitor(
            visitor_id = cacheVisitorId,
            places_id = placesId,
            vc_device_id_hash = deviceInfo.getHashedDeviceId()
        )

        RetrofitConnection.makeApiCall(
            call = { VcApi.service.gpsVisits(request) },
            onSuccess = { response ->
                val visitorId = response?.visitor_id ?: 0

                if (visitorId == 0) {
                    Log.d(TAG, "[sendGpsVisitor] Visitor match failed")
                    handleVisitFail()
                } else {
                    if (visitorId == cacheVisitorId) {
                        Log.d(TAG, "[sendGpsVisitor] Visitor remains the same: $visitorId")
                    } else {
                        Log.d(TAG, "[sendGpsVisitor] Visitor updated: $visitorId")
                        cacheVisitorId = visitorId
                        cacheVisitorPlaceId = placesId
                    }
                    visitFailCount = 0
                }

                setupAlarmManager()
            },
            onFailure = {
                Log.e(TAG, "[sendGpsVisitor] Failed to save GPS log")
                handleVisitFail()
            }
        )
    }
}