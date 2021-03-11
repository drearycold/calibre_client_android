package com.example.myapplication

import androidx.recyclerview.widget.DiffUtil
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import java.util.*
import kotlin.collections.HashMap

@Entity(primaryKeys = ["id", "libraryName"])
data class Book(
    val id: Int,
    @ColumnInfo(index = true)
    val libraryName: String,
    @ColumnInfo(index = true)
    var title: String,
    @ColumnInfo(index = true)
    var authors: String
) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
                return oldItem.id == newItem.id && oldItem.libraryName == newItem.libraryName
            }

            override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
                return oldItem == newItem
            }

        }
    }

    lateinit var uuid: String

    @ColumnInfo(index = true)
    var tags = ArrayList<String>()

    @ColumnInfo(index = true)
    var size: Int = 0

    var seriesIndex: Double = 1.0

    @ColumnInfo(index = true)
    lateinit var series: String

    @ColumnInfo(index = true)
    var rating: Int = 0

    @ColumnInfo(index = true)
    lateinit var publisher: String

    @ColumnInfo(index = true)
    lateinit var pubDate: Date

    @ColumnInfo(index = true)
    lateinit var lastModified: Date     //for metadata

    @ColumnInfo(index = true)
    lateinit var timestamp: Date        //for book format

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    lateinit var cover: ByteArray

    @ColumnInfo(index = true)
    lateinit var comments: String

    @ColumnInfo(index = true)
    var readability: Double = 0.0

    @ColumnInfo(index = true)
    var formats = HashMap<String, String>()     //type -> md5
        set(other: HashMap<String, String>) {
            field.putAll(other)
        }

    @ColumnInfo(index = true)
    var pages = 0

    var readPos = BookReadingPosition()

    fun updateReadPos(other: BookReadingPosition) {
        for ((deviceName, deviceReadingPosition) in other.deviceMap) {
            val self = readPos.getByDevice(deviceName, deviceReadingPosition.readerName)
            self.maxPage = deviceReadingPosition.maxPage

            self.lastReadPage = deviceReadingPosition.lastReadPage
            self.lastReadChapter = deviceReadingPosition.lastReadChapter


            if( self.furthestReadPage < deviceReadingPosition.furthestReadPage) {
                self.furthestReadPage = deviceReadingPosition.furthestReadPage
                self.furthestReadChapter = deviceReadingPosition.furthestReadChapter
            }

            deviceReadingPosition.lastPosition?.let {
                self.lastPosition = it
            }

            deviceReadingPosition.furthestPosition?.let {
                self.furthestPosition = it
            }
        }
    }

}