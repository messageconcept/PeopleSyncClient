/*
 * Copyright © messageconcept software GmbH, Cologne, Germany.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.messageconcept.peoplesyncclient.log

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.time.Instant
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/** Always-on, bounded log for relevant background changes. */
@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: Logger
) {

    data class Snapshot(val name: String, val contents: ByteArray)

    private val logFile = File(context.noBackupFilesDir, FILE_NAME)
    private val previousLogFile = File(context.noBackupFilesDir, PREVIOUS_FILE_NAME)

    @WorkerThread
    @Synchronized
    fun log(level: Level, message: String, throwable: Throwable? = null) {
        log(level, message, null, throwable)
    }

    @WorkerThread
    @Synchronized
    fun log(level: Level, message: String, params: Array<out Any?>?, throwable: Throwable? = null) {
        if (params == null) {
            if (throwable == null)
                logger.log(level, message)
            else
                logger.log(level, message, throwable)
        } else {
            val record = LogRecord(level, message)
            record.parameters = params
            record.thrown = throwable
            logger.log(record)
        }

        try {
            val formattedMessage = if (params != null)
                MessageFormat.format(message, *params)
            else
                message

            val details = throwable?.let {
                StringWriter().also { writer ->
                    it.printStackTrace(PrintWriter(writer))
                }.toString()
            }.orEmpty()
            val line = "${Instant.now()} ${level.name}: $formattedMessage${System.lineSeparator()}$details"
                .toByteArray(Charsets.UTF_8)
            rotateIfNecessary(line.size)
            FileOutputStream(logFile, true).use { output ->
                output.write(line)
                output.fd.sync()
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't write audit log", e)
        }
    }

    @Synchronized
    fun snapshots(): List<Snapshot> =
        listOf(previousLogFile, logFile)
            .filter { it.isFile && it.canRead() }
            .map { Snapshot(it.name, it.readBytes()) }

    @VisibleForTesting
    @Synchronized
    internal fun clear() {
        previousLogFile.delete()
        logFile.delete()
    }

    private fun rotateIfNecessary(nextEntrySize: Int) {
        if (logFile.length() + nextEntrySize <= MAX_FILE_SIZE)
            return

        previousLogFile.delete()
        if (!logFile.renameTo(previousLogFile))
            logFile.delete()
    }

    companion object {
        private const val FILE_NAME = "audit-log.txt"
        private const val PREVIOUS_FILE_NAME = "audit-log.txt.1"
        private const val MAX_FILE_SIZE = 512_000
    }

}
