/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.settings

import android.content.*
import android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED
import android.os.Bundle
import com.messageconcept.peoplesyncclient.TextTable
import java.io.Writer

class RestrictionsProvider(
        val context: Context,
        val settingsManager: SettingsManager
): SettingsProvider {

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> settingsManager.onSettingsChanged()
            }
        }
    }

    init {
        context.registerReceiver(broadCastReceiver, IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED))
    }

    override fun forceReload() {
    }

    override fun close() {
        context.unregisterReceiver(broadCastReceiver)
    }

    private fun hasKey(key: String): Boolean {
        val appRestrictions = restrictionsManager.applicationRestrictions
        return appRestrictions.containsKey(key)
    }

    override fun canWrite() = false

    override fun contains(key: String) = hasKey(key)

    private fun<T> getValue(key: String, reader: (Bundle) -> T): T? =
            try {
                val appRestrictions = restrictionsManager.applicationRestrictions
                if (appRestrictions.containsKey(key))
                    reader(appRestrictions)
                else
                    null
            } catch(e: ClassCastException) {
                null
            }

    override fun getBoolean(key: String) =
            getValue(key) { restrictions -> restrictions.getBoolean(key) }

    override fun getInt(key: String) =
            getValue(key) { restrictions -> restrictions.getInt(key) }

    override fun getLong(key: String) =
            getValue(key) { restrictions -> restrictions.getLong(key) }

    override fun getString(key: String) =
            getValue(key) { restrictions -> restrictions.getString(key) }


    override fun putBoolean(key: String, value: Boolean?) = throw NotImplementedError()
    override fun putInt(key: String, value: Int?) = throw NotImplementedError()
    override fun putLong(key: String, value: Long?) = throw NotImplementedError()
    override fun putString(key: String, value: String?) = throw NotImplementedError()

    override fun remove(key: String) = throw NotImplementedError()

    override fun dump(writer: Writer) {
        val appRestrictions = restrictionsManager.applicationRestrictions
        val keys = appRestrictions.keySet()
        val table = TextTable("Setting", "Value")
        keys.forEach { key ->
            val value = appRestrictions.get(key)
            table.addLine(key, value)
        }
        writer.write(table.toString())
    }


    class Factory : SettingsProviderFactory {
        override fun getProviders(context: Context, settingsManager: SettingsManager) = listOf(RestrictionsProvider(context, settingsManager))
    }
}