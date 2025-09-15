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
import com.messageconcept.peoplesyncclient.webdav.throwForDocumentProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class DeleteDocumentOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String) = runBlocking {
        logger.fine("WebDAV removeDocument $documentId")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        httpClientBuilder.build(doc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, doc.toHttpUrl(db))
            try {
                runInterruptible(ioDispatcher) {
                    dav.delete {
                        // successfully deleted
                    }
                }
                logger.fine("Successfully removed")
                documentDao.delete(doc)

                DocumentProviderUtils.notifyFolderChanged(context, doc.parentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider(context)
            }
        }
    }

}