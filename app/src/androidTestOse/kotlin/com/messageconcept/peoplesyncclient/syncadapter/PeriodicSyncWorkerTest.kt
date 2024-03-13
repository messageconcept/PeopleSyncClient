/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.TestWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.TestUtils
import com.messageconcept.peoplesyncclient.TestUtils.workScheduledOrRunning
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.NotificationUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors

@HiltAndroidTest
class PeriodicSyncWorkerTest {

    companion object {

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        private val accountManager = AccountManager.get(context)
        private val account = Account("Test Account", context.getString(R.string.account_type))
        private val fakeCredentials = Credentials("test", "test")

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
            // However, we need notification channels for the ongoing work notifications.
            NotificationUtils.createChannels(context)

            // Initialize WorkManager for instrumentation tests.
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

            assertTrue(AccountUtils.createAccount(context, account, AccountSettings.initialUserData(fakeCredentials)))
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
        }

        @AfterClass
        @JvmStatic
        fun removeAccount() {
            accountManager.removeAccountExplicitly(account)
        }

    }

    private val executor = Executors.newSingleThreadExecutor()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun enable_enqueuesPeriodicWorker() {
        PeriodicSyncWorker.enable(context, account, ContactsContract.AUTHORITY, 60, false)
        val workerName = PeriodicSyncWorker.workerName(account, ContactsContract.AUTHORITY)
        assertTrue(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun disable_removesPeriodicWorker() {
        PeriodicSyncWorker.enable(context, account, ContactsContract.AUTHORITY, 60, false)
        PeriodicSyncWorker.disable(context, account, ContactsContract.AUTHORITY)
        val workerName = PeriodicSyncWorker.workerName(account, ContactsContract.AUTHORITY)
        assertFalse(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun doWork_cancelsItselfOnInvalidAccount() {
        val invalidAccount = Account("invalid", context.getString(R.string.account_type))
        val authority = ContactsContract.AUTHORITY

        // Enable the PeriodicSyncWorker
        PeriodicSyncWorker.enable(context, invalidAccount, authority, 15*60, false)
        assertTrue(workScheduledOrRunning(context, PeriodicSyncWorker.workerName(account, authority)))

        // Run PeriodicSyncWorker as TestWorker
        val inputData = workDataOf(
            PeriodicSyncWorker.ARG_AUTHORITY to authority,
            PeriodicSyncWorker.ARG_ACCOUNT_NAME to invalidAccount.name,
            PeriodicSyncWorker.ARG_ACCOUNT_TYPE to invalidAccount.type
        )
        val result = TestWorkerBuilder<PeriodicSyncWorker>(context, executor, inputData).build().doWork()

        // Verify that the PeriodicSyncWorker cancelled itself
        assertTrue(result is androidx.work.ListenableWorker.Result.Failure)
        assertFalse(workScheduledOrRunning(context, PeriodicSyncWorker.workerName(invalidAccount, authority)))
    }

    @Test
    fun doWork_immediatelyEnqueuesSyncWorkerForGivenAuthority() {
        val authorities = listOf(
            context.getString(R.string.address_books_authority)
        )
        for (authority in authorities) {
            val inputData = workDataOf(
                PeriodicSyncWorker.ARG_AUTHORITY to authority,
                PeriodicSyncWorker.ARG_ACCOUNT_NAME to account.name,
                PeriodicSyncWorker.ARG_ACCOUNT_TYPE to account.type
            )
            // Run PeriodicSyncWorker as TestWorker
            TestWorkerBuilder<PeriodicSyncWorker>(context, executor, inputData).build().doWork()

            // Check the PeriodicSyncWorker enqueued the right SyncWorker
            assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context,
                SyncWorker.workerName(account, authority)
            ))
        }
    }

}