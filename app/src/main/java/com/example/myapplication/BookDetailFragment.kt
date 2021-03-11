package com.example.myapplication

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.CollapsingToolbarLayout
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.get
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.collections.sortWith

/**
 * A fragment representing a single Book detail screen.
 * This fragment is either contained in a [BookListActivity]
 * in two-pane mode (on tablets) or a [BookDetailActivity]
 * on handsets.
 */
class BookDetailFragment : Fragment() {

    private var logger: Logger = Logger.getLogger("BookDetailFragment")

    /**
     * The dummy content this fragment is presenting.
     */
    var item: Book? = null
    private var progressSpinner: Spinner? = null
    private var progressSpinnerAdapter: ArrayAdapter<ProgressItem>? = null
    private lateinit var rootView: View
    private lateinit var progressRadioGroup: RadioGroup
    private lateinit var formatRadioGroup: RadioGroup
    private val formatIdMap = TreeMap<String, Int>()
    private val formatIdButtonMap = TreeMap<Int, RadioButton>()
    private val formatIdBase = 12345

    class ProgressItem {
        var deviceName = ""
        var deviceReadingPosition = BookDeviceReadingPosition(EXTRA_READER_NAME_DEFAULT)

        override fun toString(): String {
            return "Page ${deviceReadingPosition.lastReadPage + 1} on $deviceName"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.book_detail, container, false)

        arguments?.let {
            if (it.containsKey(ARG_ITEM_ID)) {
                // Load the dummy content specified by the fragment
                // arguments. In a real-world scenario, use a Loader
                // to load content from a content provider.
                item = BookDetailActivity.getBook(it)
                activity?.findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout)?.title =
                    item?.title
            }
        }

        populateView()

