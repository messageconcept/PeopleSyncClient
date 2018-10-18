/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.messageconcept.peoplesyncclient.App
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.resource.LocalTaskList
import com.messageconcept.peoplesyncclient.settings.Settings
import java.util.*

class StartupDialogFragment: DialogFragment() {

    enum class Mode {
        AUTOSTART_PERMISSIONS,
        BATTERY_OPTIMIZATIONS,
    }

    companion object {

        const val HINT_AUTOSTART_PERMISSIONS = "hint_AutostartPermissions"
        // see https://github.com/jaredrummler/AndroidDeviceNames/blob/master/json/ for manufacturer values
        private val autostartManufacturers = arrayOf("huawei", "letv", "oneplus", "vivo", "xiaomi", "zte")

        const val HINT_BATTERY_OPTIMIZATIONS = "hint_BatteryOptimizations"

        const val ARGS_MODE = "mode"

        fun getStartupDialogs(context: Context): List<StartupDialogFragment> {
            val dialogs = LinkedList<StartupDialogFragment>()
            val settings = Settings.getInstance(context)


            // battery optimization white-listing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && settings.getBoolean(HINT_BATTERY_OPTIMIZATIONS) != false) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID))
                    dialogs.add(StartupDialogFragment.instantiate(Mode.BATTERY_OPTIMIZATIONS))
            }

            // vendor-specific auto-start information
            if (autostartManufacturers.contains(Build.MANUFACTURER.toLowerCase()) && settings.getBoolean(HINT_AUTOSTART_PERMISSIONS) != false)
                dialogs.add(StartupDialogFragment.instantiate(Mode.AUTOSTART_PERMISSIONS))

            return dialogs.reversed()
        }

        fun instantiate(mode: Mode): StartupDialogFragment {
            val frag = StartupDialogFragment()
            val args = Bundle(1)
            args.putString(ARGS_MODE, mode.name)
            frag.arguments = args
            return frag
        }

    }

    
    @SuppressLint("BatteryLife")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val settings = Settings.getInstance(requireActivity())
        val activity = requireActivity()
        val mode = Mode.valueOf(arguments!!.getString(ARGS_MODE)!!)
        return when (mode) {
            Mode.AUTOSTART_PERMISSIONS ->
                AlertDialog.Builder(activity)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.startup_autostart_permission)
                        .setMessage(getString(R.string.startup_autostart_permission_message, Build.MANUFACTURER))
                        .setPositiveButton(R.string.startup_more_info) { _, _ ->
                            UiUtils.launchUri(requireActivity(), App.homepageUrl(requireActivity()).buildUpon()
                                    .appendPath("faq").appendPath("synchronization-is-not-run-as-expected").build())
                        }
                        .setNeutralButton(R.string.startup_not_now) { _, _ -> }
                        .setNegativeButton(R.string.startup_dont_show_again) { _, _ ->
                            settings.putBoolean(HINT_AUTOSTART_PERMISSIONS, false)
                        }
                        .create()

            Mode.BATTERY_OPTIMIZATIONS ->
                AlertDialog.Builder(activity)
                        .setIcon(R.drawable.ic_info_dark)
                        .setTitle(R.string.startup_battery_optimization)
                        .setMessage(R.string.startup_battery_optimization_message)
                        .setPositiveButton(R.string.startup_battery_optimization_disable) @TargetApi(Build.VERSION_CODES.M) { _, _ ->
                            UiUtils.launchUri(requireActivity(), Uri.parse("package:" + BuildConfig.APPLICATION_ID),
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        }
                        .setNeutralButton(R.string.startup_not_now) { _, _ -> }
                        .setNegativeButton(R.string.startup_dont_show_again) { _: DialogInterface, _: Int ->
                            settings.putBoolean(HINT_BATTERY_OPTIMIZATIONS, false)
                        }
                        .create()

        }
    }

}
