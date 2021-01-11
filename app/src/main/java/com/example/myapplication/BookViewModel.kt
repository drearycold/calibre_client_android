package com.example.myapplication

import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.internal.LinkedTreeMap
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

class BookViewModel(private val bookDAO: BookDAO) : ViewModel() {
    private var logger: Logger = Logger.getLogger("BookViewModel")

    companion object {
        val simpleDateFormatLastModified = SimpleDateFormat(
            //"yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSZ",
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

    fun updateBook(result: String, libraryName: String) {
        viewModelScope.launch {
            try {
                val gson = Gson()

                val root = JsonParser.parseString(result)

                val resultElement = root.asJsonObject.get("result").asJsonObject
                val bookIds = resultElement.get("book_ids").asJsonArray

                val id = bookIds[0].asInt

                var book = bookDAO.find(id, libraryName)
                if (book == null) {
                    book = Book(id, libraryName, "", "")
                }

                val dataElement = resultElement.get("data").asJsonObject
                for ((dataKey, dataVal) in gson.fromJson(dataElement, HashMap::class.java)) {
                    val dataValMap = dataVal as LinkedTreeMap<*, *>
                    val innerVal = dataValMap[id.toString()]
                    when (dataKey as String) {
                        "formats" -> innerVal.let {
                            val formats = it as ArrayList<*>
                            for (format in formats) {
                                book.formats[format as String] = "TODO"
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
                            book.readPos = Gson().fromJson(readPosStr, BookReadingPosition::class.java)
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
            } catch( e: Exception) {
                logger.warning("UpdateBook Exception $e")
                e.printStackTrace()
            } finally {
                //TODO notify user
            }
        }
    }

    fun updateBookFromMetadata(result: String, book: Book) {
        viewModelScope.launch {
            try {
                val gson = Gson()

                val root = JsonParser.parseString(result)
                val resultElement = root.asJsonObject.get("result").asJsonObject
                val vElement = resultElement.get("v").asJsonObject
                vElement["last_modified"].asJsonObject["v"].asString.let {
                    val pp = ParsePosition(0)
                    book.lastModified =
                        simpleDateFormatLastModified.parse(it, pp) ?: Date()
                    if (pp.errorIndex > 0) {
                        pp.index = 0
                        pp.errorIndex = 0
                        book.lastModified =
                            simpleDateFormat.parse(it, pp) ?: Date()
                    }
                }

                bookDAO.insertAll(book)
            } catch( e: Exception) {
                logger.warning("UpdateBook Exception $e")
                e.printStackTrace()
            } finally {
                //TODO notify user
            }
        }
    }

    private fun matchBook(book: Book, searchStr: CharSequence): Boolean {
        return book.title.contains(searchStr, true)
    }

    fun updateBookListRV(
        activity: MainActivity,
        libraryName: String,
        searchStr: CharSequence,
        orderBy: String
    ) {
        viewModelScope.launch {
            activity.mMainBookListUpdateProgressBar.visibility = View.VISIBLE

            var bookList = bookDAO.findByLibrary(libraryName)
            if (searchStr.isNotEmpty())
                bookList = bookList.filter { matchBook(it, searchStr) }
            //bookList = bookList.filter { it.title.length > 150 }
            val adapter =
                activity.findViewById<RecyclerView>(R.id.rvMainBookList).adapter as BookListMainLibraryAdapter
            val bookListSorted = when (orderBy) {
                ORDER_BY_TITLE -> bookList.sortedBy { it.title }
                ORDER_BY_LASTREAD -> bookList.sortedByDescending { it.lastModified }
                ORDER_BY_PAGES -> bookList.sortedByDescending { it.pages }
                else -> bookList
            }
            adapter.replaceAllBooks(bookListSorted)
            adapter.notifyDataSetChanged()

            activity.mMainBookListUpdateProgressBar.visibility = View.GONE
        }
    }

    fun updateReadingProgress(book: Book, extras: Bundle?) {
        extras?.let {
            val bookId = it.getInt(EXTRA_READER_BOOK_ID)
            val libraryName = it.getString(EXTRA_READER_LIBRARY_NAME, "")
            val pageNumber = it.getInt(EXTRA_READER_PAGE_NUMBER)
            val deviceName = it.getString(EXTRA_DEVICE_NAME, EXTRA_DEVICE_NAME_DEFAULT)

            val oldPos = book.readPos.getByDevice(deviceName)
            oldPos.maxPage = extras.getInt(EXTRA_READER_PAGE_NUMBER_MAX, -1)
            if (pageNumber > oldPos.furthestReadPage) {
                oldPos.furthestReadPage = pageNumber
            }
            oldPos.lastReadPage = pageNumber
            oldPos.readerName = "MuPDF"

            viewModelScope.launch {
                bookDAO.insertAll(book)
            }
        }
    }
}