        return rootView
    }

    fun populateView() {
        item?.let {
            progressRadioGroup = rootView.findViewById(R.id.bookDetailProgressRadioGroup)
            formatRadioGroup = rootView.findViewById(R.id.bookDetailFormatRadioGroup)

            rootView.findViewById<TextView>(R.id.bookDetailTitle).text = it.title
            rootView.findViewById<TextView>(R.id.bookDetailAuthors).text = it.authors
            rootView.findViewById<TextView>(R.id.bookDetailComments).text =
                Html.fromHtml(it.comments)
            rootView.findViewById<TextView>(R.id.bookDetailSeriesText).text = it.series
            rootView.findViewById<Button>(R.id.bookDetailUpdateButton).setOnClickListener { view ->
                onRefreshButtonClicked(view)
            }


            formatRadioGroup.setOnCheckedChangeListener { group, checkedId ->
                val format = formatIdButtonMap[checkedId]?.text.toString()
                val activity = activity as BookDetailActivity
                format?.let { f ->
                    context?.let{ c ->
                        if (MainActivity.isBookFormatDownloaded(c.applicationContext, it, f)) {
                            activity.fab.setImageResource(R.drawable.fbreader_bw)
                        } else {
                            activity.fab.setImageResource(R.drawable.ic_list_download)
                        }
                    }
                }
            }

            formatRadioGroup.clearCheck()
            for(button in formatIdButtonMap.values ) {
                formatRadioGroup.removeView(button)
            }
            formatIdButtonMap.clear()
            formatIdMap.clear()

            for (format in it.formats.keys) {
                val formatButton = RadioButton(context)
                formatButton.text = format
                formatButton.id = formatIdBase + formatRadioGroup.childCount
                formatIdMap[format] = formatButton.id
                formatIdButtonMap[formatButton.id] = formatButton
                formatRadioGroup.addView(formatButton)
            }

            formatRadioGroup.check(formatIdButtonMap.firstKey())

            val deviceName = arguments?.getString(EXTRA_DEVICE_NAME) ?: EXTRA_DEVICE_NAME_DEFAULT
            val deviceReadingPosition = it.readPos.deviceMap[deviceName]
            val lastDeviceName = it.readPos.getLastPageProgressDevice()
            val lastDeviceReadingPosition = it.readPos.deviceMap[lastDeviceName]

            progressRadioGroup.clearCheck()
            rootView.findViewById<RadioButton>(R.id.bookDetailProgressContinueButton).isChecked = true
            if (deviceReadingPosition == null || deviceReadingPosition.lastReadPage == 0) {
                rootView.findViewById<RadioButton>(R.id.bookDetailProgressContinueButton).text =
                    getString(
                        R.string.start_a_new_reading
                    )

                if (lastDeviceReadingPosition == null || lastDeviceReadingPosition.lastReadPage == 0) {
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).isEnabled =
                        false
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).visibility =
                        View.GONE
                } else {
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).isEnabled =
                        true
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).visibility =
                        View.VISIBLE
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).text =
                        "Continue at ${lastDeviceReadingPosition.getLastProgressPercent()} (page ${lastDeviceReadingPosition.lastReadPage} / ${lastDeviceReadingPosition.maxPage}) from $lastDeviceName"
                }
            } else {
                rootView.findViewById<RadioButton>(R.id.bookDetailProgressContinueButton).text =
                    "Continue at ${deviceReadingPosition.getLastProgressPercent()} (page ${deviceReadingPosition.lastReadPage} / ${deviceReadingPosition.maxPage + 1})"
                if (deviceReadingPosition == lastDeviceReadingPosition || lastDeviceReadingPosition == null) {
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).isEnabled =
                        false
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).visibility =
                        View.GONE
                } else {
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).isEnabled =
                        true
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).visibility =
                        View.VISIBLE
                    rootView.findViewById<RadioButton>(R.id.bookDetailProgressLastButton).text =
                        "Continue at ${lastDeviceReadingPosition.getLastProgressPercent()} (page ${lastDeviceReadingPosition.lastReadPage} / ${lastDeviceReadingPosition.maxPage}) from $lastDeviceName"
                }
            }

            val progressList = ArrayList<ProgressItem>()
            for ((name, readingPosition) in it.readPos.deviceMap) {
                val progressItem = ProgressItem()
                progressItem.deviceName = name
                progressItem.deviceReadingPosition = readingPosition
                if (progressItem.deviceReadingPosition.lastReadPage > 0)
                    progressList.add(progressItem)
            }

            progressList.sortWith(object : Comparator<ProgressItem> {
                override fun compare(o1: ProgressItem?, o2: ProgressItem?): Int {
                    o1?.let { i1 ->
                        o2?.let { i2 ->
                            val diff =
                                i2.deviceReadingPosition.lastReadPage - i1.deviceReadingPosition.lastReadPage
                            if (diff != 0)
                                return diff
                            return i1.deviceName.compareTo(i2.deviceName)
                        }
                    }

                    return 0
                }
            })
            rootView.findViewById<RadioButton>(R.id.bookDetailProgressPickupButton).isEnabled =
                progressList.isNotEmpty()

            if (progressSpinner == null) {
                progressSpinner =
                    rootView.findViewById(R.id.bookDetailProgressPickupSpinner)
                progressSpinner?.isEnabled = false
                context?.let { c ->
                    progressSpinnerAdapter = ArrayAdapter<ProgressItem>(
                        c,
                        android.R.layout.simple_spinner_item,
                        progressList
                    ).also { adapter ->
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        progressSpinner?.adapter = adapter
                    }
                }
                progressRadioGroup.setOnCheckedChangeListener { group, checkedId ->
                    if (checkedId == R.id.bookDetailProgressContinueButton) {
                        progressSpinner?.isEnabled = false
                    } else if (checkedId == R.id.bookDetailProgressPickupButton) {
                        progressSpinner?.isEnabled = true
                    }
                }
            } else {
                progressSpinnerAdapter?.clear()
                progressSpinnerAdapter?.addAll(progressList)
            }
        }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_ITEM_ID = "item_id"
    }

    private fun onRefreshButtonClicked(view: View) {
        logger.info("enter sendMessage()")

        arguments?.let { it ->
            val calibreServer = it.getString(EXTRA_CALIBRE_SERVER)
            val libraryName = it.getString(EXTRA_LIBRARY_NAME)

            val url = "$calibreServer/cdb/cmd/list/0?library_id=$libraryName"
            val args = Bundle()
            args.putString(URL_KEY, url)
            args.putString(CMD_KEY, CALIBRE_CMD_Get_Book)
            args.putString(POST_KEY, "[[\"all\"],\"\",\"\",\"id:${item?.id}\",-1]")
            // Execute the async download.
            if (activity is BookDetailActivity) {
                (activity as BookDetailActivity).networkFragment?.apply {
                    arguments = args
                    startDownload()
                }
            }
        }
    }

    fun getSelectedReadingPosition(): BookDeviceReadingPosition? {
        item?.let {
            if (progressRadioGroup.checkedRadioButtonId == R.id.bookDetailProgressContinueButton) {
                return it.readPos.getByDevice(
                    arguments?.getString(EXTRA_DEVICE_NAME) ?: EXTRA_DEVICE_NAME_DEFAULT,
                    EXTRA_READER_NAME_DEFAULT
                )
            }
            if (progressRadioGroup.checkedRadioButtonId == R.id.bookDetailProgressLastButton) {
                return it.readPos.deviceMap[it.readPos.getLastPageProgressDevice()]
            }
            if (progressRadioGroup.checkedRadioButtonId == R.id.bookDetailProgressPickupButton) {
                return (progressSpinner?.selectedItem as ProgressItem).deviceReadingPosition
            }
        }
        return null
    }

    fun getSelectedFormat(): String? {
        return formatIdButtonMap[formatRadioGroup.checkedRadioButtonId]?.text.toString()
    }

}