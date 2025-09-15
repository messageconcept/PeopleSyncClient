/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

@Suppress("MemberVisibilityCanBePrivate")
object M3ColorScheme {

    val primaryLight = Color(0xff005bbb)
    val onPrimaryLight = Color(0xFFffffff)
    val secondaryLight = Color(0xff5a0943)
    val tertiaryLight = Color(0xff00338a)

    val primaryDark = Color(0xff5887ee)
    val secondaryDark = Color(0xff8a3b6e)
    val tertiaryDark = Color(0xff005bbb)


    val lightScheme = lightColorScheme(
        primary = primaryLight,
        secondary = secondaryLight,
        tertiary = tertiaryLight,
    )

    val darkScheme = darkColorScheme(
        primary = primaryDark,
        secondary = secondaryDark,
        tertiary = tertiaryDark,
    )

}