/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db

import androidx.test.filters.SmallTest
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.property.webdav.WebDAV
import com.messageconcept.peoplesyncclient.network.HttpClientBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CollectionTest {

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var httpClient: OkHttpClient
    private val server = MockWebServer()

    @Before
    fun setup() {
        hiltRule.inject()

        httpClient = httpClientBuilder.build()
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
        DavResource(httpClient, server.url("/"))
                .propfind(0, WebDAV.ResourceType) { response, _ ->
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