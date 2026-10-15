package com.fromtocall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Хранит три полных длительности рабочего дня: обычный, пятница и выходные.
 *
 * Значения задаёт пользователь целиком — никаких вычетов (обед и подобное)
 * приложение не делает. Выходные не считаются выходными по умолчанию:
 * если человек работает в субботу, значит у него и суббота рабочая.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val WORKDAY_FULL_MINUTES = intPreferencesKey("workday_full_minutes")
        val WORKDAY_FRIDAY_MINUTES = intPreferencesKey("workday_friday_minutes")
        val WORKDAY_SATURDAY_MINUTES = intPreferencesKey("workday_saturday_minutes")
        val WORKDAY_SUNDAY_MINUTES = intPreferencesKey("workday_sunday_minutes")
        // Старое общее значение выходных оставлено в репозитории для совместимости
        // с уже установленными сборками, но в UI больше не используется.
        val WORKDAY_WEEKEND_MINUTES = intPreferencesKey("workday_weekend_minutes")
        val ARRIVAL_HOUR = intPreferencesKey("arrival_hour")
        val ARRIVAL_MINUTE = intPreferencesKey("arrival_minute")
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val ACCENT_KEY = stringPreferencesKey("accent_key")

        const val DEFAULT_WORKDAY_FULL = 8 * 60 + 45
        const val DEFAULT_WORKDAY_FRIDAY = 7 * 60 + 45
        const val DEFAULT_WORKDAY_SATURDAY = 7 * 60 + 45
        const val DEFAULT_WORKDAY_SUNDAY = 7 * 60 + 45
        const val DEFAULT_WORKDAY_WEEKEND = 7 * 60 + 45

        /** Ключ акцентного цвета по умолчанию — текущий зелёный. */
        const val DEFAULT_ACCENT_KEY = "green"

        /** Стартовое время прихода — то, что сейчас на часах. */
        fun arrivalHourDefault(): Int = java.time.LocalTime.now().hour
        fun arrivalMinuteDefault(): Int = java.time.LocalTime.now().minute
    }

    val workdayFullMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[WORKDAY_FULL_MINUTES] ?: DEFAULT_WORKDAY_FULL
    }

    val workdayFridayMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[WORKDAY_FRIDAY_MINUTES] ?: DEFAULT_WORKDAY_FRIDAY
    }

    val workdaySaturdayMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        // Если отдельное значение для субботы не задано, переносим из старого
        // общего «weekend», чтобы уже введённые данные не потерялись.
        prefs[WORKDAY_SATURDAY_MINUTES]
            ?: prefs[WORKDAY_WEEKEND_MINUTES]
            ?: DEFAULT_WORKDAY_SATURDAY
    }

    val workdaySundayMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[WORKDAY_SUNDAY_MINUTES]
            ?: prefs[WORKDAY_WEEKEND_MINUTES]
            ?: DEFAULT_WORKDAY_SUNDAY
    }

    val arrivalHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ARRIVAL_HOUR] ?: LocalDefaults.nowHour()
    }

    val arrivalMinute: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ARRIVAL_MINUTE] ?: LocalDefaults.nowMinute()
    }

    val soundUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SOUND_URI] ?: ""
    }

    val accentKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCENT_KEY] ?: DEFAULT_ACCENT_KEY
    }

    suspend fun setWorkdayFull(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[WORKDAY_FULL_MINUTES] = minutes }
    }

    suspend fun setWorkdayFriday(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[WORKDAY_FRIDAY_MINUTES] = minutes }
    }

    suspend fun setWorkdaySaturday(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[WORKDAY_SATURDAY_MINUTES] = minutes }
    }

    suspend fun setWorkdaySunday(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[WORKDAY_SUNDAY_MINUTES] = minutes }
    }

    suspend fun setArrival(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[ARRIVAL_HOUR] = hour
            prefs[ARRIVAL_MINUTE] = minute
        }
    }

    suspend fun setSoundUri(uri: String) {
        context.dataStore.edit { prefs -> prefs[SOUND_URI] = uri }
    }

    suspend fun setAccentKey(key: String) {
        context.dataStore.edit { prefs -> prefs[ACCENT_KEY] = key }
    }
}

private object LocalDefaults {
    fun nowHour(): Int = java.time.LocalTime.now().hour
    fun nowMinute(): Int = java.time.LocalTime.now().minute
}