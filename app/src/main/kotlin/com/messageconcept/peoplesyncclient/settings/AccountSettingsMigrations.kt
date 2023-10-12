package com.messageconcept.peoplesyncclient.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.syncadapter.SyncUtils
import com.messageconcept.peoplesyncclient.util.closeCompat
import com.messageconcept.peoplesyncclient.util.setAndVerifyUserData
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.logging.Level

class AccountSettingsMigrations(
    val context: Context,
    val db: AppDatabase,
    val settings: SettingsManager,
    val account: Account,
    val accountManager: AccountManager,
    val accountSettings: AccountSettings
) {

    /**
     * Disables all sync adapter periodic syncs for every authority. Then enables
     * corresponding PeriodicSyncWorkers
     */
    @Suppress("unused","FunctionName")
    fun update_13_14() {

        // Cancel any potentially running syncs for this account (sync framework)
        ContentResolver.cancelSync(account, null)

        val authorities = listOf(
            context.getString(R.string.address_books_authority)
        )

        for (authority in authorities) {
            // Enable PeriodicSyncWorker (WorkManager), with known intervals
            v14_enableWorkManager(authority)
            // Disable periodic syncs (sync adapter framework)
            v14_disableSyncFramework(authority)
        }
    }
    private fun v14_enableWorkManager(authority: String) {
        val enabled = accountSettings.getSyncInterval(authority)?.let { syncInterval ->
            accountSettings.setSyncInterval(authority, syncInterval)
        } ?: false
        Logger.log.info("PeriodicSyncWorker for $account/$authority enabled=$enabled")
    }
    private fun v14_disableSyncFramework(authority: String) {
        // Disable periodic syncs (sync adapter framework)
        val disable: () -> Boolean = {
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            for (sync in ContentResolver.getPeriodicSyncs(account, authority))
                ContentResolver.removePeriodicSync(sync.account, sync.authority, sync.extras)

            // check whether syncs are really disabled
            var result = true
            for (sync in ContentResolver.getPeriodicSyncs(account, authority)) {
                Logger.log.info("Sync framework still has a periodic sync for $account/$authority: $sync")
                result = false
            }
            result
        }
        // try up to 10 times with 100 ms pause
        var success = false
        for (idxTry in 0 until 10) {
            success = disable()
            if (success)
                break
            Thread.sleep(200)
        }
        Logger.log.info("Sync framework periodic syncs for $account/$authority disabled=$success")
    }

    @Suppress("unused","FunctionName")
    /**
     * Not a per-account migration, but not a database migration, too, so it fits best there.
     * Best future solution would be that SettingsManager manages versions and migrations.
     *
     * Updates proxy settings from override_proxy_* to proxy_type, proxy_host, proxy_port.
     */
    private fun update_12_13() {
        // proxy settings are managed by SharedPreferencesProvider
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // old setting names
        val overrideProxy = "override_proxy"
        val overrideProxyHost = "override_proxy_host"
        val overrideProxyPort = "override_proxy_port"

        val edit = preferences.edit()
        if (preferences.contains(overrideProxy)) {
            if (preferences.getBoolean(overrideProxy, false))
            // override_proxy set, migrate to proxy_type = HTTP
                edit.putInt(Settings.PROXY_TYPE, Settings.PROXY_TYPE_HTTP)
            edit.remove(overrideProxy)
        }
        if (preferences.contains(overrideProxyHost)) {
            preferences.getString(overrideProxyHost, null)?.let { host ->
                edit.putString(Settings.PROXY_HOST, host)
            }
            edit.remove(overrideProxyHost)
        }
        if (preferences.contains(overrideProxyPort)) {
            val port = preferences.getInt(overrideProxyPort, 0)
            if (port != 0)
                edit.putInt(Settings.PROXY_PORT, port)
            edit.remove(overrideProxyPort)
        }
        edit.apply()
    }


    @Suppress("unused","FunctionName")
    /**
     * Store event URLs as URL (extended property) instead of unknown property. At the same time,
     * convert legacy unknown properties to the current format.
     */
    private fun update_11_12() {
        // nothing to do
    }

    @Suppress("unused","FunctionName")
    /**
     * The tasks sync interval should be stored in account settings. It's used to set the sync interval
     * again when the tasks provider is switched.
     */
    private fun update_10_11() {
        // nothing to do
    }

    @Suppress("unused","FunctionName")
    /**
     * Task synchronization now handles alarms, categories, relations and unknown properties.
     * Setting task ETags to null will cause them to be downloaded (and parsed) again.
     *
     * Also update the allowed reminder types for calendars.
     **/
    private fun update_9_10() {
        // nothing to do
    }

    @Suppress("unused","FunctionName")
    /**
     * It seems that somehow some non-CalDAV accounts got OpenTasks syncable, which caused battery problems.
     * Disable it on those accounts for the future.
     */
    private fun update_8_9() {
        // nothing to do
    }

    @Suppress("unused","FunctionName")
    @SuppressLint("Recycle")
    /**
     * There is a mistake in this method. [TaskContract.Tasks.SYNC_VERSION] is used to store the
     * SEQUENCE and should not be used for the eTag.
     */
    private fun update_7_8() {
        // nothing to do
    }

    @Suppress("unused")
    @SuppressLint("Recycle")
    private fun update_6_7() {
        // nothing to do for calendar colors

        // update allowed WiFi settings key
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setAndVerifyUserData(account, AccountSettings.KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setAndVerifyUserData(account, "wifi_only_ssid", null)
    }

    @Suppress("unused")
    @SuppressLint("Recycle", "ParcelClassLoader")
    private fun update_5_6() {
        context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { provider ->
            val parcel = Parcel.obtain()
            try {
                // don't run syncs during the migration
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)
                ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 0)
                ContentResolver.cancelSync(account, null)

                // get previous address book settings (including URL)
                val raw = ContactsContract.SyncState.get(provider, account)
                if (raw == null)
                    Logger.log.info("No contacts sync state, ignoring account")
                else {
                    parcel.unmarshall(raw, 0, raw.size)
                    parcel.setDataPosition(0)
                    val params = parcel.readBundle()!!
                    val url = params.getString("url")?.toHttpUrlOrNull()
                    if (url == null)
                        Logger.log.info("No address book URL, ignoring account")
                    else {
                        // create new address book
                        val info = Collection(url = url, type = Collection.TYPE_ADDRESSBOOK, displayName = account.name)
                        Logger.log.log(Level.INFO, "Creating new address book account", url)
                        val addressBookAccount = Account(
                            LocalAddressBook.accountName(account, info), context.getString(
                                R.string.account_type_address_book))
                        if (!accountManager.addAccountExplicitly(addressBookAccount, null, LocalAddressBook.initialUserData(account, info.url.toString())))
                            throw ContactsStorageException("Couldn't create address book account")

                        // move contacts to new address book
                        Logger.log.info("Moving contacts from $account to $addressBookAccount")
                        val newAccount = ContentValues(2)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
                        val affected = provider.update(
                            ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
                            newAccount,
                            "${ContactsContract.RawContacts.ACCOUNT_NAME}=? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE}=?",
                            arrayOf(account.name, account.type))
                        Logger.log.info("$affected contacts moved to new address book")
                    }

                    ContactsContract.SyncState.set(provider, account, null)
                }
            } catch(e: RemoteException) {
                throw ContactsStorageException("Couldn't migrate contacts to new address book", e)
            } finally {
                parcel.recycle()
                provider.closeCompat()
            }
        }

        // update version number so that further syncs don't repeat the migration
        accountManager.setAndVerifyUserData(account, AccountSettings.KEY_SETTINGS_VERSION, "6")

        // request sync of new address book account
        ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 1)
        accountSettings.setSyncInterval(context.getString(R.string.address_books_authority), 4*3600)
    }

    /* Android 7.1.1 OpenTasks fix */
    @Suppress("unused")
    private fun update_4_5() {
        // nothing to do
    }

    @Suppress("unused")
    private fun update_3_4() {
        accountSettings.setGroupMethod(GroupMethod.CATEGORIES)
    }

    // updates from AccountSettings version 2 and below are not supported anymore

}