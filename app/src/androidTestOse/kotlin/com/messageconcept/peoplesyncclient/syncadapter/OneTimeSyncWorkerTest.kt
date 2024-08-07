/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.syncadapter

import android.content.Context
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.messageconcept.peoplesyncclient.TestUtils
import com.messageconcept.peoplesyncclient.syncadapter.SyncManagerTest.Companion.account
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Test

@HiltAndroidTest
class OneTimeSyncWorkerTest {

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testEnqueue_enqueuesWorker() {
        OneTimeSyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
        val workerName = OneTimeSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        Assert.assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }

}