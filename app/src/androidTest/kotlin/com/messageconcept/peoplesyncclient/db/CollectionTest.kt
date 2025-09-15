/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db

import android.security.NetworkSecurityPolicy
import androidx.test.filters.SmallTest
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.property.webdav.ResourceType
import com.messageconcept.peoplesyncclient.network.HttpClient
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CollectionTest {

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var httpClient: HttpClient
    private val server = MockWebServer()

    @Before
    fun setup() {
        hiltRule.inject()

        httpClient = httpClientBuilder.build()
        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun teardown() {
        httpClient.close()
    }


    @Test
    @SmallTest
    fun testFromDavResponseAddressBook() {
        // r/w address book
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                        "<response>" +
                        "   <href>/</href>" +
                        "   <propstat><prop>" +
                        "       <resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                        "       <displayname>My Contacts</displayname>" +
                        "       <CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
            info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
        }
        assertEquals(Collection.TYPE_ADDRESSBOOK, info.type)
        assertTrue(info.privWriteContent)
        assertTrue(info.privUnbind)
        assertNull(info.supportsVEVENT)
        assertNull(info.supportsVTODO)
        assertNull(info.supportsVJOURNAL)
        assertEquals("My Contacts", info.displayName)
        assertEquals("My Contacts Description", info.description)
    }

}