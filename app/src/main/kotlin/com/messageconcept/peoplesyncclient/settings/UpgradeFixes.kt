/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import android.accounts.AccountManager
import android.content.Context
import com.messageconcept.peoplesyncclient.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Logger
import javax.inject.Inject

class UpgradeFixes @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    /**
     * The sync worker classes were moved from the syncadapter/ to the sync/worker/ namespace in v4.4.1-ose,
     * specifically in commit 7f750e22.
     * Existing WorkManager jobs that were referencing the old class names were triggering a ClassNotFoundException,
     * resulting in periodic sync being disabled.
     * To mitigate that, upstream added migration code that is triggered when opening the account in the app.
     * As requiring explicit user interaction is undesirable, this function triggers the migration code for all
     * existing accounts and is called automatically on application startup, e.g. by the periodic sync workers themselves.
     */
    fun enableNewAccountSettings() {
        val accountManager = AccountManager.get(context)

        accountManager.getAccountsByType(context.getString(R.string.account_type)).forEach { account ->
            val version = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION).toInt()
            if (version < 20) {
                logger.info("Triggering account migrations for ${account.name}")
                accountSettingsFactory.create(account)
            }
        }
    }

}