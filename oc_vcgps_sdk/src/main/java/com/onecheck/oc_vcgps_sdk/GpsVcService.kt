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
import com.onecheck.oc_vcgps_sdk.Log.LogSdk
import com.onecheck.oc_vcgps_sdk.data.Places
import com.onecheck.oc_vcgps_sdk.data.pickBestPlace
import com.onecheck.oc_vcgps_sdk.data.vcGpsLog
import com.onecheck.oc_vcgps_sdk.data.vcGpsVisitor
import com.onecheck.oc_vcgps_sdk.gps.FusedLocationProvider
import com.onecheck.oc_vcgps_sdk.retrofit.RetrofitConnection
import com.onecheck.oc_vcgps_sdk.retrofit.VcApi
import com.onecheck.oc_vcgps_sdk.util.OcSdkIdManager

class GpsVcService : Service() {

    private val TAG: String = "GpsVcService"
    private val SERVICE_NOTI_ID = 1001
    private var dozeReceiver: DozeReceiver? = null
    private var notification: Notification? = null

    // GPS 관련 객체
    private lateinit var fusedLocationProvider: FusedLocationProvider

    // 현재 GPS 스캔 주기 (60초)
    private var currentScanIntervalMs = 10 * 6000L

    // Doze 진입 시 GPS 주기(5분)
    private val DOZE_SCAN_INTERVAL_MS = 5 * 60 * 1000L

    // 일반 상태 GPS 주기(60초)
    private val NORMAL_SCAN_INTERNAL_MS = 10 * 6000L

    // Wake Lock
    private lateinit var wakeLock: PowerManager.WakeLock

    // AlarmManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent


    // 앱 아이콘 Notification에서 활용
    private var iconResId: Int? = null

    // Cache Visitor
    private var cacheVisitorId = 0

    private var cacheVisitorPlaceId = 0
    // 3번 이상 매칭 실패시 매장 나간걸로 간주
    private var visitFailCount = 0
    private val MAX_FAIL_COUNT = 3

    // 백그라운드 환경 잘 구동되는지 확인을 위한 임시 사용
    private var notificationBuilder: NotificationCompat.Builder? = null

    private var userFid:String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 사용자 ID 조회
        userFid = OcSdkIdManager.getFid(this).orEmpty()

        LogSdk.d(userFid, TAG,"[OnCreate] GpsVcService Start")

        // 위치 제공자 초기화
        fusedLocationProvider = FusedLocationProvider(this)

        // Wake Lock 초기화
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsVcService::PartialWakeLock")

