/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account",
    indices = [
        Index("name", unique = true)
    ]
)
data class DbAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String
)
