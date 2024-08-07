/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messageconcept.peoplesyncclient.ui.PermissionsActivity
import com.messageconcept.peoplesyncclient.ui.PermissionsContent
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import com.messageconcept.peoplesyncclient.util.PermissionUtils.CONTACT_PERMISSIONS
class PermissionsIntroPage: IntroPage {

    var model: PermissionsActivity.Model? = null

    override fun getShowPolicy(application: Application): IntroPage.ShowPolicy {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS
        return if (PermissionUtils.haveAnyPermission(application, permissions))
            IntroPage.ShowPolicy.DONT_SHOW
        else
            IntroPage.ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        val newModel: PermissionsActivity.Model = viewModel()
        model = newModel

        PermissionsContent(model = newModel)
    }

    // Check whether permissions have changed after user comes back from settings app
    override fun onResume(application: Application) {
        model?.checkPermissions()
    }

}