/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient

import com.messageconcept.peoplesyncclient.ui.AccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.OseAccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.intro.IntroFragmentFactory
import com.messageconcept.peoplesyncclient.ui.intro.PermissionsIntroFragment
import com.messageconcept.peoplesyncclient.ui.intro.TasksIntroFragment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class OseFlavorModule {

    //// navigation drawer handler ////

    @Binds
    abstract fun accountsDrawerHandler(handler: OseAccountsDrawerHandler): AccountsDrawerHandler


    //// intro fragments ////

    // WelcomeFragment and BatteryOptimizationsFragment modules are hardcoded there

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class PermissionsIntroFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: PermissionsIntroFragment.Factory): IntroFragmentFactory
    }

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class TasksIntroFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: TasksIntroFragment.Factory): IntroFragmentFactory
    }

}