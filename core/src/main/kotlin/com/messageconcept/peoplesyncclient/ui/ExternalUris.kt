/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri

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
            get() = "https://www.messageconcept.com/peoplesync/".toUri()

        /** info page about PeopleSync Select */
        const val PATH_DAVX5_SELECT = "davx5-select"
        const val PATH_DOWNLOAD = "download"

    }


    // helpers

    /**
     * Appends query parameters for anonymized usage statistics:
     *
     * - current package name and version (like "com.messageconcept.peoplesyncclient/4.5.9")
     * - Android version (like "16.1")
     * - screen name (like "WifiPermissionsScreen")
     *
     * Can be used by the called Website to get an idea of which versions etc. are currently used.
     *
     * @param context   used to determine package name and version (optional)
     * @param screen    info about from where the URL was opened, like a specific screen (optional)
     */
    fun Uri.Builder.withStatParams(
        context: Context? = null,
        screen: String? = null
    ): Uri.Builder {
        if (context != null) {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appendQueryParameter("pk_campaign", "${context.packageName}/${PackageInfoCompat.getLongVersionCode(packageInfo)}")
            appendQueryParameter("android-version", Build.VERSION.RELEASE)
        }

        if (screen != null)
            appendQueryParameter("pk_kwd", screen)

        return this
    }

}