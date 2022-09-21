/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import com.messageconcept.peoplesyncclient.App
import com.messageconcept.peoplesyncclient.R
import javax.inject.Inject

/**
 * Default menu items control
 */
class OseAccountsDrawerHandler @Inject constructor(): BaseAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {

            R.id.nav_website ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity)
                )

            else ->
                super.onNavigationItemSelected(activity, item)
        }
    }

}