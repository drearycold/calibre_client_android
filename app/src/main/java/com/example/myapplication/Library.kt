package com.example.myapplication

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity()
data class Library  (
    @PrimaryKey
    val name: String,

    val bookCount: Int = 0
) : Comparable<Library> {
    override fun toString(): String {
        return name;
    }

    override fun compareTo(other: Library): Int {
        return this.name.compareTo(other.name)
    }
}