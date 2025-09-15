/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package com.messageconcept.peoplesyncclient

import ezvcard.Ezvcard

/**
 * Brand-specific constants like (non-theme) colors, homepage URLs etc.
 */
object Constants {

    const val DAVDROID_GREEN_RGBA = 0xFF8bc34a.toInt()


    // product IDs for iCalendar/vCard

    const val vCardProdId = "+//IDN messageconcept.com//PeopleSync/${BuildConfig.VERSION_NAME} ez-vcard/${Ezvcard.VERSION}"

}