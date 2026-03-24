/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.di

import com.messageconcept.peoplesyncclient.startup.CrashHandlerSetup
import com.messageconcept.peoplesyncclient.startup.StartupPlugin
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface StartupPluginsModule {

    @Binds
    @IntoSet
    fun crashHandlerSetup(impl: CrashHandlerSetup): StartupPlugin

}
