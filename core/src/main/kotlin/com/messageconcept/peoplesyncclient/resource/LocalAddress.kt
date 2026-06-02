/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.resource

import at.bitfire.synctools.mapping.contacts.Contact

interface LocalAddress: LocalResource {

    fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int)

}