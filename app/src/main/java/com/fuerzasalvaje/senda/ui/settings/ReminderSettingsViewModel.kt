package com.fuerzasalvaje.senda.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.usecase.focus.ObserveReminderSettingUseCase
import com.fuerzasalvaje.senda.domain.usecase.focus.SaveReminderSettingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class ReminderSettingsUiState(val loading: Boolean = true, val setting: ReminderSetting = ReminderSetting())

/** D-13, D-15: which reminders the student receives. Every change is saved at once. */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    observe: ObserveReminderSettingUseCase,
    private val save: SaveReminderSettingUseCase,
) : ViewModel() {

    val state: StateFlow<ReminderSettingsUiState> = observe()
        .map { ReminderSettingsUiState(loading = false, setting = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettingsUiState())

    fun setEnabled(value: Boolean) = update { it.copy(enabled = value) }
    fun setDailySummary(value: Boolean) = update { it.copy(dailySummaryEnabled = value) }
    fun setRestReminder(value: Boolean) = update { it.copy(restReminderEnabled = value) }
    fun setExamRoutine(value: Boolean) = update { it.copy(examRoutineEnabled = value) }
    fun setStudyBlockAlerts(value: Boolean) = update { it.copy(studyBlockAlertsEnabled = value) }

    /** Turns one of the default anticipations (3 days, 1 day, 2 hours) on or off, keeping their order. */
    fun setOffset(offset: Duration, on: Boolean) = update { current ->
        val chosen = if (on) current.evaluationOffsets + offset else current.evaluationOffsets - offset
        current.copy(evaluationOffsets = chosen.distinct().sortedDescending())
    }

    private fun update(change: (ReminderSetting) -> ReminderSetting) {
        val updated = change(state.value.setting)
        viewModelScope.launch { save(updated) }
    }
}
