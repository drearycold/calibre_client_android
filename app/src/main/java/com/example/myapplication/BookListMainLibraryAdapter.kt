package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.logging.Logger

class BookListMainLibraryAdapter (private val mainActivity: MainActivity): RecyclerView.Adapter<BookListMainLibraryAdapter.ViewHolder>() {

    private val logger : Logger = Logger.getLogger("BookListAdapter")

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImageView: ImageView = itemView.findViewById(R.id.imageMainLibraryBookCover)
        val titleTextView: TextView = itemView.findViewById(R.id.textMainLibraryBookTitle)
        val authorTextView : TextView = itemView.findViewById(R.id.textMainLibraryBookAuthor)
        val pageCountTextView : TextView = itemView.findViewById(R.id.textMainLibraryBookPageCount)
        val progressTextView : TextView = itemView.findViewById(R.id.textMainLibraryBookProgress)
    }

    private inner class DownloadImageFromInternet(var imageView: ImageView) : AsyncTask<String, Void, Bitmap?>() {
        override fun doInBackground(vararg urls: String): Bitmap? {
            val imageURL = urls[0]
            var image: Bitmap? = null
            try {
                val `in` = java.net.URL(imageURL).openStream()
                image = BitmapFactory.decodeStream(`in`)
            }
            catch (e: Exception) {
                logger.warning("Error Message ${e.message}")
                e.printStackTrace()
            }
            return image
        }
        override fun onPostExecute(result: Bitmap?) {
            imageView.setImageBitmap(result)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val bookView = inflater.inflate(R.layout.main_library_book_card, parent, false)
        return ViewHolder(bookView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: Book = mainActivity.mBooks[position]

        holder.titleTextView.text = book.title
        holder.authorTextView.text = book.authors
        holder.pageCountTextView.text = book.pages.toString()
        holder.progressTextView.text = "0%"

        var coverURL = StringBuilder(256)
        coverURL.append( mainActivity.findViewById<EditText>(R.id.editCalibreServer).text.toString() )
        coverURL.append( "/get/thumb/${book.id}/${book.libraryName}")
        DownloadImageFromInternet(holder.coverImageView).execute(
            coverURL.toString()
        )

        holder.itemView.setOnClickListener {
            mainActivity.onBookRowClicked(book)
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
}