package com.elitebot.intelligence;

import com.elitebot.model.TradingSignal;
import com.elitebot.model.TradingSignal.SignalType;
import com.elitebot.learning.LearningEngine;
import com.elitebot.learning.LossPreventionSystem;
import com.elitebot.intelligence.PythonAnalysisClient;
import com.elitebot.intelligence.PythonAnalysisClient.SignalResult;
import com.elitebot.intelligence.PythonAnalysisClient.AnalysisResult;
import java.util.*;
import java.time.*;
import java.io.*;

/**
 * ULTIMATE TRADING BRAIN - Self-Learning Central Intelligence
 * 
 * Based on professional trading AI research:
 * - Reinforcement Learning: Learns from rewards (profit) and penalties (loss)
 * - Self-Improving: Adjusts strategies based on performance
 * - Adaptive: Changes approach based on market regime
 * - Memory: Remembers what works and what doesn't
 * - Knowledge Base: Contains trading wisdom from research
 * 
 * GOAL: Reduce losses, maximize profit, never fail
 */
public class TradingBrain {
    
    // ===== DECISION TYPES =====
    public enum Decision {
        STRONG_BUY,    // High confidence CALL - full size
        BUY,           // Normal CALL - normal size
        WAIT,          // No trade, wait for better opportunity
        SELL,          // Normal PUT - normal size
        STRONG_SELL,   // High confidence PUT - full size
        RECOVERY_BUY,  // Recovery trade CALL
        RECOVERY_SELL, // Recovery trade PUT
        AVOID          // Too dangerous - no trading
    }
    
    // ===== MARKET REGIME (Detected by brain) =====
    public enum MarketRegime {
        TRENDING_UP,    // Clear uptrend - follow trend
        TRENDING_DOWN,  // Clear downtrend - follow trend
        RANGING,        // Sideways - use mean reversion
        VOLATILE,       // High volatility - reduce position size
        QUIET,          // Low volatility - normal trading
        UNCERTAIN       // Can't determine - be cautious
    }
    
    // ===== BRAIN STATE =====
    private Decision currentDecision = Decision.WAIT;
    private double decisionConfidence = 0;
    private String decisionReason = "";
    private boolean shouldTrade = false;
    private double recommendedSizeMultiplier = 1.0;
    private SignalType recommendedDirection = SignalType.NEUTRAL;
    private MarketRegime currentRegime = MarketRegime.UNCERTAIN;
    
    // ===== MODES =====
    private boolean autoTradeMode = false;
    private boolean recoveryMode = false;
    private boolean multiCurrencyMode = false;
    
    // ===== LEARNING MEMORY (Reinforcement Learning) =====
    private Map<String, Double> strategyScores = new HashMap<>();  // Which strategies work
    private Map<String, Double> currencyScores = new HashMap<>();  // Currency performance
    private Map<Integer, Double> hourScores = new HashMap<>();     // Best hours
    private Map<MarketRegime, Double> regimeScores = new HashMap<>();  // Regime performance
    private List<TradeMemory> tradeMemory = new ArrayList<>();     // Recent trades
    private static final int MAX_MEMORY = 500;
    
    // ===== SELF-IMPROVEMENT PARAMETERS =====
    private double learningRate = 0.1;        // How fast to adapt
    private double explorationRate = 0.05;    // Try new things 5% of time
    private double riskTolerance = 0.5;       // 0 = very safe, 1 = aggressive
    
    // ===== RISK PARAMETERS =====
    // NOTE: Set to very high value to allow full learning (user requested to disable stop loss)
    private double maxDrawdownPercent = 99999.0;  // Effectively disabled for learning
    private double minConfidenceToTrade = 75.0;   // INCREASED from 65 - require more confidence
    private double recoveryConfidenceRequired = 88.0;  // INCREASED from 85
    private int consecutiveLosses = 0;
    private int consecutiveWins = 0;
    private double peakBalance = 0;
    private double currentDrawdown = 0;
    
    // ===== PERFORMANCE TRACKING =====
    private int totalBrainTrades = 0;
    private int totalBrainWins = 0;
    private double totalBrainProfit = 0;
    private double bestWinStreak = 0;
    private double worstLossStreak = 0;
    
    // ===== SIGNAL PERSISTENCE (Wait for confirmation) =====
    // Only trade if signal persists for multiple analysis cycles
    private String lastSignalDirection = "WAIT";  // Previous cycle's signal
    private String currentSignalDirection = "WAIT";  // Current cycle's signal
    private int signalPersistenceCount = 0;  // How many cycles same direction
    private static final int MIN_PERSISTENCE_CYCLES = 2;  // Require 2+ same signals
    
