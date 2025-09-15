/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.cert4android.CustomCertStore
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.di.IoDispatcher
import com.messageconcept.peoplesyncclient.repository.PreferenceRepository
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.ui.intro.BatteryOptimizationsPageModel
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import com.messageconcept.peoplesyncclient.util.broadcastReceiverFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val preferences: PreferenceRepository,
    private val settings: SettingsManager,
) : ViewModel() {


    // debugging

    private val powerManager = context.getSystemService<PowerManager>()!!
    val batterySavingExempted =
        broadcastReceiverFlow(context, IntentFilter(PermissionUtils.ACTION_POWER_SAVE_WHITELIST_CHANGED), immediate = true)
            .map { powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun verboseLogging() = preferences.logToFileFlow()
    fun updateVerboseLogging(verbose: Boolean) {
        preferences.logToFile(verbose)
    }


    // connection

    fun proxyType() = settings.getIntFlow(Settings.PROXY_TYPE)
    fun updateProxyType(type: Int) {
        settings.putInt(Settings.PROXY_TYPE, type)
    }

    fun proxyHostName() = settings.getStringFlow(Settings.PROXY_HOST)
    fun updateProxyHostName(host: String) {
        settings.putString(Settings.PROXY_HOST, host)
    }

    fun proxyPort() = settings.getIntFlow(Settings.PROXY_PORT)
    fun updateProxyPort(port: Int) {
        settings.putInt(Settings.PROXY_PORT, port)
    }


    // security

    fun distrustSystemCertificates() = settings.getBooleanFlow(Settings.DISTRUST_SYSTEM_CERTIFICATES)
    fun updateDistrustSystemCertificates(distrust: Boolean) {
        settings.putBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES, distrust)
    }

    fun resetCertificates() {
        CustomCertStore.getInstance(context).clearUserDecisions()
    }


    // user interface

    fun theme() = settings.getIntFlow(Settings.PREFERRED_THEME)
    fun updateTheme(theme: Int) {
        settings.putInt(Settings.PREFERRED_THEME, theme)
        UiUtils.updateTheme(context)
    }

    fun resetHints() {
        settings.remove(BatteryOptimizationsPageModel.HINT_BATTERY_OPTIMIZATIONS)
        settings.remove(BatteryOptimizationsPageModel.HINT_AUTOSTART_PERMISSION)
    }

}