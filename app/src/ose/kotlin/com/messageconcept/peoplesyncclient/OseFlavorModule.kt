/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import com.messageconcept.peoplesyncclient.ui.AboutActivity
import com.messageconcept.peoplesyncclient.ui.AccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.OpenSourceLicenseInfoProvider
import com.messageconcept.peoplesyncclient.ui.OseAccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.intro.BatteryOptimizationsPage
import com.messageconcept.peoplesyncclient.ui.intro.IntroPage
import com.messageconcept.peoplesyncclient.ui.intro.IntroPageFactory
import com.messageconcept.peoplesyncclient.ui.setup.LoginTypesProvider
import com.messageconcept.peoplesyncclient.ui.setup.StandardLoginTypesProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

interface OseFlavorModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider

        @Binds
        fun loginTypesProvider(impl: StandardLoginTypesProvider): LoginTypesProvider
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Global {
        @Binds
        fun introPageFactory(impl: OseIntroPageFactory): IntroPageFactory
    }


    //// intro pages ////

    @Module
    @InstallIn(SingletonComponent::class)
    interface IntroPagesModule {
        @Provides
        @IntoSet
        fun introPage(): IntroPage = BatteryOptimizationsPage()
    }

}