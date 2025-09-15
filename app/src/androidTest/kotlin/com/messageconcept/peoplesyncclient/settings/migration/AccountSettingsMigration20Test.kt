/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.settings.migration

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.core.content.contentValuesOf
import androidx.core.database.getLongOrNull
import androidx.test.rule.GrantPermissionRule
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.resource.LocalTestAddressBookProvider
import com.messageconcept.peoplesyncclient.sync.account.setAndVerifyUserData
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration20Test {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var migration: AccountSettingsMigration20

    @Inject
    lateinit var localTestAddressBookProvider: LocalTestAddressBookProvider

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val permissionsRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
    )

    val accountManager by lazy { AccountManager.get(context) }

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testMigrateAddressBooks_UrlMatchesCollection() {
        // set up legacy address-book with URL, but without collection ID
        val account = Account("test", "test")
        val url = "https://example.com/"

        db.serviceDao().insertOrReplace(Service(id = 1, accountName = account.name, type = Service.TYPE_CARDDAV, principal = null))
        val collectionId = db.collectionDao().insert(Collection(
            serviceId = 1,
            type = Collection.Companion.TYPE_ADDRESSBOOK,
            url = url.toHttpUrl()
        ))

        localTestAddressBookProvider.provide(account, mockk(relaxed = true), GroupMethod.GROUP_VCARDS) { addressBook ->

            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME, account.name)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE, account.type)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, AccountSettingsMigration20.ADDRESS_BOOK_USER_DATA_URL, url)
            accountManager.setAndVerifyUserData(addressBook.addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID, null)

            migration.migrateAddressBooks(account, cardDavServiceId = 1)

            assertEquals(
                collectionId,
                accountManager.getUserData(addressBook.addressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID).toLongOrNull()
            )
        }
    }

}