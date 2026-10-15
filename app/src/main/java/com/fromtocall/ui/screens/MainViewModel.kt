package com.fromtocall.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fromtocall.data.SettingsRepository
import com.fromtocall.service.WorkTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class MainUiState(
    val arrivalHour: Int = 9,
    val arrivalMinute: Int = 0,
    val departureMillis: Long = 0L,
    val workdayMinutes: Int = 0,
    val remainingMinutes: Long = 0,
    val isTimerRunning: Boolean = false,
    val formattedDate: String = "",
    val formattedDeparture: String = "",
    val formattedRemaining: String = "00:00",
    val progress: Float = 1f
)

/** Настройки, из которых выводится состояние экрана. */
private data class Snapshot(
    val arrivalHour: Int,
    val arrivalMinute: Int,
    val full: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val snapshot = MutableStateFlow(
        Snapshot(
            arrivalHour = SettingsRepository.arrivalHourDefault(),
            arrivalMinute = SettingsRepository.arrivalMinuteDefault(),
            full = SettingsRepository.DEFAULT_WORKDAY_FULL,
            friday = SettingsRepository.DEFAULT_WORKDAY_FRIDAY,
            saturday = SettingsRepository.DEFAULT_WORKDAY_SATURDAY,
            sunday = SettingsRepository.DEFAULT_WORKDAY_SUNDAY
        )
    )

    private val context get() = getApplication<Application>()

    init {
        // При запуске приложения начало рабочего дня — это текущее время.
        // Но если таймер уже работает, время прихода зафиксировано в момент старта:
        // показываем именно его, иначе переоткрытие приложения сбило бы учёт.
        // Пользователь может поправить время или сразу нажать «Старт».
        viewModelScope.launch {
            val app = getApplication<Application>()
            val running = WorkTimerService.isTimerActive(app)
            val saved = if (running) WorkTimerService.savedArrivalTime(app) else null
            if (saved != null) {
                settingsRepository.setArrival(saved.first, saved.second)
            } else {
                val now = LocalTime.now()
                settingsRepository.setArrival(now.hour, now.minute)
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.arrivalHour,
                settingsRepository.arrivalMinute,
                settingsRepository.workdayFullMinutes,
                settingsRepository.workdayFridayMinutes,
                settingsRepository.workdaySaturdayMinutes,
                settingsRepository.workdaySundayMinutes
            ) { values: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                Snapshot(
                    arrivalHour = values[0] as Int,
                    arrivalMinute = values[1] as Int,
                    full = values[2] as Int,
                    friday = values[3] as Int,
                    saturday = values[4] as Int,
                    sunday = values[5] as Int
                )
            }.collect { snapshot.value = it }
        }

        // Если таймер числится запущенным, а будильник в AlarmManager потерялся
        // (перезагрузка телефона, «принудительная остановка» в настройках) —
        // переставляем его. Операция идемпотентная: повторная постановка того же
        // будильника на то же время ничего не портит.
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (WorkTimerService.isTimerActive(app)) {
                WorkTimerService.rescheduleAfterBoot(app)
            }
        }

        // Пересчёт идёт и по тику, и мгновенно при смене начала или настроек.
        viewModelScope.launch {
            combine(snapshot, tickerFlow()) { s, _ -> buildState(s) }
                .collect { _uiState.value = it }
        }
    }

    private fun tickerFlow(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    private fun buildState(s: Snapshot): MainUiState {
        val today = LocalDate.now()
        val day = today.dayOfWeek

        // Какой именно рабочий день сегодня — решает пользователь в настройках.
        val workdayMinutes = when (day) {
            DayOfWeek.FRIDAY -> s.friday
            DayOfWeek.SATURDAY -> s.saturday
            DayOfWeek.SUNDAY -> s.sunday
            else -> s.full
        }

        val arrivalTime = LocalTime.of(s.arrivalHour, s.arrivalMinute)
        val departureTime = arrivalTime.plusMinutes(workdayMinutes.toLong())

        // Если начало позже окончания, смена перешла на следующие сутки.
        val arrivalDateTime = today.atTime(arrivalTime)
        var departureDateTime = today.atTime(departureTime)
        if (departureDateTime.isBefore(arrivalDateTime)) {
            departureDateTime = departureDateTime.plusDays(1)
        }

        val departureMillis = departureDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val now = LocalDateTime.now()
        val remaining = Duration.between(now, departureDateTime).toMinutes().coerceAtLeast(0L)

        val progress = if (workdayMinutes > 0 && remaining > 0) {
            (remaining.toFloat() / workdayMinutes.toFloat()).coerceIn(0f, 1f)
        } else if (remaining == 0L) {
            1f
        } else {
            0f
        }

        return MainUiState(
            arrivalHour = s.arrivalHour,
            arrivalMinute = s.arrivalMinute,
            departureMillis = departureMillis,
            workdayMinutes = workdayMinutes,
            remainingMinutes = remaining,
            isTimerRunning = WorkTimerService.isTimerActive(getApplication()),
            formattedDate = today.format(
                DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
            ),
            formattedDeparture = departureTime.format(
                DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            ),
            formattedRemaining = formatRemaining(remaining),
            progress = progress
        )
    }

    fun updateArrivalTime(hour: Int, minute: Int) {
        viewModelScope.launch { settingsRepository.setArrival(hour, minute) }
    }

    fun startTimer() {
        val state = _uiState.value
        if (state.remainingMinutes <= 0 || state.isTimerRunning) return

        viewModelScope.launch {
            val soundUri = settingsRepository.soundUri.first()
            WorkTimerService.startTimer(
                context,
                state.departureMillis,
                soundUri,
                state.arrivalHour,
                state.arrivalMinute
            )
            _uiState.value = buildState(snapshot.value)
        }
    }

    fun stopTimer() {
        WorkTimerService.stopTimer(context)
        WorkTimerService.clearTimer(context)
        _uiState.value = buildState(snapshot.value)
    }

    fun openExactAlarmSettings() {
        com.fromtocall.openExactAlarmSettings(context)
    }

    private fun formatRemaining(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.getDefault(), "%d:%02d", h, m)
    }

    companion object {
        private const val TICK_MILLIS = 15_000L
    }
}