    // ===== TRADING KNOWLEDGE BASE =====
    // Professional trading wisdom built-in
    private static final Map<String, String> TRADING_KNOWLEDGE = new HashMap<>();
    static {
        // Trend Trading Rules
        TRADING_KNOWLEDGE.put("TREND_RULE_1", "The trend is your friend - trade with it, not against it");
        TRADING_KNOWLEDGE.put("TREND_RULE_2", "Wait for pullbacks in trending markets to enter");
        TRADING_KNOWLEDGE.put("TREND_RULE_3", "In uptrend: buy dips. In downtrend: sell rallies");
        
        // Risk Management Rules
        TRADING_KNOWLEDGE.put("RISK_RULE_1", "Never risk more than 2% of capital on a single trade");
        TRADING_KNOWLEDGE.put("RISK_RULE_2", "Cut losses quickly, let winners run");
        TRADING_KNOWLEDGE.put("RISK_RULE_3", "After 3 consecutive losses, reduce position size by 50%");
        TRADING_KNOWLEDGE.put("RISK_RULE_4", "After a big win, don't get greedy - maintain discipline");
        
        // Timing Rules
        TRADING_KNOWLEDGE.put("TIME_RULE_1", "Avoid trading during major news releases");
        TRADING_KNOWLEDGE.put("TIME_RULE_2", "Most volatile hours: 8-10 AM, 2-4 PM (market hours)");
        TRADING_KNOWLEDGE.put("TIME_RULE_3", "Weekends and holidays have low liquidity - avoid");
        
        // Psychology Rules
        TRADING_KNOWLEDGE.put("PSYCH_RULE_1", "Don't revenge trade after a loss");
        TRADING_KNOWLEDGE.put("PSYCH_RULE_2", "Take breaks after winning streaks to reset");
        TRADING_KNOWLEDGE.put("PSYCH_RULE_3", "If unsure, don't trade - wait for clarity");
        
        // Pattern Rules
        TRADING_KNOWLEDGE.put("PATTERN_RULE_1", "Double bottoms are bullish - expect price to rise");
        TRADING_KNOWLEDGE.put("PATTERN_RULE_2", "Double tops are bearish - expect price to fall");
        TRADING_KNOWLEDGE.put("PATTERN_RULE_3", "Breakouts with volume are more reliable");
    }
    
    // ===== COMPONENTS =====
    private MarketIntelligence marketIntelligence;
    private NewsAnalyzer newsAnalyzer;
    private TrendPatternAnalyzer trendAnalyzer;
    private LearningEngine learningEngine;
    private LossPreventionSystem lossPrevention;
    
    // ===== PYTHON ANALYSIS (HYBRID APPROACH) =====
    private PythonAnalysisClient pythonClient;
    private boolean pythonAvailable = false;
    
    // ===== BRAIN ACTIVITY LOG =====
    private List<String> brainLog = new ArrayList<>();
    private static final int MAX_LOG = 100;
    private static final String BRAIN_DATA_FILE = "brain_memory.dat";
    
    public TradingBrain(MarketIntelligence mi, NewsAnalyzer na, 
                        TrendPatternAnalyzer ta, LearningEngine le,
                        LossPreventionSystem lp) {
        this.marketIntelligence = mi;
        this.newsAnalyzer = na;
        this.trendAnalyzer = ta;
        this.learningEngine = le;
        this.lossPrevention = lp;
        
        // Initialize Python analysis client (hybrid mode)
        initializePythonClient();
        
        initializeScores();
        loadBrainMemory();
        log("🧠 Ultimate Trading Brain initialized - Ready to learn and profit!");
    }
    
    private void initializePythonClient() {
        try {
            pythonClient = new PythonAnalysisClient();
            pythonAvailable = pythonClient.checkConnection();
            if (pythonAvailable) {
                log("🐍 Python Analysis Server connected - Hybrid mode ACTIVE");
            } else {
                log("⚠️ Python server not available - Using Java-only mode");
            }
        } catch (Exception e) {
            pythonAvailable = false;
            log("⚠️ Python client init failed: " + e.getMessage());
        }
    }
    
    private void initializeScores() {
        // Initialize strategy scores
        strategyScores.put("TREND_FOLLOWING", 50.0);
        strategyScores.put("MEAN_REVERSION", 50.0);
        strategyScores.put("BREAKOUT", 50.0);
        strategyScores.put("MOMENTUM", 50.0);
        
        // Initialize regime scores
        for (MarketRegime regime : MarketRegime.values()) {
            regimeScores.put(regime, 50.0);
        }
        
        // Initialize hour scores (0-23)
        for (int i = 0; i < 24; i++) {
            hourScores.put(i, 50.0);
        }
    }
    
