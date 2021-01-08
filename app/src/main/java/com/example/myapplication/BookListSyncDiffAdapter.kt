package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.logging.Logger

class BookListSyncDiffAdapter (private var mBooks: MutableList<Book>): RecyclerView.Adapter<BookListSyncDiffAdapter.ViewHolder>() {

    private val logger : Logger = Logger.getLogger("BookListAdapter")

    inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
        val titleTextView: TextView = itemView.findViewById<TextView>(R.id.textSyncDiffBookTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val bookView = inflater.inflate(R.layout.sync_diff_book_row_item, parent, false)
        return ViewHolder(bookView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: Book = mBooks[position]
        val textView = holder.titleTextView
        textView.text = book.title
        logger.info("onBindViewHolder pos:$position text:${textView.text}")
    }

    override fun getItemCount(): Int {
        return mBooks.size
    }

    fun replaceBooks(books: MutableList<Book>) {
        mBooks = books
    }
}