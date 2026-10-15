package com.fromtocall.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fromtocall.R
import com.fromtocall.ui.components.TimePickerWheel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        uri?.let {
            val ringtone = RingtoneManager.getRingtone(context, it)
            val title = ringtone?.getTitle(context) ?: context.getString(R.string.notification_sound)
            viewModel.updateSound(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Все четыре длительности — одинаковыми строками списка, чтобы
            // вместе с карточкой «Звук» они умещались на экране телефона
            // без прокрутки. Тап по строке открывает диалог с TimePicker.
            DurationRow(
                title = stringResource(R.string.workday_full),
                hours = uiState.workdayFullHours,
                minutes = uiState.workdayFullMinutes,
                onTimeChange = viewModel::updateWorkdayFull
            )
            Spacer(modifier = Modifier.height(8.dp))
            DurationRow(
                title = stringResource(R.string.workday_short),
                hours = uiState.workdayFridayHours,
                minutes = uiState.workdayFridayMinutes,
                onTimeChange = viewModel::updateWorkdayFriday
            )
            Spacer(modifier = Modifier.height(8.dp))
            DurationRow(
                title = stringResource(R.string.workday_saturday),
                hours = uiState.workdaySaturdayHours,
                minutes = uiState.workdaySaturdayMinutes,
                onTimeChange = viewModel::updateWorkdaySaturday
            )
            Spacer(modifier = Modifier.height(8.dp))
            DurationRow(
                title = stringResource(R.string.workday_sunday),
                hours = uiState.workdaySundayHours,
                minutes = uiState.workdaySundayMinutes,
                onTimeChange = viewModel::updateWorkdaySunday
            )

            Spacer(modifier = Modifier.height(8.dp))
            LanguageRow(
                currentTag = uiState.languageTag,
                onSelect = { tag ->
                    viewModel.setLanguage(tag)
                    // Перерисовываем активити, чтобы все строки подхватили
                    // новый locale без перезапуска приложения.
                    (context as? android.app.Activity)?.recreate()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Звук — отдельной карточкой: там кнопки и текст, не влезает в строку.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.notification_sound),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.playTestSound() }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.test_sound),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.soundName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.change_sound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.notification_sound))
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                }
                                ringtoneLauncher.launch(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    currentTag: String,
    onSelect: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = when (currentTag) {
        "ru" -> stringResource(R.string.settings_language_russian)
        "en" -> stringResource(R.string.settings_language_english)
        else -> stringResource(R.string.settings_language_system)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(currentTag, "", R.string.settings_language_system) {
                        onSelect("")
                        showDialog = false
                    }
                    LanguageOption(currentTag, "ru", R.string.settings_language_russian) {
                        onSelect("ru")
                        showDialog = false
                    }
                    LanguageOption(currentTag, "en", R.string.settings_language_english) {
                        onSelect("en")
                        showDialog = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_picker_cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(
    currentTag: String,
    tag: String,
    @androidx.annotation.StringRes labelRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = currentTag == tag,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DurationRow(
    title: String,
    hours: Int,
    minutes: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var draftHour by remember { mutableStateOf(hours) }
    var draftMinute by remember { mutableStateOf(minutes) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                draftHour = hours
                draftMinute = minutes
                showDialog = true
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatDuration(hours, minutes),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_picker_title)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePickerWheel(
                        hour = draftHour,
                        minute = draftMinute,
                        onTimeChange = { h, m ->
                            draftHour = h
                            draftMinute = m
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(draftHour, draftMinute)
                    showDialog = false
                }) { Text(stringResource(R.string.settings_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.settings_picker_cancel))
                }
            }
        )
    }
}

private fun formatDuration(hours: Int, minutes: Int): String =
    String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
