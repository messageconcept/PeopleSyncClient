/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.messageconcept.peoplesyncclient.InvalidAccountException
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import com.messageconcept.peoplesyncclient.ui.NotificationUtils.notifyIfPossible
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Utility methods related to synchronization management (authorities, workers etc.)
 */
object SyncUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncUtilsEntryPoint {
        fun appDatabase(): AppDatabase
        fun settingsManager(): SettingsManager
    }

    /**
     * Returns a list of all available sync authorities:
     *
     *   1. calendar authority
     *   2. contacts authority (only if [withContacts] is *true* - mostly we don't want it included)
     *   3. address books authority
     *   4. tasks authority/ies (if available, when tasks managing app(s) installed)
     *
     * Checking the availability of authorities may be relatively expensive, so the
     * result should be cached for the current operation.
     *
     * @param withContacts whether to add contacts authority
     * @return list of available sync authorities for main accounts
     */
    fun syncAuthorities(context: Context, withContacts: Boolean = false): List<String> {
        val result = mutableListOf(
            context.getString(R.string.address_books_authority)
        )
        if (withContacts)
            result.add(ContactsContract.AUTHORITY)
        return result
    }

}