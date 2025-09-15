/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db.migration

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class AutoMigration18Test : DatabaseMigrationTest(toVersion = 18) {

    @Test
    fun testMigration_AllAuthorities() = testMigration(
        prepare = { db ->
            // Insert service and collection to respect relation constraints
            db.execSQL("INSERT INTO service (id, accountName, type) VALUES (?, ?, ?)", arrayOf<Any?>(1, "test", 1))
            listOf(1L, 2L, 3L).forEach { id ->
                db.execSQL(
                    "INSERT INTO collection (id, serviceId, url, type, privWriteContent, privUnbind, forceReadOnly, sync) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(id, 1, "https://example.com/$id", 1, 1, 1, 0, 1)
                )
            }
            // Insert some syncstats with authorities and lastSync times
            val syncstats = listOf(
                Entry(1, 1, "com.android.contacts", 1000),
                Entry(6, 1, "unknown.authority", 1000),  // ignored
            )
            syncstats.forEach { (id, collectionId, authority, lastSync) ->
                db.execSQL(
                    "INSERT INTO syncstats (id, collectionId, authority, lastSync) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(id, collectionId, authority, lastSync)
                )
            }
        },
        validate = { db ->
            db.query("SELECT id, collectionId, dataType FROM syncstats ORDER BY id").use { cursor ->
                val found = mutableListOf<Entry>()
                db.query("SELECT id, collectionId, dataType FROM syncstats ORDER BY id").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val colIdx = cursor.getColumnIndex("collectionId")
                    val typeIdx = cursor.getColumnIndex("dataType")
                    while (cursor.moveToNext())
                        found.add(
                            Entry(cursor.getInt(idIdx), cursor.getLong(colIdx), cursor.getString(typeIdx))
                        )
                }

                // Expect one TASKS row per collection (collections 1, 2, 3)
                assertEquals(
                    listOf(
                        Entry(1, 1, "CONTACTS"),
                    ), found
                )
            }
        }
    )

    data class Entry(
        val id: Int,
        val collectionId: Long,
        val dataType: String? = null,
        val lastSync: Long? = null
    )
}
