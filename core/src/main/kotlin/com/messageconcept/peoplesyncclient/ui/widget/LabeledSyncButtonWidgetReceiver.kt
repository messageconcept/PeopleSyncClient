/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.widget

import androidx.compose.material3.ColorScheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.messageconcept.peoplesyncclient.di.qualifier.DarkColorScheme
import com.messageconcept.peoplesyncclient.di.qualifier.LightColorScheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LabeledSyncButtonWidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var model: SyncWidgetModel

    @Inject
    @LightColorScheme
    lateinit var lightColorScheme: ColorScheme

    @Inject
    @DarkColorScheme
    lateinit var darkColorScheme: ColorScheme

    override val glanceAppWidget: GlanceAppWidget
        get() = LabeledSyncButtonWidget(
            model = model,
            lightColorScheme = lightColorScheme,
            darkColorScheme = darkColorScheme
        )

}
