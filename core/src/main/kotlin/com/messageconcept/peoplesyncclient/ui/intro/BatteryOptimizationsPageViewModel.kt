/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import com.messageconcept.peoplesyncclient.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BatteryOptimizationsPageViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val settings: SettingsManager
): ViewModel() {

    companion object {

        /**
         * Whether the request for whitelisting from battery optimizations shall be shown.
         * If this setting is true or null/not set, the notice shall be shown. Only if this
         * setting is false, the notice shall not be shown.
         */
        const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"

        /**
         * Whether the autostart permission notice shall be shown. If this setting is true
         * or null/not set, the notice shall be shown. Only if this setting is false, the notice
         * shall not be shown.
         *
         * Type: Boolean
         */
        const val HINT_AUTOSTART_PERMISSION = "hint_AutostartPermissions"

        fun isExempted(context: Context) =
            context.getSystemService<PowerManager>()!!.isIgnoringBatteryOptimizations(context.packageName)
    }

    data class UiState(
        val shouldBeExempted: Boolean = true,
        val isExempted: Boolean = false
    )

    var uiState by mutableStateOf(UiState())
        private set

    val hintBatteryOptimizations = settings.getBooleanFlow(HINT_BATTERY_OPTIMIZATIONS)

    val hintAutostartPermission = settings.getBooleanFlow(HINT_AUTOSTART_PERMISSION)

    init {
        viewModelScope.launch {
            broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true).collect {
                checkBatteryOptimizations()
            }
        }
    }

    fun checkBatteryOptimizations() {
        val exempted = isExempted(context)
        uiState = uiState.copy(shouldBeExempted = exempted, isExempted = exempted)

        // if PeopleSync is whitelisted, always show a reminder as soon as it's not whitelisted anymore
        if (exempted)
            settings.remove(HINT_BATTERY_OPTIMIZATIONS)
    }

    fun updateShouldBeExempted(value: Boolean) {
        uiState = uiState.copy(shouldBeExempted = value)
    }

    fun updateHintBatteryOptimizations(value: Boolean) {
        settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, value)
    }

    fun updateHintAutostartPermission(value: Boolean) {
        settings.putBoolean(HINT_AUTOSTART_PERMISSION, value)
    }

}
