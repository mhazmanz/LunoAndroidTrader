package com.hazman.lunoandroidtrader.data.luno

import com.google.gson.annotations.SerializedName

/**
 * Data models for Luno public ticker endpoints.
 *
 * These are intentionally simple and nullable to be robust against schema changes.
 * We only rely on a few core fields; everything else is optional.
 */
data class LunoTickerResponse(
    @SerializedName("pair")
    val pair: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long? = null,

    @SerializedName("bid")
    val bid: String? = null,

    @SerializedName("ask")
    val ask: String? = null,

    @SerializedName("last_trade")
    val lastTrade: String? = null,

    @SerializedName("rolling_24_hour_volume")
    val rolling24HourVolume: String? = null,

    @SerializedName("status")
    val status: String? = null
)

/**
 * Optional multi-ticker response for /api/1/tickers.
 * Not used yet, but defined for future multi-market strategies.
 */
data class LunoTickersResponse(
    @SerializedName("tickers")
    val tickers: List<LunoTickerResponse>? = null,

    @SerializedName("timestamp")
    val timestamp: Long? = null
)
