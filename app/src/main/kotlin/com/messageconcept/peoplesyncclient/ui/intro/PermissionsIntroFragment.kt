/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import com.messageconcept.peoplesyncclient.util.PermissionUtils.CONTACT_PERMISSIONS
import javax.inject.Inject

class PermissionsIntroFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.intro_permissions, container, false)


    class Factory @Inject constructor(): IntroFragmentFactory {

        override fun getOrder(context: Context): Int {
            // show PermissionsFragment as intro fragment when no permissions are granted
            val permissions = CONTACT_PERMISSIONS
            return if (PermissionUtils.haveAnyPermission(context, permissions))
                IntroFragmentFactory.DONT_SHOW
            else
                50
        }

        override fun create() = PermissionsIntroFragment()

    }

}