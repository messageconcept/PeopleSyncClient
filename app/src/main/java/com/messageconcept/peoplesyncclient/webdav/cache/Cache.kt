/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.webdav.cache

interface Cache<K> {

    fun get(key: K): ByteArray?
    fun getOrPut(key: K, generate: () -> ByteArray): ByteArray

}