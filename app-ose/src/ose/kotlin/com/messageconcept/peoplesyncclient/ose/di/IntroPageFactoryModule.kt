/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ose.di

import com.messageconcept.peoplesyncclient.ui.intro.IntroPageFactory
import com.messageconcept.peoplesyncclient.ose.ui.intro.OseIntroPageFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface IntroPageFactoryModule {
    @Binds
    fun introPageFactory(impl: OseIntroPageFactory): IntroPageFactory
}
