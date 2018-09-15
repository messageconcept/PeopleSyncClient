/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.PermissionUtils
import com.messageconcept.peoplesyncclient.PermissionUtils.CONTACT_PERMISSIONS
import com.messageconcept.peoplesyncclient.PermissionUtils.havePermissions
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.databinding.ActivityPermissionsBinding

class PermissionsFragment: Fragment() {

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ActivityPermissionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.permissions_text, getString(R.string.app_name))

        model.needAutoResetPermission.observe(viewLifecycleOwner, { keepPermissions ->
            if (keepPermissions == true && model.haveAutoResetPermission.value == false) {
                Toast.makeText(requireActivity(), R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)))
            }
        })
        model.needContactsPermissions.observe(viewLifecycleOwner, { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermissions(CONTACT_PERMISSIONS, 0)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = mutableSetOf(*CONTACT_PERMISSIONS)
                requestPermissions(all.toTypedArray(), 0)
            }
        })

        binding.appSettings.setOnClickListener {
            PermissionUtils.showAppSettings(requireActivity())
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        val haveAutoResetPermission = MutableLiveData<Boolean>()
        val needAutoResetPermission = MutableLiveData<Boolean>()

        val haveContactsPermissions = MutableLiveData<Boolean>()
        val needContactsPermissions = MutableLiveData<Boolean>()

        val haveAllPermissions = MutableLiveData<Boolean>()
        val needAllPermissions = MutableLiveData<Boolean>()

        init {
            checkPermissions()
        }

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val keepPermissions = pm.isAutoRevokeWhitelisted
                haveAutoResetPermission.value = keepPermissions
                needAutoResetPermission.value = keepPermissions
            }

            // other permissions
            val contactPermissions = havePermissions(getApplication(), CONTACT_PERMISSIONS)
            haveContactsPermissions.value = contactPermissions
            needContactsPermissions.value = contactPermissions

            val allPermissions = contactPermissions
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}
