/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.setup

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.TestUtils.getOrAwaitValue
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.servicedetection.DavResourceFinder
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import javax.inject.Inject

// COMMENTED OUT because it doesn't run reliably [see https://github.com/bitfireAT/davx5/pull/320]
/*@HiltAndroidTest
class AccountDetailsFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()     // required for TestUtils: LiveData.getOrAwaitValue()


    @Inject
    lateinit var db: AppDatabase
    @Inject
    lateinit var settingsManager: SettingsManager

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fakeCredentials = Credentials("test", "test")

    @Before
    fun setUp() {
        hiltRule.inject()

        // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
        // However, we need notification channels for the ongoing work notifications.
        NotificationUtils.createChannels(targetContext)

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, config)
    }

    @After
    fun tearDown() {
        // Remove accounts created by tests
        val am = AccountManager.get(targetContext)
        val accounts = am.getAccountsByType(targetContext.getString(R.string.account_type))
        for (account in accounts) {
            am.removeAccountExplicitly(account)
        }
    }


    @Test
    fun testModel_CreateAccount_configuresContactsAndCalendars() {
        val accountName = "MyAccountName"
        val emptyServiceInfo = DavResourceFinder.Configuration.ServiceInfo()
        val config = DavResourceFinder.Configuration(emptyServiceInfo, emptyServiceInfo, false, "")

        // Create account -> should also set sync interval in settings
        val accountCreated = AccountDetailsFragment.Model(targetContext, db, settingsManager)
            .createAccount(accountName, fakeCredentials, config, GroupMethod.GROUP_VCARDS)
        assertTrue(accountCreated.getOrAwaitValue(5))

        // Get the created account
        val account = AccountManager.get(targetContext)
            .getAccountsByType(targetContext.getString(R.string.account_type))
            .first { account -> account.name == accountName }

        for (authority in listOf(
            targetContext.getString(R.string.address_books_authority)
        )) {
            // Check isSyncable was set
            assertEquals(1, ContentResolver.getIsSyncable(account, authority))

            // Check default sync interval was set for
            // [AccountSettings.KEY_SYNC_INTERVAL_ADDRESSBOOKS],
            // [AccountSettings.KEY_SYNC_INTERVAL_CALENDARS]
            assertEquals(
                settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL),
                AccountSettings(targetContext, account).getSyncInterval(authority)
            )
        }
    }

}*/