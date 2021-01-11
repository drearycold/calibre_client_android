package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLConnection
import java.util.*
import java.util.logging.Logger


const val EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE"
const val EXTRA_DEVICE_NAME = "com.example.myfirstapp.DEVICE_NAME"
const val EXTRA_DEVICE_NAME_DEFAULT = "My Device"
const val CALIBRE_SERVER = "com.example.myfirstapp.CALIBRE_SERVER"
const val ORDER_BY_TITLE = "Title"
const val ORDER_BY_LASTREAD = "LastRead"
const val ORDER_BY_PROGRESS = "Progress"
const val ORDER_BY_PAGES = "Pages"

const val PREF_KEY_SELECTED_LIBRARY_NAME = "MainActivitySelectedLibraryName"

const val EXTRA_SYNCING_LIBRARY_NAME = "syncing_libraryname"
const val EXTRA_READER_BOOK_ID = "mupdf_bookid"
const val EXTRA_READER_LIBRARY_NAME = "mupdf_libraryname"
const val EXTRA_READER_PAGE_NUMBER = "mupdf_pagenumber"
const val EXTRA_READER_PAGE_NUMBER_MAX = "mupdf_pagenumbermax"

const val REQUEST_SYNCING_ACTIVITY_CODE = 10
const val REQUEST_DOCUMENT_ACTIVITY_CODE = 20

class MainActivity : FragmentActivity(), DownloadCallback<DownloadCallbackData> {

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private var networkFragment: NetworkFragment? = null

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private var downloading = false

    private var logger: Logger = Logger.getLogger("MainActivity")

    var mBooks = ArrayList<Book>()
    private lateinit var mBookListAdapter: BookListMainLibraryAdapter

    private lateinit var db: AppDatabase
    private lateinit var bookViewModel: BookViewModel
    private lateinit var libraryViewModel: LibraryViewModel

    private lateinit var mEditCalibreServer: EditText
    private lateinit var mSpLibraryListMain: Spinner
    lateinit var mMainBookListUpdateProgressBar: ProgressBar

    private var readingBook: Book? = null
    private var readingBookPosition: Int? = 0
    lateinit var deviceName: String

    private var preferredFormats = ArrayList<String>()
    var formatsComponentIdMap = HashMap<String, Pair<Int, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mEditCalibreServer = findViewById(R.id.editCalibreServer)
        val sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        sharedPreferences.getString(
            getString(R.string.editCalibreServer),
            getString(R.string.editCalibreServerDefaultValue)
        ).let {
            mEditCalibreServer.setText(it)
        }

