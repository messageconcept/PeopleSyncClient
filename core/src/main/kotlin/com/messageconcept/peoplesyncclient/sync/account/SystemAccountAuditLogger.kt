/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.di.qualifier.IoDispatcher
import com.messageconcept.peoplesyncclient.log.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

/** Audits changes to PeopleSync system accounts reported by Android's AccountManager. */
@Singleton
class SystemAccountAuditLogger @Inject constructor(
    @ApplicationContext context: Context,
    private val auditLogger: AuditLogger,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) {

    private val accountManager = AccountManager.get(context)
    private val accountTypes = setOf(
        context.getString(R.string.account_type),
        context.getString(R.string.account_type_address_book)
    )
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var started = false

    @Synchronized
    fun start() {
        if (started)
            return
        started = true

        scope.launch {
            var previousAccounts: Set<Account>? = null
            callbackFlow<Set<Account>> {
                val listener = OnAccountsUpdateListener { accounts ->
                    trySend(accounts.filter { it.type in accountTypes }.toSet())
                }
                accountManager.addOnAccountsUpdatedListener(listener, null, true)
                awaitClose { accountManager.removeOnAccountsUpdatedListener(listener) }
            }
                .catch { e ->
                    auditLogger.log(Level.SEVERE, "SystemAccountAuditLogger: Couldn't observe system accounts", e)
                }
                .collect { currentAccounts ->
                    val previous = previousAccounts
                    if (previous != null) {
                        for (account in previous - currentAccounts)
                            auditLogger.log(Level.WARNING, "SystemAccountAuditLogger: System account removed: $account")
                        for (account in currentAccounts - previous)
                            auditLogger.log(Level.INFO, "SystemAccountAuditLogger: System account added: $account")
                    }
                    previousAccounts = currentAccounts
                }
        }
    }

}
