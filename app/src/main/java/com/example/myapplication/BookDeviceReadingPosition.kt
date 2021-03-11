package com.example.myapplication

import java.util.*

data class BookDeviceReadingPosition(val readerName: String) {
    var maxPage = 0
    var lastReadPage = 0
    var lastReadChapter = ""
    var furthestReadPage = 0
    var furthestReadChapter = ""
    var lastPosition = IntArray(3)
        get(): IntArray {
            if( field == null ) {
                field = IntArray(3)
                field[0] = lastReadPage
            }
            return field
        }
    var furthestPosition = IntArray(3)
        get(): IntArray {
            if( field == null ) {
                field = IntArray(3)
                field[0] = furthestReadPage
            }
            return field
        }

    fun getLastProgress(): Double {
        if( maxPage == 0 || lastReadPage == 0)
            return 0.0
        return (lastReadPage-1) * 100.0 / maxPage
    }

    fun getLastProgressPercent(): String {
        return StringBuilder().also { sb ->
            Formatter(sb).also { formatter ->
                formatter.format(FORMAT_PROGRESS, getLastProgress())
            }
        }.toString()
    }

    fun getFurthestProgress(): Double {
        if( maxPage == 0 || furthestReadPage == 0 )
            return 0.0
        return (furthestReadPage-1) * 100.0 / maxPage
    }

    override fun toString(): String {
        return "BookDeviceReadingPosition(readerName=MuPDF,maxPage=$maxPage,lastReadPage=$lastReadPage)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BookDeviceReadingPosition)
            return false
        if( lastReadPage != other.lastReadPage)
            return false
        if( furthestReadPage != other.furthestReadPage)
            return false
        return true
    }

    override fun hashCode(): Int {
        var result = readerName.hashCode()
        result = 31 * result + maxPage
        result = 31 * result + lastReadPage
        result = 31 * result + lastReadChapter.hashCode()
        result = 31 * result + furthestReadPage
        result = 31 * result + furthestReadChapter.hashCode()
        return result
    }
}
