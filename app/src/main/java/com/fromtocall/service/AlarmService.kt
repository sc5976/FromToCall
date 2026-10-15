package com.fromtocall.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fromtocall.FromToCallApp
import com.fromtocall.MainActivity
import com.fromtocall.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Текущее состояние сигнала для интерфейса. Нужен жив fact «звонит ли сейчас»,
 * иначе экран обещает кнопку отключения, которой нечем отключать.
 */
object AlarmState {
    private val _isRinging = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRinging: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRinging

    fun setRinging(value: Boolean) {
        _isRinging.value = value
    }
}

/**
 * Foreground-сервис, который реально орёт сигнал «пора уходить».
 *
 * Раньше звук игрался прямо из BroadcastReceiver, но там нет гарантии жизни
 * процесса — на реальном устройстве Ringtone успевает заглохнуть через пару
 * секунд. Здесь сервис держит wake lock, крутит MediaPlayer и сам себя гасит
 * через AUTO_STOP_MILLIS.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_STOP_MILLIS = 60_000L
        const val EXTRA_SOUND_URI = "extra_sound_uri"

        fun start(context: Context, soundUri: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_SOUND_URI, soundUri)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var savedAlarmVolume: Int = -1
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        cancelVibration()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        // Громкость потока будильника живёт отдельно от общего звука — если
        // она 0, вибрация идёт, а MediaPlayer молчит. На время звонка
        // поднимаем её до максимума, в onDestroy возвращаем обратно.
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (savedAlarmVolume < maxAlarm) {
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0) }
        }
        vibrate()
        playSound()
        AlarmState.setRinging(true)
        handler.postDelayed(autoStop, AUTO_STOP_MILLIS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val soundUri = intent.getStringExtra(EXTRA_SOUND_URI).orEmpty()
            // startForeground уже вызван в onCreate, здесь только звук
            if (player == null) {
                vibrate()
                playSound(soundUri)
                handler.removeCallbacks(autoStop)
                handler.postDelayed(autoStop, AUTO_STOP_MILLIS)
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Именно getBroadcast: StopAlarmReceiver — это BroadcastReceiver, а не
        // сервис. С getService он бы вообще не запускался, и кнопка «Отключить»
        // молча ничего не делала.
        val stopIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, StopAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FromToCallApp.CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.alarm_title))
            .setContentText(getString(R.string.alarm_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.alarm_stop_action), stopIntent)
            .build()
    }

    private fun playSound(soundUri: String = "") {
        val uri: Uri = if (soundUri.isNotEmpty()) {
            runCatching { Uri.parse(soundUri) }.getOrNull()
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } ?: run {
            Log.w("AlarmService", "no alarm/notification/ringtone uri available")
            return
        }

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
                Log.i("AlarmService", "playing alarm uri=$uri vol=${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}")
            }
        }.onFailure { e ->
            Log.w("AlarmService", "MediaPlayer failed: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { player?.release() }
            player = null
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!v.hasVibrator()) return

        // Второй аргумент createWaveform — это индекс в массиве timings, с
        // которого начинается повтор, а НЕ количество повторов. Поэтому
        // конечное число повторов здесь задать нельзя: либо индекс, либо -1
        // (повторяется, пока не отменят). Поэтому повторяем и обязательно
        // гасим вибрацию в stopSignal()/onDestroy — иначе телефон звонит дальше
        // даже после остановки звука.
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun cancelVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FromToCall:alarm").apply {
            setReferenceCounted(false)
            acquire(AUTO_STOP_MILLIS + 5_000L)
        }
    }

    override fun onDestroy() {
        AlarmState.setRinging(false)
        handler.removeCallbacks(autoStop)
        cancelVibration()
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        // Возвращаем громкость будильника ровно той, что была до сигнала.
        if (savedAlarmVolume >= 0) {
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }
            savedAlarmVolume = -1
        }
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        super.onDestroy()
    }
}

/** Кнопка «Отключить» в уведомлении. */
class StopAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlarmService.stop(context)
    }
}