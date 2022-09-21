/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import androidx.annotation.AnyThread
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.UpdateUtils
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook.Companion.USER_DATA_MAIN_ACCOUNT_TYPE
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Singleton

class AccountsUpdatedListener private constructor(
    val context: Context
): OnAccountsUpdateListener {

    @Module
    @InstallIn(SingletonComponent::class)
    object AccountsUpdatedListenerModule {
        @Provides
        @Singleton
        fun accountsUpdatedListener(@ApplicationContext context: Context) = AccountsUpdatedListener(context)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountsUpdatedListenerEntryPoint {
        fun appDatabase(): AppDatabase
    }


    fun listen() {
        val accountManager = AccountManager.get(context)
        accountManager.addOnAccountsUpdatedListener(this, null, true)
    }

    /**
     * Called when the main accounts have been updated, including when a main account has been
     * removed. In the latter case, this method fulfills two tasks:
     *
     * 1. Remove related address book accounts.
     * 2. Remove related service entries from the [AppDatabase].
     */
    @AnyThread
    override fun onAccountsUpdated(accounts: Array<out Account>) {
        /* onAccountsUpdated may be called from the main thread, but cleanupAccounts
           requires disk (database) access. So we launch it in a separate thread. */
        CoroutineScope(Dispatchers.Default).launch {
            UpdateUtils.upgradeAccounts(context)
            cleanupAccounts(context, accounts)
        }
    }

    @Synchronized
    private fun cleanupAccounts(context: Context, accounts: Array<out Account>) {
        Logger.log.log(Level.INFO, "Cleaning up accounts. Current accounts:", accounts)

        val mainAccountType = context.getString(R.string.account_type)
        val mainAccountNames = accounts
            .filter { account -> account.type == mainAccountType }
            .map { it.name }

        val addressBookAccountType = context.getString(R.string.account_type_address_book)

        // Determine if we only have old style address book accounts.
        // If so, this most likely means that we weren't able to contact the PeopleSync
        // server yet and sync the address books after an upgrade. In this case, don't delete
        // the old address books yet.
        val accountManager = AccountManager.get(context)
        val newAddressBookAccounts = accounts
                .filter { account -> account.type == addressBookAccountType }
                .filter { account -> accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE) == mainAccountType }
        val oldAddressBookAccounts = accounts
                .filter { account -> account.type == addressBookAccountType }
                .filter { account -> newAddressBookAccounts.contains(account).not() }

        if (newAddressBookAccounts.isEmpty() && oldAddressBookAccounts.isNotEmpty()) {
            Logger.log.info("Found only old address book accounts. Skipping address book accounts and service db cleanup.")
            return
        }
        if (newAddressBookAccounts.isNotEmpty() && oldAddressBookAccounts.isNotEmpty()) {
            Logger.log.info("Found old and new address book accounts. Cleaning up old address book accounts.")
            oldAddressBookAccounts.forEach() { account -> UpdateUtils.delete(context, account) }
        }

        val addressBooks = accounts
            .filter { account -> account.type == addressBookAccountType }
            .filter { account -> oldAddressBookAccounts.contains(account).not() }
            .map { addressBookAccount -> LocalAddressBook(context, addressBookAccount, null) }
        for (addressBook in addressBooks) {
            try {
                if (!mainAccountNames.contains(addressBook.mainAccount.name))
                // the main account for this address book doesn't exist anymore
                    addressBook.delete()
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't delete address book account", e)
            }
        }

        // delete orphaned services in DB
        val db = EntryPointAccessors.fromApplication(context, AccountsUpdatedListenerEntryPoint::class.java).appDatabase()
        val serviceDao = db.serviceDao()
        if (mainAccountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
    }

}