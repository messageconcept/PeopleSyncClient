/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.webdav.cache

import android.util.LruCache
import com.messageconcept.peoplesyncclient.db.WebDavDocument
import com.messageconcept.peoplesyncclient.webdav.DocumentState
import com.messageconcept.peoplesyncclient.webdav.HeadResponse
import java.util.*

class HeadResponseCache {

    companion object {
        const val MAX_SIZE = 50
    }

    data class Key(
        val docId: Long,
        val documentState: DocumentState
    )

    val cache = LruCache<Key, HeadResponse>(MAX_SIZE)


    @Synchronized
    fun get(doc: WebDavDocument, generate: () -> HeadResponse): HeadResponse {
        var key: Key? = null
        if (doc.eTag != null || doc.lastModified != null) {
            key = Key(doc.id, DocumentState(doc.eTag, doc.lastModified?.let { ts -> Date(ts) }))
            cache.get(key)?.let { info ->
                return info
            }
        }

        val info = generate()
        if (key != null)
            cache.put(key, info)
        return info
    }

}