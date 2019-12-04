/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.db

data class Credentials(
        val userName: String? = null,
        val password: String? = null,
        val certificateAlias: String? = null,
        val baseUrl: String? = null
) {

    override fun toString(): String {
        val maskedPassword = "*****".takeIf { password != null }
        return "Credentials(userName=$userName, password=$maskedPassword, certificateAlias=$certificateAlias)"
    }

}