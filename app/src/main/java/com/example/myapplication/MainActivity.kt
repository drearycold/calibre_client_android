package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.geometerplus.zlibrary.ui.android.library.ZLAndroidApplication
import java.io.File
import java.io.InputStreamReader
import java.util.logging.Logger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


const val EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE"
const val EXTRA_DEVICE_NAME = "com.example.myfirstapp.DEVICE_NAME"
const val EXTRA_DEVICE_NAME_DEFAULT = "My Device"
const val EXTRA_CALIBRE_SERVER = "com.example.myfirstapp.CALIBRE_SERVER"
const val EXTRA_SHELF_MODE = "com.example.myfirstapp.shelfmode"
const val ORDER_BY_TITLE = "Title"
const val ORDER_BY_LASTREAD = "LastRead"
const val ORDER_BY_PROGRESS = "Progress"
const val ORDER_BY_PAGES = "Pages"

const val PREF_KEY_SELECTED_LIBRARY_NAME = "MainActivitySelectedLibraryName"

const val EXTRA_LIBRARY_NAME = "extra_libraryname"
const val EXTRA_READER_BOOK_ID = "mupdf_bookid"
const val EXTRA_READER_PAGE_NUMBER = "mupdf_pagenumber"
const val EXTRA_READER_PAGE_NUMBER_MAX = "mupdf_pagenumbermax"
const val EXTRA_READER_POSITION = "android_book_reader_position"
const val EXTRA_READER_NAME = "mupdf_readername"
const val EXTRA_READER_NAME_DEFAULT = "MuPDF"
const val EXTRA_REQUEST_CODE = "myfirstapp.REQUEST_CODE"

const val REQUEST_SYNCING_ACTIVITY_CODE = 10
const val REQUEST_DOCUMENT_ACTIVITY_CODE = 20
const val REQUEST_ANDROID_BOOK_READER_ACTIVITY_SHELF_CODE = 30
const val REQUEST_ANDROID_BOOK_READER_ACTIVITY_BOOK_CODE = 40

const val REQUEST_BY_MAIN_ACTIVITY = 10
const val REQUEST_BY_BOOK_LIST_ACTIVITY = 20

class MainActivity : FragmentActivity(), DownloadCallback<DownloadCallbackData> {

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    var networkFragment: NetworkFragment? = null

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private var downloading = false

    private var logger: Logger = Logger.getLogger("MainActivity")

    private lateinit var mBookListAdapter: BookListAdapter

    lateinit var db: AppDatabase

    private lateinit var mSpLibraryListMain: Spinner
    private lateinit var mOrderBySpinner: Spinner
    private lateinit var mMainBookListUpdateProgressBar: ProgressBar
    private lateinit var rvBookList: RecyclerView

    private var readingBook: Book? = null
    private var readingBookPosition: Int? = 0

    private var preferredFormats = ArrayList<String>()
    var formatsComponentIdMap = HashMap<String, Pair<Int, Int>>()

    private var bookListUpdated = MutableLiveData<Boolean>()
    private var bookListUpdateEnabled = true

    companion object {
        var DEVICE_NAME: String? = null
        var libraryViewModel: LibraryViewModel? = null
        var bookViewModel: BookListViewModel? = null

        fun getBookFormatFile(context: Context, book: Book, format: String): File {
            return File(
                context.getExternalFilesDir(null),
                book.formats[format] ?: "__NOT_EXIST__"
            )
        }

        fun isBookFormatDownloaded(context: Context, book: Book, format: String): Boolean {
            val bookFile = getBookFormatFile(context, book, format)
            return bookFile.exists()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appContext = applicationContext
        val zlib = object : ZLAndroidApplication() {
            init {
                attachBaseContext(appContext)
                onCreate()
            }
        }



        val sharedPreferences = getPreferences(MODE_PRIVATE)
        sharedPreferences.getString(
            getString(R.string.editCalibreServer),
            getString(R.string.editCalibreServerDefaultValue)
        )?.let {
            BookLibrary.calibreServer = it
        }

        mMainBookListUpdateProgressBar =
            findViewById<ProgressBar>(R.id.mainBookListUpdateProgressBar)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "metadata"
        ).build()
        bookViewModel = BookListViewModel(db.bookDao())
        bookListUpdated.observe(this, Observer { updated ->
            when (updated) {
                true -> updateBookListFinished()
                false -> updateBookListStarted()
            }
        })
        libraryViewModel = LibraryViewModel(db.libraryDao())

        rvBookList = findViewById<View>(R.id.rvMainBookList) as RecyclerView
        mBookListAdapter = BookListAdapter(this)
        rvBookList.adapter = mBookListAdapter
        rvBookList.layoutManager = LinearLayoutManager(this)

        mBookListAdapter.rowClicked.observe(this, Observer { position ->
            onBookRowClicked(position)
        })

        val orderByItems =
            arrayOf<String>(ORDER_BY_TITLE, ORDER_BY_LASTREAD, ORDER_BY_PROGRESS, ORDER_BY_PAGES)
        mOrderBySpinner = findViewById<Spinner>(R.id.spBookListOrderBy)
        ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            orderByItems
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mOrderBySpinner.adapter = adapter
            mOrderBySpinner.setSelection(1)
        }

