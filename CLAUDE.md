# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin-based cryptocurrency trading bot that supports multiple exchanges (Binance, ByBit, Huobi, Gate.io, BitMAX). The bot implements various trading strategies including grid trading, trend-following, and arbitrage, with comprehensive backtesting and emulation capabilities.

### Core Library: XChange

This project is built on top of the **XChange library** (https://github.com/knowm/XChange), a Java library providing a streamlined API for interacting with cryptocurrency exchanges. XChange serves as the foundation for:

- **Exchange Integration**: Standardized API access across different exchanges
- **Order Management**: Unified order placement, cancellation, and status tracking  
- **Market Data**: Real-time and historical price data, order books, and trade information
- **Account Management**: Balance queries and account information retrieval
- **WebSocket Streams**: Real-time data feeds for price updates and order events

**Key XChange Dependencies in use:**
- `xchange-core`: Core XChange functionality and interfaces
- `xchange-binance`: Binance exchange integration 
- `xchange-stream-binance`: Binance WebSocket streaming
- `xchange-huobi`: Huobi exchange support
- `xchange-gateio`: Gate.io exchange integration
- `xchange-stream-huobi`: Huobi WebSocket streaming

The project extends XChange's capabilities with custom implementations for exchanges not fully supported (like ByBit) and adds advanced trading logic, strategy execution, and risk management on top of the base exchange connectivity.

## Build and Development Commands

### Build System
- **Build project**: `./gradlew build`
- **Clean build**: `./gradlew clean build`
- **Run tests**: `./gradlew test`
- **Run single test**: `./gradlew test --tests "ClassName.methodName"`
- **Main class**: `bot.Main` (Spring Boot application)

### Docker Operations
- **Start application**: `docker-compose up -d`
- **Stop application**: `docker-compose down`
- **Rebuild after code changes**: 
  ```bash
  ./gradlew clean build
  docker-compose down
  docker-compose build --no-cache
  docker-compose up -d
  ```
- **View logs**: `docker-compose logs -f`
- **Access container**: `docker-compose exec trade-bot sh`

### Running Locally
- **Main Spring Boot app**: `./gradlew bootRun` (REST API on port 8080)
- **Console version**: Run `src/main/kotlin/bot/MainConsole.kt`
- **Arbitrage bot**: Run `src/main/kotlin/bot/MainArbitrage3x.kt`
- **Candlestick collector**: Run `src/main/kotlin/bot/MainCollectCandlestickData.kt`
- **Tests with logging**: Tests use `src/test/resources/logback-test.xml`

### Emulation & Optimization
- **Run parameter optimization**: `./emulate_scripts/emulate.sh`
  - Executes Python script to optimize grid trading parameters
  - Generates result files and CSV reports in current directory
  - View results via `emulate_scripts/index.html` dashboard
- **Check balance requirements**: `./emulate_scripts/check_balance.sh`
- **Test balance endpoint**: `./emulate_scripts/test_balance_endpoint.sh`

## Architecture Overview

### Core Components

#### 1. Algorithm Framework (`bot.trade.exchanges.Algorithm`)
- Abstract base class for all trading strategies
- Handles order management, error handling, and retry logic
- Provides common functionality for price formatting, balance checks
- Manages WebSocket connections and message processing

#### 2. Trading Strategies
- **AlgorithmTrader**: Main strategy with trend detection, DCA, and hedging
- **AlgorithmGrid**: Grid trading strategy with customizable parameters
- **AlgorithmBobblesIndicator**: Indicator-based trading strategy

#### 3. Exchange Clients (`bot.trade.exchanges.clients`)
- **Client interface**: Common abstraction for all exchanges
- **ClientByBit**: ByBit futures and spot trading
- **ClientBinance/ClientBinanceFutures**: Binance integration
- **ClientHuobi, ClientGate**: Other exchange implementations
- **TestClient**: For backtesting and emulation

#### 4. Stream Processing (`bot.trade.exchanges.clients.stream`)
- Real-time WebSocket data processing
- **StreamByBitFuturesImpl**: ByBit futures WebSocket
- **StreamBinanceImpl/StreamBinanceFuturesImpl**: Binance WebSocket streams
- Handles order book updates, trade data, and kline data

#### 5. Configuration System
- **BotSettings**: Trading bot configuration (JSON-based)
- **BotSettingsTrader**: Trend-following strategy settings
- **BotSettingsGrid**: Grid trading settings
- Configuration files in `exchangeBots/` directory

#### 6. Communication Layer (`bot.communicator.Communicator`)
- Orchestrates bot lifecycle and command processing
- Abstracts communication interface (Telegram/Console/REST)
- **BotType**: Enum defining bot interfaces (TELEGRAM, CONSOLE, REST)
- **TelegramBot**: Telegram integration for remote control
- **ConsoleBot**: Direct console interaction
- Manages multiple trading bots simultaneously
- Routes commands to appropriate Algorithm instances

#### 7. REST API (`bot.trade.rest_controller.MainController`)
- **POST /emulate**: Run backtesting with `BotEmulateParams`
- **POST /get_necessary_balance**: Calculate required balance for grid parameters
- **POST /endpoint/trade**: Send trading commands
- **GET /positions**: Retrieve current positions and bot settings
- **GET /orders?botName={name}**: View order history for specific bot
- **GET /error**: Main page with bot navigation links

### Data Flow Architecture

1. **Message Processing**: Commands processed by `Communicator.onUpdate()`
2. **Bot Lifecycle**: Load → Configure → Start → Monitor → Stop
3. **Order Management**: Algorithm → Client → Exchange → Status Updates
4. **Data Collection**: WebSocket → Stream → Queue → Algorithm
5. **Emulation Flow**:
   - Python script (`optimize_params.py`) → REST API (`/emulate`) → Communicator
   - `TestClientFileData` replays historical candlestick data
   - Algorithm executes strategy against historical data
   - `TestBalance` tracks simulated profit/loss
   - Results exported to JSON/CSV for analysis

### Key Features

#### Trading Capabilities
- Multiple exchange support with unified API
- Real-time order book and trade data processing
- Advanced order types (market, limit, stop-loss, take-profit)
- Position management and risk controls
- Hedging and counter-distance strategies

#### Strategy Features
- **Trend Detection**: RSI + Hull Moving Average (HMA) combination
- **DCA (Dollar Cost Averaging)**: Configurable entry/exit levels
- **Grid Trading**: Automated buy/sell orders at regular intervals
- **Arbitrage**: Multi-exchange price difference exploitation

#### Backtesting & Emulation
- Historical data replay through `TestClientFileData`
- Configurable test parameters via `BotEmulateParams`
- CSV export capabilities for analysis
- Performance metrics and profit/loss calculation

## Development Patterns

### Configuration Management
- Settings stored as JSON in `exchangeBots/{botName}/settings.json`
- Exchange credentials in `common.conf` (use environment variables)
- Separate configs for each exchange's candlestick collection

### Error Handling
- Comprehensive retry logic for API calls
- Graceful degradation on connection failures
- Telegram notifications for critical errors
- Detailed logging with custom file processors

### Testing Strategy
- Unit tests for core algorithms (`AlgorithmTraderTest`, `AlgorithmGridTest`)
- Stream processing tests (`StreamByBitFuturesImplTest`)
- Utility function tests (`KlineConverterTest`, `TrendCalculatorTest`)
- Database service tests (`ServiceTest`)

### Data Persistence
- H2 database for order tracking and historical data
- JPA entities for orders, positions, and bot state
- File-based configuration and logging
- CSV export for analysis and reporting

## Important Notes

### Security Considerations
- Never commit API keys or secrets to the repository
- Use environment variables for sensitive configuration
- Telegram bot tokens and chat IDs should be externalized

### Performance Considerations
- WebSocket connections are persistent and managed per exchange
- Queue-based message processing to prevent blocking
- Configurable retry counts and intervals for resilience
- Memory-efficient candlestick data processing

### Development Guidelines
- Follow existing Kotlin conventions and patterns
- Use the existing Client interface for new exchange integrations
- Extend Algorithm class for new trading strategies
- Add comprehensive tests for new functionality
- Use the existing logging and error handling patterns
- **IMPORTANT: After every code change, add a short description to `docs/VERSIONS.md`** following the existing format (version number, date, problem/solution, affected files)
- **DO NOT run `./gradlew build` or `./gradlew clean build` after every completed task.** Gradle build is already included in all deploy scripts (`deploy_frontend.sh`, `quick_deploy_backend.sh`, `Dockerfile.backend`). Running it manually wastes time. Only run build explicitly when you need to verify compilation fixes or run specific tests with `./gradlew test --tests "..."`.

### XChange Integration Guidelines
- **New Exchange Support**: For exchanges supported by XChange, extend the existing Client interface and utilize XChange's standardized APIs
- **Custom Exchange Implementation**: For exchanges not in XChange (like ByBit), implement custom REST and WebSocket clients following the project's patterns in `io.bybit.api.*`
- **XChange Exceptions**: Handle XChange-specific exceptions (`ExchangeException`) in addition to standard error handling
- **API Rate Limits**: Respect exchange rate limits using XChange's built-in rate limiting where available
- **Market Data**: Leverage XChange's standardized market data structures for consistency across exchanges