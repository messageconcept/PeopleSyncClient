/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.sync

import android.content.Context
import android.provider.ContactsContract

enum class SyncDataType {

    CONTACTS;

    /**
     * Returns authorities which exist for this sync data type. Used on [TASKS] the method
     * may return an empty list if there are no tasks providers (installed tasks apps).
     *
     * @return list of authorities matching this data type
     */
    fun possibleAuthorities(): List<String> =
        when (this) {
            CONTACTS -> listOf(ContactsContract.AUTHORITY)
        }

    /**
     * Returns the authority corresponding to this datatype.
     * When more than one tasks provider exists (tasks apps installed) the authority for the active
     * tasks provider (user selected tasks app) is returned.
     *
     * @param context android context used to determine the active/selected tasks provider
     * @return the authority matching this data type or *null* for [TASKS] if no tasks app is installed
     */
    fun currentAuthority(context: Context): String? =
        when (this) {
            CONTACTS -> ContactsContract.AUTHORITY
        }


    companion object {

        fun fromAuthority(authority: String): SyncDataType {
            return when (authority) {
                ContactsContract.AUTHORITY ->
                    CONTACTS
                else -> throw IllegalArgumentException("Unknown authority: $authority")
            }
        }

    }

}
