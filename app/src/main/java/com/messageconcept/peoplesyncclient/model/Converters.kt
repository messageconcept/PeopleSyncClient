package com.messageconcept.peoplesyncclient.model

import androidx.room.TypeConverter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Converters {

    @TypeConverter
    fun httpUrlToString(url: HttpUrl?) =
            url?.toString()

    @TypeConverter
    fun stringToHttpUrl(url: String?): HttpUrl? =
            url?.let { it.toHttpUrlOrNull() }

}