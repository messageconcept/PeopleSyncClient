/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package com.messageconcept.peoplesyncclient.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.*
import android.os.Bundle
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import com.messageconcept.peoplesyncclient.InvalidAccountException
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.syncadapter.PeriodicSyncWorker
import com.messageconcept.peoplesyncclient.syncadapter.SyncUtils
import com.messageconcept.peoplesyncclient.syncadapter.SyncWorker
import com.messageconcept.peoplesyncclient.util.setAndVerifyUserData
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AuthState
import org.apache.commons.lang3.StringUtils
import java.util.logging.Level

/**
 * Manages settings of an account.
 *
 * @param context       Required to access account settings
 * @param argAccount    Account to take settings from. If this account is an address book account,
 * settings will be taken from the corresponding main account instead.
 *
 * @throws InvalidAccountException on construction when the account doesn't exist (anymore)
 * @throws IllegalArgumentException when the account type is not _PeopleSync_ or _PeopleSync address book_
 */
class AccountSettings(
    val context: Context,
    argAccount: Account
) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountSettingsEntryPoint {
        fun appDatabase(): AppDatabase
        fun settingsManager(): SettingsManager
    }

    companion object {
        const val KEY_LOGIN_BASE_URL = "login_base_url"
        const val KEY_LOGIN_USER_NAME = "login_user_name"
        const val KEY_LOGIN_PASSWORD = "login_password"

        const val CURRENT_VERSION = 14
        const val KEY_SETTINGS_VERSION = "version"

        const val KEY_SYNC_INTERVAL_ADDRESSBOOKS = "sync_interval_addressbooks"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"
        const val KEY_BASE_URL = "base_url"

        /** OAuth [AuthState] (serialized as JSON) */
        const val KEY_AUTH_STATE = "auth_state"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs
        const val KEY_IGNORE_VPNS = "ignore_vpns"           // ignore vpns at connection detection

        /** Contact group method:
         *null (not existing)*     groups as separate vCards (default);
         "CATEGORIES"              groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        /** UI preference: Show only personal collections
         value = *null* (not existing)   show all collections (default);
         "1"                             show only personal collections */
        const val KEY_SHOW_ONLY_PERSONAL = "show_only_personal"

        const val SYNC_INTERVAL_MANUALLY = -1L

        /** Static property to indicate whether AccountSettings migration is currently running.
         * **Access must be `synchronized` with `AccountSettings::class.java`.** */
        @Volatile
        var currentlyUpdating = false

        fun initialUserData(credentials: Credentials?): Bundle {
            val bundle = Bundle()
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.userName != null)
                    bundle.putString(KEY_USERNAME, credentials.userName)

                if (credentials.certificateAlias != null)
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

                if (credentials.authState != null)
                    bundle.putString(KEY_AUTH_STATE, credentials.authState.jsonSerializeString())

                if (credentials.baseUrl != null)
                    bundle.putString(KEY_BASE_URL, credentials.baseUrl)
            }

            return bundle
        }

        fun updateAccounts(context: Context) {
            val am = AccountManager.get(context)
            for (account in am.getAccountsByType(context.getString(R.string.account_type)))
                try {
                    val baseUrl = am.getUserData(account, KEY_BASE_URL)

                    // we are only interested in managed accounts and only those have the baseUrl
                    // attached to their userData
                    if (baseUrl != null) {
                        val accountSettings = AccountSettings(context, account)
                        val creds = accountSettings.credentials()

                        val settings = accountSettings.settings
                        val managedBaseUrl = settings.getString(KEY_LOGIN_BASE_URL)
                        val managedUserName = settings.getString(KEY_LOGIN_USER_NAME)
                        val managedPassword = settings.getString(KEY_LOGIN_PASSWORD)
                        // If the password has been deleted/unset, do not update existing accounts
                        // and set an empty password as this is guaranteed to lead to synchronization
                        // failures. The alternative would be to delete the account, but this might
                        // be unexpected, so do nothing instead.
                        if (managedPassword.isNullOrEmpty()) {
                            Logger.log.info("${account.name}: Managed login password has been deleted. Doing nothing.")
                            return
                        }
                        // check if baseUrl and userName match
                        if (managedBaseUrl == baseUrl && managedUserName == creds.userName) {
                            if (managedPassword != creds.password) {
                                Logger.log.info("${account.name}: Managed login password changed. Updating account settings and requesting sync.")
                                am.setPassword(account, managedPassword)
                                // Request an explicit sync after we changed the account password.
                                // This should also clear any error notifications.
                                val authority = context.getString(R.string.address_books_authority)
                                SyncWorker.enqueue(context, account, authority)
                            } else {
                                // Password is up-to-date
                            }
                        }
                    }
                } catch (ignored: InvalidAccountException) {
                    // account doesn't exist (anymore)
                }
        }

        fun isManaged(context: Context): Boolean {
            val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            val appRestrictions = restrictionsManager.applicationRestrictions

            return !appRestrictions.getString(KEY_LOGIN_BASE_URL).isNullOrEmpty()
        }

    }


    val db = EntryPointAccessors.fromApplication(context, AccountSettingsEntryPoint::class.java).appDatabase()
    val settings = EntryPointAccessors.fromApplication(context, AccountSettingsEntryPoint::class.java).settingsManager()

    val accountManager: AccountManager = AccountManager.get(context)
    val account: Account

    init {
        when (argAccount.type) {
            context.getString(R.string.account_type_address_book) -> {
                /* argAccount is an address book account, which is not a main account. However settings are
                   stored in the main account, so resolve and use the main account instead. */
                account = LocalAddressBook.mainAccount(context, argAccount)
            }
            context.getString(R.string.account_type) ->
                account = argAccount
            else ->
                throw IllegalArgumentException("Account type not supported")
        }

        // synchronize because account migration must only be run one time
        synchronized(AccountSettings::class.java) {
            val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
            var version = 0
            try {
                version = Integer.parseInt(versionStr)
            } catch (e: NumberFormatException) {
                Logger.log.log(Level.SEVERE, "Invalid account version: $versionStr", e)
            }
            Logger.log.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

            if (version < CURRENT_VERSION) {
                if (currentlyUpdating) {
                    Logger.log.severe("Redundant call: migration created AccountSettings(). This must never happen.")
                    throw IllegalStateException("Redundant call: migration created AccountSettings()")
                } else {
                    currentlyUpdating = true
                    update(version)
                    currentlyUpdating = false
                }
            }
        }
    }


    // authentication settings

    fun credentials() = Credentials(
        accountManager.getUserData(account, KEY_USERNAME),
        accountManager.getPassword(account),

        accountManager.getUserData(account, KEY_CERTIFICATE_ALIAS),

        accountManager.getUserData(account, KEY_AUTH_STATE)?.let { json ->
            AuthState.jsonDeserialize(json)
        }
    )

    fun credentials(credentials: Credentials) {
        // Basic/Digest auth
        accountManager.setAndVerifyUserData(account, KEY_USERNAME, credentials.userName)
        accountManager.setPassword(account, credentials.password)

        // client certificate
        accountManager.setAndVerifyUserData(account, KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)

        // OAuth
        accountManager.setAndVerifyUserData(account, KEY_AUTH_STATE, credentials.authState?.jsonSerializeString())
    }


    // sync. settings

    /**
     * Gets the currently set sync interval for this account in seconds.
     *
     * @param authority authority to check (for instance: [ContactsContract.AUTHORITY]])
     * @return sync interval in seconds; *[SYNC_INTERVAL_MANUALLY]* if manual sync; *null* if not set
     */
    fun getSyncInterval(authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        val key = when {
            authority == context.getString(R.string.address_books_authority) ->
                KEY_SYNC_INTERVAL_ADDRESSBOOKS
            else -> return null
        }
        return accountManager.getUserData(account, key)?.toLong()
    }

    /**
     * Sets the sync interval and en- or disables periodic sync for the given account and authority.
     * Does *not* call [ContentResolver.setIsSyncable].
     *
     * This method blocks until a worker as been created and enqueued (sync active) or removed
     * (sync disabled), so it should not be called from the UI thread.
     *
     * @param authority sync authority (like [ContactsContract.AUTHORITY])
     * @param _seconds if [SYNC_INTERVAL_MANUALLY]: automatic sync will be disabled;
     * otherwise (must be ≥ 15 min): automatic sync will be enabled and set to the given number of seconds
     */
    @WorkerThread
    fun setSyncInterval(authority: String, _seconds: Long) {
        val seconds =
            if (_seconds != SYNC_INTERVAL_MANUALLY && _seconds < 60*15)
                60*15
            else
                _seconds

        // Store (user defined) sync interval in account settings
        val key = when {
            authority == context.getString(R.string.address_books_authority) ->
                KEY_SYNC_INTERVAL_ADDRESSBOOKS
            else -> {
                Logger.log.warning("Sync interval not applicable to authority $authority")
                return
            }
        }
        accountManager.setAndVerifyUserData(account, key, seconds.toString())

        // update sync workers (needs already updated sync interval in AccountSettings)
        updatePeriodicSyncWorker(authority, seconds, getSyncWifiOnly())

        // Also enable/disable content change triggered syncs (SyncFramework automatic sync).
        // We could make this a separate user adjustable setting later on.
        setSyncOnContentChange(authority, seconds != SYNC_INTERVAL_MANUALLY)
    }

    /**
     * Enables/disables sync adapter automatic sync (content triggered sync) for the given
     * account and authority. Does *not* call [ContentResolver.setIsSyncable].
     *
     * We use the sync adapter framework only for the trigger, actual syncing is implemented
     * with WorkManager. The trigger comes in through SyncAdapterService.
     *
     * This method blocks until the sync-on-content-change has been enabled or disabled, so it
     * should not be called from the UI thread.
     *
     * @param enable    *true* enables automatic sync; *false* disables it
     * @param authority sync authority (like [ContactsContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    @WorkerThread
    fun setSyncOnContentChange(authority: String, enable: Boolean): Boolean {
        // Enable content change triggers (sync adapter framework)
        val setContentTrigger: () -> Boolean =
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            if (enable) {{
                Logger.log.fine("Enabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, true) // enables content triggers
                // Remove unwanted sync framework periodic syncs created by setSyncAutomatically
                for (periodicSync in ContentResolver.getPeriodicSyncs(account, authority))
                    ContentResolver.removePeriodicSync(periodicSync.account, periodicSync.authority, periodicSync.extras)
                /* return */ ContentResolver.getSyncAutomatically(account, authority)
            }} else {{
                Logger.log.fine("Disabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, false) // disables content triggers
                /* return */ !ContentResolver.getSyncAutomatically(account, authority)
            }}

        // try up to 10 times with 100 ms pause
        for (idxTry in 0 until 10) {
            if (setContentTrigger())
                // successfully set
                return true
            Thread.sleep(100)
        }
        return false
    }

    fun getSyncWifiOnly() =
        if (settings.containsKey(KEY_WIFI_ONLY))
            settings.getBoolean(KEY_WIFI_ONLY)
        else
            accountManager.getUserData(account, KEY_WIFI_ONLY) != null

    fun setSyncWiFiOnly(wiFiOnly: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

        // update sync workers (needs already updated wifi-only flag in AccountSettings)
        for (authority in SyncUtils.syncAuthorities(context))
            updatePeriodicSyncWorker(authority, getSyncInterval(authority), wiFiOnly)
    }

    fun getSyncWifiOnlySSIDs(): List<String>? =
        if (getSyncWifiOnly()) {
            val strSsids = if (settings.containsKey(KEY_WIFI_ONLY_SSIDS))
                settings.getString(KEY_WIFI_ONLY_SSIDS)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS)
            strSsids?.split(',')
        } else
            null
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
        accountManager.setAndVerifyUserData(account, KEY_WIFI_ONLY_SSIDS, StringUtils.trimToNull(ssids?.joinToString(",")))

    fun getIgnoreVpns(): Boolean =
        when (accountManager.getUserData(account, KEY_IGNORE_VPNS)) {
            null -> settings.getBoolean(KEY_IGNORE_VPNS)
            "0" -> false
            else -> true
        }

    fun setIgnoreVpns(ignoreVpns: Boolean) =
        accountManager.setAndVerifyUserData(account, KEY_IGNORE_VPNS, if (ignoreVpns) "1" else "0")

    /**
     * Updates the periodic sync worker of an authority according to
     *
     * - the sync interval and
     * - the _Sync WiFi only_ flag.
     *
     * @param authority   periodic sync workers for this authority will be updated
     * @param seconds     sync interval in seconds (`null` or [SYNC_INTERVAL_MANUALLY] disables periodic sync)
     * @param wiFiOnly    sync Wifi only flag
     */
    fun updatePeriodicSyncWorker(authority: String, seconds: Long?, wiFiOnly: Boolean) {
        try {
            if (seconds == null || seconds == SYNC_INTERVAL_MANUALLY) {
                Logger.log.fine("Disabling periodic sync of $account/$authority")
                PeriodicSyncWorker.disable(context, account, authority)
            } else {
                Logger.log.fine("Setting periodic sync of $account/$authority to $seconds seconds (wifiOnly=$wiFiOnly)")
                PeriodicSyncWorker.enable(context, account, authority, seconds, wiFiOnly)
            }.result.get() // On operation (enable/disable) failure exception is thrown
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Failed to set sync interval of $account/$authority to $seconds seconds", e)
        }
    }


    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        val name = settings.getString(KEY_CONTACT_GROUP_METHOD) ?:
                accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            }
            catch (_: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        accountManager.setAndVerifyUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }


    // UI settings

    /**
     * Whether only personal collections should be shown.
     *
     * @return [Pair] of values:
     *
     *   1. (first) whether only personal collections should be shown
     *   2. (second) whether the user shall be able to change the setting (= setting not locked)
     */
    fun getShowOnlyPersonal(): Pair<Boolean, Boolean> =
            when (settings.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
                0 -> Pair(false, false)
                1 -> Pair(true, false)
                else /* including -1 */ -> Pair(accountManager.getUserData(account, KEY_SHOW_ONLY_PERSONAL) != null, true)
            }

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        accountManager.setAndVerifyUserData(account, KEY_SHOW_ONLY_PERSONAL, if (showOnlyPersonal) "1" else null)
    }


    // update from previous account settings

    private fun update(baseVersion: Int) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion-1
            Logger.log.info("Updating account ${account.name} from version $fromVersion to $toVersion")
            try {
                val migrations = AccountSettingsMigrations(
                    context = context,
                    db = db,
                    settings = settings,
                    account = account,
                    accountManager = accountManager,
                    accountSettings = this
                )
                val updateProc = AccountSettingsMigrations::class.java.getDeclaredMethod("update_${fromVersion}_$toVersion")
                updateProc.invoke(migrations)

                Logger.log.info("Account version update successful")
                accountManager.setAndVerifyUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update account settings", e)
            }
        }
    }

}