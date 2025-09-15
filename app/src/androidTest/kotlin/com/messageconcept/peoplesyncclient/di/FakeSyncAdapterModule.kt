/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.di

import com.messageconcept.peoplesyncclient.sync.FakeSyncAdapter
import com.messageconcept.peoplesyncclient.sync.adapter.SyncAdapter
import com.messageconcept.peoplesyncclient.sync.adapter.SyncAdapterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SyncAdapterImpl.RealSyncAdapterModule::class])
abstract class FakeSyncAdapterModule {
    @Binds
    abstract fun provide(impl: FakeSyncAdapter): SyncAdapter
}