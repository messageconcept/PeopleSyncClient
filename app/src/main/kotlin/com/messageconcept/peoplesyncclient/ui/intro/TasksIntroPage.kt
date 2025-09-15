/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import androidx.compose.runtime.Composable
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.sync.TasksAppManager
import com.messageconcept.peoplesyncclient.ui.TasksCard
import com.messageconcept.peoplesyncclient.ui.TasksModel
import javax.inject.Inject

class TasksIntroPage @Inject constructor(
    private val settingsManager: SettingsManager,
    private val tasksAppManager: TasksAppManager
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy {
        return if (tasksAppManager.currentProvider() != null || settingsManager.getBooleanOrNull(TasksModel.HINT_OPENTASKS_NOT_INSTALLED) == false)
                ShowPolicy.DONT_SHOW
            else
                ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        TasksCard()
    }

}