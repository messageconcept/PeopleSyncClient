/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.CancellationSignal
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messageconcept.peoplesyncclient.Constants
import com.messageconcept.peoplesyncclient.Constants.withStatParams
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.servicedetection.DavResourceFinder
import com.messageconcept.peoplesyncclient.ui.DebugInfoActivity
import com.messageconcept.peoplesyncclient.ui.UiUtils.toAnnotatedString
import com.messageconcept.peoplesyncclient.ui.widget.ClickableTextWithLink

@Composable
fun DetectResourcesPage(
    loginInfo: LoginInfo,
    onSuccess: (DavResourceFinder.Configuration) -> Unit,
    model: LoginModel = viewModel()
) {
    val cancellationSignal = remember { CancellationSignal() }
    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal.cancel()
        }
    }

    LaunchedEffect(loginInfo) {
        model.detectResources(loginInfo, cancellationSignal)
    }

    val result by model.foundConfig.observeAsState()
    val foundSomething = result?.calDAV != null || result?.cardDAV != null
    val foundNothing = result?.calDAV == null && result?.cardDAV == null

    LaunchedEffect(result) {
        if (foundSomething)
            result?.let { onSuccess(it) }
    }

    Column(Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
    ) {
        if (result == null)
            DetectResourcesPage_InProgress()
        else if (foundNothing)
            DetectResourcesPage_NothingFound(
                encountered401 = result?.encountered401 ?: false,
                logs = result?.logs ?: ""
            )
    }
}

@Composable
@Preview
fun DetectResourcesPage_InProgress() {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            color = MaterialTheme.colors.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp))

        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_configuration_detection),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                stringResource(R.string.login_querying_server),
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
fun DetectResourcesPage_NothingFound(
    encountered401: Boolean,
    logs: String
) {
    Column(Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.login_configuration_detection),
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(R.string.login_no_service),
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    stringResource(R.string.login_no_service_info),
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                if (encountered401)
                    Text(
                        stringResource(R.string.login_check_credentials),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.body1
                    )

                if (logs.isNotBlank()) {
                    Text(
                        stringResource(R.string.login_logs_available),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val context = LocalContext.current
                    TextButton(
                        onClick = {
                            val intent = DebugInfoActivity.IntentBuilder(context)
                                .withLogs(logs)
                                .build()
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.login_view_logs).uppercase())
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun DetectResourcesPage_NothingFound() {
    DetectResourcesPage_NothingFound(
        encountered401 = false,
        logs = "SOME LOGS"
    )
}

@Composable
@Preview
fun DetectResourcesPage_NothingFound_401() {
    DetectResourcesPage_NothingFound(
        encountered401 = true,
        logs = ""
    )
}