package com.example.myapplication

import java.util.*

object BookLibrary {
    class SeriesInfo(val seriesName: String) {
        val books = ArrayList<Book>()
    }

    class TagInfo(val tagName: String) {
        val books = ArrayList<Book>()
    }

    val bookComparator = object: Comparator<Book> {
        override fun compare(o1: Book?, o2: Book?): Int {
            if( o1 === o2 )
                return 0
            if( o1 == null )
                return -1
            if( o2 == null )
                return 1
            var diff = o1.seriesIndex - o2.seriesIndex
            if( diff < 0 )
                return -1
            if( diff > 0)
                return 1
            return o1.title.compareTo(o2.title)
        }
    }

    var calibreServer = ""

    private val BOOKS_MAP = TreeMap<Int, Book>()
    private val seriesMap = TreeMap<String, SeriesInfo>()
    private val tagsMap = TreeMap<String, TagInfo>()

    fun books(): List<Book> {
        return BOOKS_MAP.values.toList()
    }

    fun get(id: Int): Book? {
        return BOOKS_MAP[id]
    }

    fun update(books: List<Book>) {
        BOOKS_MAP.clear()
        books.forEach {
            BOOKS_MAP[it.id] = it
        }

        updateSeries()
        updateTags()
    }

    private fun updateSeries() {
        seriesMap.entries.removeAll {
            it.value.books.clear()
            true
        }

        BOOKS_MAP.forEach {
            val seriesName = it.value.series
            if(seriesName.isNotEmpty()) {
                val seriesInfo = seriesMap.getOrPut(seriesName) { SeriesInfo(seriesName) }
                seriesInfo.books.add(it.value)
            }
        }
        seriesMap.entries.forEach {
            it.value.books.sortWith(bookComparator)
        }
    }

    private fun updateTags() {
        tagsMap.entries.removeAll {
            it.value.books.clear()
            true
        }

        BOOKS_MAP.forEach {
            val tagNames = it.value.tags
            for(tagName in tagNames) {
                if( tagName.isNotBlank()) {
                    val tagsInfo = tagsMap.getOrPut(tagName) { TagInfo(tagName) }
                    tagsInfo.books.add(it.value)
                }
            }
        }
        tagsMap.entries.forEach {
            it.value.books.sortWith(object: Comparator<Book> {
                override fun compare(o1: Book?, o2: Book?): Int {
                    if( o1 === o2 )
                        return 0
                    if( o1 == null )
                        return -1
                    if( o2 == null )
                        return 1
                    return o1.title.compareTo(o2.title)
                }
            })
        }
    }

    fun series(): Iterable<SeriesInfo> {
        return seriesMap.values.asIterable()
    }

    fun tags(): Iterable<TagInfo> {
        return tagsMap.values.asIterable()
    }

    fun getSeries(seriesName: String): SeriesInfo? {
        return seriesMap[seriesName]
    }
    fun getTag(tagName: String): TagInfo? {
        return tagsMap[tagName]
    }
}