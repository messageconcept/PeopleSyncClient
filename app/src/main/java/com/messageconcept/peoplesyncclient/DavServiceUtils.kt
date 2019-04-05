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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Transaction
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import com.messageconcept.peoplesyncclient.DavService.Companion.DAV_COLLECTION_PROPERTIES
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.*
import com.messageconcept.peoplesyncclient.model.Collection
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.DebugInfoActivity
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.HashSet
import java.util.logging.Level

object DavServiceUtils {

    fun refreshCollections(context: Context, serviceId: Long, autoSync: Boolean, forceSyncOnChanges: Boolean = true) {
        val db = AppDatabase.getInstance(context)
        val homeSetDao = db.homeSetDao()
        val collectionDao = db.collectionDao()

        val service = db.serviceDao().get(serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        val homeSets = homeSetDao.getByService(serviceId).associateBy { it.url }.toMutableMap()
        val collections = collectionDao.getByService(serviceId).associateBy { it.url }.toMutableMap()

        /**
         * Checks if the given URL defines home sets and adds them to the home set list.
         *
         * @throws java.io.IOException
         * @throws HttpException
         * @throws at.bitfire.dav4jvm.exception.DavException
         */
        fun queryHomeSets(client: OkHttpClient, url: HttpUrl, recurse: Boolean = true) {
            val related = mutableSetOf<HttpUrl>()

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
            when (service.type) {
                Service.TYPE_CARDDAV ->
                    try {
                        dav.propfind(0, DisplayName.NAME, AddressbookHomeSet.NAME, GroupMembership.NAME) { response, _ ->
                            response[AddressbookHomeSet::class.java]?.let { homeSet ->
                                for (href in homeSet.hrefs)
                                    dav.location.resolve(href)?.let {
                                        val foundUrl = UrlUtils.withTrailingSlash(it)
                                        homeSets[foundUrl] = HomeSet(0, service.id, foundUrl)
                                    }
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
                Service.TYPE_CALDAV -> {
                    try {
                        dav.propfind(0, DisplayName.NAME, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME) { response, _ ->
                            response[CalendarHomeSet::class.java]?.let { homeSet ->
                                for (href in homeSet.hrefs)
                                    dav.location.resolve(href)?.let {
                                        val foundUrl = UrlUtils.withTrailingSlash(it)
                                        homeSets[foundUrl] = HomeSet(0, service.id, foundUrl)
                                    }
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

        @Transaction
        fun saveHomesets() {
            DaoTools(homeSetDao).syncAll(
                    homeSetDao.getByService(serviceId),
                    homeSets,
                    { it.url })
        }

        @Transaction
        fun saveCollections() {
            DaoTools(collectionDao).syncAll(
                    collectionDao.getByService(serviceId),
                    collections, { it.url }) { new, old ->
                new.forceReadOnly = old.forceReadOnly
                new.sync = old.sync
            }
        }

        fun saveResults() {
            saveHomesets()
            saveCollections()
        }

        try {
            Logger.log.info("Refreshing ${service.type} collections of service #$service")

            // cancel previous notification
            NotificationManagerCompat.from(context)
                    .cancel(service.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS)

            // create authenticating OkHttpClient (credentials taken from account settings)
            HttpClient.Builder(context, AccountSettings(context, account))
                    .setForeground(true)
                    .build().use { client ->
                val httpClient = client.okHttpClient

                // refresh home set list (from principal)
                service.principal?.let { principalUrl ->
                    Logger.log.fine("Querying principal $principalUrl for home sets")
                    queryHomeSets(httpClient, principalUrl)
                }

                // remember selected collections
                val selectedCollections = HashSet<HttpUrl>()
                val deselectedCollections = HashSet<HttpUrl>()
                collections.forEach { (url, collection) ->
                    if (collection.sync)
                        selectedCollections += url
                    else
                        deselectedCollections += url
                }
                // remember number of collections before the refresh
                val numCollectionsOld = collections.size

                // now refresh homesets and their member collections
                val itHomeSets = homeSets.iterator()
                while (itHomeSets.hasNext()) {
                    val homeSet = itHomeSets.next()
                    Logger.log.fine("Listing home set ${homeSet.key}")

                    try {
                        DavResource(httpClient, homeSet.key).propfind(1, *DAV_COLLECTION_PROPERTIES) { response, relation ->
                            if (!response.isSuccess())
                                return@propfind

                            if (relation == Response.HrefRelation.SELF) {
                                // this response is about the homeset itself
                                homeSet.value.displayName = response[DisplayName::class.java]?.displayName
                                homeSet.value.privBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind ?: true
                            }

                            // in any case, check whether the response is about a useable collection
                            val info = Collection.fromDavResponse(response) ?: return@propfind
                            info.serviceId = serviceId
                            info.confirmed = true
                            Logger.log.log(Level.FINE, "Found collection", info)

                            // remember usable collections
                            if ((service.type == Service.TYPE_CARDDAV && info.type == Collection.TYPE_ADDRESSBOOK) ||
                                    (service.type == Service.TYPE_CALDAV && arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(info.type)))
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
                            DavResource(httpClient, url).propfind(0, *DAV_COLLECTION_PROPERTIES) { response, _ ->
                                if (!response.isSuccess())
                                    return@propfind

                                val collection = Collection.fromDavResponse(response) ?: return@propfind
                                collection.confirmed = true

                                // remove unusable collections
                                if ((service.type == Service.TYPE_CARDDAV && collection.type != Collection.TYPE_ADDRESSBOOK) ||
                                    (service.type == Service.TYPE_CALDAV && !arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(collection.type)) ||
                                    (collection.type == Collection.TYPE_WEBCAL && collection.source == null))
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
                    collections[url]?.let { it.sync = true }
                for (url in deselectedCollections)
                    collections[url]?.let { it.sync = false }

                val numCollectionsNew = collections.size;
                if (numCollectionsOld != numCollectionsNew) {
                    Logger.log.info("Number of collections changed for ${account.name} from ${numCollectionsOld} -> ${numCollectionsNew}, triggering sync")
                    val args = Bundle(1)
                    args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    ContentResolver.requestSync(account, context.getString(R.string.address_books_authority), args)
                }
            }

            saveResults()

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
                    .setSmallIcon(R.drawable.ic_sync_problem_notify)
                    .setContentTitle(context.getString(R.string.dav_service_refresh_failed))
                    .setContentText(context.getString(R.string.dav_service_refresh_couldnt_refresh))
                    .setContentIntent(PendingIntent.getActivity(context, 0, debugIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setSubText(account.name)
                    .setOnlyAlertOnce(alertOnlyOnce)
                    .setPriority(priority)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            NotificationManagerCompat.from(context)
                    .notify(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS, notify)
        }
    }

}