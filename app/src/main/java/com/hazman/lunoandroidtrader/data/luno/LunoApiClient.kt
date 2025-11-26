package com.hazman.lunoandroidtrader.data.luno

import com.hazman.lunoandroidtrader.data.local.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET

/**
 * Luno API client (Phase 0: read-only test of /api/1/balance).
 *
 * We:
 * - Call /api/1/balance using Retrofit + Scalars converter (String response)
 * - Parse JSON manually using org.json
 * - Return List<LunoBalance> wrapped in Result
 */
class LunoApiClient(
    private val storage: AppStorage
) {

    private val service: LunoService

    init {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC logs HTTP method + URL + status code.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val key = storage.getLunoReadOnlyKey()
                val secret = storage.getLunoReadOnlySecret()

                // If no credentials, send request without Authorization,
                // and let the API fail with an error.
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
            .addConverterFactory(ScalarsConverterFactory.create()) // we receive raw String
            .build()

        service = retrofit.create(LunoService::class.java)
    }

    /**
     * Test call: fetch account balances using read-only key.
     *
     * Returns:
     * - Result.success(list of balances) on success
     * - Result.failure(exception) on error
     */
    suspend fun testGetBalances(): Result<List<LunoBalance>> = withContext(Dispatchers.IO) {
        try {
            val key = storage.getLunoReadOnlyKey()
            val secret = storage.getLunoReadOnlySecret()

            if (key.isNullOrEmpty() || secret.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Luno read-only API key or secret is not set.")
                )
            }

            val response: Response<String> = service.getBalances()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Luno API error: HTTP ${response.code()} ${response.message()}")
                )
            }

            val bodyString = response.body()
            if (bodyString.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Luno API error: empty response body.")
                )
            }

            // Parse JSON manually
            val json = JSONObject(bodyString)

            // Luno sometimes uses "error" / "error_code" even with HTTP 200
            val errorMessage = json.optString("error", null)
            val errorCode = json.optString("error_code", null)
            if (!errorMessage.isNullOrEmpty() || !errorCode.isNullOrEmpty()) {
                val msg = buildString {
                    if (!errorMessage.isNullOrEmpty()) append(errorMessage)
                    if (!errorCode.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" (code: ")
                        else append("Error code: ")
                        append(errorCode)
                        if (errorMessage.isNotEmpty()) append(")")
                    }
                }
                return@withContext Result.failure(IllegalStateException("Luno API error: $msg"))
            }

            val balancesArray: JSONArray = json.optJSONArray("balance")
                ?: return@withContext Result.success(emptyList())

            val balances = mutableListOf<LunoBalance>()
            for (i in 0 until balancesArray.length()) {
                val item = balancesArray.optJSONObject(i) ?: continue
                val asset = item.optString("asset", "")
                val balanceStr = item.optString("balance", "0")
                val reservedStr = item.optString("reserved", "0")
                val unconfirmedStr = item.optString("unconfirmed", "0")

                val balance = balanceStr.toDoubleOrNull() ?: 0.0
                val reserved = reservedStr.toDoubleOrNull() ?: 0.0
                val unconfirmed = unconfirmedStr.toDoubleOrNull() ?: 0.0

                if (asset.isNotBlank()) {
                    balances.add(
                        LunoBalance(
                            asset = asset,
                            balance = balance,
                            reserved = reserved,
                            unconfirmed = unconfirmed
                        )
                    )
                }
            }

            Result.success(balances.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Retrofit interface for Luno API.
 * We use Scalars converter, so the response type is String.
 */
interface LunoService {

    @GET("api/1/balance")
    suspend fun getBalances(): Response<String>
}

/**
 * Our internal balance model for Luno.
 */
data class LunoBalance(
    val asset: String,
    val balance: Double,
    val reserved: Double,
    val unconfirmed: Double
)
