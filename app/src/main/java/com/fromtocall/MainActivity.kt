package com.fromtocall

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.fromtocall.data.SettingsRepository
import com.fromtocall.ui.navigation.FromToCallNavHost
import com.fromtocall.ui.theme.FromToCallTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val accentKey by settingsRepository.accentKey.collectAsState(initial = SettingsRepository.DEFAULT_ACCENT_KEY)
            FromToCallTheme(accentKey = accentKey) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RequestNotificationPermission()
                    FromToCallNavHost()
                }
            }
        }
    }
}

/**
 * На Android 13+ (API 33) уведомления по умолчанию запрещены: без этого запроса
 * ни служебное уведомление таймера, ни сигнал «пора уходить» не покажутся.
 */
@androidx.compose.runtime.Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* пользователь может отказать — сервис тогда не покажет уведомление */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Отдельная точка входа: открыть системные настройки для выдачи
 * «Точные будильники». Без неё AlarmManager умеет только неточные.
 */
fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}