package com.backlogbattlers.app.data.local

import androidx.room.TypeConverter

//------------------------------
// this class provides conversion methods for custom types stored in Room
class Converters {

    //------------------------------
    // converts a list string to a delimited string
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString("|")
    }

    //------------------------------
    // converts a delimited string back to a list string
    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) {
            return emptyList()
        }
        return value.split("|")
    }
}
