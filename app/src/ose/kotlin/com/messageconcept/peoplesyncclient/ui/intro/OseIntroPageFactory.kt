/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import javax.inject.Inject

class OseIntroPageFactory @Inject constructor(
    batteryOptimizationsPage: BatteryOptimizationsPage,
    permissionsIntroPage: PermissionsIntroPage,
    tasksIntroPage: TasksIntroPage
): IntroPageFactory {

    override val introPages = arrayOf(
        WelcomePage(),
        tasksIntroPage,
        permissionsIntroPage,
        batteryOptimizationsPage,
    )

}