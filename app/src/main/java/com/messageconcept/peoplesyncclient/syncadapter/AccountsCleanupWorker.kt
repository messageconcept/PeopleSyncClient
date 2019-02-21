/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook.Companion.USER_DATA_MAIN_ACCOUNT_TYPE
import com.messageconcept.peoplesyncclient.util.UpdateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.logging.Level

@HiltWorker
class AccountsCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    val db: AppDatabase
): Worker(appContext, workerParameters) {

    companion object {
        const val NAME = "accounts-cleanup"

        private val mutex = Semaphore(1)
        /**
         * Prevents account cleanup from being run until `unlockAccountsCleanup` is called.
         * Can only be active once at the same time globally (blocking).
         */
        fun lockAccountsCleanup() = mutex.acquire()
        /** Must be called exactly one time after calling `lockAccountsCleanup`. */
        fun unlockAccountsCleanup() = mutex.release()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AccountsCleanupWorker>()
                    .setInitialDelay(15, TimeUnit.SECONDS)   // wait some time before cleaning up accouts
                    .build())
        }
    }

    override fun doWork(): Result {
        lockAccountsCleanup()
        try {
            val accountManager = AccountManager.get(applicationContext)
            UpdateUtils.upgradeAccounts(applicationContext)
            cleanupAccounts(applicationContext, accountManager.accounts)
        } finally {
            unlockAccountsCleanup()
        }
        return Result.success()
    }

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
        val serviceDao = db.serviceDao()
        if (mainAccountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
    }

}