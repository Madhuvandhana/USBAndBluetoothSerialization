package com.example.usbserialization.data

import com.example.usbserialization.data.local.FormData
import com.example.usbserialization.data.local.FormDataDao
import com.example.usbserialization.domain.FormDataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author madhu.kumar
 */

@Singleton
class FormDataRepositoryImpl @Inject constructor(
    private val formDataDao: FormDataDao
) : FormDataRepository {

    override fun getAllFormData(): Flow<List<FormData>> {
        return formDataDao.getAllFormData()
    }

    override suspend fun insertFormData(formData: FormData) {
        formDataDao.insertFormData(formData)
    }
}
