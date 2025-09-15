/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.webdav

import android.graphics.Point
import android.os.CancellationSignal
import android.provider.DocumentsProvider
import com.messageconcept.peoplesyncclient.webdav.operation.CopyDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.CreateDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.DeleteDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.IsChildDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.MoveDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.OpenDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.OpenDocumentThumbnailOperation
import com.messageconcept.peoplesyncclient.webdav.operation.QueryChildDocumentsOperation
import com.messageconcept.peoplesyncclient.webdav.operation.QueryDocumentOperation
import com.messageconcept.peoplesyncclient.webdav.operation.QueryRootsOperation
import com.messageconcept.peoplesyncclient.webdav.operation.RenameDocumentOperation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Provides functionality on WebDav documents.
 *
 * Hilt constructor injection can't be used for content providers because SingletonComponent
 * may not ready yet when the content provider is created. So we use an explicit EntryPoint.
 *
 * Note: A DocumentsProvider is a ContentProvider and thus has no well-defined lifecycle. It
 * is created by Android when it's first accessed and then stays in memory until the process
 * is killed.
 */
class DavDocumentsProvider: DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun copyDocumentOperation(): CopyDocumentOperation
        fun createDocumentOperation(): CreateDocumentOperation
        fun deleteDocumentOperation(): DeleteDocumentOperation
        fun isChildDocumentOperation(): IsChildDocumentOperation
        fun moveDocumentOperation(): MoveDocumentOperation
        fun openDocumentOperation(): OpenDocumentOperation
        fun openDocumentThumbnailOperation(): OpenDocumentThumbnailOperation
        fun queryChildDocumentsOperation(): QueryChildDocumentsOperation
        fun queryDocumentOperation(): QueryDocumentOperation
        fun queryRootsOperation(): QueryRootsOperation
        fun renameDocumentOperation(): RenameDocumentOperation
    }

    private val entryPoint: DavDocumentsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication<DavDocumentsProviderEntryPoint>(context!!)
    }


    override fun onCreate() = true

    /* Note: shutdown() is NOT called automatically by Android; a content provider lives until
    the process is killed. */


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?) =
        entryPoint.queryRootsOperation().invoke(projection)

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        entryPoint.queryDocumentOperation().invoke(documentId, projection)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        entryPoint.queryChildDocumentsOperation().invoke(parentDocumentId, projection, sortOrder)

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        entryPoint.isChildDocumentOperation().invoke(parentDocumentId, documentId)


    /*** copy/create/delete/move/rename ***/

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String) =
        entryPoint.copyDocumentOperation().invoke(sourceDocumentId, targetParentDocumentId)

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? =
        entryPoint.createDocumentOperation().invoke(parentDocumentId, mimeType, displayName)

    override fun deleteDocument(documentId: String) =
        entryPoint.deleteDocumentOperation().invoke(documentId)

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String) =
        entryPoint.moveDocumentOperation().invoke(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)

    override fun renameDocument(documentId: String, displayName: String): String? =
        entryPoint.renameDocumentOperation().invoke(documentId, displayName)


    /*** read/write ***/

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?) =
        entryPoint.openDocumentOperation().invoke(documentId, mode, signal)

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?) =
        entryPoint.openDocumentThumbnailOperation().invoke(documentId, sizeHint, signal)

}