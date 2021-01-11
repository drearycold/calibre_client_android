package com.example.myapplication

class BookReadingPosition {

    private var deviceMap = HashMap<String, BookDeviceReadingPosition>()

    fun getByDevice(deviceName: String) : BookDeviceReadingPosition {
        return deviceMap.getOrPut(deviceName, { BookDeviceReadingPosition() })
    }
}