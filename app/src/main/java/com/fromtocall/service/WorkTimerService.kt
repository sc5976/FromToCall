package com.fromtocall.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.fromtocall.FromToCallApp
import com.fromtocall.R
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@AndroidEntryPoint
class WorkTimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE_ALARM = 420
        private const val TICK_MILLIS = 30_000L
        private const val PREFS = "fromtocall_alarm"
        private const val KEY_DEPARTURE = "departure_millis"
        private const val KEY_SOUND = "sound_uri"
        private const val KEY_ARRIVAL_HOUR = "arrival_hour"
        private const val KEY_ARRIVAL_MINUTE = "arrival_minute"

        const val ACTION_START = "com.fromtocall.START_TIMER"
        const val ACTION_STOP = "com.fromtocall.STOP_TIMER"
        const val EXTRA_DEPARTURE_TIME = "departure_time"
        const val EXTRA_SOUND_URI = "sound_uri"
        const val EXTRA_ARRIVAL_HOUR = "arrival_hour"
        const val EXTRA_ARRIVAL_MINUTE = "arrival_minute"

        fun startTimer(
            context: Context,
            departureTimeMillis: Long,
            soundUri: String,
            arrivalHour: Int,
            arrivalMinute: Int
        ) {
            val intent = Intent(context, WorkTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEPARTURE_TIME, departureTimeMillis)
                putExtra(EXTRA_SOUND_URI, soundUri)
                putExtra(EXTRA_ARRIVAL_HOUR, arrivalHour)
                putExtra(EXTRA_ARRIVAL_MINUTE, arrivalMinute)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, WorkTimerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }

        /**
         * PendingIntent считается тем же самым, только если совпадает action и
         * компонент. Поэтому и планирование, и отмена обязаны строить его
         * одинаково — иначе отмена молча ничего не делает.
         */
        private fun alarmPendingIntent(
            context: Context,
            soundUri: String,
            flags: Int
        ): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_ALARM
                putExtra(AlarmReceiver.EXTRA_SOUND_URI, soundUri)
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                intent,
                flags
            )
        }

        fun scheduleAlarm(
            context: Context,
            departureTimeMillis: Long,
            soundUri: String,
            arrivalHour: Int,
            arrivalMinute: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = alarmPendingIntent(
                context,
                soundUri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            persistAlarm(context, departureTimeMillis, soundUri, arrivalHour, arrivalMinute)

            if (departureTimeMillis <= System.currentTimeMillis()) return

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    departureTimeMillis,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                // На Android 12+ без SCHEDULE_EXACT_ALARM точное будильничество
                // недоступно — откатываемся на неточное, звонок всё равно придёт.
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    departureTimeMillis,
                    pendingIntent
                )
            }
        }

        /**
         * Храним время ухода, чтобы после перезагрузки телефона переставить будильник:
         * AlarmManager не переживает рестарт, а данные лежат в SharedPreferences.
         */
        private fun persistAlarm(
            context: Context,
            departureTimeMillis: Long,
            soundUri: String,
            arrivalHour: Int,
            arrivalMinute: Int
        ) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DEPARTURE, departureTimeMillis)
                .putString(KEY_SOUND, soundUri)
                .putInt(KEY_ARRIVAL_HOUR, arrivalHour)
                .putInt(KEY_ARRIVAL_MINUTE, arrivalMinute)
                .apply()
        }

        fun rescheduleAfterBoot(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val departure = prefs.getLong(KEY_DEPARTURE, 0L)
            if (departure <= System.currentTimeMillis()) return
            val soundUri = prefs.getString(KEY_SOUND, "").orEmpty()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = alarmPendingIntent(
                context,
                soundUri,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    departure,
                    pendingIntent
                )
            }.onFailure {
                alarmManager.set(AlarmManager.RTC_WAKEUP, departure, pendingIntent)
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = alarmPendingIntent(
                context,
                "",
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        /**
         * Запущен ли таймер на самом деле. Экрану это нужно как отдельный факт:
         * судить по часам нельзя, иначе кнопка обещает «выключить» то, что
         * никогда не включалось.
         */
        fun isTimerActive(context: Context): Boolean {
            val departure = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_DEPARTURE, 0L)
            return departure > System.currentTimeMillis()
        }

        /** Сбрасывает сохранённое время ухода — только при явной остановке. */
        fun clearTimer(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DEPARTURE)
                .remove(KEY_SOUND)
                .remove(KEY_ARRIVAL_HOUR)
                .remove(KEY_ARRIVAL_MINUTE)
                .apply()
        }

        /**
         * Время прихода, сохранённое при старте таймера. Нужно, чтобы при повторном
         * открытии приложения показывать тот приход, который уже работает, а не
         * подставлять текущий момент.
         */
        fun savedArrivalTime(context: Context): Pair<Int, Int>? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val hour = prefs.getInt(KEY_ARRIVAL_HOUR, -1)
            val minute = prefs.getInt(KEY_ARRIVAL_MINUTE, -1)
            return if (hour in 0..23 && minute in 0..59) hour to minute else null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val departureTime = intent.getLongExtra(EXTRA_DEPARTURE_TIME, 0L)
                val soundUri = intent.getStringExtra(EXTRA_SOUND_URI).orEmpty()
                val arrivalHour = intent.getIntExtra(EXTRA_ARRIVAL_HOUR, -1)
                val arrivalMinute = intent.getIntExtra(EXTRA_ARRIVAL_MINUTE, -1)
                startForeground(NOTIFICATION_ID, buildTimerNotification(departureTime))
                scheduleAlarm(this, departureTime, soundUri, arrivalHour, arrivalMinute)
                startTicking(departureTime)
            }
            ACTION_STOP -> {
                cancelAlarm(this)
                clearTimer(this)
                stopTicking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildTimerNotification(departureTimeMillis: Long): Notification {
        val text = if (departureTimeMillis > 0) {
            val departure = java.time.Instant.ofEpochMilli(departureTimeMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
            getString(
                R.string.notification_text_remaining,
                getString(R.string.departure_at, departure.format(FORMATTER)),
                formatRemaining(departureTimeMillis)
            )
        } else {
            getString(R.string.weekend_message)
        }

        return NotificationCompat.Builder(this, FromToCallApp.CHANNEL_ID_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Обновляет уведомление, чтобы «осталось» не устаревало: сервис и так живёт
     * всё время работы, пересобирать текст раз в полминуты дешевле, чем искать
     * способ тикать без пересборки.
     */
    private var tickRunnable: Runnable? = null

    private fun startTicking(departureTimeMillis: Long) {
        stopTicking()
        val tick = object : Runnable {
            override fun run() {
                notificationManager.notify(NOTIFICATION_ID, buildTimerNotification(departureTimeMillis))
                handler.postDelayed(this, TICK_MILLIS)
            }
        }
        tickRunnable = tick
        handler.postDelayed(tick, TICK_MILLIS)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private fun formatRemaining(departureTimeMillis: Long): String {
        val totalMinutes = Duration.between(
            LocalDateTime.now(),
            Instant.ofEpochMilli(departureTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        ).toMinutes().coerceAtLeast(0L)

        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) {
            getString(R.string.remaining_hours_minutes, h, m)
        } else {
            getString(R.string.remaining_minutes_only, m)
        }
    }

    override fun onDestroy() {
        stopTicking()
        cancelAlarm(this)
        super.onDestroy()
    }

    private val FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
}