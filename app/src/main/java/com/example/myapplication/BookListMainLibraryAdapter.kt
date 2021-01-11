package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.AsyncTask
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.util.logging.Logger


const val FORMAT_PROGRESS = "%.2f%%"

class BookListMainLibraryAdapter(private val mainActivity: MainActivity) :
    RecyclerView.Adapter<BookListMainLibraryAdapter.ViewHolder>() {

    private val logger: Logger = Logger.getLogger("BookListAdapter")

    var downloading: Boolean = false
    var downloadingPosition: Int = 0
    var downloadingFormat: String = ""

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImageView: ImageView = itemView.findViewById(R.id.imageMainLibraryBookCover)
        val titleTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookTitle)
        val authorTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookAuthor)
        val pageCountTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookPageCount)
        val progressTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookProgress)
        val pdfImageView: ImageView = itemView.findViewById(R.id.imagePdfIcon)
        val epubImageView: ImageView = itemView.findViewById(R.id.imageEpubIcon)
        val pdfProgressBar: ProgressBar = itemView.findViewById(R.id.pbPdfDownload)
        val epubProgressBar: ProgressBar = itemView.findViewById(R.id.pbEpubDownload)

        var formatComponents = HashMap<String, Pair<ImageView, ProgressBar>>()
    }

    companion object {
        private class DownloadImageFromInternet(var imageView: ImageView) :
            AsyncTask<String, Void, Bitmap?>() {
            override fun doInBackground(vararg urls: String): Bitmap? {
                val imageURL = urls[0]
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
                imageView.setImageBitmap(result)
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
        val book: Book = mainActivity.mBooks[position]

        holder.titleTextView.text = book.title
        holder.authorTextView.text = book.authors
        if (book.pages > 0) {
            holder.pageCountTextView.text = book.pages.toString()
        } else {
            holder.pageCountTextView.text =
                book.readPos.getByDevice(mainActivity.deviceName).maxPage.toString()
        }
        holder.progressTextView.apply {
            val readPos = book.readPos.getByDevice(mainActivity.deviceName)
            val pos = readPos.getLastProgress()
            var sb = StringBuilder()
            var formatter = Formatter(sb)
            formatter.format(FORMAT_PROGRESS, pos)
            text = sb.toString()
        }

        var coverURL = StringBuilder(256)
        coverURL.append(mainActivity.findViewById<EditText>(R.id.editCalibreServer).text.toString())
        coverURL.append("/get/thumb/${book.id}/${book.libraryName}")
        DownloadImageFromInternet(holder.coverImageView).execute(
            coverURL.toString()
        )

        holder.pdfImageView.setImageResource(R.drawable.ic_pdf_icon)

        holder.epubImageView.setImageResource(R.drawable.ic_epub_icon)

        for ((format, components) in holder.formatComponents) {
            if (downloading && format == downloadingFormat) {
                components.first.visibility = View.GONE
                components.second.visibility = View.VISIBLE
            } else if (book.formats.containsKey(format)) {
                components.first.visibility = View.VISIBLE
                components.second.visibility = View.GONE
                if (mainActivity.isBookFormatDownloaded(book, format))
                    setUnlocked(components.first)
                else
                    setLocked(components.first)
            } else {
                components.first.visibility = View.INVISIBLE
                components.second.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            mainActivity.onBookRowClicked(position)
        }
        holder.itemView.setOnLongClickListener {
            mainActivity.onBookRowLongClicked(position)
        }

        logger.info("onBindViewHolder pos:$position text:${book.title}")
    }

    override fun getItemCount(): Int {
        return mainActivity.mBooks.size
    }

    fun replaceAllBooks(books: List<Book>) {
        mainActivity.mBooks.clear()
        mainActivity.mBooks.addAll(books)
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