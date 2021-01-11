package com.example.myapplication

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import java.util.*
import kotlin.collections.HashMap

@Entity(primaryKeys = ["id", "libraryName"], indices = [Index(value = ["libraryName"]), Index(value = ["title"])])
data class Book(
        val id: Int,
        val libraryName: String,
        var title: String,
        var authors: String) {


    lateinit var uuid: String

    var tags = ArrayList<String>()
    var size: Int = 0

    var seriesIndex: Double = 1.0

    lateinit var series: String

    var rating: Int = 0

    lateinit var publisher: String

    lateinit var pubDate: Date

    lateinit var lastModified: Date     //for metadata

    lateinit var timestamp: Date        //for book format

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    lateinit var cover: ByteArray

    lateinit var comments: String

    var readability: Double = 0.0

    var formats = HashMap<String, String>()     //TODO type -> something
        set(other: HashMap<String, String>) {
            field.putAll(other)
        }

    var pages = 0

    var readPos = BookReadingPosition()

}