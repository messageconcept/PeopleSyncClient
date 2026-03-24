/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.settings.migration

import android.accounts.Account
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use

/**
 * Store event URLs as URL (extended property) instead of unknown property. At the same time,
 * convert legacy unknown properties to the current format.
 */
class AccountSettingsMigration12 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        // nothing to do
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(12)
        abstract fun provide(impl: AccountSettingsMigration12): AccountSettingsMigration
    }

}