package com.example.myapplication

import androidx.room.*

@Dao
interface LibraryDAO {
    @Query("SELECT * FROM library")
    suspend fun getAll(): List<Library>

    @Query("SELECT * FROM library WHERE name = (:libraryName)")
    suspend fun findByName(libraryName:String): Library

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg libraries: Library)

    @Delete
    suspend fun delete(library: Library)


}