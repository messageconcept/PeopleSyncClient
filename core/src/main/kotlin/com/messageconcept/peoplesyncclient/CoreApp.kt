/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import com.messageconcept.peoplesyncclient.di.qualifier.DefaultDispatcher
import com.messageconcept.peoplesyncclient.log.LogManager
import com.messageconcept.peoplesyncclient.startup.StartupPlugin
import com.messageconcept.peoplesyncclient.settings.ManagedSettings
import com.messageconcept.peoplesyncclient.settings.UpgradeFixes
import com.messageconcept.peoplesyncclient.sync.account.AccountsCleanupWorker
import com.messageconcept.peoplesyncclient.ui.UiUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

/**
 * The actual app should extend this class. The derived class must then be set
 * as `@HiltAndroidApp.`
 */
abstract class CoreApp: Application() {

    @Inject
    lateinit var logger: Logger

    /**
     * Creates the [com.messageconcept.peoplesyncclient.log.LogManager] singleton and thus initializes logging.
     */
    @Inject
    lateinit var logManager: LogManager

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Inject
    lateinit var plugins: Set<@JvmSuppressWildcards StartupPlugin>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var managedSettings: ManagedSettings

    @Inject
    lateinit var upgradeFixes: UpgradeFixes

    override fun onCreate() {
        super.onCreate()

        logger.fine("Logging using LogManager $logManager")

        // set light/dark mode
        UiUtils.updateTheme(this)   // when this is called in the asynchronous thread below, it recreates
                                 // some current activity and causes an IllegalStateException in rare cases

        // run startup plugins (sync)
        for (plugin in plugins.sortedBy { it.priority() }) {
            logger.fine("Running startup plugin: $plugin (onAppCreate)")
            plugin.onAppCreate()
        }

        // don't block UI for some background checks
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(defaultDispatcher) {
            // clean up orphaned accounts in DB from time to time
            AccountsCleanupWorker.Companion.enable(this@CoreApp)

            // create/update app shortcuts
            UiUtils.updateShortcuts(this@CoreApp)

            // trigger account updates when managed settings have changed
            managedSettings.updateAccounts()
            // trigger a (one-time) migration of the account settings for existing accounts
            upgradeFixes.enableNewAccountSettings()
            // (re-)enable automatic sync for accounts that were created with v4.4-*
            upgradeFixes.enableAutomaticSyncAllOnce()

            // run startup plugins (async)
            for (plugin in plugins.sortedBy { it.priorityAsync() }) {
                logger.fine("Running startup plugin: $plugin (onAppCreateAsync)")
                plugin.onAppCreateAsync()
            }
        }
    }

}