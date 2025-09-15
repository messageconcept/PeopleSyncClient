/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.net.Uri
import androidx.core.net.toUri
import com.messageconcept.peoplesyncclient.BuildConfig

/**
 * Links to to external pages (Web site, manual, social media etc.)
 */
object ExternalUris {

    /**
     * URLs of the PeopleSync homepage
     */
    @Suppress("unused")     // build variants
    object Homepage {

        val baseUrl
            get() = "https://www.davx5.com".toUri()

        const val PATH_FAQ = "faq"
        const val PATH_FAQ_SYNC_NOT_RUN = "synchronization-is-not-run-as-expected"
        const val PATH_FAQ_LOCATION_PERMISSION = "wifi-ssid-restriction-location-permission"
        const val PATH_OPEN_SOURCE = "donate"
        const val PATH_PRIVACY = "privacy"
        const val PATH_TESTED_SERVICES = "tested-with"
    }


    // helpers

    /**
     * Appends query parameters for anonymized usage statistics (app ID, version).
     * Can be used by the called Website to get an idea of which versions etc. are currently used.
     *
     * @param context   optional info about from where the URL was opened (like a specific Activity)
     */
    fun Uri.Builder.withStatParams(context: String? = null): Uri.Builder {
        appendQueryParameter("pk_campaign", BuildConfig.APPLICATION_ID)
        appendQueryParameter("app-version", BuildConfig.VERSION_NAME)

        if (context != null)
            appendQueryParameter("pk_kwd", context)

        return this
    }

}