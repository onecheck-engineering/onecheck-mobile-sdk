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
import com.onecheck.oc_vcgps_sdk.gps.FusedLocationProvider

class GpsVcService : Service() {

    private val TAG: String = "GpsVcService"
    private val SERVICE_NOTI_ID = 2
    private var dozeReceiver: DozeReceiver? = null
    private var notification: Notification? = null

    // GPS 관련 객체
    private lateinit var fusedLocationProvider: FusedLocationProvider

    // 스캔 주기
    private val SCAN_INTERVAL_MS = 10 * 2000L

    // Wake Lock
    private lateinit var wakeLock: PowerManager.WakeLock

    // AlarmManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntent: PendingIntent

    // 앱 아이콘 Notification에서 활용
    private var iconResId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GpsVcService Start")

        // TODO: 서버 서비스

        // 위치 제공자 초기화
        fusedLocationProvider = FusedLocationProvider(this)

        // TODO: 디바이스 정보 초기화

        // Wake Lock 초기화
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsVcService::PartialWakeLock")

        // AlarmManager 초기화
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, GpsVcService::class.java)
        alarmIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // 시스템 재시작으로 null 인텐트 들어온 경우, 가장 먼저 체크!
        if (intent == null) {
            Log.w(TAG, "Service restarted by system with null intent")
            return START_STICKY
        }

        // 최초 실행일 때만 값 설정
        val incomingIcon = intent?.getIntExtra("smallIconResId", -1) ?: -1
        if (iconResId == null && incomingIcon != -1) {
            iconResId = incomingIcon
        } else if (iconResId == null) {
            Log.e(TAG, "Missing iconResId. Stopping service.")
            stop()
            return START_NOT_STICKY
        }

        if (!wakeLock.isHeld) {
            wakeLock.acquire(SCAN_INTERVAL_MS)
        }

        setDozeReceiver()
        notification =
            iconResId?.let { createPermanentNotification("ocVcGps Service Open", it) }
        startForeground(SERVICE_NOTI_ID, notification)
        vcGpsProcess()

        return START_STICKY
    }

    private fun vcGpsProcess(){
        fusedLocationProvider.requestCurrentLocation { location ->
            if (location == null) {
                Log.e(TAG, "위치 정보를 가져오지 못했습니다.")
                setupAlarmManager()
                return@requestCurrentLocation
            }
            Log.d(TAG, "최신 위치: ${location.latitude}, ${location.longitude} ${location.getAccuracy()}")
            setupAlarmManager()
        }
    }

    private fun setDozeReceiver() {
        if(dozeReceiver == null){
            dozeReceiver = DozeReceiver()
            val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            registerReceiver(dozeReceiver, filter)
        }
    }

    private fun unsetDozeReceiver() {
        dozeReceiver?.let {
            try {
                unregisterReceiver(it)
                dozeReceiver = null
            } catch (e: Exception) {
                Log.e(TAG, "unsetDoze error\n$e")
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
        val channelName = "Onecheck Background GPS"

        // 1. 알림 채널 생성 (최초 1회만 등록됨)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN // 🔇 최소 중요도로 조용하게 처리
            ).apply {
                description = "Used for background GPS tracking"
                setShowBadge(false) // 앱 아이콘에 뱃지 표시 안함
                lockscreenVisibility = Notification.VISIBILITY_SECRET // 잠금화면에서 숨김
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. 포그라운드 알림 PendingIntent 생성
        val intent = Intent(this, GpsVcService::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 알림 빌드
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(message)
            .setStyle(NotificationCompat.InboxStyle())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN) // 시스템 알림 정렬 우선순위도 낮춤
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun setupAlarmManager() {
        // 정확한 알람 설정 (Doze 모드에서도 작동)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + SCAN_INTERVAL_MS,
            alarmIntent
        )
    }

    private fun cancelAlarmManager() {
        if (::alarmManager.isInitialized) {
            alarmManager.cancel(alarmIntent)
        }
    }


    inner class DozeReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (powerManager.isDeviceIdleMode) {
                    Log.d(TAG, "Device is in Doze mode")
                } else {
                    Log.d(TAG, "Device exited Doze mode")
                }
            }
        }
    }
}