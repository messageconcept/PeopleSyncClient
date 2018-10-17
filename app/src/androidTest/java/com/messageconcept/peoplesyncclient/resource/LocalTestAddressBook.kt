/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.vcard4android.GroupMethod

class LocalTestAddressBook(
    context: Context,
    provider: ContentProviderClient,
    override val groupMethod: GroupMethod
): LocalAddressBook(context, ACCOUNT, provider) {

    companion object {
        val ACCOUNT = Account("LocalTestAddressBook", "com.messageconcept.peoplesyncclient.test")
    }

    override var mainAccount: Account
        get() = throw NotImplementedError()
        set(value) = throw NotImplementedError()

    override var readOnly: Boolean
        get() = false
        set(value) = throw NotImplementedError()


    fun clear() {
        for (contact in queryContacts(null, null))
            contact.delete()
        for (group in queryGroups(null, null))
            group.delete()
    }

}