/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.settings.migration

import android.accounts.Account
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlin.use

/**
 * Task synchronization now handles alarms, categories, relations and unknown properties.
 * Setting task ETags to null will cause them to be downloaded (and parsed) again.
 *
 * Also update the allowed reminder types for calendars.
 */
class AccountSettingsMigration10 @Inject constructor(
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // nothing to do
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(10)
        abstract fun provide(impl: AccountSettingsMigration10): AccountSettingsMigration
    }

}