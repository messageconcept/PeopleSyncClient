/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ose.di

import com.messageconcept.peoplesyncclient.ui.setup.LoginTypesProvider
import com.messageconcept.peoplesyncclient.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface LoginTypesProviderModule {

    @Binds
    fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider

}