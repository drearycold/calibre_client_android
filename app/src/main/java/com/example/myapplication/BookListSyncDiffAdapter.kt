package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import java.util.logging.Logger

class BookListSyncDiffAdapter (private var mBooks: MutableList<DisplayMessageActivity.SyncDiffBook>): RecyclerView.Adapter<BookListSyncDiffAdapter.ViewHolder>() {

    private val logger = Logger.getLogger("BookListAdapter")
    private val dateFormat = java.text.DateFormat.getDateInstance()

    inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textSyncDiffBookTitle)
        val authorsTextView: TextView = itemView.findViewById(R.id.textSyncDiffBookAuthors)
        val lastModifiedTextView: TextView = itemView.findViewById(R.id.textSyncDiffBookLastModified)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val bookView = inflater.inflate(R.layout.sync_diff_book_row_item, parent, false)
        return ViewHolder(bookView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book: DisplayMessageActivity.SyncDiffBook = mBooks[position]
        holder.titleTextView.text = book.bookRemote?.title ?: book.bookLocal?.title
        holder.authorsTextView.text = book.bookRemote?.authors ?: book.bookLocal?.authors
        holder.lastModifiedTextView.text = "R ${dateFormat.format(book.bookRemote?.lastModified ?: Date())} vs L ${dateFormat.format(book.bookLocal?.lastModified ?: Date())}"


        logger.info("onBindViewHolder pos:$position title:${holder.titleTextView.text}")
    }

    override fun getItemCount(): Int {
        return mBooks.size
    }

    fun replaceBooks(books: MutableList<DisplayMessageActivity.SyncDiffBook>) {
        mBooks = books
    }
}