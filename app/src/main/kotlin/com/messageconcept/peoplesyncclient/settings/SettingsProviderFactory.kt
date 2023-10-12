/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.settings

import android.content.Context

interface SettingsProviderFactory {

    fun getProviders(context: Context, settingsManager: SettingsManager): Iterable<SettingsProvider>

}