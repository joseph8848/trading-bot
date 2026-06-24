package com.elitebot.state;

import com.elitebot.model.*;
import com.elitebot.simulation.PriceSimulator;
import com.elitebot.data.RealMarketDataProvider;
import com.elitebot.strategy.*;
import com.elitebot.learning.LearningEngine;
import com.elitebot.learning.LossPreventionSystem;
import com.elitebot.intelligence.MarketIntelligence;
import com.elitebot.intelligence.NewsAnalyzer;
import com.elitebot.intelligence.TrendPatternAnalyzer;
import com.elitebot.intelligence.TradingBrain;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Central state management for the trading bot.
 * Handles all mode combinations and coordinates between components.
 */
public class BotState {
    
    // Components
    private final PriceSimulator priceSimulator;
    private final RealMarketDataProvider realMarketData;  // NEW: Real market data
    private boolean useRealMarketData = true;  // NEW: Toggle for real vs simulated
    private final MultiCurrencyScanner scanner;
    private final TimeOptimizer timeOptimizer;
    private final LearningEngine learningEngine;
    private final LossPreventionSystem lossPreventionSystem;
    private final AdvancedStrategy advancedStrategy;
    private final MarketIntelligence marketIntelligence;
    private final NewsAnalyzer newsAnalyzer;
    private final TrendPatternAnalyzer trendAnalyzer;
    private final TradingBrain tradingBrain;
    
    // State flags
    private boolean botRunning = false;
    private boolean autoCurrencyEnabled = true;  // Default: AUTO
    private boolean autoTimeEnabled = true;       // Default: AUTO
    private boolean autoTradeEnabled = false;     // Default: OFF for safety
    private boolean recoveryModeEnabled = false;  // Double after loss to recover
    private boolean multiCurrencyMode = false;    // Trade multiple currencies
    
    // Manual selections (used when auto is OFF)
    private String selectedCurrency = "EUR/USD";
    private int selectedTimeMinutes = 3;
    private double tradeAmount = 15.0;   // Default $15 (base amount)
    private double balance = 1000.0;     // Starting balance
    private double startingBalance = 1000.0;  // For profit/loss calc
    
    // DYNAMIC TRADE AMOUNT SETTINGS
    private boolean autoDynamicAmountEnabled = true;  // Let bot decide trade size
    private double minTradeAmount = 5.0;   // Minimum $5 per trade
    private double maxTradeAmount = 50.0;  // Maximum $50 per trade
    
    // Risk Management - BOTH DISABLED for learning
    private double stopLoss = 99999.0;   // DISABLED for overnight testing (was $100)
    private double takeProfit = 99999.0; // DISABLED for overnight testing (was $200)
    private boolean stoppedByRisk = false;
    private String stopReason = "";
    
    // Recovery mode tracking
    private double lossToRecover = 0.0;  // Amount to recover after loss
    private boolean needsRecovery = false;
    private int recoveryAttempts = 0;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;  // Max 3 doubles
    
    // Current trading state
    private TradingSignal currentSignal = null;
    private ScanResult currentBestOpportunity = null;
    
    // Trade tracking (MULTI-TRADE SUPPORT)
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private final List<Trade> activeTrades = new CopyOnWriteArrayList<>();  // Multiple simultaneous trades
    private static final int MAX_SIMULTANEOUS_TRADES = 3;  // Max 3 trades at once
    private Trade activeTrade = null;  // Legacy support - points to most recent
    
    // POCKET OPTION LIVE TRADING
    private boolean pocketOptionEnabled = true;  // ENABLED: Send trades to Pocket Option
    private int pocketOptionTradeCount = 0;
    
    // EXTENSION TRADE DELAY - Prevent overlapping trades
    private long lastExtensionTradeTime = 0;
    private static final long MIN_EXTENSION_TRADE_DELAY_MS = 5000;  // 5 seconds between trades
    
    // Listeners
    private final List<Consumer<BotState>> stateListeners = new CopyOnWriteArrayList<>();
    
    // Stats
    private int totalTrades = 0;
    private int winCount = 0;
    
    // Analysis timing - slower for better trend detection
    private long lastAnalysisTime = 0;
    private static final long ANALYSIS_INTERVAL_MS = 2000;  // Analyze every 2 seconds
    private static final long MIN_TRADE_INTERVAL_MS = 10000; // Min 10 seconds between trades
    private long lastTradeTime = 0;
    
