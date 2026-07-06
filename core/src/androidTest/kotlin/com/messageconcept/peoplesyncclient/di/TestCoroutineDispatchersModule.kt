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
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Provides test dispatchers to be injected instead of the normal ones.
 *
 * The [standardTestDispatcher] is set as main dispatcher in [com.messageconcept.peoplesyncclient.HiltTestRunner],
 * so that tests can just use [kotlinx.coroutines.test.runTest] without providing [standardTestDispatcher].
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineDispatchersModule::class]
)
object TestCoroutineDispatchersModule {

    private val testScheduler = TestCoroutineScheduler()

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @Provides
    @SyncDispatcher
    fun syncDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun initMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    }

}