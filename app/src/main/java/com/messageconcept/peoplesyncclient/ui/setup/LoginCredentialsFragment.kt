/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.setup

import android.content.Intent
import androidx.fragment.app.Fragment

interface LoginCredentialsFragment {

    fun getFragment(intent: Intent): Fragment?

}