        // AlarmManager 초기화
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, GpsVcService::class.java)
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogSdk.d(userFid, TAG, "[onStartCommand] onStartCommand")

        if(userFid.isNullOrEmpty()){
            userFid = OcSdkIdManager.getFid(this).orEmpty()
        }

        // 권한 누락 시 크래시 방지 처리
        // 유저가 설정에서 권한을 갑작스럽게 OFF 한 경우를 대비
        // 필수 권한이 없을 경우 서비스 중단 (크래시 방지 목적)
        if(!restoreIconResId() || !prepareNotification() || !checkPermissions() || !isNotificationChannelEnabled()){
            stop()
            return START_NOT_STICKY
        }


        // 시스템 재시작으로 null 인텐트 들어온 경우, 가장 먼저 체크!
        if (intent == null) {
            LogSdk.d(userFid, TAG, "[onStartCommand] Service restarted by system with null intent")
            return START_STICKY
        }


        if (!wakeLock.isHeld) {
            wakeLock.acquire(currentScanIntervalMs)
        }

        setDozeReceiver()

        startForeground(SERVICE_NOTI_ID, notification!!)
        vcGpsProcess()
        return START_STICKY
    }

    private fun checkPermissions(): Boolean {
        if (!OcVcGpsSdk.hasAllRequiredPermissions(applicationContext, userFid)) {
            LogSdk.e(userFid, TAG, "[onStartCommand] Required permissions are missing. Stopping the service.")
            return false
        }
        return true
    }

    private fun restoreIconResId(): Boolean {
        // 최초 실행일 때만 값 설정
        if (iconResId == null) {
            val incomingIcon = OcVcGpsSdk.getSmallIconResId()
            if (incomingIcon == null || incomingIcon == -1) {
                LogSdk.e(userFid, TAG, "[onStartCommand] Missing smallIconResId. Stopping service.")
                return false
            }
            iconResId = incomingIcon
        }
        return true
    }

    private fun prepareNotification(): Boolean {
        if (notification == null) {
            notification = iconResId?.let {
                createPermanentNotification("位置サービスが稼働中です", it)
            }
            if (notification == null) {
                LogSdk.e(userFid, TAG, "[onStartCommand] Notification is null after creation attempt. Stopping service.")
                return false
            }
        }
        return true
    }

    private fun isNotificationChannelEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel("onecheck_vcgps_service")
            val isImportance = channel?.importance != NotificationManager.IMPORTANCE_NONE
            if(!isImportance){
                LogSdk.e(userFid, TAG, "[onStartCommand] Notification channel is disabled by user. Service cannot continue.")
            }
            return isImportance
        }
        return true // O 이하 버전은 알림 채널 없음
    }

    // GPS 위치 수집 및 처리 메소드
    private fun vcGpsProcess() {

        // 백그라운드 도중 권한이 제거된 경우 대비
        if (!OcVcGpsSdk.hasAllRequiredPermissions(applicationContext, userFid)) {
            LogSdk.e(userFid, TAG, "[vcGpsProcess] Required permissions missing during execution. Stopping process.")
            stop()
            return
        }

        try {
            // 현재 위치 요청
            fusedLocationProvider.requestCurrentLocation { location ->
                try {
                    if (location == null) {
                        LogSdk.e(userFid, TAG, "[vcGpsProcess] Failed to get location.")
                        handleVisitFail()
                        return@requestCurrentLocation
                    }

                    val latitude = location.latitude
                    val longitude = location.longitude
                    val accuracy = location.accuracy

                    LogSdk.d(userFid, TAG, "[vcGpsProcess] Current location: $latitude, $longitude (Accuracy: $accuracy)")

                    RetrofitConnection.makeApiCall(
                        call = { VcApi.service.getNearByPlace(latitude, longitude) },
                        onSuccess = { nearByPlaces ->
                            if (nearByPlaces.isNullOrEmpty()) {
                                LogSdk.d(userFid, TAG, "[vcGpsProcess] No nearby stores found.")
                                handleVisitFail()
                                return@makeApiCall
                            }

                            val bestPlace = pickBestPlace(userFid, latitude, longitude, accuracy.toDouble(), nearByPlaces)
                            if (bestPlace == null) {
                                LogSdk.d(userFid, TAG, "[vcGpsProcess] No suitable store matched.")
                                handleVisitFail()
                                return@makeApiCall
                            }

                            LogSdk.d(userFid, TAG, "[vcGpsProcess] Store matched: ${bestPlace.place_name}, Distance: ${bestPlace.distance}")

                            if (cacheVisitorPlaceId != 0 && cacheVisitorPlaceId != bestPlace.id) {
                                LogSdk.d(TAG, userFid,"[vcGpsProcess] Store changed detected! $cacheVisitorPlaceId → ${bestPlace.id}")
                                cacheVisitorId = 0
                                visitFailCount = 0
                            }

                            sendGpsVisitLog(latitude, longitude, bestPlace)
                            sendGpsVisitor(bestPlace.id)
                        },
                        onFailure = {
                            LogSdk.e(userFid, TAG,"[vcGpsProcess] Failed to request store information")
                            handleVisitFail()
                        }
                    )
                } catch (e: SecurityException) {
                    // 권한 변경 중 예외 발생
                    LogSdk.e(userFid, TAG, "[vcGpsProcess] SecurityException occurred - possible permission issue ${e.message}\n${Log.getStackTraceString(e)}")
                    stop()
                } catch (e: Exception) {
                    // 기타 예외
                    LogSdk.e(userFid, TAG,"[vcGpsProcess] Unexpected error occurred ${e.message}\n${Log.getStackTraceString(e)}")
                    stop()
                }
            }
        } catch (e: Exception) {

            LogSdk.e(userFid, TAG,"[vcGpsProcess] Outer exception occurred ${e.message}\n${Log.getStackTraceString(e)}")
            stop()
        }
    }

    private fun setDozeReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (dozeReceiver == null) {
                dozeReceiver = DozeReceiver()
                val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                registerReceiver(dozeReceiver, filter)
                LogSdk.d(userFid, TAG,"[setDozeReceiver] DozeReceiver registered")
            }
        } else {
            LogSdk.d(userFid, TAG,"[setDozeReceiver] ozeReceiver only available from API 23 - Skipping registration")
        }
    }

    private fun unsetDozeReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dozeReceiver?.let {
                try {
                    unregisterReceiver(it)
                    dozeReceiver = null
                    LogSdk.d(userFid, TAG,"[unsetDozeReceiver] DozeReceiver unregistered")
                } catch (e: Exception) {
                    LogSdk.e(userFid, TAG,"[unsetDozeReceiver] Error while unregistering DozeReceiver\\n$e")
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
        LogSdk.i(userFid, TAG,"SDK Service Stop")
        OcVcGpsSdk.setServiceFlag(false)
        cancelAlarmManager()
        stopSelf()
        //sound.releaseMediaPlayer()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LogSdk.d(userFid, TAG,"onTaskRemoved called = App was swiped or killed")

        // 여기선 따로 setupAlarmManager() 호출 X
        // 이미 주기적으로 예약되어 있기 때문

        // stop() 호출도 생략 (onDestroy에서 정리하니까)
    }

    override fun onDestroy() {
        LogSdk.d(userFid, TAG, "GpsVcService onDestroy - Releasing resources")

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
        val channelName = "location_tracking_service"
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
        notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(message)
            .setStyle(NotificationCompat.InboxStyle())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)

        return notificationBuilder!!.build()
    }

    private fun setupAlarmManager() {
        val triggerAtMillis =  SystemClock.elapsedRealtime() + currentScanIntervalMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23 이상 → Doze 모드에서도 정확하게 알람 울림
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            LogSdk.d(userFid, TAG,"[setupAlarmManager] Alarm set: using setExactAndAllowWhileIdle()")

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19~22 → 정확한 알람 설정 (Doze 미지원)
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            LogSdk.d(userFid, TAG,"[setupAlarmManager] Alarm set: using setExact()")
        } else {
            // API 19 미만 → 정확하지 않은 일반 알람 사용
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                alarmIntent
            )
            LogSdk.d(userFid, TAG,"[setupAlarmManager] Alarm set: using set()")
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
                    // Doze 모드 진입
                    LogSdk.d(userFid, TAG, "[DozeReceiver] Device is in Doze mode")
                    currentScanIntervalMs = DOZE_SCAN_INTERVAL_MS
                    setupAlarmManager()
                } else {
                    LogSdk.d(userFid, TAG, "[DozeReceiver] Device exited Doze mode")
                    currentScanIntervalMs = NORMAL_SCAN_INTERNAL_MS
                    setupAlarmManager()
                }
            }
        }
    }

    private fun handleVisitFail() {
        visitFailCount++
        LogSdk.d(userFid, TAG, "[handleVisitFail] Visit match failed count: $visitFailCount")


        if (visitFailCount >= MAX_FAIL_COUNT) {
            LogSdk.d(userFid, TAG, "[handleVisitFail] 3 consecutive failures → ending visit")
            LogSdk.d(userFid, TAG,"[handleVisitFail] Visitor #$cacheVisitorId ended")
            cacheVisitorId = 0
            cacheVisitorPlaceId = 0
            visitFailCount = 0
        }
        setupAlarmManager()
    }

    private fun sendGpsVisitLog(lat: Double, lng: Double, bestPlace: Places){
        val gpsLog = vcGpsLog(
            id = null,
            place_name = bestPlace?.place_name ?: "Unknown",
            distance = bestPlace?.distance?.toDouble() ?: 0.0,
            geometry_lat = lat,
            geometry_lng = lng,
            time_stamp = null
        )

        RetrofitConnection.makeApiCall(
            call = { VcApi.service.makeVcGpsLog(gpsLog) },
            onSuccess = { LogSdk.d(userFid, TAG, "[sendGpsVisitLog] GPS log saved") },
            onFailure = { LogSdk.e(userFid, TAG, "[Failed to save GPS log] Failed to save GPS log") }
        )
    }

    private fun sendGpsVisitor(placesId: Int) {
        val request = vcGpsVisitor(
            visitor_id = cacheVisitorId,
            places_id = placesId,
            fid = userFid
        )

        RetrofitConnection.makeApiCall(
            call = { VcApi.service.gpsVisits(request) },
            onSuccess = { response ->
                val visitorId = response?.visitor_id ?: 0

                if (visitorId == 0) {
                    LogSdk.d(userFid, TAG, "[sendGpsVisitor] Visitor match failed")
                    handleVisitFail()
                } else {
                    if (visitorId == cacheVisitorId) {
                        LogSdk.d(userFid, TAG, "[sendGpsVisitor] Visitor remains the same: $visitorId")
                    } else {
                        LogSdk.d(userFid, TAG, "[sendGpsVisitor] Visitor updated: $visitorId")
                        cacheVisitorId = visitorId
                        cacheVisitorPlaceId = placesId
                    }
                    visitFailCount = 0
                }

                setupAlarmManager()
            },
            onFailure = { errorMsg ->
                LogSdk.e(userFid, TAG, "$errorMsg")
                handleVisitFail()
            }
        )
    }
}