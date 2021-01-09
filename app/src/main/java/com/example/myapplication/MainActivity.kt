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

const val EXTRA_READER_BOOK_ID = "mupdf_bookid"
const val EXTRA_READER_LIBRARY_NAME = "mupdf_libraryname"
const val EXTRA_READER_PAGE_NUMBER = "mupdf_pagenumber"
const val EXTRA_READER_BOOK_POSITION = "mupdf_bookposition"

val PREFERRED_FORMATS = arrayOf("PDF", "EPUB")

class MainActivity : FragmentActivity(), DownloadCallback<DownloadCallbackData> {


    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private var networkFragment: NetworkFragment? = null

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private var downloading = false

    private var logger: Logger = Logger.getLogger("MainActivity")

    var mBooks = ArrayList<Book>()
    private var mBookListAdapter: BookListMainLibraryAdapter? = null

    private lateinit var db: AppDatabase
    private lateinit var bookViewModel: BookViewModel
    private lateinit var libraryViewModel: LibraryViewModel

    private lateinit var mEditCalibreServer: EditText

    private var readingBook: Book? = null
    private var readingBookPosition: Int? = 0
    lateinit var deviceName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mEditCalibreServer = findViewById(R.id.editCalibreServer)
        getPreferences(Context.MODE_PRIVATE).getString(
            getString(R.string.editCalibreServer),
            getString(R.string.editCalibreServerDefaultValue)
        )?.let {
            mEditCalibreServer.setText(it)
        }

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
        }

        findViewById<EditText>(R.id.editTextMainBookSearch).setOnEditorActionListener() { v, actionId, event ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    bookViewModel.updateBookListRV(
                        this,
                        findViewById<Spinner>(R.id.spLibraryListMain).toString(),
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

        findViewById<Spinner>(R.id.spLibraryListMain).onItemSelectedListener =
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

        libraryViewModel.updateLibrarySpinner(this)

        deviceName =
            Settings.Global.getString(applicationContext.contentResolver, "device_name")
    }

    fun updateBookList() {
        findViewById<ProgressBar>(R.id.mainBookListUPdateProgressBar).visibility = View.VISIBLE
        bookViewModel.updateBookListRV(
            this,
            findViewById<Spinner>(R.id.spLibraryListMain).selectedItem.toString(),
            "",
            findViewById<Spinner>(R.id.spBookListOrderBy).selectedItem as String
        )
    }

    fun updateBookListFinished() {
        findViewById<ProgressBar>(R.id.mainBookListUPdateProgressBar).visibility = View.GONE
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
        val calibreServer = mEditCalibreServer.text.toString()
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
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
                startActivity(this)
            }
            CALIBRE_CMD_Set_Metadata -> result.result?.let {
                when(result.code) {
                    200 -> {

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

    override fun finishDownloading() {
        downloading = false
        networkFragment?.cancelDownload()
    }

    private class DownloadFile(
        callback: DownloadCallback<DownloadCallbackData>,
        val mContext: Context,
        val mFilename: String
    ) : AsyncTask<String?, Void?, InputStream?>() {

        override fun doInBackground(vararg params: String?): InputStream? {
            val imageURL = params[0]
            var input: InputStream? = null
            try {
                // Download Image from URL
                input = java.net.URL(imageURL).openStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val dir = File(mContext.filesDir, "Books")
            if (!dir.exists()) {
                dir.mkdir()
            }
            val destination = File(dir, mFilename)
            try {
                destination.createNewFile()
                destination.outputStream().use { fileOutputStream ->
                    input?.copyTo(fileOutputStream)
                    fileOutputStream.flush()
                    fileOutputStream.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: InputStream?) {
            //do nothing
        }

    }

    fun onBookRowClicked(position: Int) {
        val book = mBooks[position]
        logger.info("BookRowClicked: $book")

        readingBook = book
        readingBookPosition = position

        var preferredFormat: String? = null
        for(format in PREFERRED_FORMATS) {
            if( book.formats.containsKey(format) ) {
                preferredFormat = format
                break
            }
        }


        preferredFormat?.let {
            val dir = File(applicationContext.filesDir, "Books")
            val bookFile = File(dir, "${book.libraryName} - (${book.id}).$it")
            if (bookFile.exists()) {
                startReadingBook(position, Uri.fromFile(bookFile))
            } else {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        data?.let { data ->
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
    }

    private fun startReadingBook(position: Int, uri: Uri) {
        val book = mBooks[position]
        val intent = Intent(this, DocumentActivityWithResult::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.data = uri
        intent.putExtra(EXTRA_READER_BOOK_ID, book.id)
        intent.putExtra(EXTRA_READER_LIBRARY_NAME, book.libraryName)
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName)
        intent.putExtra(EXTRA_READER_PAGE_NUMBER, book.readPos[deviceName]?.toInt())

        startActivityForResult(intent, 0)
    }
}