/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.ui

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.*
import com.messageconcept.peoplesyncclient.App
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.closeCompat
import com.messageconcept.peoplesyncclient.model.Credentials
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.ui.account.AccountActivity
import at.bitfire.vcard4android.GroupMethod
import org.apache.commons.lang3.StringUtils

class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private lateinit var account: Account


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = intent.getParcelableExtra(EXTRA_ACCOUNT)
        title = getString(R.string.settings_title, account.name)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null)
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DialogFragment.instantiate(this, AccountSettingsFragment::class.java.name, intent.extras))
                    .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                val intent = Intent(this, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                NavUtils.navigateUpTo(this, intent)
                true
            } else
                false


    class AccountSettingsFragment: PreferenceFragmentCompat(), SyncStatusObserver, Settings.OnChangeListener {
        private lateinit var settings: Settings

        lateinit var account: Account
        private var statusChangeListener: Any? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            settings = Settings.getInstance(requireActivity())
            account = arguments!!.getParcelable(EXTRA_ACCOUNT)!!
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings_account)
        }

        override fun onResume() {
            super.onResume()

            statusChangeListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this)
            settings.addOnChangeListener(this)

            reload()
        }

        override fun onPause() {
            super.onPause()
            statusChangeListener?.let {
                ContentResolver.removeStatusChangeListener(it)
                statusChangeListener = null
            }
            settings.removeOnChangeListener(this)
        }

        override fun onStatusChanged(which: Int) {
            Handler(Looper.getMainLooper()).post {
                reload()
            }
        }

        override fun onSettingsChanged()  = reload()

        private fun reload() {
            val accountSettings = AccountSettings(requireActivity(), account)

            // preference group: authentication
            val prefUserName = findPreference<EditTextPreference>("username")!!
            val prefPassword = findPreference<EditTextPreference>("password")!!
            val prefCertAlias = findPreference<Preference>("certificate_alias")!!

            val credentials = accountSettings.credentials()
            when (credentials.type) {
                Credentials.Type.UsernamePassword -> {
                    prefUserName.isVisible = true
                    prefUserName.summary = credentials.userName
                    prefUserName.text = credentials.userName
                    prefUserName.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.credentials(Credentials(newValue as String, credentials.password))
                        reload()
                        false
                    }

                    prefPassword.isVisible = true
                    prefPassword.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        accountSettings.credentials(Credentials(credentials.userName, newValue as String))
                        reload()
                        false
                    }

                    prefCertAlias.isVisible = false
                }
                Credentials.Type.ClientCertificate -> {
                    prefUserName.isVisible = false
                    prefPassword.isVisible = false

                    prefCertAlias.isVisible = true
                    prefCertAlias.summary = credentials.certificateAlias
                    prefCertAlias.setOnPreferenceClickListener {
                        KeyChain.choosePrivateKeyAlias(requireActivity(), { alias ->
                            accountSettings.credentials(Credentials(certificateAlias = alias))
                            Handler(Looper.getMainLooper()).post {
                                reload()
                            }
                        }, null, null, null, -1, credentials.certificateAlias)
                        true
                    }
                }
            }

            // preference group: sync
            // those are null if the respective sync type is not available for this account:
            val syncIntervalContacts = accountSettings.getSyncInterval(getString(R.string.address_books_authority))

            findPreference<ListPreference>("sync_interval_contacts")!!.let {
                if (syncIntervalContacts != null) {
                    it.isEnabled = true
                    it.isVisible = true
                    it.value = syncIntervalContacts.toString()
                    if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
                        it.setSummary(R.string.settings_sync_summary_manually)
                    else
                        it.summary = getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60)
                    it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                        Handler(Looper.myLooper()).post {
                            pref.isEnabled = false
                            accountSettings.setSyncInterval(getString(R.string.address_books_authority), (newValue as String).toLong())
                            reload()
                        }
                        false
                    }
                } else
                    it.isVisible = false
            }

            val prefWifiOnly = findPreference<SwitchPreferenceCompat>("sync_wifi_only")!!
            prefWifiOnly.isEnabled = !settings.has(AccountSettings.KEY_WIFI_ONLY)
            prefWifiOnly.isChecked = accountSettings.getSyncWifiOnly()
            prefWifiOnly.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, wifiOnly ->
                accountSettings.setSyncWiFiOnly(wifiOnly as Boolean)
                reload()
                false
            }

            val prefWifiOnlySSIDs = findPreference<EditTextPreference>("sync_wifi_only_ssids")!!
            val onlySSIDs = accountSettings.getSyncWifiOnlySSIDs()?.joinToString(", ")
            prefWifiOnlySSIDs.text = onlySSIDs
            if (onlySSIDs != null)
                prefWifiOnlySSIDs.summary = getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                    R.string.settings_sync_wifi_only_ssids_on_location_services else R.string.settings_sync_wifi_only_ssids_on, onlySSIDs)
            else
                prefWifiOnlySSIDs.setSummary(R.string.settings_sync_wifi_only_ssids_off)
            prefWifiOnlySSIDs.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                accountSettings.setSyncWifiOnlySSIDs((newValue as String).split(',').mapNotNull { StringUtils.trimToNull(it) }.distinct())
                reload()
                false
            }

            // getting the WiFi name requires location permission (and active location services) since Android 8.1
            // see https://issuetracker.google.com/issues/70633700
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                accountSettings.getSyncWifiOnly() && onlySSIDs != null &&
                ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)

            // preference group: CardDAV
            findPreference<ListPreference>("contact_group_method")!!.let {
                if (syncIntervalContacts != null) {
                    it.isVisible = true
                    it.value = accountSettings.getGroupMethod().name
                    it.summary = it.entry
                    if (settings.has(AccountSettings.KEY_CONTACT_GROUP_METHOD))
                        it.isEnabled = false
                    else {
                        it.isEnabled = true
                        it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, groupMethod ->
                            AlertDialog.Builder(requireActivity())
                                    .setIcon(R.drawable.ic_error_dark)
                                    .setTitle(R.string.settings_contact_group_method_change)
                                    .setMessage(R.string.settings_contact_group_method_change_reload_contacts)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        // change group method
                                        accountSettings.setGroupMethod(GroupMethod.valueOf(groupMethod as String))
                                        reload()

                                        // reload all contacts
                                        val args = Bundle(1)
                                        args.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                        ContentResolver.requestSync(account, getString(R.string.address_books_authority), args)
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            false
                        }
                    }
                } else
                    it.isVisible = false
            }
        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            if (permissions.first() == Manifest.permission.ACCESS_COARSE_LOCATION && grantResults.first() == PackageManager.PERMISSION_DENIED) {
                // location permission denied, reset SSID restriction
                AccountSettings(requireActivity(), account).setSyncWifiOnlySSIDs(null)
                reload()

                AlertDialog.Builder(requireActivity())
                        .setIcon(R.drawable.ic_network_wifi_dark)
                        .setTitle(R.string.settings_sync_wifi_only_ssids)
                        .setMessage(R.string.settings_sync_wifi_only_ssids_location_permission)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .setNeutralButton(R.string.settings_more_info_faq) { _, _ ->
                            val faqUrl = App.homepageUrl(requireActivity()).buildUpon()
                                    .appendPath("faq").appendPath("wifi-ssid-restriction-location-permission")
                                    .build()
                            val intent = Intent(Intent.ACTION_VIEW, faqUrl)
                            startActivity(Intent.createChooser(intent, null))
                        }
                        .show()
            }
        }

    }

}
