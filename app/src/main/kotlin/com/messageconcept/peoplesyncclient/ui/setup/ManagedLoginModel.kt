/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.messageconcept.peoplesyncclient.settings.Credentials
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.util.DavUtils.toURIorNull
import com.messageconcept.peoplesyncclient.util.trimToNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = ManagedLoginModel.Factory::class)
class ManagedLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    val managedSettings: ManagedSettings,
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): ManagedLoginModel
    }

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: String = "",
        var isUsernameManaged: Boolean = false,
        var isPasswordManaged: Boolean = false,
        var baseUrl: String = "",
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.toURIorNull()

        val canContinue = uri != null && username.isNotEmpty() && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo =
            LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = username.trimToNull(),
                    password = password.trimToNull()?.toCharArray(),
                    baseUrl = baseUrl.trimToNull()
                )
            )

    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = UiState(
            url = initialLoginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password?.concatToString() ?: "",
            isUsernameManaged = !managedSettings.getUsername().isNullOrEmpty(),
            isPasswordManaged = !managedSettings.getPassword().isNullOrEmpty(),
            baseUrl = managedSettings.getBaseUrl() ?: "",
        )
    }

    fun setUsername(username: String) {
        uiState = uiState.copy(username = username)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

}