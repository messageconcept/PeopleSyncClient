/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package com.messageconcept.peoplesyncclient.log

import java.util.logging.Handler
import java.util.logging.LogRecord

class StringHandler: Handler() {

    val builder = StringBuilder()

    init {
        formatter = PlainTextFormatter.DEFAULT
    }

    override fun publish(record: LogRecord) {
        builder.append(formatter.format(record))
    }

    override fun flush() {}
    override fun close() {}

    override fun toString() = builder.toString()

}
