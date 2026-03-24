/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.util

import com.google.common.base.Strings

fun CharSequence?.trimToNull() = Strings.emptyToNull(this?.trim()?.toString())

fun String.withTrailingSlash() =
    if (this.endsWith('/'))
        this
    else
        "$this/"