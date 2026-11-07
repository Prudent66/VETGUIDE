package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.repository.SettingsKeys
import com.prudent66.vetguide.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state: persisted flags read/written through Room (Phase 4). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val cloudAiEnabled: StateFlow<Boolean> = settingsRepository
        .observeBoolean(SettingsKeys.CLOUD_AI_ENABLED, default = false)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun setCloudAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(SettingsKeys.CLOUD_AI_ENABLED, enabled)
        }
    }
}