    // ===== MAIN ANALYSIS METHOD =====
    
    public Decision analyze(String currency, List<Double> prices, 
                           TradingSignal signal, double balance, double startingBalance) {
        
        // Update drawdown and peak tracking
        if (peakBalance == 0) peakBalance = startingBalance;
        if (balance > peakBalance) peakBalance = balance;
        currentDrawdown = (peakBalance - balance) / peakBalance * 100;
        
        StringBuilder reason = new StringBuilder();
        
        // ===== STEP 1: SAFETY FIRST (Risk Management) =====
        Decision safetyCheck = performSafetyChecks(balance, startingBalance);
        if (safetyCheck != null) return safetyCheck;
        
        // ===== STEP 2: DETECT MARKET REGIME =====
        currentRegime = detectMarketRegime(prices);
        reason.append("Regime:" + currentRegime.toString().substring(0, 3) + " ");
        
        // ===== STEP 3: GATHER ALL INTELLIGENCE =====
        marketIntelligence.analyzeMarket(prices, currency);
        newsAnalyzer.analyzeNews();
        trendAnalyzer.analyzePatterns(prices);
        
        // ===== STEP 4: APPLY TRADING KNOWLEDGE =====
        int buyScore = 0;
        int sellScore = 0;
        
        // Apply regime-based strategy (REINFORCEMENT LEARNING)
        double regimeBonus = regimeScores.getOrDefault(currentRegime, 50.0) - 50;
        
        switch (currentRegime) {
            case TRENDING_UP:
                // Knowledge: "The trend is your friend"
                buyScore += 25 + (int)regimeBonus;
                reason.append("TrendUp ");
                break;
            case TRENDING_DOWN:
                sellScore += 25 + (int)regimeBonus;
                reason.append("TrendDown ");
                break;
            case RANGING:
                // Knowledge: Mean reversion in ranges
                if (trendAnalyzer.getCurrentPrediction() == TrendPatternAnalyzer.Prediction.LIKELY_UP) {
                    buyScore += 20;
                } else if (trendAnalyzer.getCurrentPrediction() == TrendPatternAnalyzer.Prediction.LIKELY_DOWN) {
                    sellScore += 20;
                }
                reason.append("Range ");
                break;
            case VOLATILE:
                // Knowledge: Reduce exposure in volatile markets
                recommendedSizeMultiplier *= 0.5;
                reason.append("Volatile! ");
                break;
            default:
                reason.append("Uncertain ");
        }
        
        // ===== STEP 5: PATTERN ANALYSIS =====
        if (trendAnalyzer.suggestsCall()) {
            buyScore += 30 + (int)trendAnalyzer.getConfidenceBoost();
            reason.append("Pattern:BUY ");
        } else if (trendAnalyzer.suggestsPut()) {
            sellScore += 30 + (int)trendAnalyzer.getConfidenceBoost();
            reason.append("Pattern:SELL ");
        }
        
        // ===== STEP 6: TECHNICAL SIGNAL =====
        if (signal != null && signal.isActionable()) {
            int signalWeight = (int)(signal.getConfidence() * 0.4);
            if (signal.getSignalType() == SignalType.CALL) {
                buyScore += signalWeight;
            } else if (signal.getSignalType() == SignalType.PUT) {
                sellScore += signalWeight;
            }
        }
        
        // ===== STEP 6.5: AI ENHANCEMENT (FinGPT + Python ML) =====
        // These AIs provide INPUT to Java Brain, but Brain makes FINAL decision
        // NEVER blocks trading - only provides additional intelligence
        if (pythonAvailable && pythonClient != null && prices.size() >= 50) {
            try {
                // 1. Get Python ML signal (technical analysis)
                SignalResult pythonSignal = pythonClient.getQuickSignal(currency, prices);
                
                if (pythonSignal != null && pythonSignal.confidence >= 60) {
                    int pythonWeight = (int)(pythonSignal.confidence * 0.35);
                    
                    if (pythonSignal.isCall()) {
                        buyScore += pythonWeight;
                        reason.append("Python:CALL(" + (int)pythonSignal.confidence + "%) ");
                    } else if (pythonSignal.isPut()) {
                        sellScore += pythonWeight;
                        reason.append("Python:PUT(" + (int)pythonSignal.confidence + "%) ");
                    }
                    
                    recommendedSizeMultiplier *= pythonSignal.positionSize;
                }
                
                // 2. Get FinGPT signal (financial AI sentiment/prediction)
                SignalResult fingptSignal = pythonClient.getFinGPTSignal(currency, prices);
                
                if (fingptSignal != null && fingptSignal.confidence >= 50) {
                    // FinGPT provides a BOOST, not a requirement
                    int fingptWeight = (int)(fingptSignal.confidence * 0.25);  // Smaller weight
                    
                    if (fingptSignal.signal.equals("BUY")) {
                        buyScore += fingptWeight;
                        reason.append("FinGPT:BUY(" + (int)fingptSignal.confidence + "%) ");
                    } else if (fingptSignal.signal.equals("SELL")) {
                        sellScore += fingptWeight;
                        reason.append("FinGPT:SELL(" + (int)fingptSignal.confidence + "%) ");
                    }
                    // If FinGPT says WAIT, we just don't add its boost - never block!
                }
                
                log("🤖 AI Input: Python + FinGPT feeding Java Brain");
                
            } catch (Exception e) {
                // AI enhancement failed - continue with Java Brain alone
                log("AI enhancement error (continuing anyway): " + e.getMessage());
            }
        }
        
        // JAVA BRAIN MAKES THE FINAL DECISION BELOW (never blocked by AI disagreement)
        
        // ===== STEP 7: CURRENCY PERFORMANCE (Learned) =====
        double currencyBonus = currencyScores.getOrDefault(currency, 50.0) - 50;
        buyScore += (int)(currencyBonus * 0.2);
        sellScore += (int)(currencyBonus * 0.2);
        
        // ===== STEP 8: TIME-BASED LEARNING =====
        int currentHour = LocalTime.now().getHour();
        double hourBonus = hourScores.getOrDefault(currentHour, 50.0) - 50;
        buyScore += (int)(hourBonus * 0.1);
        sellScore += (int)(hourBonus * 0.1);
        
        // ===== STEP 9: MARKET CONDITIONS =====
        if (!marketIntelligence.shouldTrade()) {
            currentDecision = Decision.AVOID;
            decisionReason = "🚫 " + marketIntelligence.getStatusMessage();
            shouldTrade = false;
            return currentDecision;
        }
        
        if (newsAnalyzer.shouldAvoidTrading()) {
            currentDecision = Decision.AVOID;
            decisionReason = "📰 " + newsAnalyzer.getStatusMessage();
            shouldTrade = false;
            return currentDecision;
        }
        
        // ===== STEP 10: CALCULATE FINAL DECISION =====
        int netScore = buyScore - sellScore;
        decisionConfidence = 50 + Math.abs(netScore);
        decisionConfidence = Math.min(95, decisionConfidence);
        
        // Adjust confidence based on consecutive losses (Knowledge: Don't revenge trade)
        double adjustedMinConfidence = minConfidenceToTrade + (consecutiveLosses * 5);
        adjustedMinConfidence = Math.min(85, adjustedMinConfidence);
        
        // Check if we meet minimum confidence
        if (decisionConfidence < adjustedMinConfidence) {
            currentDecision = Decision.WAIT;
            decisionReason = "⏳ Confidence " + (int)decisionConfidence + "% < " + (int)adjustedMinConfidence + "% required";
            shouldTrade = false;
            return currentDecision;
        }
        
        // ===== STEP 11: POSITION SIZING (Learned) =====
        recommendedSizeMultiplier = calculateSmartPositionSize(balance);
        
        // ===== STEP 12: RECOVERY LOGIC =====
        // Recovery mode DOES NOT block normal trading!
        // It only activates when there's a high-confidence opportunity
        boolean isRecoveryOpportunity = recoveryMode && 
                                         consecutiveLosses > 0 && 
                                         decisionConfidence >= recoveryConfidenceRequired;
        
        if (isRecoveryOpportunity) {
            // High confidence opportunity - use SMALLER size during recovery (NEVER martingale!)
            if (netScore > 0) {
                currentDecision = Decision.RECOVERY_BUY;
                recommendedDirection = SignalType.CALL;
            } else {
                currentDecision = Decision.RECOVERY_SELL;
                recommendedDirection = SignalType.PUT;
            }
            // REDUCED size for recovery - protect capital, don't compound losses!
            recommendedSizeMultiplier = 0.5;
            shouldTrade = true;
            decisionReason = "🔄 Recovery opportunity (reduced size)! " + reason.toString();
            log("RECOVERY TRADE: " + currentDecision + " (" + (int)decisionConfidence + "% confidence) - REDUCED SIZE");
            return currentDecision;
        }
        // If recovery mode is ON but confidence is low, continue with NORMAL trading
        // Don't wait for recovery - trade normally and recover when opportunity comes
        
        // ===== STEP 13: SIGNAL PERSISTENCE CHECK =====
        // Only trade if signal direction is consistent for 2+ cycles
        String newDirection;
        if (netScore >= 35) {
            newDirection = "BUY";
        } else if (netScore <= -35) {
            newDirection = "SELL";
        } else {
            newDirection = "WAIT";
        }
        
        // Track signal persistence
        if (newDirection.equals(lastSignalDirection) && !newDirection.equals("WAIT")) {
            signalPersistenceCount++;
        } else {
            signalPersistenceCount = 1;  // Reset on direction change
        }
        lastSignalDirection = newDirection;
        
        // Don't trade unless signal persisted long enough
        if (signalPersistenceCount < MIN_PERSISTENCE_CYCLES && !newDirection.equals("WAIT")) {
            currentDecision = Decision.WAIT;
            decisionReason = "⏳ Signal not confirmed (" + signalPersistenceCount + "/" + MIN_PERSISTENCE_CYCLES + " cycles)";
            shouldTrade = false;
            log("WAITING: Signal needs " + (MIN_PERSISTENCE_CYCLES - signalPersistenceCount) + " more confirmations");
            return currentDecision;
        }
        
        // ===== STEP 14: FINAL DECISION =====
        // MUCH STRICTER THRESHOLDS - only trade when signal is overwhelming AND persistent
        if (netScore >= 60) {
            currentDecision = Decision.STRONG_BUY;
            recommendedDirection = SignalType.CALL;
            shouldTrade = true;
        } else if (netScore >= 35) {
            currentDecision = Decision.BUY;
            recommendedDirection = SignalType.CALL;
            shouldTrade = true;
        } else if (netScore <= -60) {
            currentDecision = Decision.STRONG_SELL;
            recommendedDirection = SignalType.PUT;
            shouldTrade = true;
        } else if (netScore <= -35) {
            currentDecision = Decision.SELL;
            recommendedDirection = SignalType.PUT;
            shouldTrade = true;
        } else {
            currentDecision = Decision.WAIT;
            recommendedDirection = SignalType.NEUTRAL;
            shouldTrade = false;
        }
        
        decisionReason = reason.toString();
        if (shouldTrade) {
            log("TRADE: " + currentDecision + " (" + (int)decisionConfidence + "%) - " + decisionReason + " [Confirmed: " + signalPersistenceCount + " cycles]");
        }
        
        return currentDecision;
    }
    
