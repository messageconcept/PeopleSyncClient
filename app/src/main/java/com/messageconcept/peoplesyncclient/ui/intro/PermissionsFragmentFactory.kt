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
import com.messageconcept.peoplesyncclient.PermissionUtils
import com.messageconcept.peoplesyncclient.PermissionUtils.CONTACT_PERMISSIONS
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.ui.intro.IIntroFragmentFactory.ShowMode

class PermissionsFragmentFactory: IIntroFragmentFactory {

    override fun shouldBeShown(context: Context, settingsManager: SettingsManager): ShowMode {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS
        return if (PermissionUtils.haveAnyPermission(context, permissions))
            ShowMode.DONT_SHOW
        else
            ShowMode.SHOW
    }

    override fun create() = PermissionsIntroFragment()


    class PermissionsIntroFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
                inflater.inflate(R.layout.intro_permissions, container, false)

    }

}