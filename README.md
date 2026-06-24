# Elite Smart Trading Bot

A JavaFX desktop trading bot with intelligent currency scanning and smart time optimization.

## Features

✅ **Multi-Currency Scanner** - Scans EUR/USD, GBP/USD, USD/JPY, BTC/USD, ETH/USD  
✅ **Smart Time Selection** - Automatically adjusts trade duration based on signal confidence  
✅ **Flexible Modes** - Full auto, manual currency, manual time, or full manual  
✅ **Real-time Simulation** - Realistic price movements with trends and momentum  
✅ **Premium Dark Theme** - Modern, clean interface  

## Requirements

- Java 17 or higher
- Internet connection (for downloading dependencies)

## Quick Start

### Option 1: Gradle (Recommended)
```bash
# If Gradle is installed:
gradle run

# Or use the wrapper script:
gradlew.bat run
```

### Option 2: Maven
```bash
mvn javafx:run
```

### Option 3: Runner Script
```bash
run.bat
```

## Project Structure

```
src/main/java/com/elitebot/
├── Main.java                # Entry point
├── model/
│   ├── TradingSignal.java   # Signal data (CALL/PUT)
│   ├── ScanResult.java      # Currency scan result
│   └── Trade.java           # Trade tracking
├── strategy/
│   ├── SimpleStrategy.java  # RSI + MA analysis
│   ├── MultiCurrencyScanner.java
│   └── TimeOptimizer.java
├── simulation/
│   └── PriceSimulator.java  # Multi-currency price sim
├── state/
│   └── BotState.java        # Central state manager
└── ui/
    └── TradingDashboard.java # JavaFX GUI
```

## Mode Combinations

| Mode | Currency | Time | Description |
|------|----------|------|-------------|
| Full Auto | AUTO | AUTO | Bot picks best currency + optimal time |
| Semi-Auto | MANUAL | AUTO | You pick currency, bot picks time |
| Semi-Auto | AUTO | MANUAL | Bot picks currency, you pick time |
| Full Manual | MANUAL | MANUAL | Full control |

## Signal Logic

- **RSI < 30** + MA Bullish → CALL (Strong)
- **RSI > 70** + MA Bearish → PUT (Strong)
- **Confidence ≥85%** → 5 min (higher payout)
- **Confidence ≥70%** → 3 min
- **Confidence ≥60%** → 1 min
