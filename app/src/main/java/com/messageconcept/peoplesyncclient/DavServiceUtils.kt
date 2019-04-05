/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient

import android.accounts.Account
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.CollectionInfo
import com.messageconcept.peoplesyncclient.model.ServiceDB.*
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.DebugInfoActivity
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.HashSet
import java.util.logging.Level

object DavServiceUtils {

    fun refreshCollections(context: Context, service: Long, autoSync: Boolean, forceSyncOnChanges: Boolean = true) {
        OpenHelper(context).use { dbHelper ->
            val db = dbHelper.writableDatabase

            val serviceType by lazy {
                db.query(Services._TABLE, arrayOf(Services.SERVICE), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return@lazy cursor.getString(0)
                } ?: throw IllegalArgumentException("Service not found")
            }

            val account by lazy {
                db.query(Services._TABLE, arrayOf(Services.ACCOUNT_NAME), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return@lazy Account(cursor.getString(0), context.getString(R.string.account_type))
                }
                throw IllegalArgumentException("Account not found")
            }

            val homeSets by lazy {
                val homeSets = mutableSetOf<HttpUrl>()
                db.query(HomeSets._TABLE, arrayOf(HomeSets.URL), "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext())
                        HttpUrl.parse(cursor.getString(0))?.let { homeSets += it }
                }
                homeSets
            }

            val collections by lazy {
                val collections = mutableMapOf<HttpUrl, CollectionInfo>()
                db.query(Collections._TABLE, null, "${Collections.SERVICE_ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues(cursor.columnCount)
                        DatabaseUtils.cursorRowToContentValues(cursor, values)
                        values.getAsString(Collections.URL)?.let { url ->
                            HttpUrl.parse(url)?.let { collections.put(it, CollectionInfo(values)) }
                        }
                    }
                }
                collections
            }

            fun readPrincipal(): HttpUrl? {
                db.query(Services._TABLE, arrayOf(Services.PRINCIPAL), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        cursor.getString(0)?.let { return HttpUrl.parse(it) }
                }
                return null
            }

            /**
             * Checks if the given URL defines home sets and adds them to the home set list.
             *
             * @throws IOException
             * @throws HttpException
             * @throws DavException
             */
            fun queryHomeSets(client: OkHttpClient, url: HttpUrl, recurse: Boolean = true) {
                var related = setOf<HttpUrl>()

                fun findRelated(root: HttpUrl, dav: Response) {
                    // refresh home sets: calendar-proxy-read/write-for
                    dav[CalendarProxyReadFor::class.java]?.let {
                        for (href in it.hrefs) {
                            Logger.log.fine("Principal is a read-only proxy for $href, checking for home sets")
                            root.resolve(href)?.let { proxyReadFor ->
                                related += proxyReadFor
                            }
                        }
                    }
                    dav[CalendarProxyWriteFor::class.java]?.let {
                        for (href in it.hrefs) {
                            Logger.log.fine("Principal is a read/write proxy for $href, checking for home sets")
                            root.resolve(href)?.let { proxyWriteFor ->
                                related += proxyWriteFor
                            }
                        }
                    }

                    // refresh home sets: direct group memberships
                    dav[GroupMembership::class.java]?.let {
                        for (href in it.hrefs) {
                            Logger.log.fine("Principal is member of group $href, checking for home sets")
                            root.resolve(href)?.let { groupMembership ->
                                related += groupMembership
                            }
                        }
                    }
                }

                val dav = DavResource(client, url)
                when (serviceType) {
                    Services.SERVICE_CARDDAV ->
                        try {
                            dav.propfind(0, AddressbookHomeSet.NAME, GroupMembership.NAME) { response, _ ->
                                response[AddressbookHomeSet::class.java]?.let { homeSet ->
                                    for (href in homeSet.hrefs)
                                        dav.location.resolve(href)?.let { homeSets += UrlUtils.withTrailingSlash(it) }
                                }

                                if (recurse)
                                    findRelated(dav.location, response)
                            }
                        } catch (e: HttpException) {
                            if (e.code/100 == 4)
                                Logger.log.log(Level.INFO, "Ignoring Client Error 4xx while looking for addressbook home sets", e)
                            else
                                throw e
                        }
                    Services.SERVICE_CALDAV -> {
                        try {
                            dav.propfind(0, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME) { response, _ ->
                                response[CalendarHomeSet::class.java]?.let { homeSet ->
                                    for (href in homeSet.hrefs)
                                        dav.location.resolve(href)?.let { homeSets.add(UrlUtils.withTrailingSlash(it)) }
                                }

                                if (recurse)
                                    findRelated(dav.location, response)
                            }
                        } catch (e: HttpException) {
                            if (e.code/100 == 4)
                                Logger.log.log(Level.INFO, "Ignoring Client Error 4xx while looking for calendar home sets", e)
                            else
                                throw e
                        }
                    }
                }

                for (resource in related)
                    queryHomeSets(client, resource, false)
            }

            fun saveHomeSets() {
                db.delete(HomeSets._TABLE, "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()))
                for (homeSet in homeSets) {
                    val values = ContentValues(2)
                    values.put(HomeSets.SERVICE_ID, service)
                    values.put(HomeSets.URL, homeSet.toString())
                    db.insertOrThrow(HomeSets._TABLE, null, values)
                }
            }

            fun saveCollections() {
                db.delete(Collections._TABLE, "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()))
                for ((_,collection) in collections) {
                    val values = collection.toDB()
                    Logger.log.log(Level.FINE, "Saving collection", values)
                    values.put(Collections.SERVICE_ID, service)
                    db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }


            try {
                Logger.log.info("Refreshing $serviceType collections of service #$service")

                // cancel previous notification
                NotificationManagerCompat.from(context)
                        .cancel(service.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS)

                // create authenticating OkHttpClient (credentials taken from account settings)
                HttpClient.Builder(context, AccountSettings(context, account))
                        .setForeground(true)
                        .build().use { client ->
                    val httpClient = client.okHttpClient

                    // refresh home set list (from principal)
                    readPrincipal()?.let { principalUrl ->
                        Logger.log.fine("Querying principal $principalUrl for home sets")
                        queryHomeSets(httpClient, principalUrl)
                    }

                    // remember selected collections
                    val selectedCollections = HashSet<HttpUrl>()
                    collections.values
                            .filter { it.selected }
                            .forEach { (url, _) -> selectedCollections += url }
                    val deselectedCollections = HashSet<HttpUrl>()
                    collections.values
                            .filter { it.selected == false }
                            .forEach { (url, _) -> deselectedCollections += url }
                    // remember number of collections before the refresh
                    val numCollectionsOld = collections.size

                    // now refresh collections (taken from home sets)
                    val itHomeSets = homeSets.iterator()
                    while (itHomeSets.hasNext()) {
                        val homeSetUrl = itHomeSets.next()
                        Logger.log.fine("Listing home set $homeSetUrl")

                        try {
                            DavResource(httpClient, homeSetUrl).propfind(1, *CollectionInfo.DAV_PROPERTIES) { response, _ ->
                                if (!response.isSuccess())
                                    return@propfind

                                val info = CollectionInfo(response)
                                info.confirmed = true
                                Logger.log.log(Level.FINE, "Found collection", info)

                                if ((serviceType == Services.SERVICE_CARDDAV && info.type == CollectionInfo.Type.ADDRESS_BOOK) ||
                                    (serviceType == Services.SERVICE_CALDAV && arrayOf(CollectionInfo.Type.CALENDAR, CollectionInfo.Type.WEBCAL).contains(info.type)))
                                    collections[response.href] = info
                            }
                        } catch(e: HttpException) {
                            if (e.code in arrayOf(403, 404, 410))
                                // delete home set only if it was not accessible (40x)
                                itHomeSets.remove()
                        }
                    }

                    // check/refresh unconfirmed collections
                    val itCollections = collections.entries.iterator()
                    while (itCollections.hasNext()) {
                        val (url, info) = itCollections.next()
                        if (!info.confirmed)
                            try {
                                DavResource(httpClient, url).propfind(0, *CollectionInfo.DAV_PROPERTIES) { response, _ ->
                                    if (!response.isSuccess())
                                        return@propfind

                                    val collectionInfo = CollectionInfo(response)
                                    collectionInfo.confirmed = true

                                    // remove unusable collections
                                    if ((serviceType == Services.SERVICE_CARDDAV && collectionInfo.type != CollectionInfo.Type.ADDRESS_BOOK) ||
                                        (serviceType == Services.SERVICE_CALDAV && !arrayOf(CollectionInfo.Type.CALENDAR, CollectionInfo.Type.WEBCAL).contains(collectionInfo.type)) ||
                                        (collectionInfo.type == CollectionInfo.Type.WEBCAL && collectionInfo.source == null))
                                        itCollections.remove()
                                }
                            } catch(e: HttpException) {
                                if (e.code in arrayOf(403, 404, 410))
                                // delete collection only if it was not accessible (40x)
                                    itCollections.remove()
                                else
                                    throw e
                            }
                    }

                    // restore selections
                    for (url in selectedCollections)
                        collections[url]?.let { it.selected = true }
                    for (url in deselectedCollections)
                        collections[url]?.let { it.selected = false }

                    val numCollectionsNew = collections.size
                    if (numCollectionsOld != numCollectionsNew && forceSyncOnChanges) {
                        Logger.log.info("Number of collections changed for ${account.name} from ${numCollectionsOld} -> ${numCollectionsNew}, triggering sync")
                        val args = Bundle(1)
                        args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        ContentResolver.requestSync(account, context.getString(R.string.address_books_authority), args)
                    }

                }

                db.beginTransactionNonExclusive()
                try {
                    saveHomeSets()
                    saveCollections()
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

            } catch(e: InvalidAccountException) {
                Logger.log.log(Level.SEVERE, "Invalid account", e)
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't refresh collection list", e)

                val debugIntent = Intent(context, DebugInfoActivity::class.java)
                debugIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e)
                debugIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account)

                val priority: Int
                val alertOnlyOnce: Boolean
                val channel: String

                if (autoSync) {
                    priority = NotificationCompat.PRIORITY_MIN
                    channel = NotificationUtils.CHANNEL_SYNC_IO_ERRORS
                    alertOnlyOnce = true
                } else {
                    priority = NotificationCompat.PRIORITY_DEFAULT
                    channel = NotificationUtils.CHANNEL_GENERAL
                    alertOnlyOnce = false
                }
                val notify = NotificationUtils.newBuilder(context, channel)
                        .setSmallIcon(R.drawable.ic_sync_error_notification)
                        .setContentTitle(context.getString(R.string.dav_service_refresh_failed))
                        .setContentText(context.getString(R.string.dav_service_refresh_couldnt_refresh))
                        .setContentIntent(PendingIntent.getActivity(context, 0, debugIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setSubText(account.name)
                        .setOnlyAlertOnce(alertOnlyOnce)
                        .setPriority(priority)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .build()
                NotificationManagerCompat.from(context)
                        .notify(service.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS, notify)
            }
        }

    }

}