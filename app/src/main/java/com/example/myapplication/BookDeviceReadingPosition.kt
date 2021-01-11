package com.example.myapplication

class BookDeviceReadingPosition {
    var maxPage = 0
    var lastReadPage = 0
    var lastReadChapter = ""
    var furthestReadPage = 0
    var furthestReadChapter = ""
    var readerName = ""

    fun getLastProgress(): Double {
        if( maxPage == 0 || lastReadPage == 0)
            return 0.0
        return (lastReadPage-1) * 100.0 / maxPage
    }

    fun getFurthestProgress(): Double {
        if( maxPage == 0 || furthestReadPage == 0 )
            return 0.0
        return (furthestReadPage-1) * 100.0 / maxPage
    }
}
