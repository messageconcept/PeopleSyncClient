/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.ui.composable.BasicTopAppBar
import com.messageconcept.peoplesyncclient.ui.composable.CardWithImage
import com.messageconcept.peoplesyncclient.ui.composable.PermissionSwitchRow
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import kotlinx.coroutines.launch
import java.util.logging.Level

class PermissionsActivity: AppCompatActivity() {

    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        BasicTopAppBar(
                            titleStringRes = R.string.app_settings_security_app_permissions
                        )
                    }
                ) { paddingValues ->
                    PermissionsContent(modifier = Modifier.padding(paddingValues), model)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        var needKeepPermissions by mutableStateOf(false)

        init {
            viewModelScope.launch {
                checkPermissions()
            }
        }

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                needKeepPermissions = pm.isAutoRevokeWhitelisted
            }
        }
    }

}


@Composable
fun PermissionsContent(
    modifier: Modifier = Modifier,
    model: PermissionsActivity.Model
) {
    val context = LocalContext.current

    PermissionsCardContent(
        keepPermissions = model.needKeepPermissions,
        onKeepPermissionsRequested = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(
                    Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                    Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                )
                try {
                    context.startActivity(intent)
                    Toast.makeText(context, R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't start Keep Permissions activity", e)
                }
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PermissionsCard_Preview() {
    AppTheme {
        PermissionsCardContent(
            keepPermissions = true,
            onKeepPermissionsRequested = {}
        )
    }
}

@Composable
fun PermissionsCardContent(
    keepPermissions: Boolean?,
    onKeepPermissionsRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        CardWithImage(
            title = stringResource(R.string.permissions_title),
            message = stringResource(
                R.string.permissions_text,
                stringResource(R.string.app_name)
            ),
            image = painterResource(R.drawable.intro_permissions),
            modifier = Modifier.padding(8.dp)
        ) {
            if (keepPermissions != null) {
                PermissionSwitchRow(
                    text = stringResource(R.string.permissions_autoreset_title),
                    summaryWhenGranted = stringResource(R.string.permissions_autoreset_status_on),
                    summaryWhenNotGranted = stringResource(R.string.permissions_autoreset_status_off),
                    allPermissionsGranted = keepPermissions,
                    onLaunchRequest = onKeepPermissionsRequested,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            val allPermissions = mutableListOf<String>()
            allPermissions.addAll(PermissionUtils.CONTACT_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                allPermissions += Manifest.permission.POST_NOTIFICATIONS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                PermissionSwitchRow(
                    text = stringResource(R.string.permissions_notification_title),
                    summaryWhenGranted = stringResource(R.string.permissions_notification_status_on),
                    summaryWhenNotGranted = stringResource(R.string.permissions_notification_status_off),
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

            PermissionSwitchRow(
                text = stringResource(R.string.permissions_contacts_title),
                summaryWhenGranted = stringResource(R.string.permissions_contacts_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_contacts_status_off),
                permissions = PermissionUtils.CONTACT_PERMISSIONS.toList(),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.permissions_app_settings_hint),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 24.dp)
            )

            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = { PermissionUtils.showAppSettings(context) }
            ) {
                Text(stringResource(R.string.permissions_app_settings).uppercase())
            }
        }
    }
}