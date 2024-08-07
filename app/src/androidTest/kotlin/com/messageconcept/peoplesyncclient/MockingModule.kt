/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import android.content.Context
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.spyk
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [ SingletonComponent::class ],
    replaces = [
        SettingsManager.SettingsManagerModule::class
    ]
)
class MockingModule {

    @Provides
    @Singleton
    fun spykSettingsManager(@ApplicationContext context: Context): SettingsManager =
        spyk(SettingsManager(context))

}