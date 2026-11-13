package com.auralis.player

import androidx.lifecycle.ViewModel
import com.auralis.player.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import com.auralis.player.settings.AppSettings
import javax.inject.Inject

/**
 * Thin Hilt-injectable bridge so MainActivity (which isn't itself a good
 * place for business logic) can read the live settings stream to drive
 * [com.auralis.player.ui.theme.AuralisTheme]. Screens that need settings
 * beyond theming inject SettingsDataStore directly instead.
 */
@HiltViewModel
class SettingsBridgeViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore,
) : ViewModel() {
    val settings: Flow<AppSettings> = settingsDataStore.settings
}
