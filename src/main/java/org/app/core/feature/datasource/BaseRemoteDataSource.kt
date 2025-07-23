package org.app.core.feature.datasource

import org.app.core.feature.model.FailureStatus
import org.app.core.feature.model.ResponseData
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class BaseRemoteDataSource {
    
    suspend fun <T> safeApiCall(apiCall: suspend () -> T): ResponseData<T> {
        try {
            return ResponseData.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            return when (throwable) {
                is HttpException -> {
                    ResponseData.Failure(FailureStatus.API_FAIL, throwable.code(), throwable.localizedMessage)
                }
                
                is ConnectException,  is UnknownHostException, is SocketTimeoutException -> {
                    ResponseData.Failure(FailureStatus.NO_INTERNET, message = throwable.localizedMessage)
                }
                
                else -> {
                    ResponseData.Failure(FailureStatus.OTHER, message = throwable.localizedMessage)
                }
            }
        }
    }
}