    // ===== SAFETY CHECKS =====
    
    private Decision performSafetyChecks(double balance, double startingBalance) {
        // Drawdown protection - DISABLED for learning (set to very high value)
        if (currentDrawdown >= maxDrawdownPercent) {
            currentDecision = Decision.AVOID;
            decisionReason = "🛑 Max drawdown " + String.format("%.1f", currentDrawdown) + "% - Protecting capital";
            shouldTrade = false;
            log("BLOCKED: Max drawdown reached");
            return currentDecision;
        }
        
        // Balance protection - DISABLED for full learning (user requested)
        // Bot will continue trading to allow brain to learn fully
        // NOTE: Re-enable this after testing is complete!
        /*
        if (balance < startingBalance * 0.1) {
            currentDecision = Decision.AVOID;
            decisionReason = "🛑 Balance critically low - Stopping to protect remaining funds";
            shouldTrade = false;
            log("BLOCKED: Critical balance");
            return currentDecision;
        }
        */
        
        return null; // All checks passed
    }
    
    // ===== MARKET REGIME DETECTION =====
    
    private MarketRegime detectMarketRegime(List<Double> prices) {
        if (prices == null || prices.size() < 50) return MarketRegime.UNCERTAIN;
        
        int size = prices.size();
        
        // Calculate volatility
        double sum = 0, sumSq = 0;
        for (int i = size - 20; i < size; i++) {
            double change = (prices.get(i) - prices.get(i-1)) / prices.get(i-1) * 100;
            sum += change;
            sumSq += change * change;
        }
        double avgChange = sum / 20;
        double volatility = Math.sqrt(sumSq / 20 - avgChange * avgChange);
        
        // Calculate trend
        double shortMA = avg(prices.subList(size - 5, size));
        double longMA = avg(prices.subList(size - 20, size));
        double trendStrength = (shortMA - longMA) / longMA * 100;
        
        // Determine regime
        if (volatility > 1.5) {
            return MarketRegime.VOLATILE;
        } else if (volatility < 0.3) {
            return MarketRegime.QUIET;
        } else if (trendStrength > 0.5) {
            return MarketRegime.TRENDING_UP;
        } else if (trendStrength < -0.5) {
            return MarketRegime.TRENDING_DOWN;
        } else {
            return MarketRegime.RANGING;
        }
    }
    
