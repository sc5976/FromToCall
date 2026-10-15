package com.fromtocall.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Только маршрутизирует: сам звук и вибрацию делает AlarmService.
 * BroadcastReceiver живёт доли секунды, поэтому держать в нём MediaPlayer бессмысленно.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "com.fromtocall.ALARM_TRIGGER"
        const val EXTRA_SOUND_URI = "sound_uri"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val soundUri = intent.getStringExtra(EXTRA_SOUND_URI).orEmpty()
        AlarmService.start(context, soundUri)
    }
}