        findViewById<EditText>(R.id.editTextMainBookSearch).setOnEditorActionListener() { v, actionId, event ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    updateBookList()
                    v.clearFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(v.windowToken, 0)
                    true
                }
                else -> false
            }
        }
        mSpLibraryListMain = findViewById(R.id.spLibraryListMain)
        mSpLibraryListMain.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //do nothing
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateBookList()
                    val sharedPref = getPreferences(MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString(
                            PREF_KEY_SELECTED_LIBRARY_NAME,
                            mSpLibraryListMain.selectedItem.toString()
                        )
                        apply()
                    }
                }
            }

        mOrderBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //do nothing
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                updateBookList()
            }
        }
        networkFragment = NetworkFragment.getInstance(supportFragmentManager)

        libraryViewModel?.let {
            it.updateLibrarySpinner(
                this,
                sharedPreferences.getString(PREF_KEY_SELECTED_LIBRARY_NAME, null)
            )
        }

        DEVICE_NAME =
            Settings.Global.getString(applicationContext.contentResolver, "device_name")

        preferredFormats.add("PDF")
        formatsComponentIdMap["PDF"] = Pair(R.id.imagePdfIcon, R.id.pbPdfDownload)
        preferredFormats.add("EPUB")
        formatsComponentIdMap["EPUB"] = Pair(R.id.imageEpubIcon, R.id.pbEpubDownload)

        mSpLibraryListMain.requestFocus()

    }

    fun updateBookList() {
        mSpLibraryListMain.selectedItem?.let {
            if (!bookListUpdateEnabled)
                return

            var searchStr = findViewById<EditText>(R.id.editTextMainBookSearch).text
            var recentDay = 7
            if( searchStr.isNotBlank() )
                recentDay = 0
            bookViewModel?.updateBookListRV(
                mBookListAdapter,
                bookListUpdated,
                it.toString(),
                searchStr,
                mOrderBySpinner.selectedItem as String,
                recentDay
            )
        }
    }

    private fun enableUpdateBookList() {
        mOrderBySpinner.isEnabled = true
        mSpLibraryListMain.isEnabled = true

        bookListUpdateEnabled = true
    }

    private fun disableUpdateBookList() {
        bookListUpdateEnabled = false

        mOrderBySpinner.isEnabled = false
        mSpLibraryListMain.isEnabled = false
    }

    fun updateBookListStarted() {
        mMainBookListUpdateProgressBar.visibility = View.VISIBLE
        disableUpdateBookList()
    }

    fun updateBookListFinished() {
        BookLibrary.update(mBookListAdapter.currentList)
        mMainBookListUpdateProgressBar.visibility = View.GONE
        enableUpdateBookList()
    }

    private fun startDownload() {
        if (!downloading) {
            // Execute the async download.
            networkFragment?.apply {
                startDownload()
                downloading = true
            }
        }
    }

    fun sendMessage(view: View) {
        logger.info("enter sendMessage()")

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(getString(R.string.editCalibreServer), BookLibrary.calibreServer)
            apply()
        }

        val url = BookLibrary.calibreServer + "/ajax/library-info"

        val args = Bundle()
        args.putString(URL_KEY, url)
        args.putString(CMD_KEY, CALIBRE_CMD_Get_Library_List)
        networkFragment?.arguments = args

        logger.info("before startDownload()")
        startDownload()
    }

    override fun updateFromDownload(result: DownloadCallbackData?) {
        // Update your UI here based on result of download.
        when (result?.command) {
            CALIBRE_CMD_Get_Library_List -> Intent(this, DisplayMessageActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, result?.result as String)
                putExtra(EXTRA_CALIBRE_SERVER, BookLibrary.calibreServer)
                startActivityForResult(this, REQUEST_SYNCING_ACTIVITY_CODE)
                downloading = false
            }
            CALIBRE_CMD_Set_Metadata -> result.result?.let {
                when (result.code) {
                    200 -> {
                        readingBook?.let { book ->
                            bookViewModel?.updateBookFromMetadata(it as String, book)
                        }
                        readingBookPosition?.let { position ->
                            mBookListAdapter.notifyItemChanged(
                                position
                            )
                        }
                    }
                    else -> {

                    }
                }
            }
        }
    }

    override fun getActiveNetworkInfo(): NetworkInfo? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo
    }

    override fun onProgressUpdate(progressCode: Int, percentComplete: Int) {
        when (progressCode) {
            // You can add UI behavior for progress updates here.
            ERROR -> {
            }
            CONNECT_SUCCESS -> {
            }
            GET_INPUT_STREAM_SUCCESS -> {
            }
            PROCESS_INPUT_STREAM_IN_PROGRESS -> {
            }
            PROCESS_INPUT_STREAM_SUCCESS -> {
            }
        }
    }

    override fun finishDownloading(result: DownloadCallbackData?) {
        when (result?.command) {
            CALIBRE_CMD_Get_Library_List -> {
                downloading = false
                networkFragment?.cancelDownload()
            }
        }
    }


    fun getPreferredFormat(book: Book): String? {
        var preferredFormat: String? = null
        for (format in preferredFormats) {
            if (book.formats.containsKey(format)) {
                preferredFormat = format
                break
            }
        }
        return preferredFormat
    }

    fun onBookRowClicked(position: Int) {
        val book = mBookListAdapter.currentList[position]
        logger.info("BookRowClicked: $book")

        readingBook = book
        readingBookPosition = position


        Intent(this, BookDetailActivity::class.java).apply {
            putExtra(EXTRA_REQUEST_CODE, REQUEST_BY_MAIN_ACTIVITY)
            putExtra(BookDetailFragment.ARG_ITEM_ID, book.id)
            putExtra(EXTRA_DEVICE_NAME, DEVICE_NAME)
            putExtra(EXTRA_LIBRARY_NAME, mSpLibraryListMain.selectedItem.toString())
            putExtra(EXTRA_CALIBRE_SERVER, BookLibrary.calibreServer)
        }.also {
            startActivity(it)
        }
        return


    }

    fun onBookRowLongClicked(position: Int): Boolean {
        var preferredFormat: String? = null
        val book = mBookListAdapter.currentList[position]
        for (format in preferredFormats) {
            if (book.formats.containsKey(format)) {
                preferredFormat = format
                break
            }
        }

        preferredFormat?.let {
            val bookFile = getBookFormatFile(book, preferredFormat)
            if (bookFile.exists()) {
                bookFile.delete()
                mBookListAdapter.notifyItemChanged(position)
            }
        }
        return true
    }

    private fun handleSyncingActivityResult(resultCode: Int, data: Intent) {
        libraryViewModel?.updateLibrarySpinner(this, data.getStringExtra(EXTRA_LIBRARY_NAME))

    }

    fun handleDocumentActivityResult(resultCode: Int, data: Intent) {
        val pageNumber = data.getIntExtra(EXTRA_READER_PAGE_NUMBER, 0)
        logger.info("pageNumber: $pageNumber")

        readingBook?.let { book ->
            bookViewModel?.updateReadingProgress(book, data.extras)
            readingBookPosition?.let { mBookListAdapter?.notifyItemChanged(it) }

            val url =
                BookLibrary.calibreServer + "/cdb/cmd/set_metadata/0?library_id=" + mSpLibraryListMain.selectedItem
            val args = Bundle()
            args.putString(URL_KEY, url)
            args.putString(CMD_KEY, CALIBRE_CMD_Set_Metadata)
            val jsonReadPos = Gson().toJson(book.readPos)
            val jsonReadPosEncoded =
                Base64.encodeToString(jsonReadPos.toByteArray(), Base64.NO_WRAP)
            args.putString(
                POST_KEY,
                "[\"fields\", ${book.id}, [[\"#read_pos\", \"$jsonReadPosEncoded\"]]]"
            )
            // Execute the async download.
            networkFragment?.apply {
                arguments = args
                startDownload()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_SYNCING_ACTIVITY_CODE -> data?.let {
                handleSyncingActivityResult(resultCode, it)
            }
            REQUEST_DOCUMENT_ACTIVITY_CODE -> data?.let {
                handleDocumentActivityResult(resultCode, it)
            }
            REQUEST_ANDROID_BOOK_READER_ACTIVITY_SHELF_CODE -> data?.let {

            }
            REQUEST_ANDROID_BOOK_READER_ACTIVITY_BOOK_CODE -> data?.let {
                val pageNumber = it.getIntExtra(EXTRA_READER_PAGE_NUMBER, 0)
                logger.info("REQUEST_ANDROID_BOOK_READER_ACTIVITY_CODE $pageNumber")
                handleDocumentActivityResult(resultCode, it)
            }
        }
    }

    private fun startReadingBook(position: Int, uri: Uri) {
        val book = mBookListAdapter.currentList[position]
        val intent = Intent(this, DocumentActivityWithResult::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = uri
        intent.putExtra(EXTRA_READER_BOOK_ID, book.id)
        intent.putExtra(EXTRA_LIBRARY_NAME, book.libraryName)
        intent.putExtra(EXTRA_DEVICE_NAME, DEVICE_NAME)
        intent.putExtra(EXTRA_READER_PAGE_NUMBER, book.readPos.getLastPageProgress())

        startActivityForResult(intent, REQUEST_DOCUMENT_ACTIVITY_CODE)
    }

    private fun startReadingBookByReaderFragment(position: Int, format: String, uri: Uri) {
        val book = mBookListAdapter.currentList[position]

        val intent = Intent(this, AndroidBookReaderActivity::class.java)
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        intent.data = uri
        intent.putExtra(EXTRA_READER_BOOK_ID, book.id)
        intent.putExtra(EXTRA_LIBRARY_NAME, book.libraryName)
        intent.putExtra(EXTRA_DEVICE_NAME, DEVICE_NAME)

        updateAndroidBookReaderPosition(book, format, book.readPos.getLastPosition())

        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivityForResult(intent, REQUEST_ANDROID_BOOK_READER_ACTIVITY_BOOK_CODE)
    }

    private fun updateAndroidBookReaderPosition(book: Book, format: String, position: IntArray) {
        var jsonFile = getBookFormatMetadataFile(book, format)
        var metadata: AndroidBookReaderActivity.Metadata
        if (!jsonFile.exists()) {
            jsonFile.createNewFile()
            metadata = AndroidBookReaderActivity.Metadata()
            metadata.title = book.title
            metadata.authors = book.authors
        } else {
            metadata = Gson().fromJson(
                InputStreamReader(jsonFile.inputStream()),
                AndroidBookReaderActivity.Metadata::class.java
            )
        }
        metadata.position = position
        var writer = jsonFile.writer()
        GsonBuilder().setPrettyPrinting().create().toJson(metadata, writer)

        writer.flush()
        writer.close()
    }

    private fun getBookFormatMetadataFile(book: Book, format: String): File {
        val bookFile = getBookFormatFile(book, format)

        return File(bookFile.parent, bookFile.nameWithoutExtension + ".json")
    }

    private fun getBookFormatFile(book: Book, format: String): File {
        return File(
            applicationContext.getExternalFilesDir(null),
            book.formats[format] ?: "__NOT_EXIST__"
        )
    }

    fun onShowLibraryButtonClicked(view: View) {
        val intent = Intent(this, BookListActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(EXTRA_CALIBRE_SERVER, BookLibrary.calibreServer)
        intent.putExtra(EXTRA_LIBRARY_NAME, mSpLibraryListMain.selectedItem.toString())
        intent.putExtra(EXTRA_DEVICE_NAME, DEVICE_NAME)
        startActivity(intent)
    }

    fun onShowShelfButtonClicked(view: View) {
        startAndroidBookReaderActivity()
    }

    private fun startAndroidBookReaderActivity() {
        val intent = Intent(this, AndroidBookReaderActivity::class.java)
        intent.action = Intent.ACTION_MAIN

        intent.putExtra(EXTRA_DEVICE_NAME, this.intent.getStringExtra(EXTRA_DEVICE_NAME))
        intent.putExtra(EXTRA_SHELF_MODE, true)

        //TODO update reading position of every book

        startActivityForResult(intent, REQUEST_ANDROID_BOOK_READER_ACTIVITY_SHELF_CODE)
    }
}