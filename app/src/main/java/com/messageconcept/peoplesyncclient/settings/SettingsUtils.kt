/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

object SettingsUtils {
    fun<T> filterPassword(key: String, value: T): T {
        return if (key.contains("Password", ignoreCase = true) && value is String && value.isNotEmpty()) {
            "***REMOVED***" as T
        } else
            value
    }

}
