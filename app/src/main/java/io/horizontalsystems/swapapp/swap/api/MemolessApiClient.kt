package io.horizontalsystems.swapapp.swap.api

import io.horizontalsystems.swapapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton Retrofit client for the public memoless API. Separate from [SwapApiClient] because it
 * targets a different host and needs no `x-api-key`.
 */
object MemolessApiClient {

    private const val BASE_URL = "https://swap.unstoppable.money/memoless/api/v1/"

    val api: MemolessApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MemolessApi::class.java)
    }
}
