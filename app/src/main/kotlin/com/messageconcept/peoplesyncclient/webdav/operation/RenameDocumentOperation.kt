/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.webdav.operation

import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.di.IoDispatcher
import com.messageconcept.peoplesyncclient.webdav.DavHttpClientBuilder
import com.messageconcept.peoplesyncclient.webdav.DocumentProviderUtils
import com.messageconcept.peoplesyncclient.webdav.DocumentProviderUtils.displayNameToMemberName
import com.messageconcept.peoplesyncclient.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class RenameDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String, displayName: String): String? = runBlocking {
        logger.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        httpClientBuilder.build(doc.mountId).use { client ->
            for (attempt in 0..DocumentProviderUtils.MAX_DISPLAYNAME_TO_MEMBERNAME_ATTEMPTS) {
                val newName = displayNameToMemberName(displayName, attempt)
                val oldUrl = doc.toHttpUrl(db)
                val newLocation = oldUrl.newBuilder()
                    .removePathSegment(oldUrl.pathSegments.lastIndex)
                    .addPathSegment(newName)
                    .build()
                try {
                    val dav = DavResource(client.okHttpClient, oldUrl)
                    runInterruptible(ioDispatcher) {
                        dav.move(newLocation, false) {
                            // successfully renamed
                        }
                    }
                    documentDao.update(doc.copy(name = newName))

                    DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)

                    return@runBlocking doc.id.toString()
                } catch (e: HttpException) {
                    e.throwForDocumentProvider(context, true)
                }
            }
        }

        null
    }

}