package com.example.usbserialization.data.local

/**
 * @author madhu.kumar
 */
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FormData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun formDataDao(): FormDataDao
}
