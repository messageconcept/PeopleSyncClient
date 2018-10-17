/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient

import android.content.ContentProviderClient
import android.os.Build

@Suppress("DEPRECATION")
fun ContentProviderClient.closeCompat() {
    if (Build.VERSION.SDK_INT >= 24)
        close()
    else
        release()
}
