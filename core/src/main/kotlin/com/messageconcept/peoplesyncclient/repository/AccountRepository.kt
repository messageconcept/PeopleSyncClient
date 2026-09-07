/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import androidx.annotation.WorkerThread
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.accounts.AccountId
import com.messageconcept.peoplesyncclient.accounts.LegacyAccount
import com.messageconcept.peoplesyncclient.db.HomeSet
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.db.ServiceType
import com.messageconcept.peoplesyncclient.di.qualifier.IoDispatcher
import com.messageconcept.peoplesyncclient.log.AuditLogger
import com.messageconcept.peoplesyncclient.resource.LocalAddressBookStore
import com.messageconcept.peoplesyncclient.servicedetection.DavResourceFinder
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Credentials
import com.messageconcept.peoplesyncclient.sync.AutomaticSyncManager
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.account.AccountsCleanupWorker
import com.messageconcept.peoplesyncclient.sync.account.InvalidAccountException
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import at.bitfire.synctools.util.AndroidAccountUtils
import at.bitfire.synctools.vcard.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Repository for managing CardDAV accounts.
 *
 * *Note:* This class is not related to address book accounts, which are managed by
 * [com.messageconcept.peoplesyncclient.resource.LocalAddressBook].
 */
class AccountRepository @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val auditLogger: AuditLogger,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    @ApplicationContext private val context: Context,
    private val collectionRepository: DavCollectionRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val homeSetRepository: DavHomeSetRepository,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: Lazy<SyncWorkerManager>,
) {

    private val accountType = context.getString(R.string.account_type)
    private val accountManager = AccountManager.get(context)

    private val accountRenameFlow = MutableSharedFlow<AccountRename>()
    
    fun getAccountNameFlow(accountId: AccountId): Flow<String> {
        return flow {
            var currentName = getAccountName(accountId)
            emit(currentName)
            
            accountRenameFlow.collect { accountRename -> 
                if (accountRename.oldName == currentName) {
                    currentName = accountRename.newName
                    emit(currentName)
                }
            }
        }
    }
    
    private fun getAccountName(accountId: AccountId): String {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount.name
        }
    }
    
    /**
     * Creates a new account with discovered services and enables periodic syncs with
     * default sync interval times.
     *
     * @param accountName   name of the account
     * @param credentials   server credentials
     * @param config        discovered server capabilities for syncable authorities
     * @param groupMethod   whether CardDAV contact groups are separate VCards or as contact categories
     *
     * @return account if account creation was successful; null otherwise (for instance because an account with this name already exists)
     */
    @WorkerThread
    fun createBlocking(
        accountName: String,
        credentials: Credentials?,
        config: DavResourceFinder.Configuration,
        groupMethod: GroupMethod,
        preconfigurationUrl: String?,
    ): Account? {
        val account = fromName(accountName)

        // create Android account
        val userData = AccountSettings.initialUserData(credentials, preconfigurationUrl)
        auditLogger.log(Level.INFO, "AccountRepository: Creating Android account {0} with initial config {1}", arrayOf(account, userData))

        if (!AndroidAccountUtils.createAccount(context, account, userData, credentials?.password)) {
            auditLogger.log(Level.WARNING, "AccountRepository: Couldn't create system account: $account")
            return null
        }
        auditLogger.log(Level.INFO, "AccountRepository: Created system account: $account")

        // add entries for account to database
        auditLogger.log(Level.INFO, "AccountRepository: Writing account configuration to database: {0}", arrayOf(config))
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                val accountSettings = accountSettingsFactory.create(account)
                accountSettings.setGroupMethod(groupMethod)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            // set up automatic sync (processes inserted services)
            automaticSyncManager.get().updateAutomaticSync(account)

        } catch (e: InvalidAccountException) {
            auditLogger.log(Level.SEVERE, "AccountRepository: Couldn't access account settings for account: $account", e)
            return null
        } catch (e: Exception) {
            auditLogger.log(Level.SEVERE, "AccountRepository: Couldn't configure newly created system account: $account", e)
            throw e
        }
        return account
    }

    suspend fun delete(accountName: String): Boolean = withContext(ioDispatcher) {
        val account = fromName(accountName)
        // remove account directly (bypassing the authenticator, which is our own)
        try {
            auditLogger.log(Level.WARNING, "AccountRepository: Deleting system account: $account")
            val removed = accountManager.removeAccountExplicitly(account)
            auditLogger.log(Level.WARNING, "AccountRepository: System account deletion result: account=$account, removed=$removed")

            // delete address books (= address book accounts)
            serviceRepository.getByAccountAndType(accountName, Service.TYPE_CARDDAV)?.let { service ->
                collectionRepository.getByService(service.id).forEach { collection ->
                    localAddressBookStore.get().deleteByCollectionId(collection.id)
                }
            }

            // delete from database
            serviceRepository.deleteByAccount(accountName)
            auditLogger.log(Level.INFO, "AccountRepository: Deleted local account data: $account")

            true
        } catch (e: Exception) {
            auditLogger.log(Level.SEVERE, "AccountRepository: Couldn't complete account deletion: $account", e)
            false
        }
    }

    fun exists(accountName: String): Boolean =
        if (accountName.isEmpty())
            false
        else
            accountManager
                .getAccountsByType(accountType)
                .any { it.name == accountName }

    fun fromName(accountName: String) =
        Account(accountName, accountType)

    fun getAll(): Array<Account> = accountManager.getAccountsByType(accountType)

    fun getAllFlow() = callbackFlow<Set<Account>> {
        val listener = OnAccountsUpdateListener { accounts ->
            trySend(accounts.filter { it.type == accountType }.toSet())
        }
        withContext(ioDispatcher) {  // causes disk I/O
            accountManager.addOnAccountsUpdatedListener(listener, null, true)
        }

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }

    /**
     * Renames an account.
     *
     * **Note**: It is highly advised to re-sync the account after renaming in order to restore
     * a consistent state.
     *
     * @param oldName current name of the account
     * @param newName new name the account shall be re named to
     *
     * @throws InvalidAccountException if the account does not exist
     * @throws IllegalArgumentException if the new account name already exists
     * @throws Exception (or sub-classes) on other errors
     */
    suspend fun rename(oldName: String, newName: String): Unit = withContext(ioDispatcher) {
        val oldAccount = fromName(oldName)
        val newAccount = fromName(newName)

        // check whether new account name already exists
        if (accountManager.getAccountsByType(context.getString(R.string.account_type)).contains(newAccount))
            throw IllegalArgumentException("Account with name \"$newName\" already exists")

        // rename account
        try {
            auditLogger.log(Level.INFO, "AccountRepository: Renaming system account from $oldAccount to $newAccount")
            /* https://github.com/bitfireAT/davx5/issues/135
            Lock accounts cleanup so that the AccountsCleanupWorker doesn't run while we rename the account
            because this can cause problems when:
            1. The account is renamed.
            2. The AccountsCleanupWorker is called BEFORE the services table is updated.
               → AccountsCleanupWorker removes the "orphaned" services because they belong to the old account which doesn't exist anymore
            3. Now the services would be renamed, but they're not here anymore. */
            AccountsCleanupWorker.lockAccountsCleanup()

            // rename account (also moves AccountSettings)
            val future = accountManager.renameAccount(oldAccount, newName, null, null)

            // wait for operation to complete (blocks calling thread)
            val newNameFromApi: Account = future.result
            if (newNameFromApi.name != newName)
                throw IllegalStateException("renameAccount returned ${newNameFromApi.name} instead of $newName")

            accountRenameFlow.emit(AccountRename(oldAccount.name, newName))
            
            // account renamed, cancel maybe running synchronization of old account
            syncWorkerManager.get().cancelAllWork(oldAccount)

            // disable periodic syncs for old account
            for (dataType in SyncDataType.entries)
                syncWorkerManager.get().disablePeriodic(oldAccount, dataType)

            // update account name references in database
            serviceRepository.renameAccount(oldName, newName)

            try {
                // update address books
                localAddressBookStore.get().updateAccount(oldAccount, newAccount, null)
            } catch (e: Exception) {
                auditLogger.log(Level.WARNING, "AccountRepository: Couldn't rename address book account from $oldAccount to $newAccount", e)
            }

            // update automatic sync
            automaticSyncManager.get().updateAutomaticSync(newAccount)
            auditLogger.log(Level.INFO, "AccountRepository: Renamed system account from $oldAccount to $newAccount")
        } catch (e: Exception) {
            auditLogger.log(Level.SEVERE, "AccountRepository: Couldn't rename system account from $oldAccount to $newAccount", e)
            throw e
        } finally {
            // release AccountsCleanupWorker mutex at the end of this async coroutine
            AccountsCleanupWorker.unlockAccountsCleanup()
        }
    }


    // helpers

    private fun insertService(
        accountName: String,
        @ServiceType type: String,
        info: DavResourceFinder.Configuration.ServiceInfo
    ): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = serviceRepository.insertOrReplaceBlocking(service)

        // insert home sets
        for (homeSet in info.homeSets)
            homeSetRepository.insertOrUpdateByUrlBlocking(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        for (collection in info.collections.values) {
            collectionRepository.insertOrUpdateByUrl(collection.copy(serviceId = serviceId))
        }

        return serviceId
    }

    private data class AccountRename(val oldName: String, val newName: String)
}