package com.example.myapplication

class BookReadingPosition {

    var deviceMap = HashMap<String, BookDeviceReadingPosition>()

    fun getByDevice(deviceName: String, readerName: String) : BookDeviceReadingPosition {
        return deviceMap.getOrPut(deviceName, { BookDeviceReadingPosition(readerName) })
    }

    fun getLastPageProgress(): Int {
        var lastReadPage = 0
        for(position in deviceMap.values) {
            if( lastReadPage < position.lastReadPage)
                lastReadPage = position.lastReadPage
        }
        return lastReadPage
    }

    fun getLastPageProgressDevice(): String? {
        var lastReadPage = 0
        var lastDeviceName: String? = null
        for((deviceName, position) in deviceMap) {
            if( lastReadPage < position.lastReadPage) {
                lastReadPage = position.lastReadPage
                lastDeviceName = deviceName
            }
        }
        return lastDeviceName
    }

    fun getLastPosition(): IntArray {
        var lastPosition = IntArray(3)
        for(position in deviceMap.values) {
            if( position.lastPosition == null )
                continue
            if( lastPosition[0] < position.lastPosition[0] ||
                lastPosition[0] == position.lastPosition[0] && lastPosition[1] < position.lastPosition[1] ||
                lastPosition[0] == position.lastPosition[0] && lastPosition[0] < position.lastPosition[0] && lastPosition[2] == position.lastPosition[2] ) {
                lastPosition = position.lastPosition
            }
        }
        return lastPosition
    }

    fun getLastDeviceOrDefault(deviceName: String): String {
        var lastProgress = 0.0
        var lastDevice: String? = null
        for((deviceName, position) in deviceMap) {
            if( position.maxPage > 0 && position.lastReadPage > 0) {
                val progress = position.lastReadPage * 1.0 / position.maxPage
                if( lastProgress < progress) {
                    lastProgress = progress
                    lastDevice = deviceName
                }
            }
        }
        return lastDevice ?: deviceName
    }

    fun getLastProgressPercent(): Double {
        var lastProgress = 0.0
        for((_, position) in deviceMap) {
            if( position.maxPage > 0 && position.lastReadPage > 0) {
                val progress = position.lastReadPage * 1.0 / position.maxPage
                if( lastProgress < progress) {
                    lastProgress = progress
                }
            }
        }
        return lastProgress
    }

    override fun toString(): String {
        return deviceMap.toString()
    }
}