        mMainBookListUpdateProgressBar =
            findViewById<ProgressBar>(R.id.mainBookListUpdateProgressBar)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "metadata"
        ).build()
        bookViewModel = BookViewModel(db.bookDao())
        libraryViewModel = LibraryViewModel(db.libraryDao())

        val rvBookList = findViewById<View>(R.id.rvMainBookList) as RecyclerView
        mBookListAdapter = BookListMainLibraryAdapter(this)
        rvBookList.adapter = mBookListAdapter
        rvBookList.layoutManager = LinearLayoutManager(this)

        val orderByItems =
            arrayOf<String>(ORDER_BY_TITLE, ORDER_BY_LASTREAD, ORDER_BY_PROGRESS, ORDER_BY_PAGES)
        val orderBySpinner = findViewById<Spinner>(R.id.spBookListOrderBy)
        ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            orderByItems
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            orderBySpinner.adapter = adapter
            orderBySpinner.setSelection(1)
        }

        findViewById<EditText>(R.id.editTextMainBookSearch).setOnEditorActionListener() { v, actionId, event ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    bookViewModel.updateBookListRV(
                        this,
                        findViewById<Spinner>(R.id.spLibraryListMain).selectedItem.toString(),
                        v.text, orderBySpinner.selectedItem as String
                    )
                    v.clearFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(v.windowToken, 0)
                    true
                }
                else -> false
            }
        }
        mSpLibraryListMain = findViewById<Spinner>(R.id.spLibraryListMain)
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
                    val sharedPref = getPreferences(Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString(PREF_KEY_SELECTED_LIBRARY_NAME, mSpLibraryListMain.selectedItem.toString())
                        apply()
                    }
                }
            }

        orderBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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

        libraryViewModel.updateLibrarySpinner(
            this,
            sharedPreferences.getString(PREF_KEY_SELECTED_LIBRARY_NAME, null)
        )

        deviceName =
            Settings.Global.getString(applicationContext.contentResolver, "device_name")

        preferredFormats.add("PDF")
        formatsComponentIdMap["PDF"] = Pair(R.id.imagePdfIcon, R.id.pbPdfDownload)
        preferredFormats.add("EPUB")
        formatsComponentIdMap["EPUB"] = Pair(R.id.imageEpubIcon, R.id.pbEpubDownload)
    }

    fun updateBookList() {
        mSpLibraryListMain.selectedItem?.let {
            bookViewModel.updateBookListRV(
                this,
                it.toString(),
                "",
                findViewById<Spinner>(R.id.spBookListOrderBy).selectedItem as String
            )
        }
    }

    fun updateBookListFinished() {
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

        val calibreServer = mEditCalibreServer.text.toString()
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(getString(R.string.editCalibreServer), calibreServer)
            apply()
        }

        val url = mEditCalibreServer.text.toString() + "/ajax/library-info"

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
                putExtra(CALIBRE_SERVER, mEditCalibreServer.text.toString())
                startActivityForResult(this, REQUEST_SYNCING_ACTIVITY_CODE)
                downloading = false
            }
            CALIBRE_CMD_Set_Metadata -> result.result?.let {
                when (result.code) {
                    200 -> {
                        readingBook?.let { book ->
                            bookViewModel.updateBookFromMetadata(it as String, book)
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
            CALIBRE_CMD_Get_Book_File -> {
                mBookListAdapter.apply {
                    clearDownloading()
                    readingBookPosition?.let { notifyItemChanged(it) }
                }
            }
        }
    }

    private class DownloadFile(
        val callback: DownloadCallback<DownloadCallbackData>,
        val mContext: Context,
        val mFilename: String
    ) : AsyncTask<String?, Void?, DownloadCallbackData?>() {
        override fun doInBackground(vararg params: String?): DownloadCallbackData? {
            var result = DownloadCallbackData()
            result.command = CALIBRE_CMD_Get_Book_File
            val imageURL = params[0]
            var connection: HttpURLConnection? = null
            try {
                // Download Image from URL
                connection = java.net.URL(imageURL).openConnection() as? HttpURLConnection
            } catch (e: Exception) {
                e.printStackTrace()
                result.code = connection?.responseCode ?: -1
                return result
            }

            val dir = File(mContext.filesDir, "Books")
            if (!dir.exists()) {
                dir.mkdir()
            }
            val destination = File(dir, mFilename)
            try {
                destination.createNewFile()
                destination.outputStream().use { fileOutputStream ->
                    connection?.inputStream?.copyTo(fileOutputStream)
                    fileOutputStream.flush()
                    fileOutputStream.close()

                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                result.code = connection?.responseCode ?: -1
            }
            return result
        }

        override fun onPostExecute(result: DownloadCallbackData?) {
            callback.finishDownloading(result)
        }

    }

    fun onBookRowClicked(position: Int) {
        val book = mBooks[position]
        logger.info("BookRowClicked: $book")

        readingBook = book
        readingBookPosition = position

        var preferredFormat: String? = null
        for (format in preferredFormats) {
            if (book.formats.containsKey(format)) {
                preferredFormat = format
                break
            }
        }

        preferredFormat?.let {
            val bookFile = getBookFormatFile(book, preferredFormat)
            if (bookFile.exists()) {
                startReadingBook(position, Uri.fromFile(bookFile))
            } else {
                mBookListAdapter.apply {
                    markDownloading(position, preferredFormat)
                    notifyItemChanged(position)
                }
                DownloadFile(
                    this,
                    applicationContext,
                    "${book.libraryName} - (${book.id}).$it"
                ).apply {
                    execute("http://peter-media.lan:8080/get/$it/${book.id}/${book.libraryName}")
                }
            }
        }
    }

    fun onBookRowLongClicked(position: Int): Boolean {
        var preferredFormat: String? = null
        val book = mBooks[position]
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
        libraryViewModel.updateLibrarySpinner(this, data.getStringExtra(EXTRA_SYNCING_LIBRARY_NAME))

    }

    private fun handleDocumentActivityResult(resultCode: Int, data: Intent) {
        val pageNumber = data.getIntExtra(EXTRA_READER_PAGE_NUMBER, 0)
        logger.info("pageNumber: $pageNumber")

        readingBook?.let { book ->
            bookViewModel.updateReadingProgress(book, data.extras)
            readingBookPosition?.let { mBookListAdapter?.notifyItemChanged(it) }

            val url =
                mEditCalibreServer.text.toString() + "/cdb/cmd/set_metadata/0?library_id=" + findViewById<Spinner>(
                    R.id.spLibraryListMain
                ).selectedItem
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
        }
    }

    private fun startReadingBook(position: Int, uri: Uri) {
        val book = mBooks[position]
        val intent = Intent(this, DocumentActivityWithResult::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = uri
        intent.putExtra(EXTRA_READER_BOOK_ID, book.id)
        intent.putExtra(EXTRA_READER_LIBRARY_NAME, book.libraryName)
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName)
        intent.putExtra(EXTRA_READER_PAGE_NUMBER, book.readPos.getByDevice(deviceName).lastReadPage)

        startActivityForResult(intent, REQUEST_DOCUMENT_ACTIVITY_CODE)
    }

    private fun getBookFormatFile(book: Book, format: String): File {
        val dir = File(applicationContext.filesDir, "Books")
        return File(dir, "${book.libraryName} - (${book.id}).$format")
    }

    fun isBookFormatDownloaded(book: Book, format: String): Boolean {
        val bookFile = getBookFormatFile(book, format)
        return bookFile.exists()
    }
}