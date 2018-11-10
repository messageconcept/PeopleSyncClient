package com.messageconcept.peoplesyncclient.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.PermissionUtils.CONTACT_PERMSSIONS
import com.messageconcept.peoplesyncclient.PermissionUtils.havePermissions
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.databinding.ActivityPermissionsBinding

class PermissionsFragment: Fragment() {

    lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this).get(Model::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ActivityPermissionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.permissions_text, getString(R.string.app_name))

        model.needContactsPermissions.observe(viewLifecycleOwner, Observer { needContacts ->
            if (needContacts && model.haveContactsPermissions.value == false)
                requestPermissions(CONTACT_PERMSSIONS, 0)
        })
        model.needAllPermissions.observe(viewLifecycleOwner, Observer { needAll ->
            if (needAll && model.haveAllPermissions.value == false) {
                val all = CONTACT_PERMSSIONS
                requestPermissions(all, 0)
            }
        })

        binding.appSettings.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
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

        val haveContactsPermissions = MutableLiveData<Boolean>()
        val needContactsPermissions = MutableLiveData<Boolean>()

        val haveAllPermissions = MutableLiveData<Boolean>()
        val needAllPermissions = MutableLiveData<Boolean>()

        init {
            checkPermissions()
        }

        fun checkPermissions() {
            val contactPermissions = havePermissions(getApplication(), CONTACT_PERMSSIONS)
            haveContactsPermissions.value = contactPermissions
            needContactsPermissions.value = contactPermissions

            val allPermissions = contactPermissions
            haveAllPermissions.value = allPermissions
            needAllPermissions.value = allPermissions
        }

    }

}