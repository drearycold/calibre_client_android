package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.internal.LinkedTreeMap
import kotlinx.coroutines.launch
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

class BookListViewModel(private val bookDAO: BookDAO) : ViewModel() {
    private var logger: Logger = Logger.getLogger("BookViewModel")


    companion object {
        val simpleDateFormatLastModified = SimpleDateFormat(
            //"yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            Locale.US
        )     //2020-12-28T07:08:18+00:00
        val simpleDateFormat =
            SimpleDateFormat(
                //"yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                Locale.US
            )     //2020-12-28T07:08:18+00:00
    }

    fun insertBook(vararg books: Book) {
        viewModelScope.launch {
            bookDAO.insertAll(*books)
        }
    }

    fun updateBook(result: String, libraryName: String, bookParam: Book? = null) {
        viewModelScope.launch {
            try {
                val gson = Gson()

                val root = JsonParser.parseString(result)

                val resultElement = root.asJsonObject.get("result").asJsonObject
                val bookIds = resultElement.get("book_ids").asJsonArray

                val id = bookIds[0].asInt

                val book =
                    bookParam ?: bookDAO.find(id, libraryName) ?: Book(id, libraryName, "", "")

                val dataElement = resultElement.get("data").asJsonObject
                for ((dataKey, dataVal) in gson.fromJson(dataElement, HashMap::class.java)) {
                    val dataValMap = dataVal as LinkedTreeMap<*, *>
                    val innerVal = dataValMap[id.toString()]
                    when (dataKey as String) {
                        "formats" -> innerVal.let { it ->
                            val formats = it as ArrayList<*>
                            for (format in formats) {
                                if (!book.formats.containsKey(format as String))
                                    book.formats[format as String] = "TODO"
                            }
                            book.formats.entries.removeAll { f ->
                                !formats.contains(f.key)
                            }
                        }
                        "comments" -> book.comments = innerVal as? String ?: ""
                        "cover" -> book.cover = ByteArray(1)
                        "last_modified" -> innerVal.let {
                            val v = it as LinkedTreeMap<*, *>
                            val pp = ParsePosition(0)
                            book.lastModified =
                                simpleDateFormatLastModified.parse(v["v"] as String, pp) ?: Date()
                            if (pp.errorIndex > 0) {
                                pp.index = 0
                                pp.errorIndex = 0
                                book.lastModified =
                                    simpleDateFormat.parse(v["v"] as String, pp) ?: Date()
                            }

                        }
                        "pubdate" -> innerVal.let {
                            val v = it as LinkedTreeMap<*, *>
                            val pp = ParsePosition(0)
                            book.pubDate = simpleDateFormat.parse(v["v"] as String, pp) ?: Date(0)
                        }
                        "timestamp" -> innerVal.let {
                            val v = it as LinkedTreeMap<*, *>
                            val vv = v["v"] as String
                            val pp = ParsePosition(0)
                            book.timestamp = simpleDateFormat.parse(v["v"] as String, pp) ?: Date()
                            logger.info("timestamp: $pp ${book.timestamp} $vv")

                        }
                        "publisher" -> book.publisher = innerVal as? String ?: ""
                        "rating" -> book.rating = (innerVal as? Double)?.toInt() ?: 0
                        "series" -> book.series = innerVal as? String ?: ""
                        "series_index" -> book.seriesIndex = innerVal as? Double ?: 0.0
                        "size" -> book.size = (innerVal as? Double)?.toInt() ?: 0
                        "tags" -> innerVal.let {
                            book.tags.clear()
                            val tags = it as ArrayList<*>
                            for (tag in tags) {
                                book.tags.add(tag as String)
                            }
                        }
                        "uuid" -> book.uuid = innerVal as String

                        "#pages" -> book.pages = (innerVal as? Double)?.toInt() ?: -1
                        "#read_pos" -> innerVal?.let {
                            val readPosStr = String(Base64.decode(innerVal as String, 0))
                            logger.info("readPosStr: $readPosStr")
                            val readPos =
                                Gson().fromJson(readPosStr, BookReadingPosition::class.java)
                            book.updateReadPos(readPos)
                        }
                        "#readability" -> book.readability = innerVal as? Double ?: 0.0

                        "title" -> innerVal.let {
                            book.title = it.toString()
                        }
                        "authors" -> innerVal.let {
                            val authors = it as ArrayList<*>
                            if (authors.isNotEmpty())
                                book.authors = authors[0] as String
                            if (authors.size > 1)
                                book.authors += ", et al."
                        }
                    }
                }
                logger.info("Book: $book")
                logger.info("\npubDate:\t\t${book.pubDate}\nlastModifed:\t${book.lastModified}\ntimestamp:\t\t${book.timestamp}")

                bookDAO.insertAll(book)
            } catch (e: Exception) {
                logger.warning("UpdateBook Exception $e")
                e.printStackTrace()
            } finally {
                //TODO notify user
            }
        }
    }

