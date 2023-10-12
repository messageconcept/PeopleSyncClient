/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.setup

import androidx.lifecycle.ViewModel
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.servicedetection.DavResourceFinder
import java.net.URI

class LoginModel: ViewModel() {

    var baseURI: URI? = null
    var credentials: Credentials? = null

    var configuration: DavResourceFinder.Configuration? = null

    /**
     * Account name that should be used as default account name when no email addresses have been found.
     */
    var suggestedAccountName: String? = null

}
