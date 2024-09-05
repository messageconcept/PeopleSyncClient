/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.setup

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.ContentResolver
import android.provider.CalendarContract
import androidx.core.os.CancellationSignal
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.InvalidAccountException
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.db.HomeSet
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.servicedetection.DavResourceFinder
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.syncadapter.AccountUtils
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.logging.Level
import javax.inject.Inject

@HiltViewModel
class LoginModel @Inject constructor(
    val context: Application,
    val db: AppDatabase,
    private val resourceFinderFactory: DavResourceFinder.Factory,
    val settingsManager: SettingsManager
): ViewModel() {

    val forcedGroupMethod = settingsManager.getStringFlow(AccountSettings.KEY_CONTACT_GROUP_METHOD).map { methodName ->
        methodName?.let {
            try {
                GroupMethod.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }


    val foundConfig = MutableLiveData<DavResourceFinder.Configuration>()

    fun detectResources(loginInfo: LoginInfo, cancellationSignal: CancellationSignal) {
        foundConfig.value = null

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val configuration = runInterruptible {
                    resourceFinderFactory.create(loginInfo.baseUri!!, loginInfo.credentials).use { finder ->
                        finder.findInitialConfiguration()
                    }
                }
                foundConfig.postValue(configuration)
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Exception during service detection", e)
            }
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }


    fun accountExists(accountName: String): LiveData<Boolean> = liveData {
        val accountType = context.getString(R.string.account_type)
        val exists =
            if (accountName.isEmpty())
                false
            else
                AccountManager.get(context)
                    .getAccountsByType(accountType)
                    .contains(Account(accountName, accountType))
        emit(exists)
    }


    interface CreateAccountResult {
        class Success(val account: Account): CreateAccountResult
        class Error(val exception: Exception?): CreateAccountResult
    }

    val createAccountResult = MutableLiveData<CreateAccountResult>()

    fun createAccount(
        credentials: Credentials?,
        foundConfig: DavResourceFinder.Configuration,
        name: String,
        groupMethod: GroupMethod
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (createAccount(name, credentials, foundConfig, groupMethod))
                    createAccountResult.postValue(CreateAccountResult.Success(Account(name, context.getString(R.string.account_type))))
                else
                    createAccountResult.postValue(CreateAccountResult.Error(null))
            } catch (e: Exception) {
                createAccountResult.postValue(CreateAccountResult.Error(e))
            }
        }
    }

    /**
     * Creates a new main account with discovered services and enables periodic syncs with
     * default sync interval times.
     *
     * @param name Name of the account
     * @param credentials Server credentials
     * @param config Discovered server capabilities for syncable authorities
     * @param groupMethod Whether CardDAV contact groups are separate VCards or as contact categories
     * @return *true* if account creation was succesful; *false* otherwise (for instance because an account with this name already exists)
     */
    fun createAccount(name: String, credentials: Credentials?, config: DavResourceFinder.Configuration, groupMethod: GroupMethod): Boolean {
        val account = Account(name, context.getString(R.string.account_type))

        // create Android account
        val userData = AccountSettings.initialUserData(credentials)
        Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

        if (!AccountUtils.createAccount(context, account, userData, credentials?.password))
            return false

        // add entries for account to service DB
        Logger.log.log(Level.INFO, "Writing account configuration to database", config)
        try {
            val accountSettings = AccountSettings(context, account)
            val defaultSyncInterval = settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)

            // Configure CardDAV service
            val addrBookAuthority = context.getString(R.string.address_books_authority)
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(name, Service.TYPE_CARDDAV, config.cardDAV)

                // initial CardDAV account settings
                accountSettings.setGroupMethod(groupMethod)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)

                // set default sync interval and enable sync regardless of permissions
                ContentResolver.setIsSyncable(account, addrBookAuthority, 1)
                accountSettings.setSyncInterval(addrBookAuthority, defaultSyncInterval)
            } else
                ContentResolver.setIsSyncable(account, addrBookAuthority, 0)

        } catch(e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
            return false
        }
        return true
    }

    private fun insertService(accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = db.serviceDao().insertOrReplace(service)

        // insert home sets
        val homeSetDao = db.homeSetDao()
        for (homeSet in info.homeSets)
            homeSetDao.insertOrUpdateByUrl(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        val collectionDao = db.collectionDao()
        for (collection in info.collections.values) {
            collection.serviceId = serviceId
            collectionDao.insertOrUpdateByUrl(collection)
        }

        return serviceId
    }

}