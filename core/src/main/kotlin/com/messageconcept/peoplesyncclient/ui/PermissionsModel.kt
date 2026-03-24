/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.util.packageChangedFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsModel @Inject constructor(
    @ApplicationContext val context: Context,
): ViewModel() {

    var needKeepPermissions by mutableStateOf<Boolean?>(null)
        private set

    init {
        viewModelScope.launch {
            // check permissions when a package (e.g. tasks app) is (un)installed
            packageChangedFlow(context).collect {
                checkPermissions()
            }
        }
    }

    fun checkPermissions() {
        val pm = context.packageManager

        // auto-reset permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            needKeepPermissions = pm.isAutoRevokeWhitelisted
        }
    }

}
