/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry

object TestUtils {

    val targetApplication by lazy { InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application }

}