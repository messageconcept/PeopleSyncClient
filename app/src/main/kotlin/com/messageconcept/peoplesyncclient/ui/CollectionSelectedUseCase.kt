/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.accounts.Account
import com.messageconcept.peoplesyncclient.di.ApplicationScope
import com.messageconcept.peoplesyncclient.di.DefaultDispatcher
import com.messageconcept.peoplesyncclient.push.PushRegistrationManager
import com.messageconcept.peoplesyncclient.repository.AccountRepository
import com.messageconcept.peoplesyncclient.repository.DavCollectionRepository
import com.messageconcept.peoplesyncclient.repository.DavServiceRepository
import com.messageconcept.peoplesyncclient.sync.worker.SyncWorkerManager
import com.messageconcept.peoplesyncclient.ui.CollectionSelectedUseCase.Companion.DELAY_MS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Performs actions when a collection was (un)selected for synchronization.
 *
 * @see handleWithDelay
 */
class CollectionSelectedUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val collectionRepository: DavCollectionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val pushRegistrationManager: PushRegistrationManager,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager
) {

    /**
     * After a delay of [DELAY_MS] ms:
     *
     * 1. Enqueues a one-time sync for account of the collection.
     * 2. Updates push subscriptions for the service of the collection.
     *
     * Resets delay when called again before delay finishes.
     *
     * @param collectionId  ID of the collection that was (un)selected for synchronization
     */
    suspend fun handleWithDelay(collectionId: Long) {
        val collection = collectionRepository.getAsync(collectionId) ?: return
        val service = serviceRepository.get(collection.serviceId) ?: return
        val account = accountRepository.fromName(service.accountName)

        // Atomically cancel, launch and remember delay coroutine of given account
        delayJobs.compute(account) { _, previousJob ->
            // Stop previous delay, if exists
            previousJob?.cancel()

            applicationScope.launch(defaultDispatcher) {
                // wait
                delay(DELAY_MS)

                // enqueue sync
                syncWorkerManager.enqueueOneTimeAllAuthorities(account)

                // update push subscriptions
                pushRegistrationManager.update(service.id)

                // remove complete job
                delayJobs -= account
            }
        }
    }


    companion object {

        /**
         * Length of delay in milliseconds
         */
        const val DELAY_MS = 5000L     // 5 seconds

        private val delayJobs: ConcurrentHashMap<Account, Job> = ConcurrentHashMap()

    }

}