    private double avg(List<Double> list) {
        return list.stream().mapToDouble(d -> d).average().orElse(0);
    }
    
    // ===== SMART POSITION SIZING =====
    
    private double calculateSmartPositionSize(double balance) {
        double size = 1.0;
        
        // Reduce after losses (Knowledge: Reduce position after losses)
        if (consecutiveLosses >= 3) {
            size *= 0.5;
            log("📉 Reducing size after " + consecutiveLosses + " losses");
        } else if (consecutiveLosses >= 2) {
            size *= 0.75;
        }
        
        // Reduce during drawdown
        if (currentDrawdown > 10) {
            size *= 0.5;
        } else if (currentDrawdown > 5) {
            size *= 0.75;
        }
        
        // Regime-based adjustment
        if (currentRegime == MarketRegime.VOLATILE) {
            size *= 0.5;
        } else if (currentRegime == MarketRegime.QUIET) {
            size *= 1.0;
        }
        
        // Increase slightly on win streaks (but cautiously)
        if (consecutiveWins >= 3) {
            size = Math.min(1.5, size * 1.1);
        }
        
        return Math.max(0.25, Math.min(2.0, size));
    }
    
    // ===== REINFORCEMENT LEARNING - RECORD RESULTS =====
    
    public void recordTradeResult(boolean won, String currency, double profit) {
        totalBrainTrades++;
        if (won) {
            totalBrainWins++;
            consecutiveWins++;
            consecutiveLosses = 0;
            if (consecutiveWins > bestWinStreak) bestWinStreak = consecutiveWins;
        } else {
            consecutiveLosses++;
            consecutiveWins = 0;
            if (consecutiveLosses > worstLossStreak) worstLossStreak = consecutiveLosses;
        }
        totalBrainProfit += profit;
        
        // ===== REINFORCEMENT LEARNING UPDATE =====
        double reward = won ? 1.0 : -1.0;
        reward *= (1 + Math.abs(profit) / 100);  // Scale by profit size
        
        // Update currency score
        double oldCurrencyScore = currencyScores.getOrDefault(currency, 50.0);
        currencyScores.put(currency, oldCurrencyScore + learningRate * reward * 10);
        
        // Update hour score
        int hour = LocalTime.now().getHour();
        double oldHourScore = hourScores.getOrDefault(hour, 50.0);
        hourScores.put(hour, oldHourScore + learningRate * reward * 5);
        
        // Update regime score
        double oldRegimeScore = regimeScores.getOrDefault(currentRegime, 50.0);
        regimeScores.put(currentRegime, oldRegimeScore + learningRate * reward * 8);
        
        // Store in memory
        TradeMemory memory = new TradeMemory(
            currency, currentDecision, currentRegime, 
            decisionConfidence, won, profit, LocalDateTime.now()
        );
        tradeMemory.add(memory);
        if (tradeMemory.size() > MAX_MEMORY) {
            tradeMemory.remove(0);
        }
        
        // Self-improve
        selfImprove();
        
        // Report to Python for risk tracking (hybrid mode)
        if (pythonAvailable && pythonClient != null) {
            try {
                // Record for standard risk tracking
                pythonClient.recordTradeResult(won, profit, currency);
                
                // Also record for Multi-AI consensus weight adaptation
                List<String> aisUsed = Arrays.asList("fingpt", "python_ml", "java_brain");
                pythonClient.recordConsensusResult(won, profit, currency, aisUsed);
            } catch (Exception e) {
                // Python recording failed - not critical
            }
        }
        
        // Log result
        log((won ? "✅ WIN" : "❌ LOSS") + " | " + currency + " | Profit: $" + String.format("%.2f", profit) + 
            " | Streak: " + (won ? consecutiveWins + "W" : consecutiveLosses + "L"));
        
        // Save memory periodically
        if (totalBrainTrades % 10 == 0) {
            saveBrainMemory();
        }
    }
    
