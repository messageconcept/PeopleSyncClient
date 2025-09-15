/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */


import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.outlined.RuleFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.db.Collection
import com.messageconcept.peoplesyncclient.ui.AppTheme
import com.messageconcept.peoplesyncclient.ui.PermissionsActivity
import com.messageconcept.peoplesyncclient.ui.account.AccountProgress
import com.messageconcept.peoplesyncclient.ui.account.AccountScreenModel
import com.messageconcept.peoplesyncclient.ui.account.CollectionsList
import com.messageconcept.peoplesyncclient.ui.account.RenameAccountDialog
import com.messageconcept.peoplesyncclient.ui.composable.ActionCard
import com.messageconcept.peoplesyncclient.ui.composable.ProgressBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    account: Account,
    onAccountSettings: () -> Unit,
    onCreateAddressBook: () -> Unit,
    onCollectionDetails: (Collection) -> Unit,
    onNavUp: () -> Unit,
    onFinish: () -> Unit
) {
    val model: AccountScreenModel = hiltViewModel(
        creationCallback = { factory: AccountScreenModel.Factory ->
            factory.create(account)
        }
    )

    val cardDavService by model.cardDavSvc.collectAsStateWithLifecycle()
    val addressBooks = model.addressBooks.collectAsLazyPagingItems()

    val context = LocalContext.current
    AccountScreen(
        accountName = account.name,
        error = model.error,
        onResetError = model::resetError,
        invalidAccount = model.invalidAccount.collectAsStateWithLifecycle(false).value,
        showOnlyPersonal = model.showOnlyPersonal.collectAsStateWithLifecycle().value,
        showOnlyPersonalLocked = model.showOnlyPersonalLocked.collectAsStateWithLifecycle().value,
        onSetShowOnlyPersonal = model::setShowOnlyPersonal,
        hasCardDav = cardDavService != null,
        canCreateAddressBook = model.canCreateAddressBook.collectAsStateWithLifecycle(false).value,
        cardDavProgress = model.cardDavProgress.collectAsStateWithLifecycle(AccountProgress.Idle).value,
        addressBooks = addressBooks,
        onUpdateCollectionSync = model::setCollectionSync,
        onCollectionDetails = onCollectionDetails,
        onRefreshCollections = model::refreshCollections,
        onSync = model::sync,
        onAccountSettings = onAccountSettings,
        onCreateAddressBook = onCreateAddressBook,
        onRenameAccount = model::renameAccount,
        onDeleteAccount = model::deleteAccount,
        onNavUp = onNavUp,
        onFinish = onFinish
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AccountScreen(
    accountName: String,
    error: String? = null,
    onResetError: () -> Unit = {},
    invalidAccount: Boolean = false,
    showOnlyPersonal: Boolean = false,
    showOnlyPersonalLocked: Boolean = false,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit = {},
    hasCardDav: Boolean,
    canCreateAddressBook: Boolean,
    cardDavProgress: AccountProgress,
    addressBooks: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onCollectionDetails: (Collection) -> Unit = {},
    onRefreshCollections: () -> Unit = {},
    onSync: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onCreateAddressBook: () -> Unit = {},
    onRenameAccount: (newName: String) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavUp: () -> Unit = {},
    onFinish: () -> Unit = {}
) {
    AppTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        LaunchedEffect(invalidAccount) {
            if (invalidAccount) {
                Toast.makeText(context, R.string.account_invalid_account, Toast.LENGTH_LONG).show()
                onFinish()
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(error) {
            if (error != null)
                scope.launch {
                    snackbarHostState.showSnackbar(error)
                    onResetError()
                }
        }

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(300)
                isRefreshing = false
            }
        }

        // tabs calculation
        var nextIdx = -1

        @Suppress("KotlinConstantConditions")
        val idxCardDav: Int? = if (hasCardDav) ++nextIdx else null
        val nrPages = (if (idxCardDav != null) 1 else 0)
        val pagerState = rememberPagerState(pageCount = { nrPages })

        val calDavScrollState = rememberLazyListState()
        val cardDavScrollState = rememberLazyListState()
        val webcalScrollState = rememberLazyListState()

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = {
                        Text(
                            text = accountName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        AccountScreen_Actions(
                            accountName = accountName,
                            canCreateAddressBook = canCreateAddressBook,
                            onCreateAddressBook = onCreateAddressBook,
                            showOnlyPersonal = showOnlyPersonal,
                            showOnlyPersonalLocked = showOnlyPersonalLocked,
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
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        text = {
                            Text(stringResource(R.string.account_refresh_collections))
                        },
                        icon = {
                            Icon(Icons.Outlined.RuleFolder, stringResource(R.string.account_refresh_collections))
                        },
                        onClick = onRefreshCollections,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (pagerState.currentPage == idxCardDav)
                        ExtendedFloatingActionButton(
                            text = {
                                Text(stringResource(R.string.account_synchronize_now))
                            },
                            icon = {
                                Icon(Icons.Default.Sync, stringResource(R.string.account_synchronize_now))
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = onSync
                        )
                }
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState)
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding)
            ) {
                if (nrPages > 0) {
                    SharedTransitionLayout {
                        val idxCurrentPage = pagerState.currentPage

                        // The icon shall be shown when the scroll state is at the top (= we can't scroll backward)
                        val currentPageScrollState = when (idxCurrentPage) {
                            idxCardDav -> cardDavScrollState
                            else -> null
                        }
                        AnimatedContent(
                            targetState = currentPageScrollState?.canScrollBackward != true
                        ) { showIcon ->
                            TabRow(selectedTabIndex = idxCurrentPage) {
                                if (idxCardDav != null)
                                    AccountScreen_Tab(
                                        selected = idxCurrentPage == idxCardDav,
                                        showIcon = showIcon,
                                        icon = Icons.Default.Group,
                                        text = stringResource(R.string.account_carddav),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                    ) {
                                        scope.launch {
                                            pagerState.scrollToPage(idxCardDav)
                                        }
                                    }
                            }
                        }
                    }

                    HorizontalPager(
                        pagerState,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { index ->
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { isRefreshing = true; onSync() }
                        ) {
                            when (index) {
                                idxCardDav ->
                                    AccountScreen_ServiceTab(
                                        requiredPermissions = listOf(Manifest.permission.WRITE_CONTACTS),
                                        progress = cardDavProgress,
                                        collections = addressBooks,
                                        onUpdateCollectionSync = onUpdateCollectionSync,
                                        onCollectionDetails = onCollectionDetails,
                                        state = cardDavScrollState
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun AccountScreen_Tab(
    selected: Boolean,
    showIcon: Boolean,
    icon: ImageVector,
    text: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    with(sharedTransitionScope) {
        if (showIcon) {
            Tab(
                selected = selected,
                onClick = onClick,
                icon = { Icon(imageVector = icon, contentDescription = text) },
                text = {
                    Text(
                        text,
                        modifier = Modifier
                            .sharedBounds(
                                rememberSharedContentState(key = text),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .padding(8.dp)
                    )
                }
            )
        } else {
            Tab(
                selected = selected,
                onClick = onClick,
                content = {
                    Text(
                        text,
                        modifier = Modifier
                            .sharedBounds(
                                rememberSharedContentState(key = text),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .padding(8.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun AccountScreen_Actions(
    accountName: String,
    canCreateAddressBook: Boolean,
    onCreateAddressBook: () -> Unit,
    showOnlyPersonal: Boolean,
    showOnlyPersonalLocked: Boolean,
    onSetShowOnlyPersonal: (showOnlyPersonal: Boolean) -> Unit,
    currentPage: Int,
    idxCardDav: Int?,
    onRenameAccount: (newName: String) -> Unit,
    onDeleteAccount: () -> Unit,
    onAccountSettings: () -> Unit
) {
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
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.create_addressbook),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                text = {
                    Text(stringResource(R.string.create_addressbook))
                },
                onClick = {
                    onCreateAddressBook()
                    overflowOpen = false
                }
            )
        }

        // GENERAL ACTIONS

        // show only personal
        DropdownMenuItem(
            leadingIcon = {
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    Checkbox(
                        checked = showOnlyPersonal,
                        enabled = !showOnlyPersonalLocked,
                        onCheckedChange = {
                            onSetShowOnlyPersonal(it)
                            overflowOpen = false
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            },
            text = {
                Text(stringResource(R.string.account_only_personal))
            },
            onClick = {
                onSetShowOnlyPersonal(!showOnlyPersonal)
                overflowOpen = false
            },
            enabled = !showOnlyPersonalLocked
        )

        // rename account
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Default.DriveFileRenameOutline,
                    contentDescription = stringResource(R.string.account_rename),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            text = {
                Text(stringResource(R.string.account_rename))
            },
            onClick = {
                showRenameAccountDialog = true
                overflowOpen = false
            }
        )

        // delete account
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.account_delete),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            text = {
                Text(stringResource(R.string.account_delete))
            },
            onClick = {
                showDeleteAccountDialog = true
                overflowOpen = false
            }
        )
    }

    // modal dialogs
    if (showRenameAccountDialog)
        RenameAccountDialog(
            oldName = accountName,
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AccountScreen_ServiceTab(
    requiredPermissions: List<String>,
    progress: AccountProgress,
    collections: LazyPagingItems<Collection>?,
    onUpdateCollectionSync: (collectionId: Long, sync: Boolean) -> Unit = { _, _ -> },
    onSubscribe: (Collection) -> Unit = {},
    onCollectionDetails: ((Collection) -> Unit)? = null,
    state: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current

    Column {
        // progress indicator
        val progressAlpha = progress.rememberAlpha()
        when (progress) {
            AccountProgress.Active -> ProgressBar(
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
            AccountProgress.Pending,
            AccountProgress.Idle -> ProgressBar(
                progress = { 1f },
                modifier = Modifier
                    .alpha(progressAlpha)
                    .fillMaxWidth()
            )
        }

        // permissions warning
        if (!LocalInspectionMode.current) {
            val permissionsState = rememberMultiplePermissionsState(requiredPermissions)
            if (!permissionsState.allPermissionsGranted)
                ActionCard(
                    icon = Icons.Default.SyncProblem,
                    actionText = stringResource(R.string.account_manage_permissions),
                    onAction = {
                        val intent = Intent(context, PermissionsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(stringResource(R.string.account_missing_permissions))
                }

            // collection list
            if (collections != null)
                CollectionsList(
                    collections,
                    onChangeSync = onUpdateCollectionSync,
                    onSubscribe = onSubscribe,
                    onCollectionDetails = onCollectionDetails,
                    modifier = Modifier.weight(1f),
                    state = state
                )
        }
    }
}

@Preview
@Composable
fun AccountScreen_Preview() {
    AccountScreen(
        accountName = "test@example.com",
        showOnlyPersonal = false,
        showOnlyPersonalLocked = true,
        hasCardDav = true,
        canCreateAddressBook = false,
        cardDavProgress = AccountProgress.Active,
        addressBooks = null,
    )
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
            Button(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}