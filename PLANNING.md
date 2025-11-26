# LunoAndroidTrader – System Planning & Design

> Single source of truth for requirements, architecture, strategy, and evolution.
> Any change to how the system behaves MUST be reflected here.

---

## 0. Project Overview

- Platform: **Android**, minimum SDK 24 (Android 7.0 – Xiaomi Mi Max 2 compatible).
- Technology: **Kotlin + Jetpack Compose + MVVM**.
- Execution model: Runs directly on the phone, no external servers.
- Exchange: **Luno** (Malaysian region).
- Messaging: **Telegram Bot** (for alerts and summaries).

### High-Level Goals

1. Start with **RM 50** on Luno.
2. Aim for **realistic, sustainable growth** towards RM 800–1000+ per month as a side income (no guarantees).
3. Focus on:
    - **Positive expectancy** strategies.
    - **Strict risk management**:
        - Fixed % risk per trade.
        - Daily loss limit.
        - Max trades per day.
        - Cooldown after hitting limits.
4. Bot must:
    - Be able to **decide autonomously** when to open/close trades under clear rules.
    - Always assume the **user may change things manually in Luno**.
    - Fail safe, not “fail big”.

---

## 1. Architecture

### 1.1 Layers

1. **Data Layer**
    - `LunoPrivateApi` (balance, orders, etc.)
    - `LunoPublicApi` (tickers, orderbook, trades – no auth required)
    - `TelegramApi`
    - Local storage (SharedPreferences / DataStore) for:
        - API keys
        - Telegram token & chat id
        - Risk settings
        - Strategy state snapshot (optional later)

2. **Domain Layer**
    - **Risk Engine**
        - Computes:
            - Max risk per trade (RM).
            - Daily loss limit (RM).
            - Whether we can take another trade today.
            - Cooldown logic.
    - **Strategy Engine**
        - Reads:
            - Account snapshot.
            - Market data (ticker / later candles).
            - Risk configuration.
            - Existing simulated / real positions.
        - Emits:
            - Proposed actions (OPEN LONG, CLOSE LONG, HOLD, SKIP).
            - Suggested entry, SL, TP.
            - Text explanation log.
    - **Simulation Engine (Paper Trading)**
        - Applies strategy decisions to internal “paper positions”.
        - Computes realised/unrealised P&L.
        - Never touches the real Luno account.

3. **App Layer (ViewModels + UI)**
    - `SettingsViewModel` + Settings screen:
        - Manage API keys and test connectivity.
        - Manage risk configuration.
    - `DashboardViewModel` + Dashboard screen:
        - Show balances and risk overview.
        - Show last paper trades and strategy decisions.
        - Expose “Run paper strategy once” actions.
    - Later: background worker / periodic runner.

4. **Notification Layer**
    - `TelegramRepository` sends:
        - Connectivity tests.
        - New trade opened (paper or real).
        - Trade closed.
        - Daily summary.
        - Warnings (daily loss limit reached, cooldown active, API errors).

---

## 2. Phased Implementation Plan

### Phase 0 – Project skeleton ✅ DONE

- Basic Android project with Compose.
- Two main tabs: **Dashboard** and **Settings**.
- Simple theme and navigation.
- Verified app runs on emulator/phone.

### Phase 1 – Connectivity & Config ✅ PARTIALLY DONE

1. **Settings screen**
    - Fields:
        - Luno API key + secret.
        - Telegram bot token + chat ID.
        - Risk per trade (%).
        - Daily loss limit (%).
        - Max trades per day.
        - Cooldown after loss (minutes).
    - Scrollable layout, with **Save** and test buttons.
    - Validation + error messages.

2. **Connectivity tests**
    - Telegram test: send a simple message to confirm config.
    - Luno test: call `/api/1/balance` to fetch balances.
    - Both working and confirmed on device.

3. **Dashboard basics**
    - Shows:
        - Approx total equity in MYR.
        - Free MYR balance.
        - Asset balances (XBT, MYR, XLM, etc.).
    - Shows:
        - Risk per trade (% and RM).
        - Daily loss limit (%).
        - Max trades per day.
        - Cooldown config.
    - Single test button:
        - `Run Paper Strategy Once (Fake Price)` – **currently uses fake price input** and creates a mock paper trade for XBTMYR.

Status:
- Phase 1 is **operational** with real balances and fake-price strategy test.

---

## 3. NEW: Phase 1.5 – Real Paper Strategy Engine (Ticker) 🚧 IN PROGRESS

