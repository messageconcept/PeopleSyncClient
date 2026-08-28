/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import com.messageconcept.peoplesyncclient.accounts.AccountId
import com.messageconcept.peoplesyncclient.accounts.AccountIdIntentSerializer
import com.messageconcept.peoplesyncclient.accounts.toAndroidAccount
import com.messageconcept.peoplesyncclient.ui.account.AccountActivity.Companion.editAccountActivityIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        
        fun createIntent(context: Context, accountId: AccountId): Intent {
            return Intent(context, AccountSettingsActivity::class.java).apply {
                AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
            }
        }
        
        fun Intent.editAccountSettingsActivityIntent(accountId: AccountId) {
            AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
        }
    }

    private val accountId by lazy {
        AccountIdIntentSerializer.fromIntent(intent, EXTRA_ACCOUNT)
            ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AccountSettingsScreen(
                accountId = accountId,
                onNavWifiPermissionsScreen = {
                    val intent = WifiPermissionsActivity.createIntent(this, accountId.toAndroidAccount())
                    startActivity(intent)
                },
                onNavUp = ::onSupportNavigateUp,
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.editAccountActivityIntent(accountId.toAndroidAccount())
    }

}