    fun updateBookFromMetadata(result: String, book: Book) {
        try {
            val root = JsonParser.parseString(result)
            val resultElement = root.asJsonObject.get("result").asJsonObject
            val vElement = resultElement.get("v").asJsonObject
            vElement["last_modified"].asJsonObject["v"].asString.let {
                val pp = ParsePosition(0)
                book.lastModified = simpleDateFormatLastModified.parse(it, pp) ?: Date()
                if (pp.errorIndex > 0) {
                    pp.index = 0
                    pp.errorIndex = 0
                    book.lastModified = simpleDateFormat.parse(it, pp) ?: Date()
                }
            }
            try {
                val userMetadataElement = vElement["user_metadata"].asJsonObject
                val readPosElement = userMetadataElement["#read_pos"].asJsonObject
                val readPosValue = readPosElement["#value#"].asString
                val readPos = Gson().fromJson(
                    String(Base64.decode(readPosValue, 0)),
                    BookReadingPosition::class.java
                )
                book.updateReadPos(readPos)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            logger.warning("UpdateBook Exception $e")
            e.printStackTrace()
        } finally {
            //TODO notify user
        }
        viewModelScope.launch {
            bookDAO.insertAll(book)
        }
    }

    private fun matchBook(book: Book, searchStr: CharSequence): Boolean {
        return book.title.contains(searchStr, true)
    }

    fun updateBookListRV(
        adapter: ListAdapter<Book, *>,
        bookListUpdated: MutableLiveData<Boolean>,
        libraryName: String,
        searchStr: CharSequence,
        orderBy: String,
        recentDay: Int = 0
    ) {
        viewModelScope.launch {
            bookListUpdated.value = false

            val nanoTimeStart = System.nanoTime()
            logger.info("findByLibrary Start: $nanoTimeStart")
            var bookList = if (recentDay > 0) {
                val curTime = Calendar.getInstance().time.time
                bookDAO.findByLibraryRecent(libraryName, curTime - recentDay * 86400000L)
            } else {
                bookDAO.findByLibrary(libraryName)
            }
            val nanoTimeEnd = System.nanoTime()
            logger.info("findByLibrary End: $nanoTimeEnd")
            val elapsedNanoTime = nanoTimeEnd - nanoTimeStart
            logger.info("findByLibrary elapsedNanoTIme: $elapsedNanoTime ${bookList.size}")
            if (searchStr.isNotEmpty())
                bookList = bookList.filter { matchBook(it, searchStr) }
            //bookList = bookList.filter { it.title.length > 150 }
            val bookListSorted = when (orderBy) {
                ORDER_BY_TITLE -> bookList.sortedBy { it.title }
                ORDER_BY_LASTREAD -> bookList.sortedByDescending { it.lastModified }
                ORDER_BY_PAGES -> bookList.sortedByDescending { it.pages }
                else -> bookList
            }

            adapter.submitList(bookListSorted) { bookListUpdated.value = true }
        }
    }

    fun updateReadingProgress(book: Book, extras: Bundle?) {
        extras?.let {
            val pageNumber = it.getInt(EXTRA_READER_PAGE_NUMBER)
            val deviceName = it.getString(EXTRA_DEVICE_NAME, EXTRA_DEVICE_NAME_DEFAULT)
            val readerName = it.getString(EXTRA_READER_NAME, EXTRA_READER_NAME_DEFAULT)

            val oldPos = book.readPos.getByDevice(deviceName, readerName)
            oldPos.maxPage = extras.getInt(EXTRA_READER_PAGE_NUMBER_MAX, -1)

            if (pageNumber > oldPos.furthestReadPage) {
                oldPos.furthestReadPage = pageNumber
            }
            oldPos.lastReadPage = pageNumber

            val newPosition = extras.getIntArray(EXTRA_READER_POSITION) ?: IntArray(3)
            oldPos.lastPosition = newPosition
            if (oldPos.furthestPosition == null)
                oldPos.furthestPosition = newPosition
            else if (oldPos.furthestPosition[0] < newPosition[0] ||
                oldPos.furthestPosition[0] == newPosition[0] && oldPos.furthestPosition[1] < newPosition[1] ||
                oldPos.furthestPosition[0] == newPosition[0] && oldPos.furthestPosition[0] < newPosition[0] && oldPos.furthestPosition[2] == newPosition[2]
            ) {
                oldPos.furthestPosition = newPosition
            }

            viewModelScope.launch {
                bookDAO.insertAll(book)
            }
        }
    }
}