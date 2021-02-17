/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient

import android.accounts.Account
import android.app.IntentService
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.*
import com.messageconcept.peoplesyncclient.model.Collection
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.DebugInfoActivity
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level
import kotlin.collections.*

@Suppress("DEPRECATION")
class DavService: IntentService("DavService") {

    companion object {

        const val ACTION_REFRESH_COLLECTIONS = "refreshCollections"
        const val EXTRA_DAV_SERVICE_ID = "davServiceID"

        /** Initialize a forced synchronization. Expects intent data
            to be an URI of this format:
            contents://<authority>/<account.type>/<account name>
         **/
        const val ACTION_FORCE_SYNC = "forceSync"

        const val AUTO_SYNC = "autoSync"

        val DAV_COLLECTION_PROPERTIES = arrayOf(
                ResourceType.NAME,
                CurrentUserPrivilegeSet.NAME,
                DisplayName.NAME,
                Owner.NAME,
                AddressbookDescription.NAME, SupportedAddressData.NAME,
                CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
                Source.NAME
        )

    }

    /**
     * List of [Service] IDs for which the collections are currently refreshed
     */
    private val runningRefresh = Collections.synchronizedSet(HashSet<Long>())

    /**
     * Currently registered [RefreshingStatusListener]s, which will be notified
     * when a collection refresh status changes
     */
    private val refreshingStatusListeners = Collections.synchronizedList(LinkedList<WeakReference<RefreshingStatusListener>>())

    @WorkerThread
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null)
            return

        val id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1)
        val auto = intent.getBooleanExtra(AUTO_SYNC, false)

        when (intent.action) {
            ACTION_REFRESH_COLLECTIONS ->
                if (runningRefresh.add(id)) {
                    refreshingStatusListeners.forEach { listener ->
                        listener.get()?.onDavRefreshStatusChanged(id, true)
                    }

                    val db = AppDatabase.getInstance(this@DavService)
                    refreshCollections(db, id, auto)
                }

            ACTION_FORCE_SYNC -> {
                val uri = intent.data!!
                val authority = uri.authority!!
                val account = Account(
                        uri.pathSegments[1],
                        uri.pathSegments[0]
                )
                forceSync(authority, account)
            }

        }
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    interface RefreshingStatusListener {
        fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean)
    }

    private val binder = InfoBinder()

    inner class InfoBinder: Binder() {
        fun isRefreshing(id: Long) = runningRefresh.contains(id)

        fun addRefreshingStatusListener(listener: RefreshingStatusListener, callImmediateIfRunning: Boolean) {
            refreshingStatusListeners += WeakReference<RefreshingStatusListener>(listener)
            if (callImmediateIfRunning)
                synchronized(runningRefresh) {
                    for (id in runningRefresh)
                        listener.onDavRefreshStatusChanged(id, true)
                }
        }

        fun removeRefreshingStatusListener(listener: RefreshingStatusListener) {
            val iter = refreshingStatusListeners.iterator()
            while (iter.hasNext()) {
                val item = iter.next().get()
                if (item == listener || item == null)
                    iter.remove()
            }
        }
    }

    override fun onBind(intent: Intent?) = binder



    /* ACTION RUNNABLES
       which actually do the work
     */

    private fun forceSync(authority: String, account: Account) {
        Logger.log.info("Forcing $authority synchronization of $account")
        val extras = Bundle(2)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
        ContentResolver.requestSync(account, authority, extras)
    }

    private fun refreshCollections(db: AppDatabase, serviceId: Long, autoSync: Boolean) {
        try {
            DavServiceUtils.refreshCollections(this, db, serviceId, autoSync)
        } finally {
            runningRefresh.remove(serviceId)
            refreshingStatusListeners.mapNotNull { it.get() }.forEach {
                it.onDavRefreshStatusChanged(serviceId, false)
            }
        }

    }

}