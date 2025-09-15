/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import android.content.Context
import androidx.compose.runtime.Composable
import com.messageconcept.peoplesyncclient.ui.PermissionsModel
import com.messageconcept.peoplesyncclient.ui.PermissionsScreen
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import com.messageconcept.peoplesyncclient.util.PermissionUtils.CONTACT_PERMISSIONS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PermissionsIntroPage @Inject constructor(
    @ApplicationContext private val context: Context
): IntroPage() {

    var model: PermissionsModel? = null

    override fun getShowPolicy(): ShowPolicy {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS
        return if (PermissionUtils.haveAnyPermission(context, permissions))
            ShowPolicy.DONT_SHOW
        else
            ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        PermissionsScreen()
    }

}