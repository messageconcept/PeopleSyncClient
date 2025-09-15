/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui.intro

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.ui.AppTheme
import com.messageconcept.peoplesyncclient.ui.M3ColorScheme

class WelcomePage: IntroPage() {

    override val customTopInsets: Boolean = true

    override fun getShowPolicy() = ShowPolicy.SHOW_ONLY_WITH_OTHERS

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    private val fontName = GoogleFont("Open Sans")
    private val fontFamily = FontFamily(Font(googleFont = fontName, fontProvider = provider))

    @Composable
    override fun ComposePage() {
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ContentLandscape()
        else
            ContentPortrait()
    }


    @Composable
    private fun ContentPortrait() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = M3ColorScheme.primaryLight)     // fill background color edge-to-edge
                .safeContentPadding()
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
                lineHeight = 40.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .weight(1.5f)
            )

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }

    @Composable
    @Preview(
        device = "id:3.7in WVGA (Nexus One)",
        showSystemUi = true
    )
    fun Preview_ContentPortrait_Light() {
        AppTheme(darkTheme = false) {
            ContentPortrait()
        }
    }

    @Composable
    @Preview(
        device = "id:3.7in WVGA (Nexus One)",
        showSystemUi = true
    )
    fun Preview_ContentPortrait_Dark() {
        AppTheme(darkTheme = true) {
            ContentPortrait()
        }
    }


    @Preview(
        showSystemUi = true,
        device = "id:medium_tablet"
    )
    @Composable
    private fun ContentLandscape() {
        AppTheme {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.primary)
                    .safeContentPadding(),
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
    }

}