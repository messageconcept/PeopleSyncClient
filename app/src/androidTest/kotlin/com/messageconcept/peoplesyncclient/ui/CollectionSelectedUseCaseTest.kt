/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.repository.DavCollectionRepository
import com.messageconcept.peoplesyncclient.repository.DavServiceRepository
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class CollectionSelectedUseCaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    val collection = Collection(
        id = 2,
        serviceId = 1,
        type = Collection.Companion.TYPE_CALENDAR,
        url = "https://example.com".toHttpUrl()
    )

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    val service = Service(
        id = 1,
        type = Service.Companion.TYPE_CALDAV,
        accountName = "test@example.com"
    )

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @BindValue
    @RelaxedMockK
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var useCase: CollectionSelectedUseCase

    @Before
    fun setUp() {
        hiltRule.inject()

        serviceRepository.insertOrReplaceBlocking(service)
        collectionRepository.insertOrUpdateByUrl(collection)
    }

    @After
    fun tearDown() {
        serviceRepository.deleteAllBlocking()
    }


    @Test
    fun testHandleWithDelay() = runTest {
        useCase.handleWithDelay(collectionId = collection.id)

        advanceUntilIdle()
        coVerify {
            syncWorkerManager.enqueueOneTimeAllAuthorities(any())
        }
    }

}