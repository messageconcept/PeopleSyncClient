/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import com.messageconcept.peoplesyncclient.ui.intro.BatteryOptimizationsPage
import com.messageconcept.peoplesyncclient.ui.intro.IntroPageFactory
import com.messageconcept.peoplesyncclient.ui.intro.PermissionsIntroPage
import com.messageconcept.peoplesyncclient.ui.intro.WelcomePage
import javax.inject.Inject

class OseIntroPageFactory @Inject constructor(): IntroPageFactory {

    override val introPages = arrayOf(
        WelcomePage(),
        PermissionsIntroPage(),
        BatteryOptimizationsPage(),
    )

}