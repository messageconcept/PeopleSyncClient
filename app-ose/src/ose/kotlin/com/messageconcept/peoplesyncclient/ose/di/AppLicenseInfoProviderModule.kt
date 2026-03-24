/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ose.di

import com.messageconcept.peoplesyncclient.ui.about.AboutActivity
import com.messageconcept.peoplesyncclient.ose.ui.about.OpenSourceLicenseInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface AppLicenseInfoProviderModule {
    @Binds
    fun appLicenseInfoProvider(impl: OpenSourceLicenseInfoProvider): AboutActivity.AppLicenseInfoProvider
}
