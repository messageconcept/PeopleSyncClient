/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

object ThemeColors {

    private val grey100 = Color(0xfff5f5f5)
    private val grey200 = Color(0xffeeeeee)
    private val grey300 = Color(0xffe0e0e0)
    private val grey400 = Color(0xffbdbdbd)
    private val grey800 = Color(0xff424242)
    private val grey900 = Color(0xff212121)
    private val grey1000 = Color(0xff121212)
    private val red400 = Color(0xffef5350)
    private val red700 = Color(0xffd32f2f)

    // https://material.io/resources/color/#!/?view.left=0&view.right=1&primary.color=005bbb&secondary.color=5a0943
    val primary = Color(0xff005bbb)
    val primaryDark = Color(0xff00338a)
    val primaryLight = Color(0xff5887ee)
    val onPrimary = Color.White
    val secondary = Color(0xff5a0943)
    val secondaryDark = Color(0xff2f001d)
    val secondaryLight = Color(0xff8a3b6e)
    val onSecondary = Color(0xfffafafa)

    val light = Colors(
        primary = primary,
        primaryVariant = primaryDark,
        onPrimary = onPrimary,
        secondary = secondary,
        secondaryVariant = secondaryLight,
        onSecondary = onSecondary,
        background = grey100,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        error = red700,
        onError = grey100,
        isLight = true
    )

    val dark = Colors(
        primary = primaryLight, // alternative: grey300
        primaryVariant = grey900,
        onPrimary = onPrimary,
        secondary = secondaryLight,
        secondaryVariant = secondaryLight,
        onSecondary = grey100,
        background = grey900,
        onBackground = grey100,
        surface = Color.Black,
        onSurface = grey100,
        error = red400,
        onError = grey100,
        isLight = false
    )

}