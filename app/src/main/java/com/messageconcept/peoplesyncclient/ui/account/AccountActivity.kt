package com.messageconcept.peoplesyncclient.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.*
import com.messageconcept.peoplesyncclient.DavService
import com.messageconcept.peoplesyncclient.DavUtils
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.AppDatabase
import com.messageconcept.peoplesyncclient.model.Collection
import com.messageconcept.peoplesyncclient.model.Service
import com.messageconcept.peoplesyncclient.ui.PermissionsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.logging.Level

class AccountActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    lateinit var model: Model
    private var refreshed = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProvider(this).get(Model::class.java)
        (intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account)?.let { account ->
            model.initialize(account)
        } ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")

        title = model.account.name
        setContentView(R.layout.activity_account)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tab_layout.setupWithViewPager(view_pager)
        val tabsAdapter = TabsAdapter(this)
        view_pager.adapter = tabsAdapter
        model.cardDavService.observe(this, Observer {
            tabsAdapter.cardDavSvcId = it
            if (!refreshed) {
                Logger.log.info("Refreshing CardDAV collections")
                val intent = Intent(this, DavService::class.java)
                intent.action = DavService.ACTION_REFRESH_COLLECTIONS
                intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, it)
                intent.putExtra(DavService.AUTO_SYNC, true)
                startService(intent)

                refreshed = true
            }

        })

        sync.setOnClickListener {
            DavUtils.requestSync(this, model.account)
            Snackbar.make(view_pager, R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_account, menu)
        return true
    }


    // menu actions

    fun openAccountSettings(menuItem: MenuItem) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
        startActivity(intent, null)
    }

    fun renameAccount(menuItem: MenuItem) {
        RenameAccountFragment.newInstance(model.account).show(supportFragmentManager, null)
    }

    fun deleteAccount(menuItem: MenuItem) {
        MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error_dark)
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

        if (Build.VERSION.SDK_INT >= 22)
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
        else
            accountManager.removeAccount(model.account, { future ->
                try {
                    if (future.result)
                        Handler(Looper.getMainLooper()).post {
                            finish()
                        }
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
                }
            }, null)
    }


    // other actions

    fun startPermissionsActivity(view: View) {
        startActivity(Intent(this, PermissionsActivity::class.java))
    }



    // adapter

    class TabsAdapter(
            val activity: AppCompatActivity
    ): FragmentStatePagerAdapter(activity.supportFragmentManager, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        
        var cardDavSvcId: Long? = null
            set(value) {
                field = value
                recalculate()
            }

        private var idxCardDav: Int? = null

        private fun recalculate() {
            var currentIndex = 0

            idxCardDav = if (cardDavSvcId != null)
                currentIndex++
            else
                null

            notifyDataSetChanged()
        }

        override fun getCount() =
                (if (idxCardDav != null) 1 else 0)

        override fun getItem(position: Int): Fragment {
            val args = Bundle(1)
            when (position) {
                idxCardDav -> {
                    val frag = AddressBooksFragment()
                    args.putLong(CollectionsFragment.EXTRA_SERVICE_ID, cardDavSvcId!!)
                    args.putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_ADDRESSBOOK)
                    frag.arguments = args
                    return frag
                }
            }
            throw IllegalArgumentException()
        }

        override fun getPageTitle(position: Int): String =
                when (position) {
                    idxCardDav -> activity.getString(R.string.account_carddav)
                    else -> throw IllegalArgumentException()
                }

    }


    // model

    class Model(application: Application): AndroidViewModel(application) {

        private var initialized = false
        lateinit var account: Account
            private set

        private val db = AppDatabase.getInstance(application)

        val cardDavService = MutableLiveData<Long>()


        @MainThread
        fun initialize(account: Account) {
            if (initialized)
                return
            initialized = true

            this.account = account

            viewModelScope.launch(Dispatchers.IO) {
                cardDavService.postValue(db.serviceDao().getIdByAccountAndType(account.name, Service.TYPE_CARDDAV))
            }
        }

        fun toggleSync(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(sync = !item.sync)
                db.collectionDao().update(newItem)
            }
        }

        fun toggleReadOnly(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(forceReadOnly = !item.forceReadOnly)
                db.collectionDao().update(newItem)
            }
        }

    }

}