> Objective: Replace the “fake price” with **real Luno market data** for a single, synchronous paper-strategy step. Still **no real orders** to Luno.

### 3.1 New components added in this phase

1. **Luno Public Ticker Client**
    - Standalone Retrofit service (no auth needed) calling:
        - `GET /api/1/ticker?pair=XBTMYR` (and later other pairs).
    - Data model: `LunoTickerResponse` with fields:
        - `pair`
        - `timestamp`
        - `bid`
        - `ask`
        - `last_trade`
        - `rolling_24_hour_volume`
        - `status`
    - Handles:
        - Network errors.
        - JSON parsing issues.
        - null/invalid fields.

2. **RealPaperStrategyEngine (domain layer)**
    - Stateless object that:
        - Fetches the **current ticker** for a specified pair (starting with `XBTMYR`).
        - Accepts as input:
            - Approx equity (RM).
            - Free MYR balance.
            - Risk per trade (%).
            - Daily loss limit (%).
            - Max trades per day.
            - Cooldown minutes.
            - Existing paper trades (for future use).
        - Computes:
            - Risk per trade in RM.
            - A conservative position size.
            - Entry, SL, TP based on simple volatility / percentage offsets.
        - Returns:
            - Action: `OPEN_LONG`, `SKIP` (we can extend with `CLOSE_LONG`, `REDUCE` later).
            - A `SimulatedTrade` object (if `OPEN_LONG`).
            - Human-readable log lines explaining:
                - Price used.
                - Position size.
                - Risk in RM.
                - Reasons to trade or skip.

    - Risk behaviour (initial simple version):
        - Never risk more than the configured risk % of **approx equity**.
        - If computed position size is effectively zero (because equity is tiny), it will **SKIP** instead of forcing a nonsense trade.
        - This is still a **very simple strategy** – it’s to get real data flowing.
            - Later phases will add:
                - Basic trend filters.
                - Momentum conditions.
                - Multi-timeframe filters.
                - Portfolio-style decision across multiple markets.

3. **No UI changes yet in this phase**
    - The dashboard still calls the **fake price** button.
    - We will wire the new engine into the dashboard in the **next step**, when we have the relevant ViewModel and Composable code in context.

---

## 4. Future Phases (not implemented yet)

### Phase 2 – Full Paper Strategy Loop

- Replace “fake price” button with:
    - `Run Paper Strategy Once (Real Price)` – calls ticker and strategy engine.
    - Show:
        - Last decision.
        - Open simulated trades with P&L.
- Add tracking of:
    - Trades per day.
    - Daily realised P&L.
    - Daily loss limit & cooldown enforcement (paper side).

### Phase 3 – Multi-market Paper Strategy

- Support multiple Luno pairs (e.g., `XBTMYR`, `ETHMYR`, `XRPMYR`, etc. where available).
- Strategy chooses:
    - Whether to use free MYR for a new trade.
    - Which market has more favourable conditions.
    - Whether to skip because risk budget is spent.
- Portfolio logic:
    - Can open multiple trades if within risk budget.
    - Can decide that a new opportunity is better and **skip** if current capital is already optimally allocated.

### Phase 4 – Background Runner

- Schedule periodic strategy runs (e.g., every 5–15 minutes) subject to Android background limitations.
- Ensure:
    - Resilience to network drops and app restarts.
    - State is persisted and restored safely.
    - Telegram notifications for major events.

### Phase 5 – (Optional) Real Trading

- Only after:
    - Extensive paper testing.
    - Clear understanding of drawdowns and behaviour.
- Introduce:
    - Live order placement on Luno with conservative size.
    - Additional safety rails:
        - Global kill switch.
        - “Dry run” mode logging what **would** be done.

---

## 5. Risk Management Principles (Always Apply)

1. Never risk more than configured **% per trade**.
2. Never exceed **daily loss limit** – even on paper.
3. Cooldown after significant loss or sequence of losses.
4. Assume:
    - Markets can gap.
    - Orders may not fill.
    - API can fail.
5. User remains responsible for:
    - Deposits/withdrawals.
    - Enabling live trading.
    - Monitoring and adjusting risk levels.

---

## 6. Change Log

- **2025-11-27 – Phase 1.5 (Real paper engine – ticker)**
    - Added: Luno public ticker client (no auth).
    - Added: RealPaperStrategyEngine (stateless, paper-only).
    - Status: Backend code in place. UI still using fake-price button; wiring will be done next.
