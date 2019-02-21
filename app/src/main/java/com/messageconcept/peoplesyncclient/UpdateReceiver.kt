/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient

import android.accounts.Account
import android.accounts.AccountManager
import android.content.*
import android.os.Build
import at.bitfire.vcard4android.GroupMethod
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.AppDatabase
import com.messageconcept.peoplesyncclient.model.Credentials
import com.messageconcept.peoplesyncclient.model.Service
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level
import kotlin.concurrent.thread

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log.fine("App got updated")
        val accountManager = AccountManager.get(context)

        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
            val versionStr = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION)

            // Old accounts don't have a version string
            if (versionStr == null) {
                val newName = account.name.removePrefix("PeopleSync ")
                val newAccount = Account(newName, context.getString(R.string.account_type))

                val userName = accountManager.getUserData(account, "user_name")
                val providedURL = accountManager.getUserData(account, "provided_url")
                val password = accountManager.getPassword(account)

                val credentials = Credentials(userName, password)

                val userData = AccountSettings.initialUserData(credentials)
                Logger.log.log(Level.INFO, "Convert Android account with existing config", arrayOf(account, userData))

                if (!accountManager.addAccountExplicitly(newAccount, credentials.password, userData)) {
                    Logger.log.info("Couldn't create account ${account.name}")
                    // TODO: Exit at this point or continue?
                }
                delete(context, account)

                thread {
                    // add entries for account to service DB
                    val db = AppDatabase.getInstance(context)
                    try {
                        val accountSettings = AccountSettings(context, newAccount)

                        val refreshIntent = Intent(context, DavService::class.java)
                        refreshIntent.action = DavService.ACTION_REFRESH_COLLECTIONS

                        val principalURL = "${providedURL}/principals/${userName}/"
                        val service = Service(0, newName, Service.TYPE_CARDDAV, principalURL.toHttpUrlOrNull())

                        val serviceID = db.serviceDao().insertOrReplace(service)

                        // initial CardDAV account settings
                        accountSettings.setGroupMethod(GroupMethod.CATEGORIES)

                        // start CardDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceID)
                        context.startService(refreshIntent)

                        // set default sync interval and enable sync regardless of permissions
                        val addrBookAuthority = context.getString(R.string.address_books_authority)
                        ContentResolver.setIsSyncable(newAccount, addrBookAuthority, 1)
                        accountSettings.setSyncInterval(context.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)

                    } catch (e: InvalidAccountException) {
                        Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
                    }
                }
            }
        }
        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
            val addressbook_url = accountManager.getUserData(account, "addressbook_url")
            val parent = accountManager.getUserData(account, "parent_account_name")

            // Clean up old accounts
            if (parent != null && addressbook_url != null) {
                delete(context, account);
                Logger.log.info("Removing old account ${account.name}")
            }
        }
    }

    fun delete(context: Context, account: Account) {
        val accountManager = AccountManager.get(context)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, null, null, null)
        else
            accountManager.removeAccount(account, null, null)
    }

}