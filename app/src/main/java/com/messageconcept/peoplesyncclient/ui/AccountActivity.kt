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
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import com.messageconcept.peoplesyncclient.DavService
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.model.CollectionInfo
import com.messageconcept.peoplesyncclient.model.ServiceDB
import com.messageconcept.peoplesyncclient.model.ServiceDB.*
import com.messageconcept.peoplesyncclient.model.ServiceDB.Collections
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_account.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level

class AccountActivity: AppCompatActivity(), Toolbar.OnMenuItemClickListener, PopupMenu.OnMenuItemClickListener, LoaderManager.LoaderCallbacks<AccountActivity.AccountInfo> {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        private fun requestSync(context: Context, account: Account) {
            val authorities = arrayOf(
                    context.getString(R.string.address_books_authority)
            )

            for (authority in authorities) {
                val extras = Bundle(2)
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
                ContentResolver.requestSync(account, authority, extras)
            }
        }

    }

    lateinit var account: Account
    private var accountInfo: AccountInfo? = null
    private var refreshed = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // account may be a PeopleSync address book account -> use main account in this case
        account = LocalAddressBook.mainAccount(this,
                requireNotNull(intent.getParcelableExtra(EXTRA_ACCOUNT)))
        title = account.name

        setContentView(R.layout.activity_account)
        val icMenu = AppCompatResources.getDrawable(this, R.drawable.ic_menu_light)

        // CardDAV toolbar
        carddav_menu.overflowIcon = icMenu
        carddav_menu.inflateMenu(R.menu.carddav_actions)
        carddav_menu.setOnMenuItemClickListener(this)

        // load CardDAV/CalDAV collections
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED })
            // we've got additional permissions; try to load everything again
            reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_account, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val itemRename = menu.findItem(R.id.rename_account)
        // renameAccount is available for API level 21+
        itemRename.isVisible = Build.VERSION.SDK_INT >= 21
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sync_now ->
                requestSync()
            R.id.settings -> {
                val intent = Intent(this, AccountSettingsActivity::class.java)
                intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
                startActivity(intent)
            }
            R.id.rename_account ->
                RenameAccountFragment.newInstance(account).show(supportFragmentManager, null)
            R.id.delete_account -> {
                AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_error_dark)
                        .setTitle(R.string.account_delete_confirmation_title)
                        .setMessage(R.string.account_delete_confirmation_text)
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            deleteAccount()
                        }
                        .show()
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh_address_books ->
                accountInfo?.carddav?.let { carddav ->
                    val intent = Intent(this, DavService::class.java)
                    intent.action = DavService.ACTION_REFRESH_COLLECTIONS
                    intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, carddav.id)
                    startService(intent)
                }
            R.id.create_address_book -> {
                val intent = Intent(this, CreateAddressBookActivity::class.java)
                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                startActivity(intent)
            }
        }
        return false
    }


    private val onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, _ ->
        if (!view.isEnabled)
            return@OnItemClickListener

        val list = parent as ListView
        val adapter = list.adapter as ArrayAdapter<CollectionInfo>
        val info = adapter.getItem(position)!!
        val nowChecked = !info.selected

        SelectCollectionTask(applicationContext, info, nowChecked, WeakReference(adapter), WeakReference(view)).execute()
    }

    private val onActionOverflowListener = { anchor: View, info: CollectionInfo ->
        val popup = PopupMenu(this, anchor, Gravity.RIGHT)
        popup.inflate(R.menu.account_collection_operations)

        with(popup.menu.findItem(R.id.force_read_only)) {
            if (info.privWriteContent)
                isChecked = info.forceReadOnly
            else
                isVisible = false
        }

        popup.menu.findItem(R.id.delete_collection).isVisible = false

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.force_read_only -> {
                    val nowChecked = !item.isChecked
                    SetReadOnlyTask(WeakReference(this), info.id!!, nowChecked).execute()
                }
                R.id.delete_collection ->
                    DeleteCollectionFragment.ConfirmDeleteCollectionFragment.newInstance(account, info).show(supportFragmentManager, null)
                R.id.properties ->
                    CollectionInfoFragment.newInstance(info).show(supportFragmentManager, null)
            }
            true
        }
        popup.show()

        // long click was handled
        true
    }


    /* TASKS */

    @SuppressLint("StaticFieldLeak")
    class SelectCollectionTask(
            val applicationContext: Context,

            val info: CollectionInfo,
            val nowChecked: Boolean,
            val adapter: WeakReference<ArrayAdapter<*>>,
            val view: WeakReference<View>
    ): AsyncTask<Void, Void, Void>() {

        override fun onPreExecute() {
            view.get()?.isEnabled = false
        }

        override fun doInBackground(vararg params: Void?): Void? {
            val values = ContentValues(1)
            values.put(Collections.SYNC, if (nowChecked) 1 else 0)

            OpenHelper(applicationContext).use { dbHelper ->
                val db = dbHelper.writableDatabase
                db.update(Collections._TABLE, values, "${Collections.ID}=?", arrayOf(info.id.toString()))
            }

            return null
        }

        override fun onPostExecute(result: Void?) {
            info.selected = nowChecked
            adapter.get()?.notifyDataSetChanged()
            view.get()?.isEnabled = true
        }

    }

    class SetReadOnlyTask(
            val activity: WeakReference<AccountActivity>,
            val id: Long,
            val nowChecked: Boolean
    ): AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void?): Void? {
            activity.get()?.let { context ->
                OpenHelper(context).use { dbHelper ->
                    val values = ContentValues(1)
                    values.put(Collections.FORCE_READ_ONLY, nowChecked)

                    val db = dbHelper.writableDatabase
                    db.update(Collections._TABLE, values, "${Collections.ID}=?", arrayOf(id.toString()))
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            activity.get()?.reload()
        }

    }


    /* LOADERS AND LOADED DATA */

    class AccountInfo {
        var carddav: ServiceInfo? = null
        var caldav: ServiceInfo? = null

        class ServiceInfo {
            var id: Long? = null
            var refreshing = false

            var hasHomeSets = false
            var collections = listOf<CollectionInfo>()
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?) =
            AccountLoader(this, account)

    fun reload() {
        LoaderManager.getInstance(this).restartLoader(0, null, this)
    }

    override fun onLoadFinished(loader: Loader<AccountInfo>, info: AccountInfo?) {
        accountInfo = info

        if (info?.caldav?.collections?.any { it.selected } != true &&
            info?.carddav?.collections?.any { it.selected} != true)
            select_collections_hint.visibility = View.VISIBLE

        carddav.visibility = info?.carddav?.let { carddav ->
            carddav_refreshing.visibility = if (carddav.refreshing) View.VISIBLE else View.GONE

            address_books.isEnabled = !carddav.refreshing
            address_books.alpha = if (carddav.refreshing) 0.5f else 1f

            carddav_menu.menu.findItem(R.id.create_address_book).isEnabled = carddav.hasHomeSets

            val adapter = AddressBookAdapter(this)
            adapter.addAll(carddav.collections)
            address_books.adapter = adapter
            address_books.onItemClickListener = onItemClickListener

            View.VISIBLE
        } ?: View.GONE

        // ask for permissions
        val requiredPermissions = mutableSetOf<String>()
        if (info?.carddav != null) {
            // if there is a CardDAV service, ask for contacts permissions
            requiredPermissions += Manifest.permission.READ_CONTACTS
            requiredPermissions += Manifest.permission.WRITE_CONTACTS
        }

        val askPermissions = requiredPermissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (askPermissions.isNotEmpty())
            ActivityCompat.requestPermissions(this, askPermissions.toTypedArray(), 0)

        if (!refreshed) {
            Logger.log.info("Refreshing CardDAV collections")
            info?.carddav?.let { carddav ->
                val intent = Intent(this, DavService::class.java)
                intent.action = DavService.ACTION_REFRESH_COLLECTIONS
                intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, carddav.id)
                intent.putExtra(DavService.AUTO_SYNC, true)
                startService(intent)
            }
            refreshed = true
        }
    }

    override fun onLoaderReset(loader: Loader<AccountInfo>) {
        address_books?.adapter = null
    }


    class AccountLoader(
            context: Context,
            val account: Account
    ): AsyncTaskLoader<AccountInfo>(context), DavService.RefreshingStatusListener, SyncStatusObserver {

        private var syncStatusListener: Any? = null

        private var davServiceConn: ServiceConnection? = null
        private var davService: DavService.InfoBinder? = null

        override fun onStartLoading() {
            // get notified when sync status changes
            if (syncStatusListener == null)
                syncStatusListener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this)

            // bind to DavService to get notified when it's running
            if (davServiceConn == null) {
                val serviceConn = object: ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        // get notified when DavService is running
                        davService = service as DavService.InfoBinder
                        service.addRefreshingStatusListener(this@AccountLoader, false)

                        onContentChanged()
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        davService = null
                    }
                }
                if (context.bindService(Intent(context, DavService::class.java), serviceConn, Context.BIND_AUTO_CREATE))
                    davServiceConn = serviceConn
            } else
                forceLoad()
        }

        override fun onReset() {
            syncStatusListener?.let {
                ContentResolver.removeStatusChangeListener(it)
                syncStatusListener = null
            }

            davService?.removeRefreshingStatusListener(this)
            davServiceConn?.let {
                context.unbindService(it)
                davServiceConn = null
            }
        }

        override fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean) =
                onContentChanged()

        override fun onStatusChanged(which: Int) =
                onContentChanged()

        override fun loadInBackground(): AccountInfo {
            val info = AccountInfo()

            OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase
                db.query(
                        Services._TABLE,
                        arrayOf(Services.ID, Services.SERVICE),
                        "${Services.ACCOUNT_NAME}=?", arrayOf(account.name),
                        null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        when (cursor.getString(1)) {
                            Services.SERVICE_CARDDAV -> {
                                val carddav = AccountInfo.ServiceInfo()
                                info.carddav = carddav
                                carddav.id = id
                                carddav.refreshing =
                                        davService?.isRefreshing(id) ?: false ||
                                        ContentResolver.isSyncActive(account, context.getString(R.string.address_books_authority))

                                val accountManager = AccountManager.get(context)
                                for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                                    val addressBook = LocalAddressBook(context, addrBookAccount, null)
                                    try {
                                        if (account == addressBook.mainAccount)
                                            carddav.refreshing = carddav.refreshing || ContentResolver.isSyncActive(addrBookAccount, ContactsContract.AUTHORITY)
                                    } catch(e: Exception) {
                                    }
                                }

                                carddav.hasHomeSets = hasHomeSets(db, id)
                                carddav.collections = readCollections(db, id)
                            }
                        }
                    }
                }
            }

            return info
        }

        private fun hasHomeSets(db: SQLiteDatabase, service: Long): Boolean {
            db.query(ServiceDB.HomeSets._TABLE, null, "${ServiceDB.HomeSets.SERVICE_ID}=?",
                    arrayOf(service.toString()), null, null, null)?.use { cursor ->
                return cursor.count > 0
            }
            return false
        }

        @SuppressLint("Recycle")
        private fun readCollections(db: SQLiteDatabase, service: Long): List<CollectionInfo>  {
            val collections = LinkedList<CollectionInfo>()
            db.query(Collections._TABLE, null, Collections.SERVICE_ID + "=?", arrayOf(service.toString()),
                    null, null, "${Collections.SUPPORTS_VEVENT} DESC,${Collections.DISPLAY_NAME}").use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues(cursor.columnCount)
                    DatabaseUtils.cursorRowToContentValues(cursor, values)
                    collections.add(CollectionInfo(values))
                }
            }

            return collections
        }

    }


    /* LIST ADAPTERS */

    class AddressBookAdapter(
            context: Context
    ): ArrayAdapter<CollectionInfo>(context, R.layout.account_carddav_item) {
        override fun getView(position: Int, _v: View?, parent: ViewGroup?): View {
            val v = _v ?: LayoutInflater.from(context).inflate(R.layout.account_carddav_item, parent, false)
            val info = getItem(position)!!

            val checked: CheckBox = v.findViewById(R.id.checked)
            checked.isChecked = info.selected

            var tv: TextView = v.findViewById(R.id.title)
            tv.text = if (!info.displayName.isNullOrBlank()) info.displayName else info.url.toString()

            tv = v.findViewById(R.id.description)
            if (info.description.isNullOrBlank())
                tv.visibility = View.GONE
            else {
                tv.visibility = View.VISIBLE
                tv.text = info.description
            }

            v.findViewById<ImageView>(R.id.read_only).visibility =
                    if (!info.privWriteContent || info.forceReadOnly) View.VISIBLE else View.GONE

            v.findViewById<ImageView>(R.id.action_overflow).setOnClickListener { view ->
                @Suppress("ReplaceSingleLineLet")
                (context as? AccountActivity)?.let {
                    it.onActionOverflowListener(view, info)
                }
            }

            return v
        }
    }

    /* DIALOG FRAGMENTS */

    class RenameAccountFragment: DialogFragment() {

        companion object {

            const val ARG_ACCOUNT = "account"

            fun newInstance(account: Account): RenameAccountFragment {
                val fragment = RenameAccountFragment()
                val args = Bundle(1)
                args.putParcelable(ARG_ACCOUNT, account)
                fragment.arguments = args
                return fragment
            }

        }

        @SuppressLint("Recycle")
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val oldAccount: Account = arguments!!.getParcelable(ARG_ACCOUNT)!!

            val editText = EditText(activity)
            editText.setText(oldAccount.name)

            return AlertDialog.Builder(activity!!)
                    .setTitle(R.string.account_rename)
                    .setMessage(R.string.account_rename_new_name)
                    .setView(editText)
                    .setPositiveButton(R.string.account_rename_rename, DialogInterface.OnClickListener { _, _ ->
                        val newName = editText.text.toString()

                        if (newName == oldAccount.name)
                            return@OnClickListener

                        // remember sync intervals
                        val oldSettings = AccountSettings(requireActivity(), oldAccount)
                        val authorities = arrayOf(
                                getString(R.string.address_books_authority)
                        )
                        val syncIntervals = authorities.map { Pair(it, oldSettings.getSyncInterval(it)) }

                        val accountManager = AccountManager.get(activity)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            accountManager.renameAccount(oldAccount, newName, {
                                // account has now been renamed
                                Logger.log.info("Updating account name references")

                                // cancel maybe running synchronization
                                ContentResolver.cancelSync(oldAccount, null)
                                for (addrBookAccount in accountManager.getAccountsByType(getString(R.string.account_type_address_book)))
                                    ContentResolver.cancelSync(addrBookAccount, null)

                                // update account name references in database
                                OpenHelper(requireActivity()).use { dbHelper ->
                                    ServiceDB.onRenameAccount(dbHelper.writableDatabase, oldAccount.name, newName)
                                }

                                // update main account of address book accounts
                                if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                                    try {
                                        requireActivity().contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { provider ->
                                            for (addrBookAccount in accountManager.getAccountsByType(getString(R.string.account_type_address_book)))
                                                try {
                                                    val addressBook = LocalAddressBook(requireActivity(), addrBookAccount, provider)
                                                    if (oldAccount == addressBook.mainAccount)
                                                        addressBook.mainAccount = Account(newName, oldAccount.type)
                                                } finally {
                                                    @Suppress("DEPRECATION")
                                                    if (Build.VERSION.SDK_INT >= 24)
                                                        provider.close()
                                                    else
                                                        provider.release()
                                                }
                                        }
                                    } catch(e: Exception) {
                                        Logger.log.log(Level.SEVERE, "Couldn't update address book accounts", e)
                                    }

                                // calendar provider doesn't allow changing account_name of Events
                                // (all events will have to be downloaded again)

                                // retain sync intervals
                                val newAccount = Account(newName, oldAccount.type)
                                val newSettings = AccountSettings(requireActivity(), newAccount)
                                for ((authority, interval) in syncIntervals) {
                                    if (interval == null)
                                        ContentResolver.setIsSyncable(newAccount, authority, 0)
                                    else {
                                        ContentResolver.setIsSyncable(newAccount, authority, 1)
                                        newSettings.setSyncInterval(authority, interval)
                                    }
                                }

                                // synchronize again
                                requestSync(activity!!, newAccount)
                            }, null)
                        activity!!.finish()
                    })
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .create()
        }
    }


    /* USER ACTIONS */

    private fun deleteAccount() {
        val accountManager = AccountManager.get(this)

        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, this, { future ->
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
            accountManager.removeAccount(account, { future ->
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

    private fun requestSync() {
        requestSync(this, account)
        Snackbar.make(findViewById(R.id.parent), R.string.account_synchronizing_now, Snackbar.LENGTH_LONG).show()
    }

}
