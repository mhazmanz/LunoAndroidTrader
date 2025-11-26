package com.hazman.lunoandroidtrader.data.luno

import com.hazman.lunoandroidtrader.data.local.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import com.squareup.moshi.Json

/**
 * Luno API client (Phase 0: read-only test).
 *
 * This client is used to:
 * - Test that the read-only API key/secret are valid
 * - Fetch account balances
 *
 * It uses HTTP Basic auth: key:secret
 */
class LunoApiClient(
    storage: AppStorage
) {

    private val apiKey: String? = storage.getLunoReadOnlyKey()
    private val apiSecret: String? = storage.getLunoReadOnlySecret()

    private val service: LunoService

    init {
        val logging = HttpLoggingInterceptor().apply {
            // For now we log BASIC details. Later we can adjust for production.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val key = apiKey
                val secret = apiSecret

                // If no API credentials are set, just proceed without Authorization.
                if (key.isNullOrEmpty() || secret.isNullOrEmpty()) {
                    return@addInterceptor chain.proceed(original)
                }

                val credential = Credentials.basic(key, secret)
                val newRequest = original.newBuilder()
                    .header("Authorization", credential)
                    .build()

                chain.proceed(newRequest)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.luno.com/") // Luno REST API base URL
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        service = retrofit.create(LunoService::class.java)
    }

    /**
     * Test call: fetch account balances from Luno using read-only key.
     *
     * Returns:
     * - Result.success(list of balances) on success
     * - Result.failure(exception) on error
     */
    suspend fun testGetBalances(): Result<List<LunoBalance>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrEmpty() || apiSecret.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Luno read-only API key or secret is not set.")
                )
            }

            val response = service.getBalances()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Luno API error: HTTP ${response.code()} ${response.message()}")
                )
            }

            val body = response.body()
            if (body == null) {
                return@withContext Result.failure(
                    IllegalStateException("Luno API error: empty response body.")
                )
            }

            // Luno often returns errors as HTTP 200 with an "error" field
            val errorMessage = body.error ?: body.errorCode
            if (!errorMessage.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Luno API error: $errorMessage")
                )
            }

            val balances = body.balance ?: emptyList()
            Result.success(balances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Retrofit interface for Luno API.
 */
interface LunoService {

    @GET("api/1/balance")
    suspend fun getBalances(): Response<LunoBalanceResponse>
}

/**
 * Top-level response for /api/1/balance
 */
data class LunoBalanceResponse(
    @Json(name = "balance") val balance: List<LunoBalance>? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_code") val errorCode: String? = null
)

/**
 * Individual asset balance (simplified).
 */
data class LunoBalance(
    @Json(name = "asset") val asset: String? = null,
    @Json(name = "balance") val balance: String? = null,
    @Json(name = "reserved") val reserved: String? = null,
    @Json(name = "unconfirmed") val unconfirmed: String? = null
)
