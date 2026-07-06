/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.db

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.property.webdav.WebDAV
import com.messageconcept.peoplesyncclient.util.DavUtils.toUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTest {

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")
    private val baseUrl = Url("https://dav.example.com/")

    private fun mockClient(xmlBody: String): HttpClient =
        HttpClient(MockEngine { respond(xmlBody, HttpStatusCode.MultiStatus, xmlHeaders) })


    @Test
    fun testFromDavResponseAddressBook() = runTest {
        mockClient(
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                    "<response>" +
                    "   <href>/</href>" +
                    "   <propstat><prop>" +
                    "       <resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                    "       <displayname>My Contacts</displayname>" +
                    "       <CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                    "   </prop></propstat>" +
                    "</response>" +
                    "</multistatus>"
        ).use { client ->
            val davResource = DavResource(client, baseUrl)
            var collectionFromResponse: Collection? = null
            davResource.propfind(0, WebDAV.ResourceType) { response, _ ->
                collectionFromResponse = Collection.fromDavResponse(response)
            }
            assertNotNull(collectionFromResponse)
            val collection = collectionFromResponse!!
            assertEquals(Collection.TYPE_ADDRESSBOOK, collection.type)
            assertTrue(collection.privWriteContent)
            assertTrue(collection.privUnbind)
            assertNull(collection.supportsVEVENT)
            assertNull(collection.supportsVTODO)
            assertNull(collection.supportsVJOURNAL)
            assertEquals("My Contacts", collection.displayName)
            assertEquals("My Contacts Description", collection.description)
        }
    }

}
