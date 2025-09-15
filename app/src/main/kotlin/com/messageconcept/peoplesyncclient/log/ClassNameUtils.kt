/*
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.messageconcept.peoplesyncclient.log

object ClassNameUtils {

    fun shortenClassName(fullClassName: String, classNameFirst: Boolean): String {
        // remove $... that is appended for anonymous classes
        val withoutSuffix = fullClassName.replace(Regex("\\$.*$"), "")

        val idxDot = withoutSuffix.lastIndexOf('.')
        if (idxDot == -1)
            return withoutSuffix

        val packageName = withoutSuffix.substring(0, idxDot)
        val className = withoutSuffix.substring(idxDot + 1)
        val shortenedPackageName = packageName.removePrefix("com.messageconcept.peoplesyncclient")
        return if (classNameFirst)
            "$className/$shortenedPackageName"
        else
            "$shortenedPackageName.$className"
    }

}