/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.ContactsContract
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.messageconcept.peoplesyncclient.TestUtils
import com.messageconcept.peoplesyncclient.sync.account.TestAccount
import com.messageconcept.peoplesyncclient.sync.adapter.SyncAdapterImpl
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException

@HiltAndroidTest
class SyncAdapterImplTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncAdapterImplProvider: Provider<SyncAdapterImpl>

    @BindValue @MockK
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    lateinit var account: Account

    private var masterSyncStateBeforeTest = ContentResolver.getMasterSyncAutomatically()

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()

        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
    }

    @After
    fun tearDown() {
        ContentResolver.setMasterSyncAutomatically(masterSyncStateBeforeTest)
        TestAccount.remove(account)
    }


    @Test
    fun testSyncAdapter_onPerformSync_cancellation() = runTest {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker takes a long time
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } just Awaits

            val sync = launch {
                syncAdapter.onPerformSync(account, Bundle(), ContactsContract.AUTHORITY, mockk(), SyncResult())
            }

            // simulate incoming cancellation from sync framework
            syncAdapter.onSyncCanceled()

            // wait for sync to finish (should happen immediately)
            sync.join()
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_returnsAfterTimeout() {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker takes a long time
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } just Awaits

            mockkStatic("kotlinx.coroutines.TimeoutKt") {   // mock global extension function
                // immediate timeout (instead of really waiting)
                coEvery { withTimeout(any<Long>(), any<suspend CoroutineScope.() -> Unit>()) } throws CancellationException("Simulated timeout")

                syncAdapter.onPerformSync(account, Bundle(), ContactsContract.AUTHORITY, mockk(), SyncResult())
            }
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_runsInTime() {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker immediately returns with success
            val success = mockk<WorkInfo>()
            every { success.state } returns WorkInfo.State.SUCCEEDED
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } returns flow {
                emit(listOf(success))
                delay(60000)    // keep the flow active
            }

            // should just run
            syncAdapter.onPerformSync(account, Bundle(), ContactsContract.AUTHORITY, mockk(), SyncResult())
        }
    }

}
