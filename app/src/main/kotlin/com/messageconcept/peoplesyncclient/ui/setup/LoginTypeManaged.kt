/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.setup

import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.Credentials
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.ui.AccountsActivity
import com.messageconcept.peoplesyncclient.ui.composable.Assistant
import com.messageconcept.peoplesyncclient.ui.composable.PasswordTextField
import java.net.URI

object LoginTypeManaged : LoginType {

    override val title
        get() = R.string.login_type_url

    override val helpUrl: Uri?
        get() = null

    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit,
        onFinish: () -> Unit
    ) {
        LoginTypeManaged_Content(
            loginInfo = loginInfo,
            onUpdateLoginInfo = onUpdateLoginInfo,
            onLogin = onDetectResources
        )
    }

}

@Composable
fun LoginTypeManaged_Content(
    loginInfo: LoginInfo,
    onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit = {},
    onLogin: () -> Unit = {}
) {
    val context = LocalContext.current

    val baseUrl by remember { mutableStateOf(
        loginInfo.baseUri?.takeIf {
            it.scheme.equals("http", ignoreCase = true) ||
            it.scheme.equals("https", ignoreCase = true)
        }?.toString() ?: ""
    ) }
    var username by remember { mutableStateOf(loginInfo.credentials?.username ?: "") }
    var password by remember { mutableStateOf(loginInfo.credentials?.password ?: "") }


    val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
    val appRestrictions = restrictionsManager.applicationRestrictions

    val isBaseUrlManaged = !appRestrictions.getString(AccountSettings.KEY_LOGIN_BASE_URL).isNullOrEmpty()
    val isUsernameManaged = !appRestrictions.getString(AccountSettings.KEY_LOGIN_USER_NAME).isNullOrEmpty()
    val isPasswordManaged = !appRestrictions.getString(AccountSettings.KEY_LOGIN_PASSWORD).isNullOrEmpty()

    // If all three parameters are provided, proceed immediately with the next step
    if (isBaseUrlManaged && isUsernameManaged && isPasswordManaged)
        onLogin()

    val newLoginInfo = LoginInfo(
        baseUri = try {
            URI(
                if (baseUrl.startsWith("http://", ignoreCase = true) || baseUrl.startsWith("https://", ignoreCase = true))
                    baseUrl
                else
                    "https://$baseUrl"
            )
        } catch (_: Exception) {
            null
        },
        credentials = Credentials(
            username = username,
            password = password,
            baseUrl = if (isBaseUrlManaged) baseUrl else null
        )
    )
    onUpdateLoginInfo(newLoginInfo)

    val ok =
        newLoginInfo.baseUri != null && (
            newLoginInfo.baseUri.scheme.equals("http", ignoreCase = true) ||
            newLoginInfo.baseUri.scheme.equals("https",ignoreCase = true)
        ) && newLoginInfo.credentials != null &&
        newLoginInfo.credentials.username?.isNotEmpty() == true &&
        newLoginInfo.credentials.password?.isNotEmpty() == true

    val focusRequester = remember { FocusRequester() }
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = ok,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_managed),
                style = MaterialTheme.typography.h5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            OutlinedTextField(
                enabled = !isUsernameManaged,
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_user_name)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            PasswordTextField(
                enabled = !isPasswordManaged,
                password = password,
                onPasswordChange = { password = it },
                labelText = stringResource(R.string.login_password),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (ok)
                        onLogin()
                }),
                modifier = if (isUsernameManaged)
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                else
                    Modifier.fillMaxWidth()
            )
            // This is a bit of a hack to not end up on the Login Type selection activity
            BackHandler {
                context.startActivity(Intent(context, AccountsActivity::class.java))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun LoginTypeManaged_Content_Preview() {
    LoginTypeManaged_Content(LoginInfo())
}