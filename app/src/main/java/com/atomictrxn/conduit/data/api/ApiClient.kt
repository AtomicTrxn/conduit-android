package com.atomictrxn.conduit.data.api

import com.atomictrxn.conduit.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(
        baseUrl: String,
        apiKey: String,
    ): OpenWebUIService {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $apiKey")
                            .build()
                    chain.proceed(request)
                }
                .apply {
                    if (BuildConfig.DEBUG) {
                        addInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            },
                        )
                    }
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWebUIService::class.java)
    }
}
