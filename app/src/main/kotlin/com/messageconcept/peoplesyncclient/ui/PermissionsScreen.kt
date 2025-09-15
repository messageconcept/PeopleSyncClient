/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.ui.composable.CardWithImage
import com.messageconcept.peoplesyncclient.ui.composable.PermissionSwitchRow
import com.messageconcept.peoplesyncclient.util.PermissionUtils
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Used when "Manage permissions" is selected in the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateUp: () -> Unit
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_settings_security_app_permissions)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateUp
                        ) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    }
                )
            }
        ) { paddingValues ->
            PermissionsScreen(modifier = Modifier.padding(paddingValues))
        }
    }
}

/**
 * Used by [PermissionsScreen] and directly embedded in [com.messageconcept.peoplesyncclient.ui.intro.PermissionsIntroPage].
 */
@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    model: PermissionsModel = viewModel()
) {
    // check permissions when the lifecycle owner (for instance Activity) is resumed
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = object: DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                model.checkPermissions()
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current
    PermissionsScreen(
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
                    Logger.getGlobal().log(Level.WARNING, "Couldn't start Keep Permissions activity", e)
                }
            }
        },
        modifier = modifier
    )
}


@Composable
fun PermissionsScreen(
    keepPermissions: Boolean?,
    onKeepPermissionsRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            imageContentScale = ContentScale.Fit,
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
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp)
            )

            val context = LocalContext.current
            OutlinedButton(
                modifier = Modifier.padding(vertical = 8.dp),
                onClick = { PermissionUtils.showAppSettings(context) }
            ) {
                Text(stringResource(R.string.permissions_app_settings))
            }
        }
    }
}

@Composable
@Preview
fun PermissionsCard_Preview() {
    AppTheme {
        PermissionsScreen(
            keepPermissions = true,
            onKeepPermissionsRequested = {},
        )
    }
}