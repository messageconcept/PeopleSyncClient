/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.annotation.OpenForTesting
import androidx.core.content.contentValuesOf
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.repository.DavCollectionRepository
import com.messageconcept.peoplesyncclient.repository.DavServiceRepository
import com.messageconcept.peoplesyncclient.resource.LocalAddressBookStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * [com.messageconcept.peoplesyncclient.sync.Syncer] now users collection IDs instead of URLs to match
 * local and remote (database) collections.
 *
 * This migration writes the database collection IDs to the local collections. If we wouldn't do that,
 * the syncer would not be able to find the correct local collection for a remote collection and
 * all local collections would be deleted and re-created.
 */
class AccountSettingsMigration20 @Inject constructor(
    @ApplicationContext context: Context,
    private val addressBookStore: LocalAddressBookStore,
    private val collectionRepository: DavCollectionRepository,
    private val serviceRepository: DavServiceRepository,
): AccountSettingsMigration {

    val accountManager = AccountManager.get(context)

    override fun migrate(account: Account) {
        runBlocking {
            serviceRepository.getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { cardDavService ->
                migrateAddressBooks(account, cardDavService.id)
            }
        }
    }

    @OpenForTesting
    internal fun migrateAddressBooks(account: Account, cardDavServiceId: Long) {
        addressBookStore.acquireContentProvider()?.use { provider ->
            for (addressBook in addressBookStore.getAll(account, provider)) {
                val url = accountManager.getUserData(addressBook.addressBookAccount, ADDRESS_BOOK_USER_DATA_URL) ?: continue
                val collection = collectionRepository.getByServiceAndUrl(cardDavServiceId, url) ?: continue
                addressBook.dbCollectionId = collection.id
            }
        }
    }

    companion object {
        internal const val ADDRESS_BOOK_USER_DATA_URL = "url"
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(20)
        abstract fun provide(impl: AccountSettingsMigration20): AccountSettingsMigration
    }

}