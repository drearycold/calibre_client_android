package com.example.myapplication

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import kotlin.collections.HashMap

class Converters {
    @TypeConverter
    fun fromHashMapStringString(map: HashMap<String?, String?>?): String? = Gson().toJson(map)

    @TypeConverter
    fun toHashMapStringString(value: String?): HashMap<String?, String?>? {
        val mapType = object : TypeToken<HashMap<String?, String?>?>() {}.type
        return Gson().fromJson<HashMap<String?, String?>>(value, mapType)
    }

     @TypeConverter
    fun fromTimestamp(value: Long?) = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?) = date?.time?.toLong()

    @TypeConverter
    fun fromArrayListString(list: ArrayList<String>?): String? = Gson().toJson(list)

    @TypeConverter
    fun toArrayListString(value: String): ArrayList<String?>? {
        val listType = object : TypeToken<ArrayList<String?>?>(){}.type
        return Gson().fromJson<ArrayList<String?>>(value, listType)
    }
}