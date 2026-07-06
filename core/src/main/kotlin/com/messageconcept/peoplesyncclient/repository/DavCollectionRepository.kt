/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.repository

import android.accounts.Account
import android.content.Context
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.GoneException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.exception.NotFoundException
import at.bitfire.dav4jvm.ktor.withTrailingSlash
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import com.messageconcept.peoplesyncclient.ProductIds
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.db.CollectionType
import com.messageconcept.peoplesyncclient.db.HomeSet
import com.messageconcept.peoplesyncclient.network.HttpClientBuilder
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.util.DavUtils
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import java.io.StringWriter
import java.util.UUID
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Repository for managing collections.
 */
class DavCollectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val logger: Logger,
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    private val productIds: Lazy<ProductIds>,
    private val serviceRepository: DavServiceRepository
) {

    private val dao = db.collectionDao()

    /**
     * Returns the number of collections that are push capable.
     */
    suspend fun countSyncEnabledPushCapable() =  dao.countSyncEnabledPushCapable()

    /**
     * Returns the total number of collections in all accounts that are user enabled for synchronization.
     */
    suspend fun countSyncEnabled() = dao.countSyncEnabled()

    /**
     * Determines the amount of sync-enabled push capable collections in all accounts combined.
     * @return
     *  - [PushCollectionsAmount.All] if all the sync-enabled collections in all accounts are push-capable OR if zero sync-enabled collections exist
     *  - [PushCollectionsAmount.Some] if some (but not all) sync-enabled collections are push-capable
     *  - [PushCollectionsAmount.None] if no sync-enabled collections are push-capable
     */
    suspend fun getAmountPushCapable(): PushCollectionsAmount {
        val syncEnabled = countSyncEnabled()
        val syncEnabledPushCapable = countSyncEnabledPushCapable()

        return when (syncEnabledPushCapable) {
            syncEnabled -> PushCollectionsAmount.All // Also matches zero sync-enabled collections
            0 -> PushCollectionsAmount.None
            else -> PushCollectionsAmount.Some
        }
    }

    /**
     * Whether there are any collections that are push capable.
     */
    suspend fun anyPushCapable(): Boolean = dao.anyPushCapable()

    /**
     * Creates address book collection on server and locally
     */
    suspend fun createAddressBook(
        account: Account,
        homeSet: HomeSet,
        displayName: String,
        description: String?
    ) {
        val folderName = UUID.randomUUID().toString()
        val url = URLBuilder(homeSet.url)
            .appendPathSegments(folderName, encodeSlash = true)
            .build()
            .withTrailingSlash()

        // create collection on server
        createOnServer(
            account = account,
            url = url,
            method = "MKCOL",
            xmlBody = generateMkColXml(
                addressBook = true,
                displayName = displayName,
                description = description
            )
        )

        // no HTTP error -> create collection locally
        val collection = Collection(
            serviceId = homeSet.serviceId,
            homeSetId = homeSet.id,
            url = url,
            type = Collection.TYPE_ADDRESSBOOK,
            displayName = displayName,
            description = description
        )
        dao.insertAsync(collection)
    }

    /**
     * Create calendar collection on server and locally
     */
    suspend fun createCalendar(
        account: Account,
        homeSet: HomeSet,
        color: Int?,
        displayName: String,
        description: String?,
        timeZoneId: String?,
        supportVEVENT: Boolean,
        supportVTODO: Boolean,
        supportVJOURNAL: Boolean
    ) {
        val folderName = UUID.randomUUID().toString()
        val url = URLBuilder(homeSet.url)
            .appendPathSegments(folderName, encodeSlash = true)
            .build()
            .withTrailingSlash()

        // create collection on server
        createOnServer(
            account = account,
            url = url,
            method = "MKCALENDAR",
            xmlBody = generateMkColXml(
                addressBook = false,
                displayName = displayName,
                description = description,
                color = color,
                timezoneId = timeZoneId,
                supportsVEVENT = supportVEVENT,
                supportsVTODO = supportVTODO,
                supportsVJOURNAL = supportVJOURNAL
            )
        )

        // no HTTP error -> create collection locally
        val collection = Collection(
            serviceId = homeSet.serviceId,
            homeSetId = homeSet.id,
            url = url,
            type = Collection.TYPE_CALENDAR,
            displayName = displayName,
            description = description,
            color = color,
            timezoneId = timeZoneId,
            supportsVEVENT = supportVEVENT,
            supportsVTODO = supportVTODO,
            supportsVJOURNAL = supportVJOURNAL
        )
        dao.insertAsync(collection)

        // Trigger service detection (because the collection may actually have other properties than the ones we have inserted).
        // Some servers are known to change the supported components (VEVENT, …) after creation.
        RefreshCollectionsWorker.enqueue(context, homeSet.serviceId)
    }

    /** Deletes the given collection from the server and the database. */
    suspend fun deleteRemote(collection: Collection) {
        val service = serviceRepository.getBlocking(collection.serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        httpClientBuilder.get()
            .fromAccountAsync(account)
            .buildKtor()
            .use { httpClient ->
                try {
                    DavResource(httpClient, collection.url).delete {
                        // success, otherwise an exception would have been thrown → delete locally, too
                        delete(collection)
                    }
                } catch (e: HttpException) {
                    if (e is NotFoundException || e is GoneException) {
                        // HTTP 404 Not Found or 410 Gone (collection is not there anymore) -> delete locally, too
                        logger.info("Collection ${collection.url} not found on server, deleting locally")
                        delete(collection)
                    } else
                        throw e
                }
            }
    }

    suspend fun getSyncableByTopic(topic: String) = dao.getSyncableByPushTopic(topic)

    fun get(id: Long) = dao.get(id)
    suspend fun getAsync(id: Long) = dao.getAsync(id)

    fun getFlow(id: Long) = dao.getFlow(id)

    suspend fun getByService(serviceId: Long) = dao.getByService(serviceId)

    fun getByServiceAndUrl(serviceId: Long, url: String) = dao.getByServiceAndUrl(serviceId, url)

    fun getByServiceAndSync(serviceId: Long) = dao.getByServiceAndSync(serviceId)

    /**
     * Inserts or updates the collection.
     *
     * On update, it will _not_ update the flags
     *  - [Collection.sync] and
     *  - [Collection.forceReadOnly],
     *  but use the values of the already existing collection.
     *
     * @param newCollection Collection to be inserted or updated
     */
    fun insertOrUpdateByUrlRememberSync(newCollection: Collection) {
        db.runInTransaction {
            // remember locally set flags
            val oldCollection = dao.getByServiceAndUrl(newCollection.serviceId, newCollection.url.toString())
            val newCollectionWithFlags =
                if (oldCollection != null)
                    newCollection.copy(sync = oldCollection.sync, forceReadOnly = oldCollection.forceReadOnly)
                else
                    newCollection

            // commit new collection to database
            insertOrUpdateByUrl(newCollectionWithFlags)
        }
    }

    /**
     * Creates or updates the existing collection if it exists (URL)
     */
    fun insertOrUpdateByUrl(collection: Collection) {
        dao.insertOrUpdateByUrl(collection)
    }

    /**
     * Returns paging source to retrieve collections for given service, of given collection type and
     * depending on whether they are considered personal or not (see [HomeSet.personal]).
     */
    fun pageByServiceAndType(serviceId: Long, @CollectionType type: String, onlyPersonal: Boolean) =
        if (onlyPersonal)
            dao.pagePersonalByServiceAndType(serviceId, type)
        else
            dao.pageByServiceAndType(serviceId, type)

    /**
     * Sets the flag for whether read-only should be enforced on the local collection
     */
    suspend fun setForceReadOnly(id: Long, forceReadOnly: Boolean) {
        dao.updateForceReadOnly(id, forceReadOnly)
    }

    /**
     * Whether or not the local collection should be synced with the server
     */
    suspend fun setSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
    }

    /**
     * Updates the push subscription details for a collection with the given ID.
     *
     * @param id ID of the collection to update.
     * @param subscriptionUrl New push subscription URL (can be `null` to clear the value).
     * @param registeredEndpoint New registered endpoint URL (can be `null` to clear the value).
     * @param expires New expiration timestamp for the push subscription (can be `null` to clear the value).
     */
    suspend fun updatePushSubscription(id: Long, subscriptionUrl: String?, registeredEndpoint: String?, expires: Long?) {
        dao.updatePushSubscription(
            id = id,
            pushRegisteredEndpoint = registeredEndpoint,
            pushSubscription = subscriptionUrl,
            pushSubscriptionExpires = expires
        )
    }

    /**
     * Deletes the collection locally
     */
    fun delete(collection: Collection) {
        dao.delete(collection)
    }


    // helpers

    /**
     * Creates a new collection on the server using the specified account and URL.
     *
     * Uses the provided HTTP method and XML body to perform a MKCOL request (collection creation).
     *
     * @param account Account to use for authentication and server connection.
     * @param url Target URL where the collection should be created.
     * @param method HTTP method to use for the MKCOL request (should be `MKCALENDAR` or `MKCOL`).
     * @param xmlBody XML body containing collection metadata (e.g., display name, properties).
     */
    private suspend fun createOnServer(account: Account, url: Url, method: String, xmlBody: String) {
        httpClientBuilder.get()
            .fromAccountAsync(account)
            .buildKtor()
            .use { httpClient ->
                DavResource(httpClient, url).mkCol(
                    xmlBody = xmlBody,
                    methodName = method
                ) {
                    // success, otherwise an exception would have been thrown
                }
            }
    }

    private fun generateMkColXml(
        addressBook: Boolean,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timezoneId: String? = null,
        supportsVEVENT: Boolean = true,
        supportsVTODO: Boolean = true,
        supportsVJOURNAL: Boolean = true
    ): String {
        val writer = StringWriter()
        val serializer = XmlUtils.newSerializer()
        serializer.apply {
            setOutput(writer)

            startDocument("UTF-8", null)
            setPrefix("", WebDAV.NS_WEBDAV)
            setPrefix("CAL", CalDAV.NS_CALDAV)
            setPrefix("CARD", CardDAV.NS_CARDDAV)

            if (addressBook)
                startTag(WebDAV.NS_WEBDAV, "mkcol")
            else
                startTag(CalDAV.NS_CALDAV, "mkcalendar")

            insertTag(WebDAV.Set) {
                insertTag(WebDAV.Prop) {
                    insertTag(WebDAV.ResourceType) {
                        insertTag(WebDAV.Collection)
                        if (addressBook)
                            insertTag(CardDAV.Addressbook)
                        else
                            insertTag(CalDAV.Calendar)
                    }

                    displayName?.let {
                        insertTag(WebDAV.DisplayName) {
                            text(it)
                        }
                    }

                    if (addressBook) {
                        // addressbook-specific properties
                        description?.let {
                            insertTag(CardDAV.AddressbookDescription) {
                                text(it)
                            }
                        }
                    }
                }
            }
            if (addressBook)
                endTag(WebDAV.NS_WEBDAV, "mkcol")
            else
                endTag(CalDAV.NS_CALDAV, "mkcalendar")
            endDocument()
        }
        return writer.toString()
    }

    enum class PushCollectionsAmount {
        All,     // All collections are push-capable
        Some,    // Some collections are push-capable
        None;    // Zero collections are push-capable
    }

}