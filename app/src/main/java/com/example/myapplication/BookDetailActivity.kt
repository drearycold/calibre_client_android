package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Base64
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.logging.Logger

/**
 * An activity representing a single Book detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [BookListActivity].
 */
class BookDetailActivity : AppCompatActivity(), DownloadCallback<DownloadCallbackData> {

    var networkFragment: NetworkFragment? = null
    var bookDetailFragment: BookDetailFragment? = null
    var book: Book? = null
    lateinit var fab: FloatingActionButton

    companion object {
        fun getBook(data: Bundle): Book? {
            val requestBy = data.getInt(EXTRA_REQUEST_CODE, -1)
            val bookId = data.getInt(BookDetailFragment.ARG_ITEM_ID, -1)
            return BookLibrary.get(bookId)
        }
    }

    private class DownloadFile(
        val callback: DownloadCallback<DownloadCallbackData>,
        val mContext: Context,
        val mFilename: String
    ) : AsyncTask<String?, Void?, DownloadCallbackData?>() {
        private var logger: Logger = Logger.getLogger("BookDetailActivity.DownloadFile")

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


            val dir = File(mContext.getExternalFilesDir(null), "tmp")
            if (!dir.exists()) {
                dir.mkdir()
            }
            val tmpFile = File(dir, mFilename)
            try {
                tmpFile.createNewFile()
                tmpFile.outputStream().use { fileOutputStream ->
                    connection?.inputStream?.copyTo(fileOutputStream)
                    fileOutputStream.flush()
                    fileOutputStream.close()
                }

                logger.info("TMPFILE SIZE ${tmpFile.length()}")
                //val md5 = MD5.calculateMD5(tmpFile)
                val md5 = MD5.calculateMD5(mFilename)

                logger.info("TMPFILE MD5 $md5")
                val ext = tmpFile.extension.toLowerCase()

                val destFileName = "$md5.$ext"
                val destFile = File(mContext.getExternalFilesDir(null), destFileName)
                tmpFile.renameTo(destFile)
                result.result = destFileName
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)
        setSupportActionBar(findViewById(R.id.detail_toolbar))
        fab = findViewById(R.id.fab)

        networkFragment = NetworkFragment.getInstance(supportFragmentManager)


        book = intent.extras?.let {
            getBook(it)
        }

        fab.setOnClickListener {
            book?.also {
                bookDetailFragment?.getSelectedFormat()?.let { format ->
                    if (MainActivity.isBookFormatDownloaded(applicationContext, it, format)) {
                        startReadingBookByReaderFragment(it, format)
                    } else {
                        DownloadFile(
                            this,
                            applicationContext,
                            "${it.libraryName} - (${it.id}).$format"
                        ).apply {
                            execute(intent.getStringExtra(EXTRA_CALIBRE_SERVER) + "/get/$format/${it.id}/${it.libraryName}")
                        }
                    }
                }
            }
        }

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don"t need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            bookDetailFragment = BookDetailFragment().apply {
                arguments = Bundle().apply {
                    book?.id?.let {
                        putInt(
                            BookDetailFragment.ARG_ITEM_ID, it
                        )
                        putInt(EXTRA_REQUEST_CODE, intent.getIntExtra(EXTRA_REQUEST_CODE, -1))
                    }
                    putString(
                        EXTRA_DEVICE_NAME,
                        intent.getStringExtra(EXTRA_DEVICE_NAME)
                    )
                    putString(
                        EXTRA_LIBRARY_NAME,
                        intent.getStringExtra(EXTRA_LIBRARY_NAME)
                    )
                    putString(
                        EXTRA_CALIBRE_SERVER,
                        intent.getStringExtra(EXTRA_CALIBRE_SERVER)
                    )
                }
            }

            supportFragmentManager.beginTransaction()
                .add(R.id.book_detail_container, bookDetailFragment!!)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back

