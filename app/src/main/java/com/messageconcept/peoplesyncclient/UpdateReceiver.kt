/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Notification
import android.app.PendingIntent
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.property.CurrentUserPrincipal
import at.bitfire.vcard4android.GroupMethod
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.Credentials
import com.messageconcept.peoplesyncclient.model.ServiceDB
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.AccountsActivity
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import okhttp3.HttpUrl
import java.net.URI
import java.net.URLDecoder
import java.util.logging.Level

class UpdateReceiver : BroadcastReceiver() {
    companion object {
        const val KEY_USERNAME = "user_name"
        const val KEY_PROVIDED_URL = "provided_url"
        const val KEY_PARENT_ACCOUNT_NAME = "parent_account_name"
        const val KEY_ADDRESSBOOK_URL = "addressbook_url"
    }

    class LoginInfo (val uri: URI, val credentials: Credentials)

    class PrincipalLookupTask(context: Context) : AsyncTask<LoginInfo, Void, String>() {
        val mContext = context

        override fun doInBackground(vararg params: LoginInfo?): String? {
            val loginInfo = params[0]!!

            val log = java.util.logging.Logger.getLogger("peoplesync.PrincipalFinder")!!

            val httpClient: HttpClient = HttpClient.Builder(mContext, logger = log, useCustomCertManager = false)
                    .addAuthentication(null, loginInfo.credentials)
                    .setForeground(true)
                    .build()
            var principal: HttpUrl? = null
            try {
                DavResource(httpClient.okHttpClient, HttpUrl.get(loginInfo.uri)!!, log).propfind(0, CurrentUserPrincipal.NAME) { response, _ ->
                    response[CurrentUserPrincipal::class.java]?.href?.let { href ->
                        response.requestedUrl.resolve(href)?.let {
                            log.info("Found current-user-principal: $it")
                            principal = it
                        }
                    }
                }
            } catch (e: Exception) {
                log.log(Level.WARNING, "Couldn't query current-user-principal", e)
            }

            return principal?.toString()
        }
    }

    // determine principal URL
    private fun getPrincipalUrl(context: Context, parent: Account, loginInfo: LoginInfo): String? {
        val accountManager = AccountManager.get(context)

        Logger.log.info("Querying server for principal name")
        val principalUrl = PrincipalLookupTask(context).execute(loginInfo).get()
        if (principalUrl != null)
            return principalUrl

        Logger.log.info("Querying old addressbooks for principal name")
        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
            val parentAccount = accountManager.getUserData(account, KEY_PARENT_ACCOUNT_NAME)
            if (parentAccount != null && parentAccount == parent.name) {
                val addressbookUrl = accountManager.getUserData(account, KEY_ADDRESSBOOK_URL)
                val tokens = addressbookUrl.trim('/').split('/')
                val principal = URLDecoder.decode(tokens[tokens.size - 2], "UTF-8")
                if (principal != null)
                    return "${loginInfo.uri.toASCIIString()}/principals/${principal}/"
            }
        }

        // fallback to userName if it looks like a principal name
        if(loginInfo.credentials.userName != null && loginInfo.credentials.userName.contains('@')) {
            return "${loginInfo.uri.toASCIIString()}/principals/${loginInfo.credentials.userName}/"
        }

        return null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log.fine("App got updated")
        val accountManager = AccountManager.get(context)
        var showNotification = false


        for (account in accountManager.getAccountsByType(context.getString(R.string.account_type))) {
            val versionStr = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION)

            // Old accounts don't have a version string
            if (versionStr == null) {
                val newName = account.name.removePrefix("PeopleSync ")
                val newAccount = Account(newName, context.getString(R.string.account_type))

                val userName = accountManager.getUserData(account, KEY_USERNAME)
                val providedURL = accountManager.getUserData(account, KEY_PROVIDED_URL)
                val password = accountManager.getPassword(account)

                val credentials = Credentials(userName, password)
                val loginInfo = LoginInfo(URI.create(providedURL), credentials)

                var principalUrl = getPrincipalUrl(context, account, loginInfo)

                delete(context, account)

                // If we couldn't determine a principal from looking at old addressbooks, we simply
                // delete the old account and continue. The alternative would be to start a service
                // discovery with the given user account information but this requires network access
                if (principalUrl == null) {
                    Logger.log.info("Could not determine principal for ${account.name}")
                    continue
                }

                val userData = AccountSettings.initialUserData(credentials)
                Logger.log.log(Level.INFO, "Convert Android account with existing config", arrayOf(account, userData, principalUrl))

                if (!accountManager.addAccountExplicitly(newAccount, credentials.password, userData)) {
                    Logger.log.info("Couldn't create account ${account.name}")
                    // TODO: Exit at this point or continue?
                }

                showNotification = true

                // add entries for account to service DB
                ServiceDB.OpenHelper(context).use { dbHelper ->
                    val db = dbHelper.writableDatabase
                    try {
                        val accountSettings = AccountSettings(context, newAccount)

                        val refreshIntent = Intent(context, DavService::class.java)
                        refreshIntent.action = DavService.ACTION_REFRESH_COLLECTIONS

                        val serviceValues = ContentValues(3)
                        serviceValues.put(ServiceDB.Services.ACCOUNT_NAME, newName)
                        serviceValues.put(ServiceDB.Services.SERVICE, ServiceDB.Services.SERVICE_CARDDAV)
                        serviceValues.put(ServiceDB.Services.PRINCIPAL, principalUrl)

                        val serviceID = db.insertWithOnConflict(ServiceDB.Services._TABLE, null, serviceValues, SQLiteDatabase.CONFLICT_REPLACE)

                        // initial CardDAV account settings
                        accountSettings.setGroupMethod(GroupMethod.CATEGORIES)

                        // start CardDAV service detection (refresh collections)
                        refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceID)
                        context.startService(refreshIntent)

                        // contact sync is automatically enabled by isAlwaysSyncable="true" in res/xml/sync_address_books.xml
                        accountSettings.setSyncInterval(context.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)

                    } catch (e: InvalidAccountException) {
                        Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
                    }
                }
            }
        }

        if (showNotification) {
            val appIntent = Intent(context, AccountsActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT)

            val notify = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_DEBUG)
                    .setSmallIcon(R.drawable.ic_account_update_notify)
                    .setContentTitle(context.getString(R.string.update_receiver_notification_title))
                    .setContentText(context.getString(R.string.update_receiver_notification_text))
                    .setContentIntent(pendingIntent)
                    .addAction(0, context.getString(R.string.update_receiver_notification_action), pendingIntent)
                    .setOnlyAlertOnce(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .build()
            NotificationManagerCompat.from(context)
                    .notify(null, NotificationUtils.NOTIFY_UPDATE, notify)
        }

    }

    fun delete(context: Context, account: Account) {
        val accountManager = AccountManager.get(context)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, null, null, null)
        else
            accountManager.removeAccount(account, null, null)
    }

}