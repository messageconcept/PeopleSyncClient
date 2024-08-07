/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.messageconcept.peoplesyncclient.Constants
import com.messageconcept.peoplesyncclient.Constants.COMMUNITY_URL
import com.messageconcept.peoplesyncclient.Constants.FEDIVERSE_URL
import com.messageconcept.peoplesyncclient.Constants.MANUAL_URL
import com.messageconcept.peoplesyncclient.Constants.withStatParams
import com.messageconcept.peoplesyncclient.R
import javax.inject.Inject

open class OseAccountsDrawerHandler @Inject constructor(): AccountsDrawerHandler() {

    companion object {
        const val WEB_CONTEXT = "AccountsDrawerHandler"
    }

    @Composable
    override fun MenuEntries(
        snackbarHostState: SnackbarHostState
    ) {
        val uriHandler = LocalUriHandler.current

        // Most important entries
        ImportantEntries(snackbarHostState)

        // External links
        MenuHeading(R.string.navigation_drawer_external_links)
        MenuEntry(
            icon = Icons.Default.Home,
            title = stringResource(R.string.navigation_drawer_website),
            onClick = {
                uriHandler.openUri(Constants.HOMEPAGE_URL
                    .buildUpon()
                    .withStatParams(WEB_CONTEXT)
                    .build().toString())
            }
        )
    }

    @Composable
    @Preview
    fun MenuEntries_Standard_Preview() {
        Column {
            MenuEntries(SnackbarHostState())
        }
    }

}