/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.servicedetection.RefreshCollectionsWorker
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.syncadapter.OneTimeSyncWorker
import com.messageconcept.peoplesyncclient.ui.AppTheme
import com.messageconcept.peoplesyncclient.ui.PermissionsActivity
import com.messageconcept.peoplesyncclient.ui.composable.ActionCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject
    lateinit var modelFactory: AccountModel.Factory
    val model by viewModels<AccountModel> {
        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }

    /** Tri-state enum to represent active / pending / idle status */
    enum class Progress {
        Active,     // syncing or refreshing
        Pending,    // sync pending
        Idle
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.invalid.observe(this) { invalid ->
            if (invalid)
                // account does not exist anymore
                finish()
        }
        model.cardDavSvc.observe(this) {
            Logger.log.info("Triggering sync")
            OneTimeSyncWorker.enqueueAllAuthorities(this, model.account)
        }
        model.renameAccountError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                model.renameAccountError.value = null
            }
        }

        setContent {
            AppTheme {
                val cardDavSvc by model.cardDavSvc.observeAsState()
                val canCreateAddressBook by model.canCreateAddressBook.observeAsState(false)
                val cardDavRefreshing by model.cardDavRefreshing.observeAsState(false)
                val cardDavSyncPending by model.cardDavSyncPending.observeAsState(false)
                val cardDavSyncing by model.cardDavSyncing.observeAsState(false)
                val cardDavProgress: Progress = when {
                    cardDavRefreshing || cardDavSyncing -> Progress.Active
                    cardDavSyncPending -> Progress.Pending
                    else -> Progress.Idle
                }
                val addressBooks by model.addressBooksPager.observeAsState()

                AccountOverview(
                    account = model.account,
                    showOnlyPersonal =
                        model.showOnlyPersonal.observeAsState(
                            AccountSettings.ShowOnlyPersonal(onlyPersonal = false, locked = true)
                        ).value,
                    onSetShowOnlyPersonal = {
                        model.setShowOnlyPersonal(it)
                    },
                    hasCardDav = cardDavSvc != null,
                    canCreateAddressBook = canCreateAddressBook,
                    cardDavProgress = cardDavProgress,
                    cardDavRefreshing = cardDavRefreshing,
                    addressBooks = addressBooks?.flow?.collectAsLazyPagingItems(),
                    onUpdateCollectionSync = { collectionId, sync ->
                        model.setCollectionSync(collectionId, sync)
                    },
                    onChangeForceReadOnly = { id, forceReadOnly ->
                        model.setCollectionForceReadOnly(id, forceReadOnly)
                    },
                    onSync = {
                        OneTimeSyncWorker.enqueueAllAuthorities(this, model.account, manual = true)
                    },
                    onAccountSettings = {
                        val intent = Intent(this, AccountSettingsActivity::class.java)
                        intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, model.account)
                        startActivity(intent, null)
                    },
                    onRenameAccount = { newName ->
                        model.renameAccount(newName)
                    },
                    onDeleteAccount = {
                        model.deleteAccount()
                    },
                    onNavigateUp = ::onSupportNavigateUp
                )
            }
        }
    }

    /**
     * Subscribes to a Webcal using a compatible app like ICSx5.
     *
     * @return true if a compatible Webcal app is installed, false otherwise
     */
    private fun subscribeWebcal(item: Collection): Boolean {
        // subscribe
        var uri = Uri.parse(item.source.toString())
        when {
            uri.scheme.equals("http", true) -> uri = uri.buildUpon().scheme("webcal").build()
            uri.scheme.equals("https", true) -> uri = uri.buildUpon().scheme("webcals").build()
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        item.displayName?.let { intent.putExtra("title", it) }
        item.color?.let { intent.putExtra("color", it) }

        if (packageManager.resolveActivity(intent, 0) != null) {
            startActivity(intent)
            return true
        }

        return false
    }

}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AccountOverview(
    account: Account,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    hasCardDav: Boolean,
    canCreateAddressBook: Boolean,
    cardDavProgress: AccountActivity.Progress,
    cardDavRefreshing: Boolean,
    addressBooks: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onRenameAccount: (newName: String) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val context = LocalContext.current

    val pullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        pullRefreshing,
        onRefresh = onSync
    )

    // tabs calculation
    var nextIdx = -1
    @Suppress("KotlinConstantConditions")
    val idxCardDav: Int? = if (hasCardDav) ++nextIdx else null
    val nrPages =
        (if (idxCardDav != null) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { nrPages })

    // snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    AccountOverview_SnackbarContent(
        snackbarHostState = snackbarHostState,
        currentPageIsCardDav = pagerState.currentPage == idxCardDav,
        cardDavRefreshing = cardDavRefreshing
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                title = {
                    Text(
                        account.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    AccountOverview_Actions(
                        account = account,
                        canCreateAddressBook = canCreateAddressBook,
                        showOnlyPersonal = showOnlyPersonal,
                        onSetShowOnlyPersonal = onSetShowOnlyPersonal,
                        currentPage = pagerState.currentPage,
                        idxCardDav = idxCardDav,
                        onRenameAccount = onRenameAccount,
                        onDeleteAccount = onDeleteAccount,
                        onAccountSettings = onAccountSettings
                    )
                }
            )
        },
        floatingActionButton = {
            Column {
                if (pagerState.currentPage == idxCardDav)
                    FloatingActionButton(onClick = onSync) {
                        // Material 3: add Tooltip
                        Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                    }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        modifier = Modifier.pullRefresh(pullRefreshState)
    ) { padding ->
        Column {
            if (nrPages > 0) {
                HorizontalPager(
                    pagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(padding)
                ) { index ->
                    Box {
                        when (index) {
                            idxCardDav ->
                                ServiceTab(
                                    requiredPermissions = listOf(Manifest.permission.WRITE_CONTACTS),
                                    progress = cardDavProgress,
                                    collections = addressBooks,
                                    onUpdateCollectionSync = onUpdateCollectionSync,
                                    onChangeForceReadOnly = onChangeForceReadOnly
                                )
                        }

                        PullRefreshIndicator(
                            refreshing = pullRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AccountOverview_CardDAV_CalDAV() {
    AccountOverview(
        account = Account("test@example.com", "test"),
        showOnlyPersonal = AccountSettings.ShowOnlyPersonal(false, true),
        onSetShowOnlyPersonal = {},
        hasCardDav = true,
        canCreateAddressBook = false,
        cardDavProgress = AccountActivity.Progress.Active,
        cardDavRefreshing = false,
        addressBooks = null,
    )
}

@Composable
fun AccountOverview_Actions(
    account: Account,
    canCreateAddressBook: Boolean,
    showOnlyPersonal: AccountSettings.ShowOnlyPersonal,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    currentPage: Int,
    idxCardDav: Int?,
    onRenameAccount: (newName: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onAccountSettings: () -> Unit
) {
    val context = LocalContext.current

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showRenameAccountDialog by remember { mutableStateOf(false) }

    var overflowOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onAccountSettings) {
        Icon(Icons.Default.Settings, stringResource(R.string.account_settings))
    }
    IconButton(onClick = { overflowOpen = !overflowOpen }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.options_menu))
    }
    DropdownMenu(
        expanded = overflowOpen,
        onDismissRequest = { overflowOpen = false }
    ) {
        // TAB-SPECIFIC ACTIONS

        // create collection
        if (currentPage == idxCardDav && canCreateAddressBook) {
            // create address book
            DropdownMenuItem(onClick = {
                val intent = Intent(context, CreateAddressBookActivity::class.java)
                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent)

                overflowOpen = false
            }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.create_addressbook),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.create_addressbook))
            }
        }

        // GENERAL ACTIONS

        // rename account
        DropdownMenuItem(onClick = {
            showRenameAccountDialog = true
            overflowOpen = false
        }) {
            Icon(
                Icons.Default.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.account_rename),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.account_rename))
        }

        // delete account
        DropdownMenuItem(onClick = {
            showDeleteAccountDialog = true
            overflowOpen = false
        }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.account_delete),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.account_delete))
        }
    }

    // modal dialogs
    if (showRenameAccountDialog)
        RenameAccountDialog(
            oldName = account.name,
            onRenameAccount = { newName ->
                onRenameAccount(newName)
                showRenameAccountDialog = false
            },
            onDismiss = { showRenameAccountDialog = false }
        )
    if (showDeleteAccountDialog)
        DeleteAccountDialog(
            onConfirm = onDeleteAccount,
            onDismiss = { showDeleteAccountDialog = false }
        )
}

