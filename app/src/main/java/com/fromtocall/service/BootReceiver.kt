package com.fromtocall.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager не переживает перезагрузку телефона, поэтому после BOOT_COMPLETED
 * переставляем будильник на сохранённое время ухода.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WorkTimerService.rescheduleAfterBoot(context)
        }
    }
}