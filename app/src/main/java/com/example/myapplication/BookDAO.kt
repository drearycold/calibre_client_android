package com.example.myapplication

import androidx.room.*

@Dao
interface BookDAO {
    @Query("SELECT * FROM book")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM book WHERE libraryName = (:libraryName)")
    suspend fun findByLibrary(libraryName: String): List<Book>

    @Query("SELECT * FROM book WHERE title = (:title)")
    suspend fun findByName(title: String): Book

    @Query("SELECT * FROM book WHERE id = (:id) AND libraryName = (:libraryName)")
    suspend fun find(id: Int, libraryName: String): Book

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg books: Book)

    @Delete
    suspend fun delete(book: Book)
}