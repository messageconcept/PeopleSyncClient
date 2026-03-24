/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.di

import com.messageconcept.peoplesyncclient.settings.DefaultsProvider
import com.messageconcept.peoplesyncclient.settings.SettingsProvider
import com.messageconcept.peoplesyncclient.settings.SharedPreferencesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreSettingsProvidersModule {

    // sorted by descending priority (provides with higher priority are queried first)

    @Binds
    @IntoMap
    @IntKey(/* priority */ 10)
    abstract fun sharedPreferencesProvider(impl: SharedPreferencesProvider): SettingsProvider

    @Binds
    @IntoMap
    @IntKey(/* priority */ 0)
    abstract fun defaultsProvider(impl: DefaultsProvider): SettingsProvider

}
