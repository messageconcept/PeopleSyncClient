/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import javax.inject.Inject

class FakeIntroPageFactory @Inject constructor(): IntroPageFactory {

    override val introPages: Array<IntroPage>
        get() = throw NotImplementedError()

}