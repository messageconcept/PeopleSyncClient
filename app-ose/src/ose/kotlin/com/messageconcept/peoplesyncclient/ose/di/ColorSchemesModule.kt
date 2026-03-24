/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ose.di

import androidx.compose.material3.ColorScheme
import com.messageconcept.peoplesyncclient.di.qualifier.DarkColorScheme
import com.messageconcept.peoplesyncclient.di.qualifier.LightColorScheme
import com.messageconcept.peoplesyncclient.ui.OseTheme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class ColorSchemesModule {

    @Provides
    @LightColorScheme
    fun lightColorScheme(): ColorScheme = OseTheme.lightScheme

    @Provides
    @DarkColorScheme
    fun darkColorScheme(): ColorScheme = OseTheme.darkScheme

}
