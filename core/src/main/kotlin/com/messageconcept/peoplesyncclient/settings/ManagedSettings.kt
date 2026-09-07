/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.annotation.WorkerThread
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.di.qualifier.IoDispatcher
import com.messageconcept.peoplesyncclient.log.AuditLogger
import com.messageconcept.peoplesyncclient.settings.AccountSettings.Companion.KEY_BASE_URL
import com.messageconcept.peoplesyncclient.settings.AccountSettings.Companion.KEY_USERNAME
import com.messageconcept.peoplesyncclient.sync.account.InvalidAccountException
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManagedSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val syncWorkerManager: SyncWorkerManager
) {

    companion object {
        private const val KEY_LOGIN_BASE_URL = "login_base_url"
        private const val KEY_LOGIN_USER_NAME = "login_user_name"
        private const val KEY_LOGIN_PASSWORD = "login_password"
        private const val KEY_ORGANIZATION = "organization"
    }

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var restrictions: Bundle

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            auditLogger.log(Level.INFO, "ManagedSettings: Application restrictions changed")
                            // update cached app restrictions
                            restrictions = restrictionsManager.applicationRestrictions
                            updateAccounts()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    @Inject
    lateinit var accountsettingsFactory: AccountSettings.Factory

    init {
        // cache app restrictions to avoid unnecessary disk access
        restrictions = restrictionsManager.applicationRestrictions
        context.registerReceiver(broadCastReceiver, IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED))
    }

    fun getBaseUrl(): String? {
        return restrictions.getString(KEY_LOGIN_BASE_URL)
    }

    fun getUsername(): String? {
        return restrictions.getString(KEY_LOGIN_USER_NAME)
    }

    fun getPassword(): String? {
        return restrictions.getString(KEY_LOGIN_PASSWORD)
    }

    fun getOrganization(): String? {
        return restrictions.getString(KEY_ORGANIZATION)
    }

    @WorkerThread
    fun updateAccounts() {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(context.getString(R.string.account_type))

        for (account in accounts)
            try {
                val baseUrl = accountManager.getUserData(account, KEY_BASE_URL)

                // we are only interested in managed accounts and only those have the baseUrl
                // attached to their userData
                if (baseUrl != null) {
                    val username = accountManager.getUserData(account, KEY_USERNAME)
                    val password = accountManager.getPassword(account)

                    val managedBaseUrl = getBaseUrl()
                    val managedUsername = getUsername()
                    val managedPassword = getPassword()
                    // If the password has been deleted/unset, do not update existing accounts
                    // and set an empty password as this is guaranteed to lead to synchronization
                    // failures. The alternative would be to delete the account, but this might
                    // be unexpected, so do nothing instead.
                    if (managedPassword.isNullOrEmpty()) {
                        auditLogger.log(Level.INFO, "ManagedSettings: ${account.name}: Managed login password has been deleted. Doing nothing.")
                        return
                    }
                    // check if baseUrl and userName match
                    if (managedBaseUrl == baseUrl && managedUsername == username) {
                        if (managedPassword != password) {
                            auditLogger.log(Level.INFO, "ManagedSettings: ${account.name}: Managed login password changed. Updating account settings and requesting sync.")
                            accountManager.setPassword(account, managedPassword)
                            // Request an explicit sync after we changed the account password.
                            // This should also clear any error notifications.
                            syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
                            auditLogger.log(Level.INFO, "ManagedSettings: ${account.name}: Managed login password updated and sync requested.")
                        } else {
                            // Password is up-to-date
                        }
                    } else {
                        auditLogger.log(
                            Level.INFO,
                            "ManagedSettings: ${account.name}: Skipping password update; " +
                                "baseUrlMatches=${managedBaseUrl == baseUrl}, usernameMatches=${managedUsername == username}"
                        )
                    }
                }
            } catch (e: InvalidAccountException) {
                // account doesn't exist (anymore)
                auditLogger.log(Level.WARNING, "ManagedSettings: ${account.name}: Account no longer exists.", e)
            } catch (e: Exception) {
                auditLogger.log(Level.SEVERE, "ManagedSettings: ${account.name}: Couldn't update managed account.", e)
                throw e
            }
    }

    fun isManaged(): Boolean {
        return !restrictions.getString(KEY_LOGIN_BASE_URL).isNullOrEmpty()
    }
}