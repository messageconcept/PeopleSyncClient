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
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.io.Writer
import javax.inject.Inject

class RestrictionsProvider @Inject constructor(
    @ApplicationContext val context: Context
): SettingsProvider {

    private var onChangeListener: SettingsProvider.OnChangeListener? = null
    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

    private var restrictions: Bundle

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_APPLICATION_RESTRICTIONS_CHANGED -> {
                    // update cached app restrictions
                    restrictions = restrictionsManager.applicationRestrictions
                    onChangeListener?.onSettingsChanged(null)
                }
            }
        }
    }

    init {
        // cache app restrictions to avoid unnecessary disk access
        restrictions = restrictionsManager.applicationRestrictions
        context.registerReceiver(broadCastReceiver, IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED))
    }



    override fun canWrite() = false

    override fun close() {
        context.unregisterReceiver(broadCastReceiver)
    }

    override fun setOnChangeListener(listener: SettingsProvider.OnChangeListener) {
        onChangeListener = listener
    }

    override fun forceReload() {
    }


    private fun hasKey(key: String): Boolean {
        return restrictions.containsKey(key)
    }

    override fun contains(key: String) = hasKey(key)

    private fun<T> getValue(key: String, reader: (Bundle) -> T): T? =
            try {
                if (restrictions.containsKey(key))
                    reader(restrictions)
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
            getValue(key) { restrictions -> restrictions.getInt(key).toLong() }

    override fun getString(key: String) =
            getValue(key) { restrictions -> restrictions.getString(key) }


    override fun putBoolean(key: String, value: Boolean?) = throw NotImplementedError()
    override fun putInt(key: String, value: Int?) = throw NotImplementedError()
    override fun putLong(key: String, value: Long?) = throw NotImplementedError()
    override fun putString(key: String, value: String?) = throw NotImplementedError()

    override fun remove(key: String) = throw NotImplementedError()

    override fun dump(writer: Writer) {
        val keys = restrictions.keySet()
        val table = TextTable("Setting", "Value")
        keys.forEach { key ->
            val value = restrictions.get(key)
            table.addLine(key, value)
        }
        writer.write(table.toString())
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class RestrictionsProviderModule {
        @Binds
        @IntoMap
        @IntKey(/* priority */ 20)
        abstract fun restrictionsProvider(impl: RestrictionsProvider): SettingsProvider
    }

}