/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

class RestrictionsProvider(
        context: Context
): SettingsProvider {

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager


    override fun forceReload() {
    }

    override fun close() {
    }

    private fun hasKey(key: String): Boolean {
        val appRestrictions = restrictionsManager.applicationRestrictions
        return appRestrictions.containsKey(key)
    }

    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = hasKey(key)
        return Pair(has, !has)
    }

    private fun<T> getValue(key: String, reader: (Bundle) -> T): Pair<T?, Boolean> {
        val appRestrictions = restrictionsManager.applicationRestrictions
        if (appRestrictions.containsKey(key))
            try {
                val v = reader(appRestrictions)
                return Pair(v, false)
            } catch(e: ClassCastException) {
                // If we fail to read a setting allow other providers to supply the setting ("true")
                return Pair(null, true)
            }

        // If the key is not specified allow other providers to supply the setting ("true")
        return Pair(null, true)
    }

    override fun getBoolean(key: String): Pair<Boolean?, Boolean> =
            getValue(key) { restrictions -> restrictions.getBoolean(key) }

    override fun getInt(key: String): Pair<Int?, Boolean> =
            getValue(key) { restrictions -> restrictions.getInt(key) }

    override fun getLong(key: String): Pair<Long?, Boolean> =
            getValue(key) { restrictions -> restrictions.getLong(key) }

    override fun getString(key: String): Pair<String?, Boolean> =
            getValue(key) { restrictions -> restrictions.getString(key) }


    override fun isWritable(key: String) = Pair(false, !hasKey(key))

    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false

    override fun remove(key: String) = false


    class Factory : ISettingsProviderFactory {
        override fun getProviders(context: Context) = listOf(RestrictionsProvider(context))
    }

}