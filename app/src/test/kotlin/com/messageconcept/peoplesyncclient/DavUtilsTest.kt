/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient

import com.messageconcept.peoplesyncclient.util.DavUtils
import com.messageconcept.peoplesyncclient.util.DavUtils.parent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DavUtilsTest {

    val exampleURL = "http://example.com/"


    @Test
    fun testAcceptAnything() {
        assertEquals("*/*", DavUtils.acceptAnything(null))
        assertEquals("some/thing;v=2.1, */*;q=0.8", DavUtils.acceptAnything("some/thing;v=2.1".toMediaType()))
    }

    @Test
    fun testARGBtoCalDAVColor() {
        assertEquals("#00000000", DavUtils.ARGBtoCalDAVColor(0))
        assertEquals("#123456FF", DavUtils.ARGBtoCalDAVColor(0xFF123456.toInt()))
        assertEquals("#000000FF", DavUtils.ARGBtoCalDAVColor(0xFF000000.toInt()))
    }

    @Test
    fun testLastSegmentOfUrl() {
        assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL.toHttpUrl()))
        assertEquals("dir", DavUtils.lastSegmentOfUrl((exampleURL + "dir").toHttpUrl()))
        assertEquals("dir", DavUtils.lastSegmentOfUrl((exampleURL + "dir/").toHttpUrl()))
        assertEquals("file.html", DavUtils.lastSegmentOfUrl((exampleURL + "dir/file.html").toHttpUrl()))
    }

    @Test
    fun testParent() {
        // with trailing slash
        assertEquals("http://example.com/1/2/".toHttpUrl(), "http://example.com/1/2/3/".toHttpUrl().parent())
        assertEquals("http://example.com/1/".toHttpUrl(), "http://example.com/1/2/".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/1/".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/".toHttpUrl().parent())

        // without trailing slash
        assertEquals("http://example.com/1/2/".toHttpUrl(), "http://example.com/1/2/3".toHttpUrl().parent())
        assertEquals("http://example.com/1/".toHttpUrl(), "http://example.com/1/2".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/1".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com".toHttpUrl().parent())
    }

}
