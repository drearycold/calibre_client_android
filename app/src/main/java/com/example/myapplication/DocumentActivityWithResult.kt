package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.TextView
import androidx.core.net.toFile
import androidx.core.widget.addTextChangedListener
import com.artifex.mupdf.viewer.DocumentActivity
import java.util.logging.Logger

class DocumentActivityWithResult: DocumentActivity() {

    private var logger: Logger = Logger.getLogger("DocumentActivityWithResult")

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.extras?.getInt(EXTRA_READER_PAGE_NUMBER)?.also { pageNumber ->
            if( pageNumber > 0) {
                val prefs = getPreferences(MODE_PRIVATE)
                with(prefs.edit()) {
                    putInt("page" + intent.data!!.pathSegments.last(), pageNumber - 1)
                    apply()
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    override fun onBackPressed() {
        var intent = Intent()
        intent.putExtras(this.intent)
        val pageNumber = findViewById<TextView>(R.id.pageNumber).text
        val sep = pageNumber.indexOf(" / ")

        intent.putExtra(EXTRA_READER_PAGE_NUMBER, pageNumber.substring(0, sep).toInt())
        setResult(RESULT_OK, intent)
        finish()

        super.onBackPressed()
    }
}