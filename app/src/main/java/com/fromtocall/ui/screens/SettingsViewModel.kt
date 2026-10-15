package com.fromtocall.ui.screens

import android.app.Application
import android.media.RingtoneManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fromtocall.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val workdayFullHours: Int = 8,
    val workdayFullMinutes: Int = 45,
    val workdayFridayHours: Int = 7,
    val workdayFridayMinutes: Int = 45,
    val workdaySaturdayHours: Int = 7,
    val workdaySaturdayMinutes: Int = 45,
    val workdaySundayHours: Int = 7,
    val workdaySundayMinutes: Int = 45,
    val soundUri: String = "",
    val soundName: String = "Default",
    // Тег выбранного языка: "" = системный, "ru", "en". Состояние читается
    // из AppCompatDelegate при инициализации, а не из DataStore, чтобы был
    // единый источник правды.
    val languageTag: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val full = settingsRepository.workdayFullMinutes.first()
            val friday = settingsRepository.workdayFridayMinutes.first()
            val saturday = settingsRepository.workdaySaturdayMinutes.first()
            val sunday = settingsRepository.workdaySundayMinutes.first()
            val sound = settingsRepository.soundUri.first()

            val context = getApplication<Application>()
            val soundName = if (sound.isNotEmpty()) {
                try {
                    val uri = android.net.Uri.parse(sound)
                    android.media.RingtoneManager.getRingtone(context, uri)
                        ?.getTitle(context) ?: "Default"
                } catch (e: Exception) {
                    "Default"
                }
            } else {
                "Default"
            }

            _uiState.value = SettingsUiState(
                workdayFullHours = full / 60,
                workdayFullMinutes = full % 60,
                workdayFridayHours = friday / 60,
                workdayFridayMinutes = friday % 60,
                workdaySaturdayHours = saturday / 60,
                workdaySaturdayMinutes = saturday % 60,
                workdaySundayHours = sunday / 60,
                workdaySundayMinutes = sunday % 60,
                soundUri = sound,
                soundName = soundName,
                languageTag = currentLanguageTag()
            )
        }
    }

    fun updateWorkdayFull(hours: Int, minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setWorkdayFull(hours * 60 + minutes)
            _uiState.value = _uiState.value.copy(
                workdayFullHours = hours,
                workdayFullMinutes = minutes
            )
        }
    }

    fun updateWorkdayFriday(hours: Int, minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setWorkdayFriday(hours * 60 + minutes)
            _uiState.value = _uiState.value.copy(
                workdayFridayHours = hours,
                workdayFridayMinutes = minutes
            )
        }
    }

    fun updateWorkdaySaturday(hours: Int, minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setWorkdaySaturday(hours * 60 + minutes)
            _uiState.value = _uiState.value.copy(
                workdaySaturdayHours = hours,
                workdaySaturdayMinutes = minutes
            )
        }
    }

    fun updateWorkdaySunday(hours: Int, minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setWorkdaySunday(hours * 60 + minutes)
            _uiState.value = _uiState.value.copy(
                workdaySundayHours = hours,
                workdaySundayMinutes = minutes
            )
        }
    }

    fun updateSound(uri: String) {
        viewModelScope.launch {
            settingsRepository.setSoundUri(uri)
            _uiState.value = _uiState.value.copy(soundUri = uri)
        }
    }

    fun playTestSound() {
        val context = getApplication<Application>()
        try {
            val uri = if (_uiState.value.soundUri.isNotEmpty()) {
                android.net.Uri.parse(_uiState.value.soundUri)
            } else {
                android.media.RingtoneManager
                    .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            }
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Применяет выбранный язык. Хранилище — AppCompatDelegate, а не DataStore:
     * система сама запоминает выбор и сама применяет его при следующем старте.
     * Чтобы интерфейс сразу перерисовался на новом языке, вызвавшая сторона
     * должна пересоздать активити (это делает Composable, см. SettingsScreen).
     */
    fun setLanguage(tag: String) {
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
        _uiState.value = _uiState.value.copy(languageTag = tag)
    }

    private fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        return locales.get(0)?.toLanguageTag()?.substringBefore("-") ?: ""
    }
}