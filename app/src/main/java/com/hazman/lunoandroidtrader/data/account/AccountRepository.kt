package com.hazman.lunoandroidtrader.data.account

import com.hazman.lunoandroidtrader.data.luno.LunoApiClient
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.BalanceSnapshot

/**
 * AccountRepository is responsible for:
 * - Fetching raw balances from LunoApiClient
 * - Mapping them into domain-level AccountSnapshot
 *
 * For now:
 * - totalEquityMyr = MYR free balance only
 * - freeBalanceMyr = MYR balance
 * - Other assets are listed but not yet converted to MYR
 *
 * Later we will extend this to:
 * - Fetch ticker prices
 * - Convert crypto balances to MYR for more accurate equity
 */
class AccountRepository(
    private val lunoApiClient: LunoApiClient
) {

    suspend fun loadAccountSnapshot(): Result<AccountSnapshot> {
        return lunoApiClient.getBalances().map { balances ->
            var myrBalance = 0.0

            val balanceSnapshots = balances.map { lunoBalance ->
                if (lunoBalance.asset == "MYR") {
                    myrBalance = lunoBalance.balance
                }

                BalanceSnapshot(
                    asset = lunoBalance.asset,
                    amount = lunoBalance.balance
                )
            }

            // For now, treat total equity as MYR balance only.
            AccountSnapshot(
                totalEquityMyr = myrBalance,
                freeBalanceMyr = myrBalance,
                balances = balanceSnapshots
            )
        }
    }
}
