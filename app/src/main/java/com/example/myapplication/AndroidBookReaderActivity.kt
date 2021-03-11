package com.example.myapplication

import android.content.Intent
import android.util.Log
import android.widget.TextView
import com.github.axet.bookreader.activities.MainActivity
import com.github.axet.bookreader.widgets.FBReaderView

class AndroidBookReaderActivity : MainActivity() {

    class Metadata {
        var created = 0L
        var last = 0L
        var authors = ""
        var title = ""
        var position = IntArray(3)
    }

    override fun onBackPressed() {
        var intent = Intent()
        intent.putExtras(this.intent)

        if (!intent.getBooleanExtra(EXTRA_SHELF_MODE, false)) {
            val fbReaderView = findViewById<FBReaderView>(R.id.main_view)
            val pluginView = fbReaderView.pluginview

            if (pluginView != null) {
                intent.putExtra(EXTRA_READER_PAGE_NUMBER, pluginView.current.pageNumber)
                intent.putExtra(
                    EXTRA_READER_PAGE_NUMBER_MAX, pluginView.current.pagesCount
                )
                intent.putExtra(
                    EXTRA_READER_POSITION,
                    intArrayOf(pluginView.current.pageNumber, 0, 0)
                )
            } else {
                val fbView = fbReaderView.app.currentView as FBReaderView.CustomView
                val pagePosition = fbView.pagePosition()
                intent.putExtra(
                    EXTRA_READER_PAGE_NUMBER,
                    pagePosition.Current
                )
                intent.putExtra(
                    EXTRA_READER_PAGE_NUMBER_MAX,
                    pagePosition.Total
                )

                val position = fbReaderView.position
                intent.putExtra(
                    EXTRA_READER_POSITION,
                    intArrayOf(position.paragraphIndex, position.elementIndex, position.charIndex)
                )

                Log.i("PagePosition", "${pagePosition.Current} / ${pagePosition.Total}")
                Log.i(
                    "Index",
                    "${position.paragraphIndex} : ${position.elementIndex} : ${position.charIndex}"
                )
            }

            super.onBackPressed()
        }

        setResult(RESULT_OK, intent)
        finish()

        super.onBackPressed()
    }

}