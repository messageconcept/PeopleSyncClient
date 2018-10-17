package com.messageconcept.peoplesyncclient.ui.intro

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.log.Logger
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.ui.intro.IIntroFragmentFactory.ShowMode
import com.github.paolorotolo.appintro.AppIntro2
import java.util.*

class IntroActivity: AppIntro2() {

    companion object {

        private val serviceLoader = ServiceLoader.load(IIntroFragmentFactory::class.java)!!
        private val introFragmentFactories = serviceLoader.toList()
        init {
            introFragmentFactories.forEach {
                Logger.log.fine("Registered intro fragment ${it::class.java}")
            }
        }

        fun shouldShowIntroActivity(context: Context): Boolean {
            val settings = Settings.getInstance(context)
            return introFragmentFactories.any {
                val show = it.shouldBeShown(context, settings)
                Logger.log.fine("Intro fragment $it: showMode=$show")
                show == ShowMode.SHOW
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings.getInstance(this)

        val factoriesWithMode = introFragmentFactories.associate { Pair(it, it.shouldBeShown(this, settings)) }
        val showAll = factoriesWithMode.values.any { it == ShowMode.SHOW }
        for ((factory, mode) in factoriesWithMode)
            if (mode == ShowMode.SHOW || (mode == ShowMode.SHOW_NOT_ALONE && showAll))
                addSlide(factory.create())

        setBarColor(resources.getColor(R.color.primaryDarkColor))
        showSkipButton(false)
    }


    override fun onBackPressed() {
        if (pager.isFirstSlide(fragments.size))
            setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        setResult(Activity.RESULT_OK)
        finish()
    }

}