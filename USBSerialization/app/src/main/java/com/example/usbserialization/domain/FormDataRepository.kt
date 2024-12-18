package com.example.usbserialization.domain
import com.example.usbserialization.data.local.FormData
import kotlinx.coroutines.flow.Flow
/**
 * @author madhu.kumar
 * Created 9/30/24 at 1:03 PM
 */
interface FormDataRepository {
    fun getAllFormData(): Flow<List<FormData>>
    suspend fun insertFormData(formData: FormData)
}