                navigateUpTo(Intent(this, BookListActivity::class.java).apply {
                    putExtra(
                        EXTRA_DEVICE_NAME,
                        intent.getStringExtra(EXTRA_DEVICE_NAME)
                    )
                    putExtra(
                        EXTRA_LIBRARY_NAME,
                        intent.getStringExtra(EXTRA_LIBRARY_NAME)
                    )
                    putExtra(
                        EXTRA_CALIBRE_SERVER,
                        intent.getStringExtra(EXTRA_CALIBRE_SERVER)
                    )
                })

                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun startReadingBookByReaderFragment(book: Book, format: String) {
        val intent = Intent(this, AndroidBookReaderActivity::class.java)
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        intent.data = Uri.fromFile(MainActivity.getBookFormatFile(applicationContext, book, format))
        intent.putExtra(EXTRA_READER_BOOK_ID, book.id)
        intent.putExtra(EXTRA_LIBRARY_NAME, book.libraryName)
        intent.putExtra(EXTRA_DEVICE_NAME, this.intent.getStringExtra(EXTRA_DEVICE_NAME))

        bookDetailFragment?.getSelectedReadingPosition()?.let {
            updateAndroidBookReaderPosition(book, format, it.lastPosition)
        }

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
        val bookFile = MainActivity.getBookFormatFile(applicationContext, book, format)

        return File(bookFile.parent, bookFile.nameWithoutExtension + ".json")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ANDROID_BOOK_READER_ACTIVITY_BOOK_CODE -> data?.let {
                handleDocumentActivityResult(resultCode, data)
            }
        }
    }

    override fun updateFromDownload(result: DownloadCallbackData?) {
        when (result?.command) {
            CALIBRE_CMD_Show_Metadata, CALIBRE_CMD_Set_Metadata -> result.result?.let {
                when (result.code) {
                    200 -> {
                        intent.extras?.let { e -> getBook(e) }?.also { book ->
                            MainActivity.bookViewModel?.updateBookFromMetadata(
                                it as String,
                                book
                            )
                            bookDetailFragment?.let { fragment ->
                                fragment.populateView()
                            }
                        }
                    }
                    else -> {

                    }
                }
            }
            CALIBRE_CMD_Get_Book -> result.result?.let { result ->
                book?.let { book ->
                    MainActivity.bookViewModel?.updateBook(
                        result as String,
                        book.libraryName,
                        book
                    )
                    bookDetailFragment?.let { fragment ->
                        fragment.populateView()
                        fragment.getSelectedFormat()?.let { format ->
                            DownloadFile(
                                this,
                                applicationContext,
                                "${book.libraryName} - (${book.id}).$format"
                            ).apply {
                                execute(intent.getStringExtra(EXTRA_CALIBRE_SERVER) + "/get/$format/${book.id}/${book.libraryName}")
                            }
                        }
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
    }

    override fun finishDownloading(result: DownloadCallbackData?) {
        when (result?.command) {
            CALIBRE_CMD_Get_Book_File -> {
                when (result.code) {
                    200 -> {
                        fab.setImageResource(R.drawable.fbreader_bw)
                        val destFileName = result.result as String
                        book?.let {
                            it.formats[bookDetailFragment?.getSelectedFormat().toString()] =
                                destFileName
                            MainActivity.bookViewModel?.insertBook(it)
                        }
                    }
                }
            }
        }
    }

    private fun handleDocumentActivityResult(resultCode: Int, data: Intent) {
        book?.let {
            MainActivity.bookViewModel?.updateReadingProgress(it, data.extras)

            val url =
                intent.getStringExtra(EXTRA_CALIBRE_SERVER) + "/cdb/cmd/set_metadata/0?library_id=" + intent.getStringExtra(
                    EXTRA_LIBRARY_NAME
                )
            val args = Bundle()
            args.putString(URL_KEY, url)
            args.putString(CMD_KEY, CALIBRE_CMD_Set_Metadata)
            val jsonReadPos = Gson().toJson(it.readPos)
            val jsonReadPosEncoded =
                Base64.encodeToString(jsonReadPos.toByteArray(), Base64.NO_WRAP)
            args.putString(
                POST_KEY,
                "[\"fields\", ${it.id}, [[\"#read_pos\", \"$jsonReadPosEncoded\"]]]"
            )
            // Execute the async download.
            networkFragment?.apply {
                arguments = args
                startDownload()
            }
        }
    }


}