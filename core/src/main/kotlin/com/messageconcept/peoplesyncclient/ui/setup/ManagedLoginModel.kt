/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.ui.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.trimToNull
import com.messageconcept.peoplesyncclient.settings.Credentials
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.util.DavUtils.toURIorNull
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
        val password: TextFieldState = TextFieldState(),
        var isUsernameManaged: Boolean = false,
        var isPasswordManaged: Boolean = false,
        var baseUrl: String = "",
        var organization: String = "",
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.toURIorNull()

        val canContinue     // we have to use get() because password is not immutable
            get() = uri != null && username.isNotEmpty() && password.text.toString().isNotEmpty()

        fun asLoginInfo(): LoginInfo =
            LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = username.trimToNull(),
                    password = password.text.toString().trimToNull()?.toSensitiveString(),
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
            password = TextFieldState(initialLoginInfo.credentials?.password?.asString() ?: ""),
            isUsernameManaged = !managedSettings.getUsername().isNullOrEmpty(),
            isPasswordManaged = !managedSettings.getPassword().isNullOrEmpty(),
            baseUrl = managedSettings.getBaseUrl() ?: "",
            organization = managedSettings.getOrganization() ?: "",
        )
    }

    fun setUsername(username: String) {
        uiState = uiState.copy(username = username)
    }

}