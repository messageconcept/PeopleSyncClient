/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.di

import com.messageconcept.peoplesyncclient.di.qualifier.DefaultDispatcher
import com.messageconcept.peoplesyncclient.di.qualifier.IoDispatcher
import com.messageconcept.peoplesyncclient.di.qualifier.MainDispatcher
import com.messageconcept.peoplesyncclient.di.qualifier.SyncDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CoroutineDispatchersModule {

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * A dispatcher for background sync operations. They're not run on [ioDispatcher] because there can
     * be many long-blocking operations at the same time which should never block other I/O operations
     * like database access for the UI.
     *
     * It uses the I/O dispatcher and limits the number of parallel operations to the number of available processors.
     *
     * **Never use the syncDispatcher outside the SyncManager because this can cause deadlocks!**
     * For instance don't use it for sync-related I/O.
     */
    @Provides
    @SyncDispatcher
    @Singleton
    fun syncDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

}