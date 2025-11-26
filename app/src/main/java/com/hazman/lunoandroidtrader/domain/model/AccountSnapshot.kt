package com.hazman.lunoandroidtrader.domain.model

/**
 * Represents a snapshot of the account at a given time.
 *
 * Later this will be filled using:
 * - Luno balances
 * - Market prices (to compute total equity in MYR)
 * - Open positions and P&L
 */
data class AccountSnapshot(
    val totalEquityMyr: Double,
    val freeBalanceMyr: Double,
    val balances: List<BalanceSnapshot>
)

/**
 * Individual asset balance in domain layer form.
 *
 * For now we only store:
 * - asset code ("MYR", "XBT", "ETH", etc.)
 * - total amount (including reserved/unconfirmed if needed)
 */
data class BalanceSnapshot(
    val asset: String,
    val amount: Double
)
