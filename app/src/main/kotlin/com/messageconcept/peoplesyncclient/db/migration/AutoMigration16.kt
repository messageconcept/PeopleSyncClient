/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db.migration

import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * The timezone column has been renamed to timezoneId, but still contains the VTIMEZONE.
 * So we need to parse the VTIMEZONE, extract the timezone ID and save it back.
 */
@ProvidedAutoMigrationSpec
@RenameColumn(tableName = "collection", fromColumnName = "timezone", toColumnName = "timezoneId")
class AutoMigration16 @Inject constructor(): AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // do nothing as the timezone column should be empty
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AutoMigrationModule {
        @Binds @IntoSet
        abstract fun provide(impl: AutoMigration16): AutoMigrationSpec
    }

}