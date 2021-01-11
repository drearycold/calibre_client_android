package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.io.*
import java.net.URL
import java.util.logging.Logger
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

private const val TAG = "NetworkFragment"
internal const val URL_KEY = "UrlKey"
internal const val CMD_KEY = "CmdKey"
internal const val POST_KEY = "PostKey"
internal const val BINARY_KEY = "BinaryKey"

internal const val CALIBRE_CMD_Get_Library_List = "GetLibraryList"
internal const val CALIBRE_CMD_Get_Library_Books = "GetLibraryBooks"
internal const val CALIBRE_CMD_Get_Book = "GetBook"
internal const val CALIBRE_CMD_Get_Book_Cover = "GetBookCover"
internal const val CALIBRE_CMD_Set_Metadata = "SetMetadata"
internal const val CALIBRE_CMD_Get_Book_File = "GetBookFile"

open class NetworkFragment : Fragment() {
    private var callback: DownloadCallback<DownloadCallbackData>? = null
    private var downloadTask: DownloadTask? = null
    private var urlString: String? = null
    private var cmdString: String? = null

    private var logger: Logger = Logger.getLogger("NetworkFragment")

    companion object {
        /**
         * Static initializer for NetworkFragment that sets the URL of the host it will be
         * downloading from.
         */
        fun getInstance(fragmentManager: FragmentManager): NetworkFragment {
            val networkFragment = NetworkFragment()
            fragmentManager.beginTransaction().add(networkFragment, TAG).commit()
            return networkFragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Host Activity will handle callbacks from task.
        callback = context as? DownloadCallback<DownloadCallbackData>
    }

    override fun onDetach() {
        super.onDetach()
        // Clear reference to host Activity to avoid memory leak.
        callback = null
    }

    override fun onDestroy() {
        // Cancel task when Fragment is destroyed.
        cancelDownload()
        super.onDestroy()
    }

    /**
     * Start non-blocking execution of DownloadTask.
     */
    fun startDownload() {
        urlString = arguments?.getString(URL_KEY)
        cmdString = arguments?.getString(CMD_KEY)

        cancelDownload()
        callback?.also {
            downloadTask = DownloadTask(it, this).apply {
                execute(urlString)
            }
        }
    }

    /**
     * Cancel (and interrupt if necessary) any ongoing DownloadTask execution.
     */
    fun cancelDownload() {
        downloadTask?.cancel(true)
    }

    /**
     * Implementation of AsyncTask designed to fetch data from the network.
     */
    private class DownloadTask(callback: DownloadCallback<DownloadCallbackData>, fragment: NetworkFragment)
        : AsyncTask<String, Int, DownloadTask.Result>() {

        private var logger: Logger = Logger.getLogger("DownloadTask")

        private var callback: DownloadCallback<DownloadCallbackData>? = null

        private var fragment: NetworkFragment? = fragment

        init {
            setCallback(callback)
        }

        internal fun setCallback(callback: DownloadCallback<DownloadCallbackData>) {
            this.callback = callback
        }

        /**
         * Wrapper class that serves as a union of a result value and an exception. When the download
         * task has completed, either the result value or exception can be a non-null value.
         * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
         */
        internal class Result {
            var resultValue: String? = null
            var exception: Exception? = null
            var dataValue: ByteBuffer? = null
            var code: Int? = 0

            constructor(resultValue: String) {
                this.resultValue = resultValue
            }

            constructor(exception: Exception) {
                this.exception = exception
            }

            constructor(dataValue: ByteBuffer) {
                this.dataValue = dataValue
            }
        }

        /**
         * Cancel background network operation if we do not have network connectivity.
         */
        override fun onPreExecute() {
            if (callback != null) {
                val networkInfo = callback?.getActiveNetworkInfo()
                if (networkInfo?.isConnected == false
                        || networkInfo?.type != ConnectivityManager.TYPE_WIFI
                        && networkInfo?.type != ConnectivityManager.TYPE_MOBILE) {
                    // If no connectivity, cancel task and update Callback with null data.
                    callback?.updateFromDownload(null)
                    cancel(true)
                }
            }
        }

        /**
         * Defines work to perform on the background thread.
         */
        override fun doInBackground(vararg urls: String): DownloadTask.Result? {
            logger.info("enter doInBackground")
            var result: Result? = null
            if (!isCancelled && urls.isNotEmpty()) {
                val urlString = urls[0]
                result = try {
                    val url = URL(urlString)
                    downloadUrl(url) ?: throw IOException("No response received.")
                } catch (e: Exception) {
                    Result(e)
                }

            }
            return result
        }

        /**
         * Updates the DownloadCallback with the result.
         */
        override fun onPostExecute(result: Result?) {
            callback?.apply {
                var data = DownloadCallbackData()
                data.code = result?.code ?: 0
                data.command = fragment?.cmdString
                result?.exception?.also { exception ->
                    data.result = exception.message
                    updateFromDownload(data)
                    return
                }
                result?.resultValue?.also { resultValue ->
                    data.result = resultValue
                    updateFromDownload(data)
                    return
                }
                finishDownloading(data)
            }
        }

        /**
         * Override to add special behavior for cancelled AsyncTask.
         */
        override fun onCancelled(result: Result) {}

        /**
         * Given a URL, sets up a connection and gets the HTTP response body from the server.
         * If the network request is successful, it returns the response body in String form. Otherwise,
         * it will throw an IOException.
         */
        @Throws(IOException::class)
        private fun downloadUrl(url: URL): Result? {
            var connection: HttpURLConnection? = null
            return try {
                connection = (url.openConnection() as? HttpURLConnection)
                logger.info("enter downloadUrl, url:$url")

                connection?.run {
                    // Timeout for reading InputStream arbitrarily set to 3000ms.
                    readTimeout = 3000
                    // Timeout for connection.connect() arbitrarily set to 3000ms.
                    connectTimeout = 3000
                    // Already true by default but setting just in case; needs to be true since this request
                    // is carrying an input (response) body.
                    doInput = true
                    // Open communications link (network traffic occurs here).
                    val postStr = fragment?.arguments?.getString(POST_KEY)
                    if(postStr != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        val postBytes = postStr.toByteArray(StandardCharsets.UTF_8)
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Content-Length", postBytes.size.toString())
                        try {
                            val outputStream: DataOutputStream = DataOutputStream(connection.outputStream)
                            outputStream.write(postBytes)
                            outputStream.flush()
                        } catch (exception: java.lang.Exception) {

                        }
                    } else {
                        connection.requestMethod = "GET"
                        connect()
                    }
                    //publishProgress(CONNECT_SUCCESS)
                    logger.info("enter connection.run, connection:$this")
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("HTTP error code: $responseCode")
                    }
                    // Retrieve the response body as an InputStream.
                    //publishProgress(GET_INPUT_STREAM_SUCCESS, 0)
                    inputStream?.let { stream ->
                        when (fragment?.arguments?.getBoolean(BINARY_KEY, false)) {
                            true -> {
                                // Converts Stream to String with max length of 500.
                                readStreamBytes(stream, 5000000)?.let {
                                    Result(it)
                                }
                            }
                            else -> {
                                // Converts Stream to String with max length of 500.
                                readStream(stream, 5000000)?.let {
                                    Result(it)
                                }
                            }
                        }
                    }?.also {
                        it.code = connection.responseCode
                    }
                }
            } finally {
                // Close Stream and disconnect HTTPS connection.
                connection?.inputStream?.close()
                connection?.disconnect()
            }
        }

        /**
         * Converts the contents of an InputStream to a String.
         */
        @Throws(IOException::class, UnsupportedEncodingException::class)
        fun readStream(stream: InputStream, maxReadSize: Int): String? {
            val reader: Reader? = InputStreamReader(stream, "UTF-8")
            val rawBuffer = CharArray(maxReadSize)
            val buffer = StringBuffer()
            var readSize: Int = reader?.read(rawBuffer) ?: -1
            var maxReadBytes = maxReadSize
            while (readSize != -1 && maxReadBytes > 0) {
                if (readSize > maxReadBytes) {
                    readSize = maxReadBytes
                }
                buffer.append(rawBuffer, 0, readSize)
                maxReadBytes -= readSize
                readSize = reader?.read(rawBuffer) ?: -1
            }
            return buffer.toString()
        }

        @Throws(IOException::class, UnsupportedEncodingException::class)
        fun readStreamBytes(stream: InputStream, maxReadSize: Int): ByteBuffer? {
            val rawBuffer = ByteArray(maxReadSize)
            val buffer = ByteBuffer.allocate(maxReadSize)
            var readSize: Int = stream?.read(rawBuffer) ?: -1
            var maxReadBytes = maxReadSize
            while (readSize != -1 && maxReadBytes > 0) {
                if (readSize > maxReadBytes) {
                    readSize = maxReadBytes
                }

                buffer.put(rawBuffer, 0, readSize)
                maxReadBytes -= readSize
                readSize = stream?.read(rawBuffer) ?: -1
            }
            return buffer
        }
    }

}
