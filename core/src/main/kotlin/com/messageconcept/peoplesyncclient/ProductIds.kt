/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import ezvcard.Ezvcard
import javax.inject.Inject

@Reusable
class ProductIds @Inject constructor(
    @ApplicationContext context: Context
) {

    // HTTP User-Agent

    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    private val versionName = packageInfo.versionName ?: PackageInfoCompat.getLongVersionCode(packageInfo).toString()
    val httpUserAgent = "PeopleSync/$versionName Android/${Build.VERSION.RELEASE}"


    // product IDs for vCard

    val vCardProdId = "+//IDN messageconcept.com//PeopleSync/$versionName ez-vcard/${Ezvcard.VERSION}"

}