package com.example.myapplication

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger


const val FORMAT_PROGRESS = "%.2f%%"

class BookListAdapter(private val parentActivity: Activity) :
    ListAdapter<Book, BookListAdapter.ViewHolder>(Book.DIFF_CALLBACK) {

    private val logger: Logger = Logger.getLogger("BookListAdapter")

    var downloading: Boolean = false
    var downloadingPosition: Int = 0
    var downloadingFormat: String = ""

    var rowClicked = MutableLiveData<Int>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImageView: ImageView = itemView.findViewById(R.id.imageMainLibraryBookCover)
        val titleTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookTitle)
        var lastReadTextView: TextView = itemView.findViewById(R.id.textLastRead)
        val authorTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookAuthor)
        val pageCountTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookPageCount)
        val progressTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookProgress)
        val pdfImageView: ImageView = itemView.findViewById(R.id.imagePdfIcon)
        val epubImageView: ImageView = itemView.findViewById(R.id.imageEpubIcon)
        val pdfProgressBar: ProgressBar = itemView.findViewById(R.id.pbPdfDownload)
        val epubProgressBar: ProgressBar = itemView.findViewById(R.id.pbEpubDownload)

        var formatComponents = HashMap<String, Pair<ImageView, ProgressBar>>()

        var coverImageURL = ""
    }

    companion object {
        val lastReadDateFormat = SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss",
            Locale.US
        )

        private class DownloadImageFromInternet(var viewHolder: ViewHolder) :
            AsyncTask<String, Void, Bitmap?>() {
            var imageURL= ""
            override fun doInBackground(vararg urls: String): Bitmap? {
                imageURL = urls[0]
                var image: Bitmap? = null
                try {
                    val `in` = java.net.URL(imageURL).openStream()
                    image = BitmapFactory.decodeStream(`in`)
                } catch (e: Exception) {
                    Log.w("DownloadImage", "Error Message ${e.message}")
                    e.printStackTrace()
                }
                return image
            }

            override fun onPostExecute(result: Bitmap?) {
                if( imageURL == viewHolder.coverImageURL)
                    viewHolder.coverImageView.setImageBitmap(result)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val bookView = inflater.inflate(R.layout.main_library_book_card, parent, false)
        return ViewHolder(bookView).apply {
            formatComponents["PDF"] = Pair(pdfImageView, pdfProgressBar)
            formatComponents["EPUB"] = Pair(epubImageView, epubProgressBar)
        }

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: Book = getItem(position)

        holder.coverImageView.setImageResource(android.R.color.transparent)
        val coverURL = StringBuilder(256)
        coverURL.append(BookLibrary.calibreServer)
        coverURL.append("/get/thumb/${book.id}/${book.libraryName}")
        holder.coverImageURL = coverURL.toString()
        DownloadImageFromInternet(holder).execute(
            holder.coverImageURL
        )
        logger.info("onBindViewHolder ${book.title} $coverURL")

        holder.titleTextView.text = book.title
        holder.authorTextView.text = book.authors
        var maxPage = ""
        logger.info("ReadPos: ${book.readPos}")

        var lastReadDevice = book.readPos.getLastDeviceOrDefault(MainActivity.DEVICE_NAME ?: EXTRA_DEVICE_NAME_DEFAULT)
        val readPos = book.readPos.getByDevice(lastReadDevice, EXTRA_DEVICE_NAME_DEFAULT)
        maxPage = if (readPos.maxPage > 0) {
            readPos.maxPage.toString()
        } else {
            book.pages.toString()
        }

        holder.pageCountTextView.text = if (readPos.lastReadPage > 0) {
            "${readPos.lastReadPage} / $maxPage"
        } else {
            maxPage
        }
        holder.lastReadTextView.text = "${lastReadDateFormat.format(book.lastModified)} on $lastReadDevice"
        holder.progressTextView.apply {
            val pos = readPos.getLastProgress()
            val sb = StringBuilder()
            val formatter = Formatter(sb)
            formatter.format(FORMAT_PROGRESS, pos)
            text = sb.toString()
        }

        holder.pdfImageView.setImageResource(R.drawable.ic_pdf_icon)

        holder.epubImageView.setImageResource(R.drawable.ic_epub_icon)

        for ((format, components) in holder.formatComponents) {
            if (downloading && format == downloadingFormat) {
                components.first.visibility = View.GONE
                components.second.visibility = View.VISIBLE
            } else if (book.formats.containsKey(format)) {
                components.first.visibility = View.VISIBLE
                components.second.visibility = View.GONE
                if (MainActivity.isBookFormatDownloaded(parentActivity.applicationContext, book, format))
                    setUnlocked(components.first)
                else
                    setLocked(components.first)
            } else {
                components.first.visibility = View.INVISIBLE
                components.second.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            rowClicked.value = position
        }

        logger.info("onBindViewHolder pos:$position text:${book.title}")
    }

    fun setLocked(v: ImageView) {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f) //0 means grayscale
        val cf = ColorMatrixColorFilter(matrix)
        v.colorFilter = cf
        v.imageAlpha = 128 // 128 = 0.5
    }

    fun setUnlocked(v: ImageView) {
        v.colorFilter = null
        v.imageAlpha = 255
    }

    fun markDownloading(position: Int, format: String) {
        downloading = true
        downloadingPosition = position
        downloadingFormat = format
    }

    fun clearDownloading() {
        downloading = false
    }

}