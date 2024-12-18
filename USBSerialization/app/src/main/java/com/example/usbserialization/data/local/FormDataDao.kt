package com.example.usbserialization.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
/**
 * @author madhu.kumar
 */

@Dao
interface FormDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormData(formData: FormData)

    @Query("SELECT * FROM form_data")
    fun getAllFormData(): Flow<List<FormData>>
}
