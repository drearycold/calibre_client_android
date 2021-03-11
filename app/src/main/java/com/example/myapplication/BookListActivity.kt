package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.widget.NestedScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.core.view.iterator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.*

/**
 * An activity representing a list of Pings. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [BookDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class BookListActivity : AppCompatActivity() {

    class SortInfo {
        var criteria = ""
        var iconId = 0
    }

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var bookListUpdated = MutableLiveData<Boolean>()
    private var bookListFiltered = MutableLiveData<Boolean>()

    private lateinit var bookListView: RecyclerView
    private lateinit var adapter: BookListAdapter
    private lateinit var bookListUpdatingProgressBar: ProgressBar

    private lateinit var fab: FloatingActionButton
    private lateinit var fabMenu: PopupMenu
    private lateinit var toolbar: Toolbar
    private lateinit var filterMenu: Menu
    private var sortCriteria = ORDER_BY_LASTREAD
    private var sortOrder = -1  //1: ascending, -1: descending
    private var sortIdCriteriaMap = HashMap<Int, SortInfo>(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_list)

        toolbar = findViewById(R.id.bookListToolbar)
        setSupportActionBar(toolbar)
        toolbar.title = title

        fab = findViewById(R.id.fab)
        fabMenu = PopupMenu(this, fab)
        fabMenu.inflate(R.menu.menu_book_list_filter)

        bookListView = findViewById(R.id.book_list)
        findViewById<FloatingActionButton>(R.id.fabUp).setOnClickListener {
            (bookListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        }
        findViewById<FloatingActionButton>(R.id.fabDown).setOnClickListener {
            bookListView.adapter?.let { it1 ->
                (bookListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    it1.itemCount-1, 0)
            }
        }

        if (findViewById<NestedScrollView>(R.id.book_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }

        bookListUpdatingProgressBar = findViewById(R.id.bookListUpdatingProgressBar)

        setupRecyclerView(
            findViewById(R.id.book_list), intent.getStringExtra(
                EXTRA_LIBRARY_NAME
            )
        )
    }

    private fun filterBookList() {
        bookListFiltered.value = false

        var filteredBooks = TreeSet(BookLibrary.bookComparator)
        val tmpBooks = TreeSet(BookLibrary.bookComparator)
        for (seriesMenuItem in toolbar.menu.findItem(R.id.seriesItemMenu).subMenu.iterator()) {
            if (seriesMenuItem.isChecked)
                BookLibrary.getSeries(seriesMenuItem.title.toString())?.let {
                    tmpBooks.addAll(it.books)
                }
        }
        if(tmpBooks.isNotEmpty()) {
            filteredBooks.addAll(tmpBooks)
            tmpBooks.clear()
        }
        
        for (tagMenuItem in toolbar.menu.findItem(R.id.tagsItemMenu).subMenu.iterator()) {
            if (tagMenuItem.isChecked)
                BookLibrary.getTag(tagMenuItem.title.toString())?.let {
                    tmpBooks.addAll(it.books)
                }
        }
        if(tmpBooks.isNotEmpty()) {
            filteredBooks.intersect(tmpBooks)
            tmpBooks.clear()
        }


        for (formatMenuItem in toolbar.menu.findItem(R.id.formatsItemMenu).subMenu.iterator()) {

        }

        val bookList = if( filteredBooks.isNotEmpty()) filteredBooks.toList(); else BookLibrary.books()

        val bookListSorted = when (sortCriteria to sortOrder) {
            ORDER_BY_TITLE to 1 -> bookList.sortedBy { it.title }
            ORDER_BY_TITLE to -1 -> bookList.sortedByDescending { it.title }
            ORDER_BY_LASTREAD to 1 -> bookList.sortedBy { it.lastModified }
            ORDER_BY_LASTREAD to -1 -> bookList.sortedByDescending { it.lastModified }
            ORDER_BY_PAGES to 1 -> bookList.sortedBy { it.pages }
            ORDER_BY_PAGES to -1 -> bookList.sortedByDescending { it.pages }
            ORDER_BY_PROGRESS to 1 -> bookList.sortedBy { it.readPos.getLastProgressPercent() }
            ORDER_BY_PROGRESS to -1 -> bookList.sortedByDescending { it.readPos.getLastProgressPercent() }
            else -> bookList
        }

        adapter.submitList(bookListSorted.toList()) {
            bookListFiltered.value = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            menuInflater.inflate(R.menu.menu_book_list_main, menu)

            val filterSubMenu = menu.findItem(R.id.bookListFilterAction).subMenu
            menuInflater.inflate(R.menu.menu_book_list_filter, filterSubMenu)

            val sortSubMenu = menu.findItem(R.id.bookListSortAction).subMenu
            menuInflater.inflate(R.menu.menu_book_list_sort, sortSubMenu)

            menu.findItem(R.id.bookListSortAction).setIcon(R.drawable.afc_ic_menu_sort_by_date_desc)

            sortIdCriteriaMap[R.id.lastModifiedItem] = SortInfo().apply {
                criteria = ORDER_BY_LASTREAD
                iconId = R.drawable.afc_ic_menu_sort_by_date_asc
            }
            sortIdCriteriaMap[-R.id.lastModifiedItem] = SortInfo().apply {
                criteria = ORDER_BY_LASTREAD
                iconId = R.drawable.afc_ic_menu_sort_by_date_desc
            }
            sortIdCriteriaMap[R.id.titleItem] = SortInfo().apply {
                criteria = ORDER_BY_TITLE
                iconId = R.drawable.afc_ic_menu_sort_by_name_asc
            }
            sortIdCriteriaMap[-R.id.titleItem] = SortInfo().apply {
                criteria = ORDER_BY_TITLE
                iconId = R.drawable.afc_ic_menu_sort_by_name_desc
            }
            sortIdCriteriaMap[R.id.progressItem] = SortInfo().apply {
                criteria = ORDER_BY_PROGRESS
                iconId = R.drawable.afc_button_sort_as
            }
            sortIdCriteriaMap[-R.id.progressItem] = SortInfo().apply {
                criteria = ORDER_BY_PROGRESS
                iconId = R.drawable.afc_button_sort_de
            }
            sortIdCriteriaMap[R.id.lengthItem] = SortInfo().apply {
                criteria = ORDER_BY_PAGES
                iconId = R.drawable.afc_ic_menu_sort_by_size_asc
            }
            sortIdCriteriaMap[-R.id.lengthItem] = SortInfo().apply {
                criteria = ORDER_BY_PAGES
                iconId = R.drawable.afc_ic_menu_sort_by_size_desc
            }

            updateFilterMenu()
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var needUpdate = false
        when (item.groupId) {
            R.id.seriesGroup, R.id.tagsGroup -> {
                item.isChecked = !item.isChecked
                needUpdate = true
            }
            R.id.sortGroup -> {
                sortIdCriteriaMap[item.itemId]?.let { sortInfo ->
                    if (sortCriteria == sortInfo.criteria) {
                        sortOrder = -sortOrder
                    } else {
                        sortCriteria = sortInfo.criteria
                    }
                    needUpdate = true
                }
                sortIdCriteriaMap[item.itemId * sortOrder]?.let { sortInfo ->
                    toolbar.menu.findItem(R.id.bookListSortAction).setIcon(sortInfo.iconId)
                }
            }
        }
        when (item.itemId) {
            R.id.clearFilterItem -> {
                for (seriesMenuItem in toolbar.menu.findItem(R.id.seriesItemMenu).subMenu.iterator()) {
                    seriesMenuItem.isChecked = false
                }
                for (tagMenuItem in toolbar.menu.findItem(R.id.tagsItemMenu).subMenu.iterator()) {
                    tagMenuItem.isChecked = false
                }
                needUpdate = true
            }
            R.id.bookListSearchAction -> {

            }
        }
        if (needUpdate) {
            filterBookList()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, libraryName: String?) {
        adapter = BookListAdapter(this)
        recyclerView.adapter = adapter
        adapter.rowClicked.observe(this, Observer { position ->
            onBookRowClicked(position)
        })

        bookListUpdated.observe(this, Observer { updated ->
            when (updated) {
                true -> updateBookListFinished()
                false -> updateBookListStarted()
            }
        })

        bookListFiltered.observe(this, Observer { updated ->
            when (updated) {
                true -> filterBookListFinished()
                false -> filterBookListStarted()
            }
        })

        libraryName?.let {
            MainActivity.bookViewModel?.updateBookListRV(
                adapter,
                bookListUpdated,
                libraryName,
                "",
                ORDER_BY_LASTREAD
            )
        }
    }

    private fun onBookRowClicked(position: Int) {
        val book = adapter.currentList[position]
        if (twoPane) {
            val fragment = BookDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(BookDetailFragment.ARG_ITEM_ID, book.id)
                }
            }
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.book_detail_container, fragment)
                .commit()
        } else {
            val intent = Intent(this, BookDetailActivity::class.java).apply {
                putExtra(BookDetailFragment.ARG_ITEM_ID, book.id)
                putExtra(EXTRA_REQUEST_CODE, REQUEST_BY_BOOK_LIST_ACTIVITY)
                putExtra(
                    EXTRA_DEVICE_NAME, intent.getStringExtra(
                        EXTRA_DEVICE_NAME
                    )
                )
                putExtra(
                    EXTRA_LIBRARY_NAME, intent.getStringExtra(
                        EXTRA_LIBRARY_NAME
                    )
                )
                putExtra(
                    EXTRA_CALIBRE_SERVER, intent.getStringExtra(
                        EXTRA_CALIBRE_SERVER
                    )
                )
            }
            startActivity(intent)
        }
    }

    @Synchronized
    private fun updateFilterMenu() {
        toolbar.menu.let { menu ->
            try {
                val seriesSubMenu = menu.findItem(R.id.seriesItemMenu).subMenu
                val tagsSubMenu = menu.findItem(R.id.tagsItemMenu).subMenu

                seriesSubMenu.clear()
                for (seriesInfo in BookLibrary.series()) {
                    seriesSubMenu.add(R.id.seriesGroup, Menu.NONE, Menu.NONE, seriesInfo.seriesName)
                        .apply { isCheckable = true }
                }
                tagsSubMenu.clear()
                for (tagInfo in BookLibrary.tags()) {
                    tagsSubMenu.add(R.id.tagsGroup, Menu.NONE, Menu.NONE, tagInfo.tagName)
                        .apply { isCheckable = true }
                }
            } catch( e: NullPointerException) {
                //menu has not been initialized
            }
        }
    }

    private fun updateBookListStarted() {
        bookListUpdatingProgressBar.visibility = View.VISIBLE
    }

    private fun updateBookListFinished() {
        BookLibrary.update(adapter.currentList)
        updateFilterMenu()
        bookListUpdatingProgressBar.visibility = View.GONE
    }

    private fun filterBookListStarted() {
        bookListUpdatingProgressBar.visibility = View.VISIBLE
    }

    private fun filterBookListFinished() {
        bookListUpdatingProgressBar.visibility = View.GONE
    }
}