/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.security.KeyChain
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Task
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.syncadapter.OneTimeSyncWorker
import com.messageconcept.peoplesyncclient.syncadapter.Syncer
import com.messageconcept.peoplesyncclient.ui.AppTheme
import com.messageconcept.peoplesyncclient.ui.composable.ActionCard
import com.messageconcept.peoplesyncclient.ui.composable.EditTextInputDialog
import com.messageconcept.peoplesyncclient.ui.composable.MultipleChoiceInputDialog
import com.messageconcept.peoplesyncclient.ui.composable.Setting
import com.messageconcept.peoplesyncclient.ui.composable.SettingsHeader
import com.messageconcept.peoplesyncclient.ui.composable.SwitchSetting
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import javax.inject.Inject

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        const val ACCOUNT_SETTINGS_HELP_URL = "https://manual.davx5.com/settings.html#account-settings"
    }

    private val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = account.name

        setContent {
            AppTheme {
                val uriHandler = LocalUriHandler.current

                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
                                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
                                }
                            },
                            title = { Text(account.name) },
                            actions = {
                                IconButton(onClick = {
                                    uriHandler.openUri(ACCOUNT_SETTINGS_HELP_URL)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Box(Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())) {
                        AccountSettings_FromModel(
                            snackbarHostState = snackbarHostState,
                            model = model
                        )
                    }
                }
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }


    @Composable
    fun AccountSettings_FromModel(
        snackbarHostState: SnackbarHostState,
        model: Model
    ) {
        Column(Modifier.padding(8.dp)) {
            SyncSettings(
                contactsSyncInterval = model.syncIntervalContacts.observeAsState().value,
                onUpdateContactsSyncInterval = { model.updateSyncInterval(getString(R.string.address_books_authority), it) },
                syncOnlyOnWifi = model.syncWifiOnly.observeAsState(false).value,
                onUpdateSyncOnlyOnWifi = { model.updateSyncWifiOnly(it) },
                onlyOnSsids = model.syncWifiOnlySSIDs.observeAsState().value,
                onUpdateOnlyOnSsids = { model.updateSyncWifiOnlySSIDs(it) },
                ignoreVpns = model.ignoreVpns.observeAsState(false).value,
                onUpdateIgnoreVpns = { model.updateIgnoreVpns(it) }
            )

            val credentials by model.credentials.observeAsState()
            credentials?.let {
                AuthenticationSettings(
                    snackbarHostState = snackbarHostState,
                    credentials = it,
                    onUpdateCredentials = { model.updateCredentials(it) }
                )
            }

            CardDavSettings(
                contactGroupMethod = model.contactGroupMethod.observeAsState(GroupMethod.GROUP_VCARDS).value,
                onUpdateContactGroupMethod = { model.updateContactGroupMethod(it) }
            )
        }
    }

    @Composable
    fun SyncSettings(
        contactsSyncInterval: Long?,
        onUpdateContactsSyncInterval: ((Long) -> Unit) = {},
        syncOnlyOnWifi: Boolean,
        onUpdateSyncOnlyOnWifi: (Boolean) -> Unit = {},
        onlyOnSsids: List<String>?,
        onUpdateOnlyOnSsids: (List<String>) -> Unit = {},
        ignoreVpns: Boolean,
        onUpdateIgnoreVpns: (Boolean) -> Unit = {}
    ) {
        val context = LocalContext.current

        Column {
            SettingsHeader(false) {
                Text(stringResource(R.string.settings_sync))
            }

            if (contactsSyncInterval != null)
                SyncIntervalSetting(
                    icon = Icons.Default.Contacts,
                    name = R.string.settings_sync_interval_contacts,
                    syncInterval = contactsSyncInterval,
                    onUpdateSyncInterval = onUpdateContactsSyncInterval
                )

            SwitchSetting(
                icon = Icons.Default.Wifi,
                name = stringResource(R.string.settings_sync_wifi_only),
                summaryOn = stringResource(R.string.settings_sync_wifi_only_on),
                summaryOff = stringResource(R.string.settings_sync_wifi_only_off),
                checked = syncOnlyOnWifi,
                onCheckedChange = onUpdateSyncOnlyOnWifi
            )

            var showWifiOnlySsidsDialog by remember { mutableStateOf(false) }
            Setting(
                icon = null,
                name = stringResource(R.string.settings_sync_wifi_only_ssids),
                enabled = syncOnlyOnWifi,
                summary =
                    if (onlyOnSsids != null)
                        stringResource(R.string.settings_sync_wifi_only_ssids_on, onlyOnSsids.joinToString(", "))
                    else
                        stringResource(R.string.settings_sync_wifi_only_ssids_off),
                onClick = {
                    showWifiOnlySsidsDialog = true
                }
            )
            if (showWifiOnlySsidsDialog)
                EditTextInputDialog(
                    title = stringResource(R.string.settings_sync_wifi_only_ssids_message),
                    initialValue = onlyOnSsids?.joinToString(", ") ?: "",
                    onValueEntered = { newValue ->
                        val newSsids = newValue.split(',')
                            .map { it.trim() }
                            .distinct()
                        onUpdateOnlyOnSsids(newSsids)
                        showWifiOnlySsidsDialog = false
                    },
                    onDismiss = { showWifiOnlySsidsDialog = false }
                )

            // TODO make canAccessWifiSsid live-capable
            val canAccessWifiSsid =
                if (LocalInspectionMode.current)
                    false
                else
                    PermissionUtils.canAccessWifiSsid(context)
            if (onlyOnSsids != null && !canAccessWifiSsid)
                ActionCard(
                    icon = Icons.Default.SyncProblem,
                    actionText = stringResource(R.string.settings_sync_wifi_only_ssids_permissions_action),
                    onAction = {
                        val intent = Intent(context, WifiPermissionsActivity::class.java)
                        intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, account)
                        startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.settings_sync_wifi_only_ssids_permissions_required))
                }

            SwitchSetting(
                icon = null,
                name = stringResource(R.string.settings_ignore_vpns),
                summaryOn = stringResource(R.string.settings_ignore_vpns_on),
                summaryOff = stringResource(R.string.settings_ignore_vpns_off),
                checked = ignoreVpns,
                onCheckedChange = onUpdateIgnoreVpns
            )
        }
    }

    @Composable
    fun SyncIntervalSetting(
        icon: ImageVector,
        @StringRes name: Int,
        syncInterval: Long,
        onUpdateSyncInterval: (Long) -> Unit
    ) {
        var showSyncIntervalDialog by remember { mutableStateOf(false) }
        Setting(
            icon = icon,
            name = stringResource(name),
            summary =
                if (syncInterval == AccountSettings.SYNC_INTERVAL_MANUALLY)
                    stringResource(R.string.settings_sync_summary_manually)
                else
                    stringResource(R.string.settings_sync_summary_periodically, syncInterval / 60),
            onClick = {
                showSyncIntervalDialog = true
            }
        )
        if (showSyncIntervalDialog) {
            val syncIntervalNames = stringArrayResource(R.array.settings_sync_interval_names)
            val syncIntervalSeconds = stringArrayResource(R.array.settings_sync_interval_seconds)
            MultipleChoiceInputDialog(
                title = stringResource(name),
                namesAndValues = syncIntervalNames.zip(syncIntervalSeconds),
                initialValue = syncInterval.toString(),
                onValueSelected = { newValue ->
                    try {
                        val seconds = newValue.toLong()
                        onUpdateSyncInterval(seconds)
                    } catch (_: NumberFormatException) {
                    }
                    showSyncIntervalDialog = false
                },
                onDismiss = {
                    showSyncIntervalDialog = false
                }
            )
        }
    }

    @Composable
    @Preview
    fun SyncSettings_Preview() {
        SyncSettings(
            contactsSyncInterval = 60*60,
            syncOnlyOnWifi = false,
            onlyOnSsids = listOf("SSID1", "SSID2"),
            ignoreVpns = true
        )
    }

    @Composable
    fun AuthenticationSettings(
        credentials: Credentials,
        snackbarHostState: SnackbarHostState = SnackbarHostState(),
        onUpdateCredentials: (Credentials) -> Unit = {}
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Column(Modifier.padding(8.dp)) {
            SettingsHeader(false) {
                Text(stringResource(R.string.settings_authentication))
            }

            if (credentials.authState != null) {       // OAuth
                Setting(
                    icon = Icons.Default.Password,
                    name = stringResource(R.string.settings_oauth),
                    summary = stringResource(R.string.settings_oauth_summary),
                    onClick = {
                        // GoogleLoginFragment replacement
                    }
                )

            } else { // username/password
                if (credentials.username != null) {
                    var showUsernameDialog by remember { mutableStateOf(false) }
                    Setting(
                        icon = Icons.Default.AccountCircle,
                        name = stringResource(R.string.settings_username),
                        summary = credentials.username,
                        enabled = !model.settings.containsKey(AccountSettings.KEY_LOGIN_USER_NAME),
                        onClick = {
                            showUsernameDialog = true
                        }
                    )
                    if (showUsernameDialog)
                        EditTextInputDialog(
                            title = stringResource(R.string.settings_username),
                            initialValue = credentials.username,
                            onValueEntered = { newValue ->
                                onUpdateCredentials(credentials.copy(username = newValue))
                            },
                            onDismiss = { showUsernameDialog = false }
                        )
                }

                if (credentials.password != null) {
                    var showPasswordDialog by remember { mutableStateOf(false) }
                    Setting(
                        icon = Icons.Default.Password,
                        name = stringResource(R.string.settings_password),
                        summary = stringResource(R.string.settings_password_summary),
                        enabled = !model.settings.containsKey(AccountSettings.KEY_LOGIN_PASSWORD),
                        onClick = {
                            showPasswordDialog = true
                        }
                    )
                    if (showPasswordDialog)
                        EditTextInputDialog(
                            title = stringResource(R.string.settings_password),
                            inputLabel = stringResource(R.string.settings_new_password),
                            initialValue = null, // Do not show the existing password
                            passwordField = true,
                            onValueEntered = { newValue ->
                                onUpdateCredentials(credentials.copy(password = newValue))
                            },
                            onDismiss = { showPasswordDialog = false }
                        )
                }
            }
        }
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_ClientCertificate() {
        AuthenticationSettings(
            credentials = Credentials(certificateAlias = "alias")
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_OAuth() {
        AuthenticationSettings(
            credentials = Credentials(authState = AuthState())
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_UsernamePassword() {
        AuthenticationSettings(
            credentials = Credentials(username = "user", password = "password")
        )
    }

    @Composable
    @Preview
    fun AuthenticationSettings_Preview_UsernamePassword_ClientCertificate() {
        AuthenticationSettings(
            credentials = Credentials(username = "user", password = "password", certificateAlias = "alias")
        )
    }



    @Composable
    fun CardDavSettings(
        contactGroupMethod: GroupMethod,
        onUpdateContactGroupMethod: (GroupMethod) -> Unit = {}
    ) {
        Column {
            SettingsHeader {
                Text(stringResource(R.string.settings_carddav))
            }

            val groupMethodNames = stringArrayResource(R.array.settings_contact_group_method_entries)
            val groupMethodValues = stringArrayResource(R.array.settings_contact_group_method_values)
            var showGroupMethodDialog by remember { mutableStateOf(false) }
            Setting(
                icon = Icons.Default.Contacts,
                name = stringResource(R.string.settings_contact_group_method),
                summary = groupMethodNames[groupMethodValues.indexOf(contactGroupMethod.name)],
                onClick = {
                    showGroupMethodDialog = true
                }
            )
            if (showGroupMethodDialog)
                MultipleChoiceInputDialog(
                    title = stringResource(R.string.settings_contact_group_method),
                    namesAndValues = groupMethodNames.zip(groupMethodValues),
                    initialValue = contactGroupMethod.name,
                    onValueSelected = { newValue ->
                        onUpdateContactGroupMethod(GroupMethod.valueOf(newValue))
                        showGroupMethodDialog = false
                    },
                    onDismiss = { showGroupMethodDialog = false }
                )
        }
    }

    @Composable
    @Preview
    fun CardDavSettings_Preview() {
        CardDavSettings(
            contactGroupMethod = GroupMethod.GROUP_VCARDS
        )
    }


    class Model @AssistedInject constructor(
        val context: Application,
        val settings: SettingsManager,
        @Assisted val account: Account
    ): ViewModel(), SettingsManager.OnChangeListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        private var accountSettings: AccountSettings? = null

        // settings
        val syncIntervalContacts = MutableLiveData<Long>()

        val syncWifiOnly = MutableLiveData<Boolean>()
        val syncWifiOnlySSIDs = MutableLiveData<List<String>>()
        val ignoreVpns = MutableLiveData<Boolean>()

        val credentials = MutableLiveData<Credentials>()

        val contactGroupMethod = MutableLiveData<GroupMethod>()


        init {
            accountSettings = AccountSettings(context, account)

            settings.addOnChangeListener(this)

            reload()
        }

        override fun onCleared() {
            super.onCleared()
            settings.removeOnChangeListener(this)
        }

        override fun onSettingsChanged() {
            Logger.log.info("Settings changed")
            reload()
        }

        fun reload() {
            val accountSettings = accountSettings ?: return

            syncIntervalContacts.postValue(
                accountSettings.getSyncInterval(context.getString(R.string.address_books_authority))
            )

            syncWifiOnly.postValue(accountSettings.getSyncWifiOnly())
            syncWifiOnlySSIDs.postValue(accountSettings.getSyncWifiOnlySSIDs())
            ignoreVpns.postValue(accountSettings.getIgnoreVpns())

            credentials.postValue(accountSettings.credentials())

            contactGroupMethod.postValue(accountSettings.getGroupMethod())
        }


        fun updateSyncInterval(authority: String, syncInterval: Long) {
            CoroutineScope(Dispatchers.Default).launch {
                accountSettings?.setSyncInterval(authority, syncInterval)
                reload()
            }
        }

        fun updateSyncWifiOnly(wifiOnly: Boolean) {
            accountSettings?.setSyncWiFiOnly(wifiOnly)
            reload()
        }

        fun updateSyncWifiOnlySSIDs(ssids: List<String>?) {
            accountSettings?.setSyncWifiOnlySSIDs(ssids)
            reload()
        }

        fun updateIgnoreVpns(ignoreVpns: Boolean) {
            accountSettings?.setIgnoreVpns(ignoreVpns)
            reload()
        }

        fun updateCredentials(credentials: Credentials) {
            accountSettings?.credentials(credentials)
            reload()
        }

        fun updateContactGroupMethod(groupMethod: GroupMethod) {
            accountSettings?.setGroupMethod(groupMethod)
            reload()

            resync(
                authority = context.getString(R.string.address_books_authority),
                fullResync = true
            )
        }

        /**
         * Initiates re-synchronization for given authority.
         *
         * @param authority authority to re-sync
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
         */
        private fun resync(authority: String, fullResync: Boolean) {
            val resync =
                if (fullResync)
                    OneTimeSyncWorker.FULL_RESYNC
                else
                    OneTimeSyncWorker.RESYNC
            OneTimeSyncWorker.enqueue(context, account, authority, resync = resync)
        }

    }

}