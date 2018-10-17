/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui

import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuItem

interface IAccountsDrawerHandler {

    fun initMenu(context: Context, menu: Menu)

    fun onNavigationItemSelected(activity: Activity, item: MenuItem)

}
