/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.repository.AccountRepository
import com.messageconcept.peoplesyncclient.repository.DavCollectionRepository
import com.messageconcept.peoplesyncclient.repository.DavServiceRepository
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.account.InvalidAccountException
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountScreenModel.Factory::class)
class AccountScreenModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountRepository: AccountRepository,
    accountProgressUseCase: AccountProgressUseCase,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext val context: Context,
    private val collectionSelectedUseCase: Lazy<CollectionSelectedUseCase>,
    getBindableHomesetsFromService: GetBindableHomeSetsFromServiceUseCase,
    getServiceCollectionPager: GetServiceCollectionPagerUseCase,
    private val logger: Logger,
    serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager,
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountScreenModel
    }

    /**
     * Only acquire account settings on a worker thread!
     */
    private val accountSettings: AccountSettings? by lazy {
        try {
            accountSettingsFactory.create(account)
        } catch (_: InvalidAccountException) {
            null
        }
    }

    init {
        logger.log(Level.INFO, "Triggering sync for $account")
        sync()
    }

    /** whether the account is invalid and the screen shall be closed */
    val invalidAccount = accountRepository.getAllFlow().map { accounts ->
        !accounts.contains(account)
    }

    /**
     * Whether to show only personal collections.
     */
    private val _showOnlyPersonal = MutableStateFlow(false)
    val showOnlyPersonal = _showOnlyPersonal.asStateFlow()
    private suspend fun reloadShowOnlyPersonal() = withContext(Dispatchers.Default) {
        accountSettings?.let {
            _showOnlyPersonal.value = it.getShowOnlyPersonal()
        }
    }
    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        viewModelScope.launch {
            accountSettings?.setShowOnlyPersonal(showOnlyPersonal)
            reloadShowOnlyPersonal()
        }
    }

    /**
     * Whether the user setting to show only personal collections is locked.
     */
    private var _showOnlyPersonalLocked = MutableStateFlow(false)
    val showOnlyPersonalLocked = _showOnlyPersonalLocked.asStateFlow()
    private suspend fun reloadShowOnlyPersonalLocked() = withContext(Dispatchers.Default) {
        accountSettings?.let {
            _showOnlyPersonalLocked.value = it.getShowOnlyPersonalLocked()
        }
    }

    init {
        viewModelScope.launch {
            reloadShowOnlyPersonal()
            reloadShowOnlyPersonalLocked()
        }
    }

    val cardDavSvc = serviceRepository
        .getCardDavServiceFlow(account.name)
        .stateIn(viewModelScope, initialValue = null, started = SharingStarted.Eagerly)
    private val bindableAddressBookHomesets = getBindableHomesetsFromService(cardDavSvc)
    val canCreateAddressBook = bindableAddressBookHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val cardDavProgress: Flow<AccountProgress> = accountProgressUseCase(
        account = account,
        serviceFlow = cardDavSvc,
        dataTypes = listOf(SyncDataType.CONTACTS)
    )
    val addressBooks = getServiceCollectionPager(cardDavSvc, Collection.TYPE_ADDRESSBOOK, showOnlyPersonal)


    var error by mutableStateOf<String?>(null)
        private set

    fun resetError() { error = null }


    var showNoWebcalApp by mutableStateOf(false)
        private set

    fun noWebcalApp() { showNoWebcalApp = true }
    fun resetShowNoWebcalApp() { showNoWebcalApp = false }


    // actions

    /** Deletes the account from the system (won't touch collections on the server). */
    fun deleteAccount() {
        viewModelScope.launch {
            accountRepository.delete(account.name)
        }
    }

    fun refreshCollections() {
        cardDavSvc.value?.let { svc ->
            RefreshCollectionsWorker.enqueue(context, svc.id)
        }
    }

    /**
     * Renames the [account] to given name.
     *
     * @param newName new account name
     */
    fun renameAccount(newName: String) {
        viewModelScope.launch {
            try {
                accountRepository.rename(account.name, newName)

                // synchronize again
                val newAccount = Account(newName, context.getString(R.string.account_type))
                syncWorkerManager.enqueueOneTimeAllAuthorities(newAccount, manual = true)
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't rename account", e)
                error = e.localizedMessage
            }
        }
    }

    fun setCollectionSync(id: Long, sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setSync(id, sync)
            collectionSelectedUseCase.get().handleWithDelay(id)
        }
    }

    fun sync() {
        syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
    }

}