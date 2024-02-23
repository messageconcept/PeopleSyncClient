/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient

import com.messageconcept.peoplesyncclient.ui.AboutActivity
import com.messageconcept.peoplesyncclient.ui.AccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.OpenSourceLicenseInfoProvider
import com.messageconcept.peoplesyncclient.ui.OseAccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.intro.IntroFragmentFactory
import com.messageconcept.peoplesyncclient.ui.intro.PermissionsIntroFragment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet

interface OseFlavorModules {

    @Module
    @InstallIn(ActivityComponent::class)
    interface ForActivities {
        @Binds
        fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler

        @Binds
        fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider
    }


    //// intro fragments ////

    @Module
    @InstallIn(ActivityComponent::class)
    interface PermissionsIntroFragmentModule {
        @Binds @IntoSet
        fun getFactory(factory: PermissionsIntroFragment.Factory): IntroFragmentFactory
    }

}