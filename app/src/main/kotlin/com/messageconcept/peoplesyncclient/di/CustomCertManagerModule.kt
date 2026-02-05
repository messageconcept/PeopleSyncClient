/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.di

import android.content.Context
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.cert4android.SettingsProvider
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.settings.Settings
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.ui.ForegroundTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.internal.tls.OkHostnameVerifier
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
/**
 * cert4android integration module
 */
class CustomCertManagerModule {

    @Provides
    @Singleton
    fun customCertManager(
        @ApplicationContext context: Context,
        settings: SettingsManager
    ): Optional<CustomCertManager> =
        if (BuildConfig.allowCustomCerts)
            Optional.of(CustomCertManager(
                certStore = CustomCertStore.getInstance(context),
                settings = object : SettingsProvider {

                    override val appInForeground: Boolean
                        get() = ForegroundTracker.inForeground.value

                    override val trustSystemCerts: Boolean
                        get() = !settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)

                }
            ))
        else
            Optional.empty()

    @Provides
    @Singleton
    fun customHostnameVerifier(
        customCertManager: Optional<CustomCertManager>
    ): Optional<CustomCertManager.HostnameVerifier> =
        if (BuildConfig.allowCustomCerts && customCertManager.isPresent) {
            val hostnameVerifier = customCertManager.get().HostnameVerifier(OkHostnameVerifier)
            Optional.of(hostnameVerifier)
        } else
            Optional.empty()

}