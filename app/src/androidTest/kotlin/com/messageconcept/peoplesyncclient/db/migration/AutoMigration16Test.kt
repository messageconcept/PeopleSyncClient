/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db.migration

import com.messageconcept.peoplesyncclient.db.Collection.Companion.TYPE_CALENDAR
import com.messageconcept.peoplesyncclient.db.Service
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@HiltAndroidTest
class AutoMigration16Test: DatabaseMigrationTest(toVersion = 16) {

    @Test
    fun testMigrate_WithoutTimezone() = testMigration(
        prepare = { db ->
            db.execSQL(
                "INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)",
                arrayOf(1, "test", Service.Companion.TYPE_CALDAV)
            )
            db.execSQL(
                "INSERT INTO collection (id, serviceId, type, url, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(1, 1, TYPE_CALENDAR, "https://example.com", true, true, false, false)
            )
        }
    ) { db ->
        db.query("SELECT timezoneId FROM collection WHERE id=1").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }
    }

}