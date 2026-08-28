/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.accounts.AccountId
import com.messageconcept.peoplesyncclient.accounts.toAndroidAccount
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.di.qualifier.IoDispatcher
import com.messageconcept.peoplesyncclient.network.OAuthIntegration
import com.messageconcept.peoplesyncclient.repository.AccountRepository
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Credentials
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.sync.ResyncType
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountSettingsViewModel.Factory::class)
class AccountSettingsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val oAuthIntegration: OAuthIntegration,
    private val settings: SettingsManager,
    private val syncWorkerManager: SyncWorkerManager,
    private val managedSettings: ManagedSettings,
): ViewModel(), SettingsManager.OnChangeListener {

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): AccountSettingsViewModel
    }

    // settings
    data class UiState(
        val accountName: String = "",
        val status: String? = null,

        val hasContactsSync: Boolean = false,
        val syncIntervalContacts: Long? = null,

        val syncWifiOnly: Boolean = false,
        val syncWifiOnlySSIDs: List<String>? = null,
        val ignoreVpns: Boolean = false,

        val credentials: Credentials = Credentials(),
        val allowCredentialsChange: Boolean = true,

        val contactGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,

        val allowUsernameChange: Boolean = true,
        val allowPasswordChange: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val serviceDao = db.serviceDao()

    /**
     * Only acquire account settings on a worker thread!
     */
    private val accountSettings by lazy { accountSettingsFactory.create(accountId.toAndroidAccount()) }


    init {
        settings.addOnChangeListener(this)
        viewModelScope.launch {
            reload()
            
            accountRepository.getAccountNameFlow(accountId).collect { accountName ->
                _uiState.update { 
                    it.copy(accountName = accountName)
                }
            }
        }
    }

    override fun onCleared() {
        authService.dispose()
        settings.removeOnChangeListener(this)
    }

    override fun onSettingsChanged() {
        viewModelScope.launch {
            reload()
        }
    }

    private suspend fun reload() = withContext(ioDispatcher) {
        val hasContactsSync = serviceDao.getByAccountAndType(accountId, Service.TYPE_CARDDAV) != null

        _uiState.update { 
            it.copy(
                status = null,

                hasContactsSync = hasContactsSync,
                syncIntervalContacts = accountSettings.getSyncInterval(SyncDataType.CONTACTS),

                syncWifiOnly = accountSettings.getSyncWifiOnly(),
                syncWifiOnlySSIDs = accountSettings.getSyncWifiOnlySSIDs(),
                ignoreVpns = accountSettings.getIgnoreVpns(),

                credentials = accountSettings.credentials(),
                allowCredentialsChange = accountSettings.changingCredentialsAllowed(),

                contactGroupMethod = accountSettings.getGroupMethod(),

                allowUsernameChange = managedSettings.getUsername().isNullOrEmpty(),
                allowPasswordChange = managedSettings.getPassword().isNullOrEmpty(),
            )
        }
    }


    fun updateContactsSyncInterval(syncInterval: Long) {
        CoroutineScope(ioDispatcher).launch {
            accountSettings.setSyncInterval(SyncDataType.CONTACTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) = CoroutineScope(ioDispatcher).launch {
        accountSettings.setSyncWiFiOnly(wifiOnly)
        reload()
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) = CoroutineScope(ioDispatcher).launch {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
        reload()
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) = CoroutineScope(ioDispatcher).launch {
        accountSettings.setIgnoreVpns(ignoreVpns)
        reload()
    }


    fun authorizationContract() = OAuthIntegration.AuthorizationContract(authService)

    fun newAuthorizationRequest(): AuthorizationRequest? =
        accountSettings.credentials().authState?.lastAuthorizationResponse?.request

    fun authenticate(authResponse: AuthorizationResponse) {
        CoroutineScope(ioDispatcher).launch {
            try {
                // save new credentials
                val authState = oAuthIntegration.authenticate(authService, authResponse)
                accountSettings.updateAuthState(authState)

                _uiState.update {
                    it.copy(status = context.getString(R.string.settings_reauthorize_oauth_success))
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Authentication failed", e)
                _uiState.update {
                    it.copy(status = e.localizedMessage)
                }
            }
        }
    }

    fun authCodeFailed() {
        _uiState.update {
            it.copy(status = context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
        }
    }

    fun updateCredentials(credentials: Credentials) = CoroutineScope(ioDispatcher).launch {
        accountSettings.credentials(credentials)
        reload()
    }

    fun updateContactGroupMethod(groupMethod: GroupMethod) = CoroutineScope(ioDispatcher).launch {
        accountSettings.setGroupMethod(groupMethod)
        reload()

        resync(SyncDataType.CONTACTS, ResyncType.RESYNC_ENTRIES)
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param dataType  type of data to synchronize
     * @param resync    whether only the list of entries (resync) or also all entries
     *                  themselves (full resync) shall be downloaded again
     */
    private fun resync(dataType: SyncDataType, resync: ResyncType) {
        syncWorkerManager.enqueueOneTime(accountId.toAndroidAccount(), dataType = dataType, resync = resync)
    }

}