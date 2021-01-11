package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

class DisplayMessageActivity : FragmentActivity(), DownloadCallback<DownloadCallbackData>  {

    private val logger: Logger = Logger.getLogger("DisplayMessageActivity")

    private var syncing = false

    private var updating = false
    private var cancelUpdate = false
    private var updateIndex = 0

    private lateinit var networkFragment: NetworkFragment

    private var mBooks = HashMap<String, ArrayList<Book>>()
    private lateinit var mBooksSelected : ArrayList<Book>
    private lateinit var mBookListSyncDiffAdapter: BookListSyncDiffAdapter

    private var calibreServer: String = ""

    private lateinit var mLibraryListSpinner: Spinner

    private lateinit var dbMetadata: AppDatabase
    private lateinit var bookViewModel: BookViewModel

    data class LibraryInfo (
            @SerializedName("default_library")
            var defaultLibrary: String,
            @SerializedName("library_map")
            var libraryMap: TreeMap<String, String>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_message)

        val message = intent.getStringExtra(EXTRA_MESSAGE)
        calibreServer = intent.getStringExtra(CALIBRE_SERVER)!!

        logger.info("onCreate message:$message")

        val libraryInfo = Gson().fromJson<LibraryInfo>(message, LibraryInfo::class.java)

        logger.info("onCreate libraryInfo.defaultLibrary:${libraryInfo.defaultLibrary}")
        logger.info("onCreate libraryInfo.libraryMap:${libraryInfo.libraryMap}")

        val libraryList = ArrayList<Library>()
        for( libraryName in libraryInfo.libraryMap.keys.toList()) {
            libraryList.add(Library(libraryName))
            mBooks[libraryName] = ArrayList()
        }

        mLibraryListSpinner = findViewById<Spinner>(R.id.spSyncLibraryList)
        ArrayAdapter<Library>(this,
            android.R.layout.simple_spinner_item,
            libraryList
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mLibraryListSpinner.adapter = adapter
            mLibraryListSpinner.setSelection(libraryList.indexOf(Library(libraryInfo.defaultLibrary)))
        }

        mBooksSelected = mBooks[libraryInfo.defaultLibrary]!!

        mLibraryListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position)
                mBooksSelected = mBooks[item.toString()]!!
                updateRVBookList()
            }
        }

        dbMetadata = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "metadata").build()
        bookViewModel = BookViewModel(dbMetadata.bookDao())

        val libraryViewModel = LibraryViewModel(dbMetadata.libraryDao())
        for(library in libraryList) {
            libraryViewModel.insertLibrary(library)
        }

        val rvBookList = findViewById<View>(R.id.rvSyncDiffBookList) as RecyclerView

        mBookListSyncDiffAdapter = BookListSyncDiffAdapter(mBooksSelected)
        rvBookList.adapter = mBookListSyncDiffAdapter
        rvBookList.layoutManager = LinearLayoutManager(this)

        networkFragment = NetworkFragment.getInstance(
            supportFragmentManager)
    }

    private fun updateRVBookList() {
        mBookListSyncDiffAdapter.replaceBooks(mBooksSelected)
        mBookListSyncDiffAdapter.notifyDataSetChanged()
    }

    fun onSyncButton(view: View) {
        val url = calibreServer + "/cdb/cmd/list/0?library_id=" + findViewById<Spinner>(R.id.spSyncLibraryList).selectedItem
        val args = Bundle()
        args.putString(URL_KEY, url)
        args.putString(CMD_KEY, CALIBRE_CMD_Get_Library_Books)
        args.putString(POST_KEY, "[[\"id\",\"title\",\"authors\"],\"\",\"\",\"\",-1]")
        networkFragment.arguments = args
        if (!syncing) {
            // Execute the async download.
            networkFragment.apply {
                startDownload()
                syncing = true
            }
        }
    }

    fun onUpdateLibraryButton(view: View) {
        if(mBooksSelected.isEmpty())
            return

        if(!updating) {
            findViewById<Button>(R.id.btnUpdateLibrary).text = getString(R.string.btnCancelUpdateLibrary)
            updating = true
            updateIndex = 0

            findViewById<ProgressBar>(R.id.updateProgressBar).apply {
                mBooksSelected.let {
                    this.max = it.size
                    this.progress = 0
                }
            }

            updateOneBookFromQueue()
        } else {
            cancelUpdate = true
        }


    }

    private fun updateFromGetLibraryBooks(result: String) {
        val gson = Gson()

        val root = JsonParser.parseString(result)
        logger.info("updateFromDownload root: ${root.isJsonObject}")

        val resultElement = root.asJsonObject.get("result").asJsonObject
        val bookIds = resultElement.get("book_ids").asJsonArray

        val books = TreeMap<Int, Book>()
        val curLibraryName = (findViewById<Spinner>(R.id.spSyncLibraryList).selectedItem as Library).name
        for(idAny in bookIds) {
            val id = idAny.asInt
            books[id] = Book(id, curLibraryName,
                    getString(R.string.bookTitleDefault),
                    getString(R.string.bookAuthorsDefault)
            )
        }

        val dataElement = resultElement.get("data").asJsonObject
        val bookTitles = gson.fromJson(dataElement.get("title").asJsonObject, TreeMap::class.java)
        val bookAuthors = gson.fromJson(dataElement.get("authors").asJsonObject, TreeMap::class.java)
        logger.info("updateFromDownload bookTitles: $bookTitles")


        for((idStr,title) in bookTitles) {
            val id = (idStr as String).toInt()
            books[id]!!.title = title as String
        }
        for((idStr,authorsAny) in bookAuthors) {
            val id = (idStr as String).toInt()
            val authors = authorsAny as ArrayList<*>
            if( authors.size > 1) {
                books[id]!!.authors = authors[0] as String + ", et al."
            } else {
                books[id]!!.authors = authors[0] as String
            }
        }
        mBooksSelected.addAll(books.values)
        logger.info("updateFromDownload mBooks: ${mBooksSelected.size}")

        updateRVBookList()
    }

    private fun updateOneBookFromQueue() {
        val book = mBooksSelected[updateIndex++]
        val url = calibreServer + "/cdb/cmd/list/0?library_id=" + findViewById<Spinner>(R.id.spSyncLibraryList).selectedItem
        val args = Bundle()
        args.putString(URL_KEY, url)
        args.putString(CMD_KEY, CALIBRE_CMD_Get_Book)
        args.putString(POST_KEY, "[[\"all\"],\"\",\"\",\"id:${book.id}\",-1]")
        networkFragment.arguments = args
        // Execute the async download.
        networkFragment.apply {
            startDownload()
        }
    }

    private fun updateFromGetBook(result: String) {
        logger.info(result)
        findViewById<ProgressBar>(R.id.updateProgressBar).incrementProgressBy(1)

        if( cancelUpdate || updateIndex == mBooksSelected.size) {
            updating = false
            cancelUpdate = false
            findViewById<Button>(R.id.btnUpdateLibrary).text = getString(R.string.btnUpdateLibrary)
        } else {
            updateOneBookFromQueue()
        }

        bookViewModel.updateBook(result, (findViewById<Spinner>(R.id.spSyncLibraryList).selectedItem as Library).name)


    }

    override fun updateFromDownload(result: DownloadCallbackData?) {
        logger.info("updateFromDownload: ${result?.command} ${result?.code} ${result?.result.toString()}")
        syncing = false

        when(result?.command) {
            CALIBRE_CMD_Get_Library_Books -> result.result?.let { updateFromGetLibraryBooks(it as String) }
            CALIBRE_CMD_Get_Book -> result.result?.let { updateFromGetBook(it as String) }
            CALIBRE_CMD_Get_Book_Cover -> result.result?.let {  }
        }

    }

    override fun getActiveNetworkInfo(): NetworkInfo? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
        result?.let {
            when(result?.command) {
                CALIBRE_CMD_Get_Library_Books -> result.result?.let {
                    syncing = false
                }
                else -> {
                    //do nothing
                }
            }
            networkFragment.cancelDownload()
        }
    }

    override fun onBackPressed() {
        var intent = Intent()
        intent.putExtras(this.intent)
        mLibraryListSpinner.selectedItem?.let {
            intent.putExtra(EXTRA_SYNCING_LIBRARY_NAME, (it as Library).name)
        }

        setResult(RESULT_OK, intent)
        finish()

        super.onBackPressed()
    }
}