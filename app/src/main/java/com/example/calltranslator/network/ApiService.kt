package com.example.calltranslator.network

import com.example.calltranslator.BuildConfig
import com.example.calltranslator.model.TranslationRequest
import com.example.calltranslator.model.TranslationResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("translate")
    suspend fun translateText(
        @Body request: TranslationRequest,
        @Header("X-App-Secret") secret: String = BuildConfig.BACKEND_SECRET
    ): TranslationResponse
}
