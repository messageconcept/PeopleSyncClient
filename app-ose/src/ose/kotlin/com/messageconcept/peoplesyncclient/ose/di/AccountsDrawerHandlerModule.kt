/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ose.di

import com.messageconcept.peoplesyncclient.ui.AccountsDrawerHandler
import com.messageconcept.peoplesyncclient.ui.OseAccountsDrawerHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface AccountsDrawerHandlerModule {
    @Binds
    fun accountsDrawerHandler(impl: OseAccountsDrawerHandler): AccountsDrawerHandler
}