    // Simplified version for backward compatibility
    public void recordTradeResult(boolean won) {
        recordTradeResult(won, "UNKNOWN", won ? 10.0 : -10.0);
    }
    
    // ===== SELF-IMPROVEMENT =====
    
    private void selfImprove() {
        // Analyze recent performance
        if (tradeMemory.size() < 20) return;
        
        // Get last 20 trades
        List<TradeMemory> recent = tradeMemory.subList(
            Math.max(0, tradeMemory.size() - 20), tradeMemory.size()
        );
        
        // Calculate recent win rate
        long recentWins = recent.stream().filter(m -> m.won).count();
        double recentWinRate = (double) recentWins / recent.size();
        
        // Adjust learning rate based on performance
        if (recentWinRate < 0.4) {
            // Losing more - learn faster, be more careful
            learningRate = Math.min(0.2, learningRate * 1.1);
            minConfidenceToTrade = Math.min(85, minConfidenceToTrade + 1);
            log("🔧 Self-adjust: Increasing caution (win rate: " + (int)(recentWinRate*100) + "%)");
        } else if (recentWinRate > 0.6) {
            // Winning well - can be slightly more aggressive
            learningRate = Math.max(0.05, learningRate * 0.95);
            minConfidenceToTrade = Math.max(60, minConfidenceToTrade - 0.5);
            log("🔧 Self-adjust: Performance good (win rate: " + (int)(recentWinRate*100) + "%)");
        }
        
        // Find best performing currency
        Map<String, int[]> currencyStats = new HashMap<>();
        for (TradeMemory m : recent) {
            currencyStats.computeIfAbsent(m.currency, k -> new int[2]);
            currencyStats.get(m.currency)[0]++;
            if (m.won) currencyStats.get(m.currency)[1]++;
        }
        
        String bestCurrency = null;
        double bestRate = 0;
        for (Map.Entry<String, int[]> entry : currencyStats.entrySet()) {
            if (entry.getValue()[0] >= 3) {
                double rate = (double) entry.getValue()[1] / entry.getValue()[0];
                if (rate > bestRate) {
                    bestRate = rate;
                    bestCurrency = entry.getKey();
                }
            }
        }
        if (bestCurrency != null && bestRate > 0.6) {
            log("🌟 Best recent currency: " + bestCurrency + " (" + (int)(bestRate*100) + "% win rate)");
        }
    }
    
