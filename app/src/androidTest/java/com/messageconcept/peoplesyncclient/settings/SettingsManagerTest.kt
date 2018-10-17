/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.settings

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsManagerTest {

    val settingsManager by lazy { SettingsManager.getInstance(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Test
    fun testContainsKey_NotExisting() {
        assertFalse(settingsManager.containsKey("notExisting"))
    }

    @Test
    fun testContainsKey_Existing() {
        // provided by DefaultsProvider
        assertEquals(Settings.PROXY_TYPE_SYSTEM, settingsManager.getInt(Settings.PROXY_TYPE))
    }

}