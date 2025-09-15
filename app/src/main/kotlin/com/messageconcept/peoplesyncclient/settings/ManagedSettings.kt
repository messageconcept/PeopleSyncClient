/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ManagedSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val KEY_LOGIN_BASE_URL = "login_base_url"
        private const val KEY_LOGIN_USER_NAME = "login_user_name"
        private const val KEY_LOGIN_PASSWORD = "login_password"
    }

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    private var restrictions: Bundle

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    // update cached app restrictions
                    restrictions = restrictionsManager.applicationRestrictions
                }
            }
        }
    }

    init {
        // cache app restrictions to avoid unnecessary disk access
        restrictions = restrictionsManager.applicationRestrictions
        context.registerReceiver(broadCastReceiver, IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED))
    }

    fun getBaseUrl(): String? {
        return restrictions.getString(KEY_LOGIN_BASE_URL)
    }

    fun getUsername(): String? {
        return restrictions.getString(KEY_LOGIN_USER_NAME)
    }

    fun getPassword(): String? {
        return restrictions.getString(KEY_LOGIN_PASSWORD)
    }
}