/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.repository

import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Principal
import javax.inject.Inject

class PrincipalRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.principalDao()

    fun getBlocking(id: Long): Principal = dao.get(id)

}