    // Startup protection - don't trade until system is warmed up
    private long startupTime = 0;
    private static final long STARTUP_WARMUP_MS = 30000; // 30 seconds warmup before trading
    
    public BotState() {
        this.priceSimulator = new PriceSimulator();
        this.realMarketData = new RealMarketDataProvider();
        this.realMarketData.setFallbackSimulator(priceSimulator);
        this.realMarketData.setUseFallback(true);  // Use simulated data as fallback
        this.scanner = new MultiCurrencyScanner(priceSimulator);
        
        // WIRE REAL MARKET DATA INTO SCANNER - This makes signals based on real prices!
        this.scanner.setRealMarketDataProvider(realMarketData);
        this.scanner.setUseRealData(useRealMarketData);  // Enable/disable based on toggle
        
        this.timeOptimizer = new TimeOptimizer();
        this.learningEngine = new LearningEngine();
        this.lossPreventionSystem = new LossPreventionSystem();
        this.advancedStrategy = new AdvancedStrategy();
        this.marketIntelligence = new MarketIntelligence();
        this.newsAnalyzer = new NewsAnalyzer();
        this.trendAnalyzer = new TrendPatternAnalyzer();
        
        // Central Trading Brain - consolidates all intelligence
        this.tradingBrain = new TradingBrain(
            marketIntelligence, newsAnalyzer, trendAnalyzer,
            learningEngine, lossPreventionSystem
        );
        
        // Listen to price updates
        priceSimulator.addPriceListener(prices -> updateAnalysis());
        
        // Log data mode
        System.out.println("📊 BotState initialized - Real Market Data: " + (useRealMarketData ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Start the trading bot
     */
    public void start() {
        if (botRunning) return;
        botRunning = true;
        startupTime = System.currentTimeMillis();  // Record startup time for warmup
        priceSimulator.start();
        System.out.println("🚀 Bot starting - warming up for " + (STARTUP_WARMUP_MS/1000) + " seconds...");
        notifyListeners();
    }
    
    /**
     * Stop the trading bot
     */
    public void stop() {
        if (!botRunning) return;
        botRunning = false;
        priceSimulator.stop();
        notifyListeners();
    }
    
    /**
     * Shutdown all components
     */
    public void shutdown() {
        stop();
        priceSimulator.shutdown();
    }
    
    /**
     * Update analysis based on new prices
     * Delegates all trading decisions to TradingBrain
     */
    private void updateAnalysis() {
        if (!botRunning) return;
        
        // Check if stopped by risk management
        if (stoppedByRisk) {
            return;
        }
        
        // Check take profit only (STOP LOSS DISABLED - let bot learn from losses)
        double profitLoss = balance - startingBalance;
        // STOP LOSS DISABLED: Bot continues trading regardless of losses
        // if (profitLoss <= -stopLoss) { ... }
        
        if (profitLoss >= takeProfit) {
            stoppedByRisk = true;
            stopReason = "🎯 TAKE PROFIT reached (+$" + String.format("%.2f", takeProfit) + ")";
            autoTradeEnabled = false;
            notifyListeners();
            return;
        }
        
        // Throttle analysis for better trend detection
        long now = System.currentTimeMillis();
        if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
            return;  // Skip, too soon
        }
        lastAnalysisTime = now;
        
        // Get the appropriate currency based on mode
        String currency = getActiveCurrency();
        List<Double> prices = getPriceHistory(currency);  // Uses real data when enabled
        
        // Scan all currencies for opportunities
        currentBestOpportunity = scanner.getBestOpportunity();
        
        // ===== SMART CURRENCY SWITCHING =====
        // If a better opportunity exists with significantly higher confidence, switch to it
        if (autoCurrencyEnabled && currentBestOpportunity != null) {
            double currentConfidence = currentSignal != null ? currentSignal.getConfidence() : 0;
            double bestConfidence = currentBestOpportunity.getConfidence();
            
            // Switch if best opportunity is 15%+ better than current
            if (bestConfidence > currentConfidence + 15 && 
                !currentBestOpportunity.getCurrency().equals(currency)) {
                currency = currentBestOpportunity.getCurrency();
                prices = getPriceHistory(currency);  // Uses real data when enabled
                System.out.println("🔄 Switched to " + currency + " (confidence: " + (int)bestConfidence + "% vs " + (int)currentConfidence + "%)");
            }
        }
        
        // Generate signal using advanced strategy
        TradingSignal advancedSignal = advancedStrategy.analyzeWithConfirmation(currency, prices);
        if (advancedSignal.isActionable()) {
            currentSignal = advancedSignal;
        } else {
            ScanResult result = scanner.scanCurrency(currency);
            currentSignal = result.getSignal();
        }
        
        // ===== THE BRAIN DECIDES EVERYTHING =====
        TradingBrain.Decision decision = tradingBrain.analyze(
            currency, prices, currentSignal, balance, startingBalance
        );
        
        // Update stop reason with brain status
        stopReason = tradingBrain.getStatusMessage();
        
        // SIGNAL PREVIEW MODE: When a good signal is detected, notify the dashboard
        // for manual confirmation instead of auto-executing
        if (tradingBrain.shouldTrade() && currentSignal != null && currentSignal.isActionable()) {
            // STARTUP WARMUP CHECK - don't trade until system is warmed up
            boolean isWarmedUp = (now - startupTime) >= STARTUP_WARMUP_MS;
            
            // MULTI-TRADE CHECK: Can we add another trade?
            activeTrades.removeIf(t -> !t.isPending());  // Clean up completed
            boolean hasCapacity = activeTrades.size() < MAX_SIMULTANEOUS_TRADES;
            
            // Get set of currencies currently being traded
            java.util.Set<String> tradingCurrencies = activeTrades.stream()
                .filter(t -> t.isPending())
                .map(t -> t.getCurrency())
                .collect(java.util.stream.Collectors.toSet());
            
            // Check if best currency is available
            boolean bestCurrencyAvailable = !tradingCurrencies.contains(currency);
            
            // FALLBACK: If best currency is blocked, find next best available
            String tradeCurrency = currency;
            TradingSignal tradeSignal = currentSignal;
            if (!bestCurrencyAvailable && hasCapacity) {
                ScanResult nextBest = scanner.getNextBestOpportunity(tradingCurrencies);
                if (nextBest != null) {
                    tradeCurrency = nextBest.getCurrency();
                    tradeSignal = nextBest.getSignal();
                    bestCurrencyAvailable = true;  // We found an alternative
                }
            }
            
            if (!isWarmedUp) {
                long remainingWarmup = (STARTUP_WARMUP_MS - (now - startupTime)) / 1000;
                stopReason = "⏳ Warming up... " + remainingWarmup + "s remaining";
            } else if (now - lastTradeTime >= MIN_TRADE_INTERVAL_MS && 
                hasCapacity && bestCurrencyAvailable) {
                
                // Calculate trade amount for preview
                double previewAmount;
                if (autoDynamicAmountEnabled) {
                    double sizeMultiplier = tradingBrain.getRecommendedSizeMultiplier();
                    previewAmount = tradeAmount * sizeMultiplier;
                    previewAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, previewAmount));
                } else {
                    previewAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, tradeAmount));
                }
                int previewDuration = getActiveTimeMinutes();
                
                // If AUTO-TRADE is enabled, execute immediately
                if (tradingBrain.isAutoTradeMode()) {
                    // Update current signal to the one we're actually trading
                    if (!tradeCurrency.equals(currency)) {
                        currentSignal = tradeSignal;
                        selectedCurrency = tradeCurrency;  // Update for executeTrade()
                    }
                    executeTrade();
                    lastTradeTime = now;
                } else {
                    // Otherwise, notify the dashboard to show the preview
                    notifySignalReady(tradeCurrency, tradeSignal, previewAmount, previewDuration);
                    stopReason = "⚡ SIGNAL READY - Check preview panel!";
                }
            } else if (!hasCapacity) {
                stopReason = "📊 Max trades active (" + activeTrades.size() + "/" + MAX_SIMULTANEOUS_TRADES + ")";
            } else if (!bestCurrencyAvailable) {
                stopReason = "⏳ All currencies busy (" + tradingCurrencies.size() + " active)";
            }
        }
        
        notifyListeners();
    }
    
    // ==================== MODE HANDLING ====================
    
    /**
     * Get the currency to use based on current mode
     */
    public String getActiveCurrency() {
        if (autoCurrencyEnabled) {
            // AUTO: Use best opportunity
            return currentBestOpportunity != null ? 
                   currentBestOpportunity.getCurrency() : "EUR/USD";
        } else {
            // MANUAL: Use user selection
            return selectedCurrency;
        }
    }
    
    /**
     * Get the time duration based on current mode and signal
     */
    public int getActiveTimeMinutes() {
        if (autoTimeEnabled) {
            // AUTO: Based on signal confidence
            double confidence = currentSignal != null ? currentSignal.getConfidence() : 50;
            return timeOptimizer.getOptimalTimeMinutes(confidence);
        } else {
            // MANUAL: Use user selection
            return selectedTimeMinutes;
        }
    }
    
    /**
     * Get recommendation text for time
     */
    public String getTimeRecommendation() {
        if (currentSignal == null) return "Waiting for signal...";
        return timeOptimizer.getRecommendationReason(currentSignal.getConfidence());
    }
    
    // ==================== TRADING ====================
    
    /**
     * Execute a trade with current settings
     * MULTI-TRADE: Can have up to MAX_SIMULTANEOUS_TRADES running at once
     */
    public Trade executeTrade() {
        String currency = getActiveCurrency();
        
        // Clean up completed trades from active list
        activeTrades.removeIf(t -> !t.isPending());
        
        // Check if we already have a trade on THIS currency
        boolean hasTradeOnCurrency = activeTrades.stream()
            .anyMatch(t -> t.getCurrency().equals(currency) && t.isPending());
        if (hasTradeOnCurrency) {
            return null;  // Already trading this currency
        }
        
        // Check if we've hit max simultaneous trades
        if (activeTrades.size() >= MAX_SIMULTANEOUS_TRADES) {
            return null;  // Too many active trades
        }
        
        if (currentSignal == null || !currentSignal.isActionable()) {
            return null;  // No valid signal
        }
        
        double price = priceSimulator.getCurrentPrice(currency);
        int duration = getActiveTimeMinutes();
        
        // ===== SMART DYNAMIC TRADE AMOUNT (Kelly-Inspired) =====
        double adjustedAmount;
        
        if (autoDynamicAmountEnabled) {
            // Get performance metrics from brain
            double sizeMultiplier = tradingBrain.getRecommendedSizeMultiplier();
            double confidence = tradingBrain.getDecisionConfidence();
            double winRate = getWinRate();
            int consecutiveWins = tradingBrain.getConsecutiveWins();
            int consecutiveLosses = tradingBrain.getConsecutiveLosses();
            
            // ===== STEP 1: TIERED CONFIDENCE MULTIPLIER =====
            // More aggressive scaling for higher confidence
            double confidenceMultiplier;
            if (confidence >= 90) {
                confidenceMultiplier = 2.5;  // Very high confidence = 2.5x
            } else if (confidence >= 85) {
                confidenceMultiplier = 2.0;  // High confidence = 2x
            } else if (confidence >= 80) {
                confidenceMultiplier = 1.5;  // Good confidence = 1.5x
            } else if (confidence >= 75) {
                confidenceMultiplier = 1.0;  // Minimum confidence = 1x (base)
            } else {
                confidenceMultiplier = 0.6;  // Low confidence = smaller trades
            }
            
            // ===== STEP 2: STREAK ADJUSTMENT =====
            // Increase after wins, decrease after losses
            double streakMultiplier = 1.0;
            if (consecutiveWins >= 3) {
                streakMultiplier = 1.3;  // Hot streak = +30%
            } else if (consecutiveWins >= 2) {
                streakMultiplier = 1.15;  // Winning = +15%
            } else if (consecutiveLosses >= 3) {
                streakMultiplier = 0.5;  // Cold streak = -50% (protect capital!)
            } else if (consecutiveLosses >= 2) {
                streakMultiplier = 0.7;  // Losing = -30%
            }
            
            // ===== STEP 3: WIN RATE ADJUSTMENT =====
            // If bot is performing well historically, trade bigger
            double winRateMultiplier = 1.0;
            if (totalTrades >= 10) {  // Need at least 10 trades to judge
                if (winRate >= 60) {
                    winRateMultiplier = 1.2;  // Profitable bot = +20%
                } else if (winRate >= 55) {
                    winRateMultiplier = 1.1;  // Good bot = +10%
                } else if (winRate < 45) {
                    winRateMultiplier = 0.6;  // Losing bot = -40% (be cautious!)
                }
            }
            
            // ===== STEP 4: CALCULATE FINAL AMOUNT =====
            adjustedAmount = tradeAmount * sizeMultiplier * confidenceMultiplier * streakMultiplier * winRateMultiplier;
            
            // Apply min/max limits
            adjustedAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, adjustedAmount));
            
            // Safety: Cap at 10% of balance (protect against ruin)
            adjustedAmount = Math.min(adjustedAmount, balance * 0.10);
            
            // Round to 2 decimal places
            adjustedAmount = Math.round(adjustedAmount * 100.0) / 100.0;
            
            System.out.println("🎯 Smart Amount: $" + String.format("%.2f", adjustedAmount) + 
                " | Conf:" + (int)confidence + "% (" + String.format("%.1f", confidenceMultiplier) + "x)" +
                " | Streak:" + String.format("%.1f", streakMultiplier) + "x" +
                " | WR:" + String.format("%.0f", winRate) + "% (" + String.format("%.1f", winRateMultiplier) + "x)");
        } else {
            // CONSTANT AMOUNT: Use fixed trade amount (user preference)
            adjustedAmount = tradeAmount;
            // Still respect min/max limits
            adjustedAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, adjustedAmount));
        }
        
        // GET DIRECTION FROM BRAIN (or current signal if brain doesn't specify)
        TradingSignal.SignalType direction = tradingBrain.getRecommendedDirection();
        if (direction == TradingSignal.SignalType.NEUTRAL && currentSignal != null) {
            direction = currentSignal.getSignalType();
        }
        
        double confidence = tradingBrain.getDecisionConfidence();
        
        activeTrade = new Trade(
            currency,
            direction,
            price,
            duration,
            confidence,
            adjustedAmount
        );
        
        // Add to active trades list (MULTI-TRADE support)
        activeTrades.add(activeTrade);
        System.out.println("📊 Active trades: " + activeTrades.size() + "/" + MAX_SIMULTANEOUS_TRADES + 
            " | Trading: " + currency);
        
        tradeHistory.add(0, activeTrade);  // Add to front
        totalTrades++;
        balance -= adjustedAmount;  // Deduct adjusted amount
        
        // POCKET OPTION LIVE TRADING (via Chrome Extension - RELIABLE!)
        if (pocketOptionEnabled && tradingBrain.getPythonClient() != null) {
            var pythonClient = tradingBrain.getPythonClient();
            
            // CHECK: Enforce minimum delay between extension trades
            long timeSinceLastTrade = System.currentTimeMillis() - lastExtensionTradeTime;
            if (timeSinceLastTrade < MIN_EXTENSION_TRADE_DELAY_MS) {
                long waitTime = MIN_EXTENSION_TRADE_DELAY_MS - timeSinceLastTrade;
                System.out.println("⏳ Waiting " + (waitTime/1000) + "s before next extension trade...");
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Prepare trade parameters
            String poDirection = (direction == TradingSignal.SignalType.CALL) ? "CALL" : "PUT";
            
            System.out.println("🚀 Sending trade to EXTENSION: " + poDirection + " $" + adjustedAmount + " on " + currency + " (" + duration + " min)");
            
            // Execute via Chrome Extension (user's browser clicks the buttons!)
            int durationSeconds = duration * 60;  // Convert minutes to seconds for extension
            boolean executed = pythonClient.executeExtensionTrade(currency, poDirection, adjustedAmount, durationSeconds);
            
            if (executed) {
                lastExtensionTradeTime = System.currentTimeMillis();  // Record trade time
                pocketOptionTradeCount++;
                pythonClient.recordTradeSuccess();
                System.out.println("✅ EXTENSION trade sent: " + poDirection + " $" + adjustedAmount + " on " + currency);
            } else {
                pythonClient.recordTradeFailure();
                System.err.println("❌ EXTENSION trade FAILED! Failures: " + pythonClient.getConsecutiveFailures());
                
                if (pythonClient.getConsecutiveFailures() >= 3) {
                    stopReason = "🚨 " + pythonClient.getConsecutiveFailures() + " trade failures - check extension connection!";
                }
            }
        } else {
            if (!pocketOptionEnabled) {
                System.out.println("⚡ Pocket Option trading disabled - simulation only");
            }
        }
        
        // Schedule trade completion
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> completeTrade(activeTrade), duration, TimeUnit.MINUTES);
        executor.shutdown();
        
        notifyListeners();
        return activeTrade;
    }
    
    /**
     * Complete an active trade
     */
    private void completeTrade(Trade trade) {
        if (trade == null) return;
        
        double exitPrice = priceSimulator.getCurrentPrice(trade.getCurrency());
        trade.complete(exitPrice);
        
        boolean won = trade.isWon();
        double profit = 0;
        
        if (won) {
            winCount++;
            // Payout: 85% profit on win
            profit = trade.getAmount() * 0.85;
            balance += trade.getAmount() + profit;
        } else {
            // Loss was already deducted
            profit = -trade.getAmount();
        }
        
        // NOTIFY THE BRAIN - with full trade details for learning
        tradingBrain.recordTradeResult(won, trade.getCurrency(), profit);
        
        // Learn from this trade
        learningEngine.learnFromTrade(trade);
        
        // Update loss prevention system
        lossPreventionSystem.recordTradeResult(trade);
        
        if (activeTrade == trade) {
            activeTrade = null;
        }
        
        notifyListeners();
    }
    
    /**
     * Execute a trade with a specific signal - called from Trade Preview Panel.
     * This allows manual confirmation before trade execution.
     */
    public Trade executeTradeWithSignal(String currency, TradingSignal signal) {
        if (signal == null || !signal.isActionable()) {
            return null;
        }
        
        // Clean up completed trades from active list
        activeTrades.removeIf(t -> !t.isPending());
        
        // Check if we already have a trade on THIS currency
        boolean hasTradeOnCurrency = activeTrades.stream()
            .anyMatch(t -> t.getCurrency().equals(currency) && t.isPending());
        if (hasTradeOnCurrency) {
            System.out.println("⚠️ Already trading " + currency);
            return null;
        }
        
        // Check if we've hit max simultaneous trades
        if (activeTrades.size() >= MAX_SIMULTANEOUS_TRADES) {
            System.out.println("⚠️ Max trades reached (" + MAX_SIMULTANEOUS_TRADES + ")");
            return null;
        }
        
        double price = priceSimulator.getCurrentPrice(currency);
        int duration = getActiveTimeMinutes();
        
        // Calculate trade amount
        double adjustedAmount;
        if (autoDynamicAmountEnabled) {
            double sizeMultiplier = tradingBrain.getRecommendedSizeMultiplier();
            adjustedAmount = tradeAmount * sizeMultiplier;
            adjustedAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, adjustedAmount));
            adjustedAmount = Math.min(adjustedAmount, balance * 0.10);
        } else {
            adjustedAmount = Math.max(minTradeAmount, Math.min(maxTradeAmount, tradeAmount));
        }
        adjustedAmount = Math.round(adjustedAmount * 100.0) / 100.0;
        
        TradingSignal.SignalType direction = signal.getSignalType();
        double confidence = signal.getConfidence();
        
        Trade trade = new Trade(
            currency,
            direction,
            price,
            duration,
            confidence,
            adjustedAmount
        );
        
        activeTrades.add(trade);
        activeTrade = trade;
        tradeHistory.add(0, trade);
        totalTrades++;
        balance -= adjustedAmount;
        
        System.out.println("✅ EXECUTED from preview: " + direction + " $" + adjustedAmount + " on " + currency);
        
        // Execute on Pocket Option if enabled
        if (pocketOptionEnabled && tradingBrain.getPythonClient() != null) {
            var pythonClient = tradingBrain.getPythonClient();
            String poDirection = (direction == TradingSignal.SignalType.CALL) ? "CALL" : "PUT";
            int durationSeconds = duration * 60;  // Convert minutes to seconds
            boolean executed = pythonClient.executeExtensionTrade(currency, poDirection, adjustedAmount, durationSeconds);
            if (executed) {
                pocketOptionTradeCount++;
                System.out.println("✅ Trade sent to broker: " + poDirection + " $" + adjustedAmount);
            }
        }
        
        // Schedule trade completion
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> completeTrade(trade), duration, TimeUnit.MINUTES);
        executor.shutdown();
        
        lastTradeTime = System.currentTimeMillis();
        notifyListeners();
        return trade;
    }
    
    // Signal preview callback - dashboard registers to get notified
    private Consumer<SignalPreviewData> signalPreviewCallback = null;
    
    /**
     * Register callback to receive signal preview notifications.
     */
    public void setSignalPreviewCallback(Consumer<SignalPreviewData> callback) {
        this.signalPreviewCallback = callback;
    }
    
    /**
     * Notify dashboard that a new signal is ready for preview.
     */
    public void notifySignalReady(String currency, TradingSignal signal, double amount, int duration) {
        if (signalPreviewCallback != null) {
            signalPreviewCallback.accept(new SignalPreviewData(currency, signal, amount, duration));
        }
    }
    
    /**
     * Data class for signal preview notifications.
     */
    public static class SignalPreviewData {
        public final String currency;
        public final TradingSignal signal;
        public final double amount;
        public final int duration;
        
        public SignalPreviewData(String currency, TradingSignal signal, double amount, int duration) {
            this.currency = currency;
            this.signal = signal;
            this.amount = amount;
            this.duration = duration;
        }
    }
    
    // ==================== SETTERS (for GUI) ====================
    
    public void setAutoCurrencyEnabled(boolean enabled) {
        this.autoCurrencyEnabled = enabled;
        notifyListeners();
    }
    
    public void setAutoTimeEnabled(boolean enabled) {
        this.autoTimeEnabled = enabled;
        notifyListeners();
    }
    
    public void setSelectedCurrency(String currency) {
        this.selectedCurrency = currency;
        notifyListeners();
    }
    
    public void setSelectedTimeMinutes(int minutes) {
        this.selectedTimeMinutes = minutes;
        notifyListeners();
    }
    
    public void setAutoTradeEnabled(boolean enabled) {
        this.autoTradeEnabled = enabled;
        tradingBrain.setAutoTradeMode(enabled);  // Route through brain
        notifyListeners();
    }
    
    public void setRecoveryModeEnabled(boolean enabled) {
        this.recoveryModeEnabled = enabled;
        tradingBrain.setRecoveryMode(enabled);  // Route through brain
        notifyListeners();
    }
    
    public void setMultiCurrencyMode(boolean enabled) {
        this.multiCurrencyMode = enabled;
        tradingBrain.setMultiCurrencyMode(enabled);  // Route through brain
        notifyListeners();
    }
    
    public void setTradeAmount(double amount) {
        this.tradeAmount = Math.max(1, amount);  // Minimum $1
        notifyListeners();
    }
    
    public void setAutoDynamicAmountEnabled(boolean enabled) {
        this.autoDynamicAmountEnabled = enabled;
        notifyListeners();
    }
    
    public void setMinTradeAmount(double amount) {
        this.minTradeAmount = Math.max(1, amount);  // At least $1
        notifyListeners();
    }
    
    public void setMaxTradeAmount(double amount) {
        this.maxTradeAmount = Math.max(minTradeAmount, amount);  // Must be >= min
        notifyListeners();
    }
    
    public void setStopLoss(double amount) {
        this.stopLoss = Math.max(1, amount);
        notifyListeners();
    }
    
    public void setTakeProfit(double amount) {
        this.takeProfit = Math.max(1, amount);
        notifyListeners();
    }
    
    public void resetRiskStop() {
        this.stoppedByRisk = false;
        this.stopReason = "";
        this.startingBalance = balance;
        notifyListeners();
    }
    
    // ==================== GETTERS ====================
    
    public boolean isBotRunning() { return botRunning; }
    public boolean isAutoCurrencyEnabled() { return autoCurrencyEnabled; }
    public boolean isAutoTimeEnabled() { return autoTimeEnabled; }
    public boolean isAutoTradeEnabled() { return autoTradeEnabled; }
    public boolean isRecoveryModeEnabled() { return recoveryModeEnabled; }
    public boolean isMultiCurrencyMode() { return multiCurrencyMode; }
    public boolean isNeedsRecovery() { return needsRecovery; }
    public int getRecoveryAttempts() { return recoveryAttempts; }
    public double getTradeAmount() { return tradeAmount; }
    public boolean isAutoDynamicAmountEnabled() { return autoDynamicAmountEnabled; }
    public double getMinTradeAmount() { return minTradeAmount; }
    public double getMaxTradeAmount() { return maxTradeAmount; }
    public double getBalance() { return balance; }
    public double getStopLoss() { return stopLoss; }
    public double getTakeProfit() { return takeProfit; }
    public boolean isStoppedByRisk() { return stoppedByRisk; }
    public String getStopReason() { return stopReason; }
    public String getSelectedCurrency() { return selectedCurrency; }
    public int getSelectedTimeMinutes() { return selectedTimeMinutes; }
    public TradingSignal getCurrentSignal() { return currentSignal; }
    public ScanResult getCurrentBestOpportunity() { return currentBestOpportunity; }
    public Trade getActiveTrade() { return activeTrade; }
    public List<Trade> getActiveTrades() { return new ArrayList<>(activeTrades); }
    public int getMaxSimultaneousTrades() { return MAX_SIMULTANEOUS_TRADES; }
    public List<Trade> getTradeHistory() { return new ArrayList<>(tradeHistory); }
    public PriceSimulator getPriceSimulator() { return priceSimulator; }
    public RealMarketDataProvider getRealMarketDataProvider() { return realMarketData; }
    public MultiCurrencyScanner getScanner() { return scanner; }
    public TimeOptimizer getTimeOptimizer() { return timeOptimizer; }
    public LearningEngine getLearningEngine() { return learningEngine; }
    public LossPreventionSystem getLossPreventionSystem() { return lossPreventionSystem; }
    public MarketIntelligence getMarketIntelligence() { return marketIntelligence; }
    public NewsAnalyzer getNewsAnalyzer() { return newsAnalyzer; }
    public TrendPatternAnalyzer getTrendAnalyzer() { return trendAnalyzer; }
    public TradingBrain getTradingBrain() { return tradingBrain; }
    
    // ==================== REAL MARKET DATA ====================
    
    /**
     * Get price history - uses real data when enabled, falls back to simulated
     */
    public List<Double> getPriceHistory(String currency) {
        if (useRealMarketData) {
            List<Double> realPrices = realMarketData.getPriceHistory(currency);
            if (realPrices != null && !realPrices.isEmpty()) {
                return realPrices;
            }
        }
        // Fallback to simulated data
        return priceSimulator.getPriceHistory(currency);
    }
    
    public boolean isUseRealMarketData() { return useRealMarketData; }
    
    public void setUseRealMarketData(boolean enabled) {
        this.useRealMarketData = enabled;
        this.scanner.setUseRealData(enabled);  // Propagate to scanner
        System.out.println("📊 Real Market Data: " + (enabled ? "ENABLED" : "DISABLED"));
        notifyListeners();
    }
    
    public String getMarketDataStatus() {
        if (useRealMarketData) {
            return realMarketData.getStatusMessage();
        } else {
            return "📈 Simulated data mode";
        }
    }
    
    public double getProfitLoss() {
        return balance - startingBalance;
    }
    
    public double getCurrentPrice() {
        return priceSimulator.getCurrentPrice(getActiveCurrency());
    }
    
    public double getWinRate() {
        return totalTrades > 0 ? (double) winCount / totalTrades * 100 : 0;
    }
    
    public int getTotalTrades() { return totalTrades; }
    public int getWinCount() { return winCount; }
    
    public List<String> getAvailableCurrencies() {
        return scanner.getAvailableCurrencies();
    }
    
    // ==================== POCKET OPTION SETTINGS ====================
    
    public boolean isPocketOptionEnabled() { return pocketOptionEnabled; }
    
    public void setPocketOptionEnabled(boolean enabled) {
        this.pocketOptionEnabled = enabled;
        System.out.println("🎰 Pocket Option live trading: " + (enabled ? "ENABLED" : "DISABLED"));
        notifyListeners();
    }
    
    public int getPocketOptionTradeCount() { return pocketOptionTradeCount; }
    
    // ==================== LISTENERS ====================
    
    public void addStateListener(Consumer<BotState> listener) {
        stateListeners.add(listener);
    }
    
    private void notifyListeners() {
        for (Consumer<BotState> listener : stateListeners) {
            try {
                listener.accept(this);
            } catch (Exception e) {
                System.err.println("State listener error: " + e.getMessage());
            }
        }
    }
}
