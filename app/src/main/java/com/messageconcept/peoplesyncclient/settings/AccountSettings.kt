/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.messageconcept.peoplesyncclient.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.messageconcept.peoplesyncclient.*
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.AppDatabase
import com.messageconcept.peoplesyncclient.model.Collection
import com.messageconcept.peoplesyncclient.model.Credentials
import com.messageconcept.peoplesyncclient.model.Service
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.StringUtils
import java.util.logging.Level

/**
 * Manages settings of an account.
 *
 * @throws InvalidAccountException on construction when the account doesn't exist (anymore)
 */
@Suppress("FunctionName")
class AccountSettings(
        val context: Context,
        val account: Account
) {

    companion object {
        const val KEY_LOGIN_BASE_URL = "login_base_url"
        const val KEY_LOGIN_USER_NAME = "login_user_name"
        const val KEY_LOGIN_PASSWORD = "login_password"

        const val CURRENT_VERSION = 11
        const val KEY_SETTINGS_VERSION = "version"

        /** Stores the tasks sync interval (in minutes) so that it can be set again when the provider is switched */
        const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"
        const val KEY_BASE_URL = "base_url"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs

        /** Contact group method:
        value = null (not existing)     groups as separate VCards (default)
        "CATEGORIES"            groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        const val SYNC_INTERVAL_MANUALLY = -1L


        fun initialUserData(credentials: Credentials?): Bundle {
            val bundle = Bundle(2)
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.userName != null)
                    bundle.putString(KEY_USERNAME, credentials.userName)
                if (credentials.certificateAlias != null)
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)
                if (credentials.baseUrl != null)
                    bundle.putString(KEY_BASE_URL, credentials.baseUrl)
            }


            return bundle
        }

    }
    
    
    val accountManager: AccountManager = AccountManager.get(context)
    val settings = SettingsManager.getInstance(context)

    init {
        synchronized(AccountSettings::class.java) {
            val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
            var version = 0
            try {
                version = Integer.parseInt(versionStr)
            } catch (e: NumberFormatException) {
            }
            Logger.log.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

            if (version < CURRENT_VERSION)
                update(version)
        }
    }


    // authentication settings

    fun credentials() = Credentials(
            accountManager.getUserData(account, KEY_USERNAME),
            accountManager.getPassword(account),
            accountManager.getUserData(account, KEY_CERTIFICATE_ALIAS)
    )

    fun credentials(credentials: Credentials) {
        accountManager.setUserData(account, KEY_USERNAME, credentials.userName)
        accountManager.setPassword(account, credentials.password)
        accountManager.setUserData(account, KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)
    }


    // sync. settings

    fun getSyncInterval(authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        return if (ContentResolver.getSyncAutomatically(account, authority))
            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period ?: SYNC_INTERVAL_MANUALLY
        else
            SYNC_INTERVAL_MANUALLY
    }

    /**
     * Sets the sync interval and enables/disables automatic sync for the given account and authority.
     * Does *not* call [ContentResolver.setIsSyncable].
     *
     * This method blocks until the settings have arrived in the sync framework, so it should not
     * be called from the UI thread.
     *
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @param seconds if [SYNC_INTERVAL_MANUALLY]: automatic sync will be disabled;
     * otherwise: automatic sync will be enabled and set to the given number of seconds
     *
     * @return whether the sync interval was successfully set
     */
    @WorkerThread
    fun setSyncInterval(authority: String, seconds: Long): Boolean {
        /* Ugly hack: because there is no callback for when the sync status/interval has been
        updated, we need to make this call blocking. */
        val setInterval: () -> Boolean =
                if (seconds == SYNC_INTERVAL_MANUALLY) {
                    {
                        Logger.log.fine("Disabling automatic sync of $account/$authority")
                        ContentResolver.setSyncAutomatically(account, authority, false)

                        /* return */ ContentResolver.getSyncAutomatically(account, authority) == false
                    }
                } else {
                    {
                        Logger.log.fine("Setting automatic sync of $account/$authority to $seconds seconds")
                        ContentResolver.setSyncAutomatically(account, authority, true)
                        ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)

                        /* return */ ContentResolver.getSyncAutomatically(account, authority) &&
                                ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period == seconds
                    }
                }

        // try up to 10 times with 100 ms pause
        var success: Boolean = false
        for (idxTry in 0 until 10) {
            success = setInterval()
            if (success)
                break
            Thread.sleep(100)
        }

        if (!success)
            return false

        return true
    }

    fun getSyncWifiOnly() =
            if (settings.containsKey(KEY_WIFI_ONLY))
                settings.getBoolean(KEY_WIFI_ONLY)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY) != null
    fun setSyncWiFiOnly(wiFiOnly: Boolean) =
            accountManager.setUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

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
            accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, StringUtils.trimToNull(ssids?.joinToString(",")))


    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        val name = settings.getString(KEY_CONTACT_GROUP_METHOD) ?:
                accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            }
            catch (e: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }


    // update from previous account settings

    private fun update(baseVersion: Int) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion-1
            Logger.log.info("Updating account ${account.name} from version $fromVersion to $toVersion")
            try {
                val updateProc = this::class.java.getDeclaredMethod("update_${fromVersion}_$toVersion")
                updateProc.invoke(this)

                Logger.log.info("Account version update successful")
                accountManager.setUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update account settings", e)
            }
        }
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
        // add calendar colors
        // nothing to do

        // update allowed WiFi settings key
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setUserData(account, "wifi_only_ssid", null)
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
                        val addressBookAccount = Account(LocalAddressBook.accountName(account, info), context.getString(R.string.account_type_address_book))
                        if (!accountManager.addAccountExplicitly(addressBookAccount, null, LocalAddressBook.initialUserData(account, info.url.toString())))
                            throw ContactsStorageException("Couldn't create address book account")

                        // move contacts to new address book
                        Logger.log.info("Moving contacts from $account to $addressBookAccount")
                        val newAccount = ContentValues(2)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
                        val affected = provider.update(ContactsContract.RawContacts.CONTENT_URI.buildUpon()
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
        accountManager.setUserData(account, KEY_SETTINGS_VERSION, "6")

        // request sync of new address book account
        ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 1)
        setSyncInterval(context.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)
    }

    /* Android 7.1.1 OpenTasks fix */
    @Suppress("unused")
    private fun update_4_5() {
        // nothing to do
    }

    @Suppress("unused")
    private fun update_3_4() {
        setGroupMethod(GroupMethod.CATEGORIES)
    }

    // updates from AccountSettings version 2 and below are not supported anymore

}
