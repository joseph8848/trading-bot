package com.elitebot.learning;

import com.elitebot.model.Trade;
import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * SMART LOSS PREVENTION SYSTEM (v2.0)
 * 
 * Now includes:
 * 1. ANALYSIS BREAKS - Pause and LEARN from mistakes
 * 2. Adaptive confidence based on learned patterns
 * 3. Currency blacklisting
 * 4. Smart resume with applied lessons
 */
public class LossPreventionSystem {
    
    // The smart analyzer
    private final TradeAnalyzer analyzer = new TradeAnalyzer();
    
    // Streak tracking
    private int consecutiveLosses = 0;
    private int consecutiveWins = 0;
    private int maxConsecutiveLosses = 0;
    
    // ANALYSIS BREAK
    private boolean inAnalysisBreak = false;
    private LocalDateTime breakEndTime = null;
    private static final int BREAK_DURATION_SECONDS = 60;  // 1 minute analysis break
    private static final int LOSSES_BEFORE_BREAK = 5;
    
    // Confidence requirements (HIGHER than before)
    private static final double BASE_MIN_CONFIDENCE = 80.0;  // Raised from 60!
    
    // Drawdown tracking
    private double peakBalance = 0;
    private double currentDrawdown = 0;
    
    // Session stats
    private int sessionTrades = 0;
    private int sessionWins = 0;
    private int sessionLosses = 0;
    
    // Strategy mode
    public enum StrategyMode {
        CAREFUL,      // After analysis break - apply lessons
        NORMAL,       // Default mode
        CONSERVATIVE, // After some losses
        ULTRA_SAFE    // Many losses - very high requirements
    }
    
    private StrategyMode currentMode = StrategyMode.NORMAL;
    
    // Market condition
    public enum MarketCondition {
        TRENDING, RANGING, CHAOTIC, UNKNOWN
    }
    private MarketCondition currentMarketCondition = MarketCondition.UNKNOWN;
    
    /**
     * Check if trading is allowed
     * Now includes ANALYSIS BREAKS that learn from mistakes
     */
    public boolean canTrade(double currentBalance, double startingBalance) {
        // Track drawdown
        if (peakBalance == 0) peakBalance = startingBalance;
        if (currentBalance > peakBalance) peakBalance = currentBalance;
        currentDrawdown = (peakBalance - currentBalance) / peakBalance;
        
        // CHECK: Are we in an analysis break?
        if (inAnalysisBreak) {
            if (LocalDateTime.now().isAfter(breakEndTime)) {
                // Break is over - resume with lessons learned
                endAnalysisBreak();
                return true;
            } else {
                // Still in break - analyzing
                return false;
            }
        }
        
        // Update strategy mode
        updateStrategyMode();
        
        return true;
    }
    
