/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.ui.composable.Assistant

@Composable
fun StandardLoginTypePage(
    selectedLoginType: LoginType,
    onSelectLoginType: (LoginType) -> Unit,
    onContinue: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_continue),
        nextEnabled = true,
        onNext = onContinue
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_generic_login),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            for (type in StandardLoginTypesProvider.genericLoginTypes)
                LoginTypeSelector(
                    title = stringResource(type.title),
                    selected = type == selectedLoginType,
                    onSelect = { onSelectLoginType(type) }
                )
        }
    }
}

@Composable
fun LoginTypeSelector(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit = {}
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onSelect)
                .padding(bottom = 4.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
            Text(
                title,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


@Composable
@Preview
fun LoginScreen_Preview() {
    LoginScreen(
        loginTypesProvider = StandardLoginTypesProvider(),
        initialLoginType = LoginTypeUrl
    )
}