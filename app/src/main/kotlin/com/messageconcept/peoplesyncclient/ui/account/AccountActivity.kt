/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.databinding.ActivityAccountBinding
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.db.Service
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.syncadapter.SyncWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
                ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }

    private val warningsModel by viewModels<AppWarningsModel>()

    private lateinit var binding: ActivityAccountBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = model.account.name

        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        model.accountExists.observe(this) { accountExists ->
            if (!accountExists)
                finish()
        }

        model.services.observe(this) { services ->
            val cardDavServiceId = services.firstOrNull { it.type == Service.TYPE_CARDDAV }?.id

            Logger.log.info("Triggering sync")
            SyncWorker.enqueueAllAuthorities(getApplication(), model.account)

            val viewPager = binding.viewPager
            val adapter = FragmentsAdapter(this, cardDavServiceId)
            viewPager.adapter = adapter

            // connect ViewPager with TabLayout (top bar with tabs)
            TabLayoutMediator(binding.tabLayout, viewPager) { tab, position ->
                tab.text = adapter.getHeading(position)
            }.attach()
        }

        // "Sync now" fab
        TooltipCompat.setTooltipText(binding.sync, binding.sync.contentDescription)
        warningsModel.networkAvailable.observe(this) { networkAvailable ->
            binding.sync.setOnClickListener {
                val msgId =
                    if (warningsModel.networkAvailable.value == true)
                        R.string.sync_started
                    else
                        R.string.no_internet_sync_scheduled
                Snackbar.make(
                    binding.sync,
                    msgId,
                    Snackbar.LENGTH_SHORT
                ).show()
                SyncWorker.enqueueAllAuthorities(this, model.account)
            }
        }

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.activity_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem) =
                when (menuItem.itemId) {
                    R.id.settings -> {
                        openAccountSettings()
                        true
                    }
                    R.id.rename_account -> {
                        renameAccount()
                        true
                    }
                    R.id.delete_account -> {
                        deleteAccountDialog()
                        true
                    }
                    else -> false
                }
        })
    }


    // menu actions

    fun openAccountSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
        startActivity(intent, null)
    }

    fun renameAccount() {
        RenameAccountFragment.newInstance(model.account).show(supportFragmentManager, null)
    }

    fun deleteAccountDialog() {
        MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle(R.string.account_delete_confirmation_title)
                .setMessage(R.string.account_delete_confirmation_text)
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    deleteAccount()
                }
                .show()
    }

    private fun deleteAccount() {
        val accountManager = AccountManager.get(this)

        accountManager.removeAccount(model.account, this, { future ->
            try {
                if (future.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                    Handler(Looper.getMainLooper()).post {
                        finish()
                    }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
            }
        }, null)
    }


    // adapter

    class FragmentsAdapter(
        val activity: FragmentActivity,
        private val cardDavSvcId: Long?
    ): FragmentStateAdapter(activity) {

        private val idxCardDav: Int?

        init {
            var currentIndex = 0

            idxCardDav = if (cardDavSvcId != null)
                currentIndex++
            else
                null
        }

        override fun getItemCount() =
            (if (idxCardDav != null) 1 else 0)

        override fun createFragment(position: Int) =
            when (position) {
                idxCardDav ->
                    AddressBooksFragment().apply {
                        arguments = Bundle(2).apply {
                            putLong(CollectionsFragment.EXTRA_SERVICE_ID, cardDavSvcId!!)
                            putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_ADDRESSBOOK)
                        }
                    }
                else -> throw IllegalArgumentException()
            }

        fun getHeading(position: Int) =
            when (position) {
                idxCardDav -> activity.getString(R.string.account_carddav)
                else -> throw IllegalArgumentException()
            }

    }


    // model

    class Model @AssistedInject constructor(
        application: Application,
        val db: AppDatabase,
        @Assisted val account: Account
    ): AndroidViewModel(application), OnAccountsUpdateListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        val accountManager: AccountManager = AccountManager.get(application)
        val accountSettings by lazy { AccountSettings(application, account) }

        val accountExists = MutableLiveData<Boolean>()
        val services = db.serviceDao().getServiceTypeAndIdsByAccount(account.name)

        val showOnlyPersonal = MutableLiveData<Boolean>()
        val showOnlyPersonalWritable = MutableLiveData<Boolean>()


        init {
            accountManager.addOnAccountsUpdatedListener(this, null, true)
            viewModelScope.launch(Dispatchers.IO) {
                accountSettings.getShowOnlyPersonal().let { (value, locked) ->
                    showOnlyPersonal.postValue(value)
                    showOnlyPersonalWritable.postValue(locked)
                }
            }
        }

        override fun onCleared() {
            accountManager.removeOnAccountsUpdatedListener(this)
        }

        override fun onAccountsUpdated(accounts: Array<out Account>) {
            accountExists.postValue(accounts.contains(account))
        }

        fun toggleReadOnly(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(forceReadOnly = !item.forceReadOnly)
                db.collectionDao().update(newItem)
            }
        }

        fun toggleShowOnlyPersonal() {
            showOnlyPersonal.value?.let { oldValue ->
                val newValue = !oldValue
                accountSettings.setShowOnlyPersonal(newValue)
                showOnlyPersonal.postValue(newValue)
            }
        }

        fun toggleSync(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(sync = !item.sync)
                db.collectionDao().update(newItem)
            }
        }

    }

}