    /**
     * Get time remaining in break (for UI)
     */
    public int getBreakSecondsRemaining() {
        if (!inAnalysisBreak || breakEndTime == null) return 0;
        long remaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), breakEndTime);
        return (int) Math.max(0, remaining);
    }
    
    /**
     * Start an analysis break - triggered after consecutive losses
     */
    private void startAnalysisBreak() {
        inAnalysisBreak = true;
        breakEndTime = LocalDateTime.now().plusSeconds(BREAK_DURATION_SECONDS);
        
        // SMART ANALYSIS: Learn from the losses
        TradeAnalyzer.AnalysisResult result = analyzer.analyzeRecentLosses(consecutiveLosses);
        
        System.out.println("\n" + "═".repeat(50));
        System.out.println(result.report);
        System.out.println("═".repeat(50) + "\n");
        
        System.out.println("⏸️ ANALYSIS BREAK: Learning from " + consecutiveLosses + " losses...");
        System.out.println("   Resuming in " + BREAK_DURATION_SECONDS + " seconds with lessons applied");
    }
    
    /**
     * End the analysis break and apply lessons
     */
    private void endAnalysisBreak() {
        inAnalysisBreak = false;
        breakEndTime = null;
        consecutiveLosses = 0;  // Reset streak
        currentMode = StrategyMode.CAREFUL;  // Apply lessons mode
        
        System.out.println("✅ Analysis break complete! Applied " + 
            analyzer.getLearnedMistakes().size() + " lessons");
        System.out.println("🔄 Resuming trading with smarter strategy...\n");
    }
    
    /**
     * Update strategy mode based on performance
     */
    private void updateStrategyMode() {
        if (consecutiveLosses >= 5 || currentDrawdown > 0.25) {
            currentMode = StrategyMode.ULTRA_SAFE;
            currentMarketCondition = MarketCondition.CHAOTIC;
        } else if (consecutiveLosses >= 3 || currentDrawdown > 0.15) {
            currentMode = StrategyMode.CONSERVATIVE;
            currentMarketCondition = MarketCondition.RANGING;
        } else if (consecutiveWins >= 2) {
            currentMarketCondition = MarketCondition.TRENDING;
            // Stay in current mode, don't get aggressive after analysis
            if (currentMode != StrategyMode.CAREFUL) {
                currentMode = StrategyMode.NORMAL;
            }
        } else {
            if (currentMode != StrategyMode.CAREFUL) {
                currentMode = StrategyMode.NORMAL;
            }
        }
    }
    
    /**
     * Get minimum confidence - NOW SMARTER
     * Uses learned minimum from analyzer + mode adjustments
     */
    public double getMinimumConfidence() {
        double base = Math.max(BASE_MIN_CONFIDENCE, analyzer.getLearnedMinConfidence());
        
        switch (currentMode) {
            case CAREFUL:
                return base + 5;   // Extra careful after lessons
            case NORMAL:
                return base;
            case CONSERVATIVE:
                return base + 10;
            case ULTRA_SAFE:
                return Math.min(95, base + 15);
            default:
                return base;
        }
    }
    
    /**
     * Get trade size multiplier
     */
    public double getTradeSizeMultiplier() {
        switch (currentMode) {
            case CAREFUL:
                return 0.5;   // Half size after lessons
            case NORMAL:
                return 1.0;
            case CONSERVATIVE:
                return 0.5;
            case ULTRA_SAFE:
                return 0.25;
            default:
                return 1.0;
        }
    }
    
    /**
     * Check if currency is allowed (uses analyzer blacklist)
     */
    public boolean isCurrencyAllowed(String currency) {
        return !analyzer.isCurrencyBlacklisted(currency);
    }
    
    /**
     * Check if current hour is good for trading
     */
    public boolean isGoodTradingTime() {
        return !analyzer.isBadHour();
    }
    
    /**
     * Record trade result - triggers analysis break if needed
     */
    public void recordTradeResult(Trade trade) {
        if (trade == null || trade.isPending()) return;
        
        // Record in analyzer for smart analysis
        analyzer.recordTrade(trade);
        
        sessionTrades++;
        
        if (trade.isWon()) {
            sessionWins++;
            consecutiveWins++;
            consecutiveLosses = 0;
            
            // Rehabilitate currency after wins
            if (consecutiveWins >= 3) {
                analyzer.rehabilitateCurrency(trade.getCurrency());
            }
            
            // Exit careful mode after consistent wins
            if (consecutiveWins >= 2 && currentMode == StrategyMode.CAREFUL) {
                currentMode = StrategyMode.NORMAL;
            }
        } else {
            sessionLosses++;
            consecutiveLosses++;
            consecutiveWins = 0;
            
            if (consecutiveLosses > maxConsecutiveLosses) {
                maxConsecutiveLosses = consecutiveLosses;
            }
            
            // TRIGGER ANALYSIS BREAK after too many losses
            if (consecutiveLosses >= LOSSES_BEFORE_BREAK && !inAnalysisBreak) {
                startAnalysisBreak();
            }
        }
        
        updateStrategyMode();
    }
    
    /**
     * Get status message for UI
     */
    public String getStatusMessage() {
        if (inAnalysisBreak) {
            return "🧠 ANALYZING... (" + getBreakSecondsRemaining() + "s remaining)";
        }
        
        String modeEmoji = "";
        switch (currentMode) {
            case CAREFUL: modeEmoji = "🎓"; break;  // Learned lessons
            case NORMAL: modeEmoji = "✅"; break;
            case CONSERVATIVE: modeEmoji = "⚠️"; break;
            case ULTRA_SAFE: modeEmoji = "🛡️"; break;
        }
        
        String marketInfo = "";
        switch (currentMarketCondition) {
            case TRENDING: marketInfo = "Trending"; break;
            case RANGING: marketInfo = "Ranging"; break;
            case CHAOTIC: marketInfo = "Chaotic"; break;
            default: marketInfo = "Analyzing"; break;
        }
        
        return String.format("%s %s | Market: %s | Conf: %.0f%%+ | Size: %.0f%%",
            modeEmoji, currentMode, marketInfo, 
            getMinimumConfidence(), getTradeSizeMultiplier() * 100);
    }
    
    /**
     * Get learned insights from analyzer
     */
    public String getLearnedInsights() {
        StringBuilder sb = new StringBuilder();
        sb.append("🧠 LEARNED PATTERNS:\n");
        
        Set<String> blacklisted = analyzer.getBlacklistedCurrencies();
        if (!blacklisted.isEmpty()) {
            sb.append("❌ Blacklisted: ").append(String.join(", ", blacklisted)).append("\n");
        }
        
        for (String lesson : analyzer.getLearnedMistakes()) {
            sb.append("• ").append(lesson).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get session statistics
     */
    public String getSessionStats() {
        double winRate = sessionTrades > 0 ? (double) sessionWins / sessionTrades * 100 : 0;
        return String.format("Session: %d trades, %.1f%% win rate, Max streak: %d losses",
            sessionTrades, winRate, maxConsecutiveLosses);
    }
    
    // Getters
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public int getConsecutiveWins() { return consecutiveWins; }
    public double getCurrentDrawdown() { return currentDrawdown * 100; }
    public boolean isPaused() { return inAnalysisBreak; }
    public boolean isInAnalysisBreak() { return inAnalysisBreak; }
    public StrategyMode getCurrentMode() { return currentMode; }
    public MarketCondition getCurrentMarketCondition() { return currentMarketCondition; }
    public TradeAnalyzer getAnalyzer() { return analyzer; }
}
