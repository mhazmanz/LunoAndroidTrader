package com.hazman.lunoandroidtrader.data.luno

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Public (no-auth) Luno API service.
 *
 * This is deliberately separate from any private/authenticated Retrofit instance
 * used for balances and orders. Public endpoints do not need HMAC signing.
 */
interface LunoPublicService {

    /**
     * GET /api/1/ticker?pair=XXXYYY
     *
     * Latest ticker indicators for a single currency pair.
     */
    @GET("api/1/ticker")
    suspend fun getTicker(
        @Query("pair") pair: String
    ): Response<LunoTickerResponse>

    /**
     * GET /api/1/tickers
     *
     * Latest tickers for all active Luno exchanges.
     * Not yet used, but implemented for future multi-market logic.
     */
    @GET("api/1/tickers")
    suspend fun getTickers(): Response<LunoTickersResponse>

    companion object {

        // We keep a single shared instance for the whole app.
        @Volatile
        private var INSTANCE: LunoPublicService? = null

        fun getInstance(): LunoPublicService {
            val existing = INSTANCE
            if (existing != null) {
                return existing
            }

            synchronized(this) {
                val again = INSTANCE
                if (again != null) {
                    return again
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.luno.com/") // Official Luno REST base URL
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val created = retrofit.create(LunoPublicService::class.java)
                INSTANCE = created
                return created
            }
        }
    }
}