    // ===== PERSISTENCE =====
    
    public void saveBrainMemory() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(BRAIN_DATA_FILE))) {
            writer.println("# Brain Memory - " + LocalDateTime.now());
            writer.println("totalTrades=" + totalBrainTrades);
            writer.println("totalWins=" + totalBrainWins);
            writer.println("totalProfit=" + totalBrainProfit);
            writer.println("learningRate=" + learningRate);
            writer.println("minConfidence=" + minConfidenceToTrade);
            writer.println("bestWinStreak=" + bestWinStreak);
            writer.println("worstLossStreak=" + worstLossStreak);
            
            // Save currency scores
            for (Map.Entry<String, Double> entry : currencyScores.entrySet()) {
                writer.println("currency=" + entry.getKey() + "," + entry.getValue());
            }
            
            // Save hour scores
            for (Map.Entry<Integer, Double> entry : hourScores.entrySet()) {
                writer.println("hour=" + entry.getKey() + "," + entry.getValue());
            }
            
            // Save regime scores
            for (Map.Entry<MarketRegime, Double> entry : regimeScores.entrySet()) {
                writer.println("regime=" + entry.getKey().name() + "," + entry.getValue());
            }
            
            log("💾 Brain memory saved (" + totalBrainTrades + " trades learned)");
        } catch (IOException e) {
            System.err.println("Failed to save brain memory: " + e.getMessage());
        }
    }
    
    public void loadBrainMemory() {
        File file = new File(BRAIN_DATA_FILE);
        if (!file.exists()) {
            log("🆕 Starting with fresh brain memory");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) continue;
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                
                switch (parts[0]) {
                    case "totalTrades": totalBrainTrades = Integer.parseInt(parts[1]); break;
                    case "totalWins": totalBrainWins = Integer.parseInt(parts[1]); break;
                    case "totalProfit": totalBrainProfit = Double.parseDouble(parts[1]); break;
                    case "learningRate": learningRate = Double.parseDouble(parts[1]); break;
                    case "minConfidence": minConfidenceToTrade = Double.parseDouble(parts[1]); break;
                    case "bestWinStreak": bestWinStreak = Double.parseDouble(parts[1]); break;
                    case "worstLossStreak": worstLossStreak = Double.parseDouble(parts[1]); break;
                    case "currency":
                        String[] cparts = parts[1].split(",");
                        if (cparts.length == 2) {
                            currencyScores.put(cparts[0], Double.parseDouble(cparts[1]));
                        }
                        break;
                    case "hour":
                        String[] hparts = parts[1].split(",");
                        if (hparts.length == 2) {
                            hourScores.put(Integer.parseInt(hparts[0]), Double.parseDouble(hparts[1]));
                        }
                        break;
                    case "regime":
                        String[] rparts = parts[1].split(",");
                        if (rparts.length == 2) {
                            regimeScores.put(MarketRegime.valueOf(rparts[0]), Double.parseDouble(rparts[1]));
                        }
                        break;
                }
            }
            log("🧠 Loaded brain memory: " + totalBrainTrades + " trades, $" + String.format("%.2f", totalBrainProfit) + " profit");
        } catch (IOException e) {
            System.err.println("Failed to load brain memory: " + e.getMessage());
        }
    }
    
    // ===== MODE CONTROLS =====
    
    public void setAutoTradeMode(boolean enabled) {
        this.autoTradeMode = enabled;
        log("🤖 Auto-trade: " + (enabled ? "ON" : "OFF"));
    }
    
    public void setRecoveryMode(boolean enabled) {
        this.recoveryMode = enabled;
        log("🔄 Recovery mode: " + (enabled ? "ON" : "OFF"));
    }
    
    public void setMultiCurrencyMode(boolean enabled) {
        this.multiCurrencyMode = enabled;
        log("🔀 Multi-currency: " + (enabled ? "ON" : "OFF"));
    }
    
    // ===== STATUS METHODS =====
    
    public String getStatusMessage() {
        String emoji = "";
        switch (currentDecision) {
            case STRONG_BUY: emoji = "🟢🟢"; break;
            case BUY: emoji = "🟢"; break;
            case WAIT: emoji = "⏳"; break;
            case SELL: emoji = "🔴"; break;
            case STRONG_SELL: emoji = "🔴🔴"; break;
            case RECOVERY_BUY: case RECOVERY_SELL: emoji = "🔄"; break;
            case AVOID: emoji = "🚫"; break;
        }
        return String.format("%s %s (%.0f%%) | Size: %.0f%% | %s", 
            emoji, currentDecision, decisionConfidence, 
            recommendedSizeMultiplier * 100, currentRegime);
    }
    
    public String getBrainActivity() {
        StringBuilder sb = new StringBuilder();
        sb.append("🧠 ULTIMATE BRAIN STATUS\n");
        sb.append("══════════════════════\n");
        sb.append("Trades: " + totalBrainTrades + " (");
        sb.append(String.format("%.1f", totalBrainTrades > 0 ? (double)totalBrainWins/totalBrainTrades*100 : 0) + "% win)\n");
        sb.append("Profit: $" + String.format("%.2f", totalBrainProfit) + "\n");
        sb.append("Streaks: " + (int)bestWinStreak + "W / " + (int)worstLossStreak + "L\n");
        sb.append("Regime: " + currentRegime + "\n");
        sb.append("Drawdown: " + String.format("%.1f", currentDrawdown) + "%\n");
        sb.append("\n📋 Recent:\n");
        
        int start = Math.max(0, brainLog.size() - 5);
        for (int i = start; i < brainLog.size(); i++) {
            sb.append("• " + brainLog.get(i) + "\n");
        }
        
        return sb.toString();
    }
    
    private void log(String message) {
        String timestamp = LocalTime.now().toString().substring(0, 8);
        brainLog.add(timestamp + " " + message);
        if (brainLog.size() > MAX_LOG) brainLog.remove(0);
        System.out.println("🧠 " + message);
    }
    
    // ===== GETTERS =====
    
    public Decision getCurrentDecision() { return currentDecision; }
    public double getDecisionConfidence() { return decisionConfidence; }
    public String getDecisionReason() { return decisionReason; }
    public boolean shouldTrade() { return shouldTrade; }
    public double getRecommendedSizeMultiplier() { return recommendedSizeMultiplier; }
    public SignalType getRecommendedDirection() { return recommendedDirection; }
    public boolean isAutoTradeMode() { return autoTradeMode; }
    public boolean isRecoveryMode() { return recoveryMode; }
    public boolean isMultiCurrencyMode() { return multiCurrencyMode; }
    public double getCurrentDrawdown() { return currentDrawdown; }
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public int getConsecutiveWins() { return consecutiveWins; }
    public MarketRegime getCurrentRegime() { return currentRegime; }
    public int getTotalBrainTrades() { return totalBrainTrades; }
    public int getTotalBrainWins() { return totalBrainWins; }
    public double getTotalBrainProfit() { return totalBrainProfit; }
    public List<String> getBrainLog() { return new ArrayList<>(brainLog); }
    
    // ===== INNER CLASS FOR MEMORY =====
    
    private static class TradeMemory {
        String currency;
        Decision decision;
        MarketRegime regime;
        double confidence;
        boolean won;
        double profit;
        LocalDateTime time;
        
        TradeMemory(String currency, Decision decision, MarketRegime regime,
                   double confidence, boolean won, double profit, LocalDateTime time) {
            this.currency = currency;
            this.decision = decision;
            this.regime = regime;
            this.confidence = confidence;
            this.won = won;
            this.profit = profit;
            this.time = time;
        }
    }
    
    // ===== POCKET OPTION ACCESS =====
    
    /**
     * Get the Python analysis client for Pocket Option integration
     */
    public PythonAnalysisClient getPythonClient() {
        return pythonClient;
    }
}
