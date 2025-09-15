/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.ui.account.AccountActivity
import com.messageconcept.peoplesyncclient.ui.intro.IntroActivity
import com.messageconcept.peoplesyncclient.ui.setup.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class AccountsActivity: AppCompatActivity() {

    @Inject
    lateinit var accountsDrawerHandler: AccountsDrawerHandler

    @Inject
    lateinit var managedSettings: ManagedSettings

    private val introActivityLauncher = registerForActivityResult(IntroActivity.Contract) { cancelled ->
        if (cancelled)
            finish()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle "Sync all" intent from launcher shortcut
        val syncAccounts = intent.action == Intent.ACTION_SYNC

        setContent {
            AccountsScreen(
                initialSyncAccounts = syncAccounts,
                onShowAppIntro = {
                    introActivityLauncher.launch(null)
                },
                accountsDrawerHandler = accountsDrawerHandler,
                onAddAccount = {
                    val intent = Intent(this, LoginActivity::class.java)
                    // attach infos from managed settings
                    managedSettings.getBaseUrl()?.let {
                        intent.putExtra(LoginActivity.EXTRA_URL, it)
                        intent.putExtra(LoginActivity.EXTRA_LOGIN_MANAGED, true)
                    }
                    managedSettings.getUsername()?.let {
                        intent.putExtra(LoginActivity.EXTRA_USERNAME, it)
                    }
                    startActivity(intent)
                },
                onShowAccount = { account ->
                    val intent = Intent(this, AccountActivity::class.java)
                    intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                onManagePermissions = {
                    startActivity(Intent(this, PermissionsActivity::class.java))
                }
            )
        }
    }

}