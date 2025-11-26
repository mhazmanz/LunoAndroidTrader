## Phase 1 – Risk Configuration (Completed)

- Added secure storage fields in `AppStorage`:
    - `riskPerTradePercent`
    - `dailyLossLimitPercent`
    - `maxTradesPerDay`
    - `cooldownMinutesAfterLoss`
    - `liveTradingEnabled`

- Created domain models:
    - `RiskConfig` in `domain/model/RiskConfig.kt`
    - `AccountSnapshot` and `BalanceSnapshot` in `domain/model/AccountSnapshot.kt`

- Implemented `RiskManager` (`domain/risk/RiskManager.kt`) with:
    - `computeMaxRiskPerTradeMyr`
    - `computePositionSizeMyr`
    - `canOpenNewTrade` (currently basic, to be extended later with daily P&L tracking and cooldown)

- Extended `SettingsScreen`:
    - Added editable risk fields:
        - Risk per trade (%)
        - Daily loss limit (%)
        - Max trades per day
        - Cooldown after loss (minutes)
    - Still includes:
        - Luno read-only API key/secret
        - Telegram bot token / chat ID
        - Live trading toggle
    - Buttons:
        - "Test Luno Connection"
        - "Send Telegram Test"
