/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.ui.intro

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.messageconcept.peoplesyncclient.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class WelcomeFragment: Fragment() {

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    private val fontName = GoogleFont("Open Sans")
    private val fontFamily = FontFamily(Font(googleFont = fontName, fontProvider = provider))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ContentLandscape()
                else
                    ContentPortrait()
            }
        }
    }

    @Preview(
        device = "id:3.7in WVGA (Nexus One)",
        showSystemUi = true
    )
    @Composable
    private fun ContentPortrait() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(R.color.primaryDarkColor)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_white),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .weight(1.5f)
                    .scale(0.8f)
            )

            Text(
                text = stringResource(R.string.intro_slogan1),
                color = Color.White,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp)
                    .weight(0.5f)
            )

            Text(
                text = stringResource(R.string.intro_slogan2),
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .weight(1.5f)
            )

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }

    @Preview(
        showSystemUi = true,
        device = "id:medium_tablet"
    )
    @Composable
    private fun ContentLandscape() {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(R.color.primaryDarkColor))
                .padding(
                    bottom = dimensionResource(
                        com.github.appintro.R.dimen.appintro2_bottombar_height
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_white),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 48.dp)
                    .weight(1.5f)
                    .scale(0.8f)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .weight(2f)
            ) {
                Text(
                    text = stringResource(R.string.intro_slogan1),
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.intro_slogan2),
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )
            }
        }
    }


    @Module
    @InstallIn(ActivityComponent::class)
    abstract class WelcomeFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: Factory): IntroFragmentFactory
    }

    class Factory @Inject constructor() : IntroFragmentFactory {

        override fun getOrder(context: Context) = -1000

        override fun create() = WelcomeFragment()

    }

}