@Composable
fun AccountOverview_SnackbarContent(
    snackbarHostState: SnackbarHostState,
    currentPageIsCardDav: Boolean,
    cardDavRefreshing: Boolean,
) {
    val context = LocalContext.current

    // show snackbar when refreshing collection list
    val currentTabRefreshing = cardDavRefreshing
    LaunchedEffect(currentTabRefreshing) {
        if (currentTabRefreshing)
            snackbarHostState.showSnackbar(
                context.getString(R.string.account_refreshing_collections),
                duration = SnackbarDuration.Indefinite
            )
    }
}

@Composable
@Preview
fun DeleteAccountDialog(
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_confirmation_title)) },
        text = { Text(stringResource(R.string.account_delete_confirmation_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok).uppercase())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel).uppercase())
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ServiceTab(
    requiredPermissions: List<String>,
    progress: AccountActivity.Progress,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
) {
    val context = LocalContext.current

    Column {
        // progress indicator
        val progressAlpha = progressAlpha(progress)
        when (progress) {
            AccountActivity.Progress.Active -> LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
            AccountActivity.Progress.Pending,
            AccountActivity.Progress.Idle -> LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                progress = 1f,
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
        }

        // permissions warning
        val permissionsState = rememberMultiplePermissionsState(requiredPermissions)
        if (!permissionsState.allPermissionsGranted)
            ActionCard(
                icon = Icons.Default.SyncProblem,
                actionText = stringResource(R.string.account_manage_permissions),
                onAction = {
                    val intent = Intent(context, PermissionsActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.account_missing_permissions))
            }

        // collection list
        if (collections != null)
            CollectionsList(
                collections,
                onChangeSync = onUpdateCollectionSync,
                onChangeForceReadOnly = onChangeForceReadOnly,
                onSubscribe = onSubscribe,
                modifier = Modifier.weight(1f)
            )
    }
}

@Composable
fun progressAlpha(progress: AccountActivity.Progress): Float {
    val progressAlpha by animateFloatAsState(
        when (progress) {
            AccountActivity.Progress.Active -> 1f
            AccountActivity.Progress.Pending -> 0.5f
            AccountActivity.Progress.Idle -> 0f
        },
        label = "progressAlpha",
        animationSpec = tween(500)
    )
    return progressAlpha
}